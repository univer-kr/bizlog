package kr.univer.bizlog.service

import ai.pleos.playground.vehicle.Vehicle
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kr.univer.bizlog.data.AppDatabase
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.round1
import kr.univer.bizlog.domain.trip.TripAction
import kr.univer.bizlog.domain.trip.TripEvent
import kr.univer.bizlog.domain.trip.TripStateMachine
import kr.univer.bizlog.gateway.VehicleGateway
import kr.univer.bizlog.gateway.fake.FakeVehicleGateway
import kr.univer.bizlog.gateway.pleos.PleosVehicleGateway
import kr.univer.bizlog.gateway.pleos.PleosVoiceGateway
import kr.univer.bizlog.gateway.VoiceGateway
import kr.univer.bizlog.domain.classify.categoryFromAnswer

private const val TAG = "BizLog"

/** 정차 직후 목적 질문의 진행 상태. */
data class PurposeAsk(
    val trip: LedgerRecord,
    val phase: Phase,
    val answer: String? = null,
    val category: Category? = null,
) { enum class Phase { ASKING, LISTENING, RECORDED, NOT_HEARD } }

/** 화면이 구독하는 실시간 상태. 서비스가 유일한 작성자. */
data class LiveState(
    val odometerKm: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val parkingBrakeOn: Boolean = false,
    val tripStartOdoKm: Double? = null,
    val tripStartedAt: Instant? = null,
    val ask: PurposeAsk? = null,
)

/** 포그라운드 기록 서비스: 차량 신호 → 상태머신 → 장부 append. 화면이 꺼져도 기록이 이어진다. */
class RecordingService : Service() {
    companion object {
        private val _state = MutableStateFlow(LiveState())
        val state: StateFlow<LiveState> = _state

        fun start(context: Context) = context.startForegroundService(Intent(context, RecordingService::class.java))

        /** 화면에서 버튼으로 고르거나 닫았을 때. */
        fun dismissAsk() = _state.update { it.copy(ask = null) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vehicle: Vehicle? = null
    private var voice: VoiceGateway? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification())
        if (!started) {
            started = true
            scope.launch { record(connectVehicle()) }
        }
        return START_STICKY
    }

    private fun connectVehicle(): VehicleGateway = try {
        PleosVehicleGateway(Vehicle(applicationContext).apply { initialize() }.also { vehicle = it })
    } catch (e: Exception) {
        Log.e(TAG, "Vehicle SDK 연결 실패 → 신호 없이 동작", e)
        FakeVehicleGateway()
    }

    private suspend fun record(gateway: VehicleGateway) {
        val repo = LedgerRepository(AppDatabase.get(this))
        val settings = AppSettings(this)
        val machine = TripStateMachine()
        val vin = gateway.vin()
        val ticks = flow { while (true) { delay(30_000); emit(TripEvent.Tick(Instant.now())) } }
        merge(
            gateway.odometerKm.map { TripEvent.OdometerChanged(round1(it.toDouble()), Instant.now()) },
            gateway.gear.map { TripEvent.GearChanged(it, Instant.now()) },
            gateway.parkingBrakeOn.map { TripEvent.ParkingBrake(it, Instant.now()) },
            ticks,
        ).collect { event -> // 한 줄로 순서대로 처리 → 상태머신은 동시 접근이 없다
            when (event) {
                is TripEvent.OdometerChanged -> _state.update { it.copy(odometerKm = event.km) }
                is TripEvent.GearChanged -> _state.update { it.copy(gear = event.gear) }
                is TripEvent.ParkingBrake -> _state.update { it.copy(parkingBrakeOn = event.on) }
                is TripEvent.Tick -> Unit
            }
            for (action in machine.handle(event)) {
                Log.i(TAG, "$event → $action")
                when (action) {
                    is TripAction.StartTrip -> _state.update { it.copy(tripStartOdoKm = action.startOdoKm, tripStartedAt = action.at, ask = null) }
                    is TripAction.FinishTrip -> {
                        _state.update { it.copy(tripStartOdoKm = null, tripStartedAt = null) }
                        val saved = repo.appendTrip(action.toRecord(vin, settings))
                        saved.forEach { Log.i(TAG, "장부 #${it.seq} ${it.kind} ${it.startOdoKm}→${it.endOdoKm} (${it.distanceKm} km) ${it.hash.take(8)}") }
                        val trip = saved.last()
                        if (trip.category == Category.UNKNOWN) scope.launch { askPurpose(repo, trip) }
                    }
                    TripAction.DiscardTrip -> _state.update { it.copy(tripStartOdoKm = null, tripStartedAt = null) }
                }
            }
        }
    }

    /**
     * 정차 직후 한 번 묻고 한마디로 끝낸다. 답을 못 들으면 화면이 버튼으로 대신한다.
     * shortcut: 내비 목적지 연동 전이라 미분류 트립마다 묻는다. NaviHelper(P1-4) 뒤엔 처음 방문지에서만 묻는다.
     */
    private suspend fun askPurpose(repo: LedgerRepository, trip: LedgerRecord) {
        val v = voice ?: PleosVoiceGateway(applicationContext) { AppSettings(this).demoVoice }.also { voice = it }
        fun set(ask: PurposeAsk?) = _state.update { if (it.tripStartedAt == null) it.copy(ask = ask) else it }
        set(PurposeAsk(trip, PurposeAsk.Phase.ASKING))
        v.speak("도착했어요. 이번 운행은 어떤 업무였나요?")
        if (_state.value.ask?.trip?.seq != trip.seq) return // 그새 버튼으로 골랐거나 다시 출발
        set(PurposeAsk(trip, PurposeAsk.Phase.LISTENING))
        val answer = v.listenOnce()
        if (_state.value.ask?.trip?.seq != trip.seq) return
        if (answer == null) { set(PurposeAsk(trip, PurposeAsk.Phase.NOT_HEARD)); return }
        val category = categoryFromAnswer(answer)
        repo.appendCorrection(trip, category = category, purpose = answer)
        set(PurposeAsk(trip, PurposeAsk.Phase.RECORDED, answer, category))
        v.speak("${category.spoken()}로 기록했어요")
    }

    private fun Category.spoken() = when (this) {
        Category.COMMUTE -> "출퇴근"; Category.BUSINESS -> "일반 업무"; Category.PERSONAL -> "비업무"; Category.UNKNOWN -> "미분류"
    }

    // shortcut: 사용자 = 설정의 프로필 1명. 시트 위치로 프로필을 고르는 건 P1.
    private fun TripAction.FinishTrip.toRecord(vin: String?, settings: AppSettings) = LedgerRecord(
        seq = 0, id = UUID.randomUUID().toString(), kind = RecordKind.TRIP,
        date = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
        startedAt = startedAt, endedAt = endedAt, startOdoKm = startOdoKm, endOdoKm = endOdoKm,
        category = Category.UNKNOWN, vehicleVin = vin,
        driverId = settings.driverName.ifBlank { null }, driverName = settings.driverName.ifBlank { null },
        driverDept = settings.driverDept.ifBlank { null },
    )

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("recording", "운행 기록", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "recording")
            .setContentTitle("비즈로그")
            .setContentText("계기판 거리로 운행을 기록하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() } // 구독 해제 끝난 뒤
        vehicle?.release()
        voice?.release()
        super.onDestroy()
    }
}

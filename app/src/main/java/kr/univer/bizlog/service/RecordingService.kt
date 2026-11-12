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

private const val TAG = "BizLog"

/** 화면이 구독하는 실시간 상태. 서비스가 유일한 작성자. */
data class LiveState(
    val odometerKm: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val parkingBrakeOn: Boolean = false,
    val tripStartOdoKm: Double? = null,
    val tripStartedAt: Instant? = null,
)

/** 포그라운드 기록 서비스: 차량 신호 → 상태머신 → 장부 append. 화면이 꺼져도 기록이 이어진다. */
class RecordingService : Service() {
    companion object {
        private val _state = MutableStateFlow(LiveState())
        val state: StateFlow<LiveState> = _state

        fun start(context: Context) = context.startForegroundService(Intent(context, RecordingService::class.java))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vehicle: Vehicle? = null
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
                    is TripAction.StartTrip -> _state.update { it.copy(tripStartOdoKm = action.startOdoKm, tripStartedAt = action.at) }
                    is TripAction.FinishTrip -> {
                        _state.update { it.copy(tripStartOdoKm = null, tripStartedAt = null) }
                        val saved = repo.appendTrip(action.toRecord(vin))
                        saved.forEach { Log.i(TAG, "장부 #${it.seq} ${it.kind} ${it.startOdoKm}→${it.endOdoKm} (${it.distanceKm} km) ${it.hash.take(8)}") }
                    }
                    TripAction.DiscardTrip -> _state.update { it.copy(tripStartOdoKm = null, tripStartedAt = null) }
                }
            }
        }
    }

    // shortcut: 사용자·구분은 아직 비워 둠. 시트 프로필(P1)·장부 칩 지정(P0-5) 단계에서 채운다.
    private fun TripAction.FinishTrip.toRecord(vin: String?) = LedgerRecord(
        seq = 0, id = UUID.randomUUID().toString(), kind = RecordKind.TRIP,
        date = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
        startedAt = startedAt, endedAt = endedAt, startOdoKm = startOdoKm, endOdoKm = endOdoKm,
        category = Category.UNKNOWN, vehicleVin = vin,
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
        super.onDestroy()
    }
}

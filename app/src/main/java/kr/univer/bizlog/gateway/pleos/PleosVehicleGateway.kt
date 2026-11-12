package kr.univer.bizlog.gateway.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.gateway.VehicleGateway

private const val TAG = "BizLog"

/** Pleos Vehicle SDK 구현. Capability 확인 → 현재값 Get → Callback 구독 순서. */
class PleosVehicleGateway(private val vehicle: Vehicle) : VehicleGateway {

    override val odometerKm: Flow<Float> = callbackFlow {
        val odometer = vehicle.getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(km: Float?) { km?.let { trySend(it) } }
            override fun onFailed(e: Exception) { Log.w(TAG, "ODO 구독 실패", e) }
        }
        odometer.checkCurrentDistanceDrivenCapabilities({ config ->
            Log.i(TAG, "ODO capability=${config.accessType}")
            if (config.accessType == AccessType.NONE) return@checkCurrentDistanceDrivenCapabilities
            // 현재값을 먼저 받고 나서 구독한다. 구독 직후 SDK가 이전 값을 한 번 더 보내기 때문(실측 2026-11-12).
            odometer.getCurrentDistanceDriven({ km ->
                km?.let { trySend(it) }
                odometer.registerDistanceDriven(listener)
            }, {
                Log.w(TAG, "ODO 조회 실패", it)
                odometer.registerDistanceDriven(listener)
            })
        }, { Log.w(TAG, "ODO capability 실패 → 기능 끔", it) })
        awaitClose { odometer.unregisterDistanceDriven(listener) }
    }
        // shortcut: 실행 중엔 줄어드는 값을 무시(위 재전송 대비). 계기판 교체 판정은 장부 체인이 앱 재시작 때 한다.
        .runningReduce { max, km -> maxOf(max, km) }
        .distinctUntilChanged()

    override val gear: Flow<Gear> = callbackFlow {
        val gearApi = vehicle.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(g: VehicleGear?) { trySend(g.toGear()) }
            override fun onFailed(e: Exception) { Log.w(TAG, "기어 구독 실패", e) }
        }
        gearApi.getCurrentGearSelectionState({
            trySend(it.toGear())
            gearApi.registerGearSelection(listener)
        }, {
            Log.w(TAG, "기어 조회 실패", it)
            gearApi.registerGearSelection(listener)
        })
        awaitClose { gearApi.unregisterGearSelection(listener) }
    }.distinctUntilChanged() // SDK가 같은 값을 주기적으로 다시 보냄

    /** 주차 브레이크 = Safety 도메인 경고등(PARKING_BRAKE / ELECTRONIC_PARKING_BRAKE). */
    override val parkingBrakeOn: Flow<Boolean> = callbackFlow {
        val safety = vehicle.getSafety()
        fun Telltales?.parkingOn(): Boolean = this?.telltales.orEmpty().any {
            (it.type == TelltaleType.PARKING_BRAKE || it.type == TelltaleType.ELECTRONIC_PARKING_BRAKE) && it.state != TelltaleState.OFF
        }
        val listener = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(t: Telltales?) { trySend(t.parkingOn()) }
            override fun onFailed(e: Exception) { Log.w(TAG, "경고등 구독 실패", e) }
        }
        safety.checkTelltalesCapabilities({ config ->
            Log.i(TAG, "Telltale capability=${config.accessType}")
            if (config.accessType == AccessType.NONE) return@checkTelltalesCapabilities
            safety.getTelltalesInfo({ trySend(it.parkingOn()) }, { Log.w(TAG, "경고등 조회 실패", it) })
            safety.registerTelltales(listener)
        }, { Log.w(TAG, "Telltale capability 실패 → 기능 끔", it) })
        awaitClose { safety.unregisterTelltales(listener) }
    }.distinctUntilChanged()

    override suspend fun vin(): String? = suspendCancellableCoroutine { cont ->
        val carInfo = vehicle.getCarInfo()
        carInfo.checkVINInfoCapabilities({ config ->
            if (config.accessType == AccessType.NONE) cont.resume(null)
            else carInfo.getVINInfo({ cont.resume(it) }, { Log.w(TAG, "VIN 조회 실패", it); cont.resume(null) })
        }, { cont.resume(null) })
    }
}

private fun VehicleGear?.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    else -> Gear.D // DRIVE, 수동 1~9단은 모두 주행
}

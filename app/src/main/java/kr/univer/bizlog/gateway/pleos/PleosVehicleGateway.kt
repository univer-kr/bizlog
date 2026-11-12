package kr.univer.bizlog.gateway.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
            odometer.getCurrentDistanceDriven({ km -> km?.let { trySend(it) } }, { Log.w(TAG, "ODO 조회 실패", it) })
            odometer.registerDistanceDriven(listener)
        }, { Log.w(TAG, "ODO capability 실패 → 기능 끔", it) })
        awaitClose { odometer.unregisterDistanceDriven(listener) }
    }.distinctUntilChanged() // SDK가 같은 값을 주기적으로 다시 보냄

    override val gear: Flow<Gear> = callbackFlow {
        val gearApi = vehicle.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(g: VehicleGear?) { trySend(g.toGear()) }
            override fun onFailed(e: Exception) { Log.w(TAG, "기어 구독 실패", e) }
        }
        gearApi.getCurrentGearSelectionState({ trySend(it.toGear()) }, { Log.w(TAG, "기어 조회 실패", it) })
        gearApi.registerGearSelection(listener)
        awaitClose { gearApi.unregisterGearSelection(listener) }
    }.distinctUntilChanged() // SDK가 같은 값을 주기적으로 다시 보냄
}

private fun VehicleGear?.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    else -> Gear.D // DRIVE, 수동 1~9단은 모두 주행
}

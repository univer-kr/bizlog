package kr.univer.bizlog.gateway.pleos

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** AAOS 주행 규제. true = 주행 중 UI(상태 카드)만 허용. 지원 안 되면 아무 값도 내보내지 않는다. */
fun uxRestricted(context: Context): Flow<Boolean> = callbackFlow {
    val car = try { Car.createCar(context) } catch (e: Exception) { null }
    val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
    if (manager == null) {
        Log.w("BizLog", "CarUxRestrictions 미지원 → 기어로만 주행 판단")
        awaitClose { car?.disconnect() }
        return@callbackFlow
    }
    trySend(manager.currentCarUxRestrictions.isRequiresDistractionOptimization)
    manager.registerListener { trySend(it.isRequiresDistractionOptimization) }
    awaitClose {
        manager.unregisterListener()
        car.disconnect()
    }
}

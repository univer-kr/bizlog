package kr.univer.bizlog.gateway

import kotlinx.coroutines.flow.Flow
import kr.univer.bizlog.domain.model.Gear

/** 차량 신호의 경계. 지원하지 않는 신호는 값을 내보내지 않는다(앱은 계속 동작). */
interface VehicleGateway {
    /** 계기판 누적 주행거리(km). 현재값 1회 후 바뀔 때마다. */
    val odometerKm: Flow<Float>
    val gear: Flow<Gear>
    val parkingBrakeOn: Flow<Boolean>
    suspend fun vin(): String?
}

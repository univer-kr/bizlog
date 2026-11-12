package kr.univer.bizlog.gateway

import kotlinx.coroutines.flow.Flow
import kr.univer.bizlog.domain.model.Gear

/** 차량 신호의 경계. 지원하지 않는 신호는 값을 내보내지 않는다(폴백은 호출 측). */
interface VehicleGateway {
    /** 계기판 누적 주행거리(km). 현재값 1회 후 변경될 때마다. */
    val odometerKm: Flow<Float>
    val gear: Flow<Gear>
}

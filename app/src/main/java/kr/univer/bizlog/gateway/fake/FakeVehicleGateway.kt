package kr.univer.bizlog.gateway.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.gateway.VehicleGateway

/** 테스트용, 그리고 Vehicle SDK 연결 실패 시 폴백. 기본값은 "아무 신호도 지원 안 함". */
class FakeVehicleGateway(
    override val odometerKm: Flow<Float> = emptyFlow(),
    override val gear: Flow<Gear> = emptyFlow(),
    override val parkingBrakeOn: Flow<Boolean> = emptyFlow(),
    private val vin: String? = null,
) : VehicleGateway {
    override suspend fun vin(): String? = vin
}

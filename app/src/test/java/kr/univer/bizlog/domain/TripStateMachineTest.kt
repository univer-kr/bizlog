package kr.univer.bizlog.domain

import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.domain.trip.TripAction.DiscardTrip
import kr.univer.bizlog.domain.trip.TripAction.FinishTrip
import kr.univer.bizlog.domain.trip.TripAction.StartTrip
import kr.univer.bizlog.domain.trip.TripEvent.GearChanged
import kr.univer.bizlog.domain.trip.TripEvent.OdometerChanged
import kr.univer.bizlog.domain.trip.TripEvent.ParkingBrake
import kr.univer.bizlog.domain.trip.TripEvent.Tick
import kr.univer.bizlog.domain.trip.TripStateMachine
import org.junit.Assert.assertEquals
import org.junit.Test

class TripStateMachineTest {
    private val sm = TripStateMachine()

    @Test fun `U1 P→D, ODO 100_0→112_5, D→P 이면 트립 12_5km`() {
        sm.handle(OdometerChanged(100.0, t(0)))
        assertEquals(listOf(StartTrip(100.0, t(1))), sm.handle(GearChanged(Gear.D, t(1))))
        sm.handle(OdometerChanged(112.5, t(10)))
        val out = sm.handle(GearChanged(Gear.P, t(11)))
        assertEquals(listOf(FinishTrip(100.0, 112.5, t(1), t(11))), out)
        assertEquals(12.5, (out[0] as FinishTrip).distanceKm, 0.0)
    }

    @Test fun `U2 ODO 변화 없이 P 이면 트립 버림`() {
        sm.handle(OdometerChanged(100.0, t(0)))
        sm.handle(GearChanged(Gear.D, t(1)))
        assertEquals(listOf(DiscardTrip), sm.handle(GearChanged(Gear.P, t(2))))
    }

    @Test fun `U3 D 상태에서 ODO가 5분 정지하면 종료`() {
        sm.handle(OdometerChanged(100.0, t(0)))
        sm.handle(GearChanged(Gear.D, t(0)))
        sm.handle(OdometerChanged(105.0, t(1)))
        assertEquals(emptyList<Any>(), sm.handle(Tick(t(5))))
        assertEquals(listOf(FinishTrip(100.0, 105.0, t(0), t(6))), sm.handle(Tick(t(6))))
    }

    @Test fun `U4 기어 신호 없이 ODO 증가만으로 트립 시작`() {
        sm.handle(OdometerChanged(100.0, t(0)))
        assertEquals(listOf(StartTrip(100.0, t(1))), sm.handle(OdometerChanged(101.0, t(1))))
    }

    @Test fun `기어 P가 확인된 상태의 ODO 변화는 트립이 아니다 (시연 준비 중 값 맞추기)`() {
        sm.handle(GearChanged(Gear.P, t(0)))
        sm.handle(OdometerChanged(0.0, t(0)))
        assertEquals(emptyList<Any>(), sm.handle(OdometerChanged(48360.0, t(1))))
        assertEquals(listOf(StartTrip(48360.0, t(2))), sm.handle(GearChanged(Gear.D, t(2))))
    }

    @Test fun `주차 브레이크 체결로도 종료`() {
        sm.handle(OdometerChanged(100.0, t(0)))
        sm.handle(GearChanged(Gear.D, t(0)))
        sm.handle(OdometerChanged(103.0, t(2)))
        assertEquals(listOf(FinishTrip(100.0, 103.0, t(0), t(3))), sm.handle(ParkingBrake(true, t(3))))
    }

    @Test fun `ODO를 모르는 채 D 이면 첫 ODO 값으로 시작`() {
        assertEquals(emptyList<Any>(), sm.handle(GearChanged(Gear.D, t(0))))
        assertEquals(listOf(StartTrip(100.0, t(0))), sm.handle(OdometerChanged(100.0, t(1))))
    }
}

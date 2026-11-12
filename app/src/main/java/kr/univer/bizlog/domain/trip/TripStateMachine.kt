package kr.univer.bizlog.domain.trip

import java.time.Duration
import java.time.Instant
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.domain.model.round1

sealed interface TripEvent {
    val at: Instant
    data class OdometerChanged(val km: Double, override val at: Instant) : TripEvent
    data class GearChanged(val gear: Gear, override val at: Instant) : TripEvent
    data class ParkingBrake(val on: Boolean, override val at: Instant) : TripEvent
    data class Tick(override val at: Instant) : TripEvent
}

sealed interface TripAction {
    data class StartTrip(val startOdoKm: Double, val at: Instant) : TripAction
    data class FinishTrip(val startOdoKm: Double, val endOdoKm: Double, val startedAt: Instant, val endedAt: Instant) : TripAction {
        val distanceKm: Double get() = round1(endOdoKm - startOdoKm)
    }
    /** 0 km 트립. 저장하지 않는다. */
    data object DiscardTrip : TripAction
}

/**
 * IDLE ⇄ DRIVING. 시작: 기어 D/R 또는 ODO 증가. 종료: 기어 P, 주차 브레이크, ODO가 idleTimeout 동안 정지.
 * 시각은 이벤트로만 받는다(결정적).
 */
class TripStateMachine(private val idleTimeout: Duration = Duration.ofMinutes(5)) {
    private class Active(var startOdo: Double?, val startedAt: Instant, var lastMoveAt: Instant)

    private var lastOdo: Double? = null
    private var trip: Active? = null

    val isDriving: Boolean get() = trip != null

    fun handle(e: TripEvent): List<TripAction> {
        val active = trip
        return when (e) {
            is TripEvent.OdometerChanged -> {
                val prev = lastOdo
                lastOdo = e.km
                when {
                    active == null && prev != null && e.km > prev -> start(prev, e.at)
                    active == null -> emptyList()
                    active.startOdo == null -> { // 기어 D가 ODO보다 먼저 온 경우
                        active.startOdo = e.km
                        listOf(TripAction.StartTrip(e.km, active.startedAt))
                    }
                    else -> {
                        if (e.km != prev) active.lastMoveAt = e.at
                        emptyList()
                    }
                }
            }
            is TripEvent.GearChanged -> when {
                active == null && (e.gear == Gear.D || e.gear == Gear.R) -> start(lastOdo, e.at)
                active != null && e.gear == Gear.P -> finish(e.at)
                else -> emptyList()
            }
            is TripEvent.ParkingBrake -> if (active != null && e.on) finish(e.at) else emptyList()
            is TripEvent.Tick ->
                if (active != null && Duration.between(active.lastMoveAt, e.at) >= idleTimeout) finish(e.at) else emptyList()
        }
    }

    private fun start(startOdo: Double?, at: Instant): List<TripAction> {
        trip = Active(startOdo, at, at)
        return if (startOdo == null) emptyList() else listOf(TripAction.StartTrip(startOdo, at))
    }

    private fun finish(at: Instant): List<TripAction> {
        val active = trip ?: return emptyList()
        trip = null
        val start = active.startOdo
        val end = lastOdo
        if (start == null || end == null || round1(end - start) <= 0.0) return listOf(TripAction.DiscardTrip)
        return listOf(TripAction.FinishTrip(start, end, active.startedAt, at))
    }
}

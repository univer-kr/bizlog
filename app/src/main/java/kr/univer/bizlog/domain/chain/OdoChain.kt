package kr.univer.bizlog.domain.chain

import java.security.MessageDigest
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.fmt1

sealed interface Continuity {
    data object Continuous : Continuity
    /** 앱 밖에서 움직인 구간. GAP 레코드로 남긴다. */
    data class Gap(val fromOdoKm: Double, val toOdoKm: Double) : Continuity
    /** ODO가 줄었다(계기판 교체 등). GAP 대신 비고에 플래그. */
    data object Discontinuity : Continuity
}

sealed interface ChainStatus {
    data object Ok : ChainStatus
    data class BrokenAt(val seq: Long) : ChainStatus
    data class GapCount(val count: Int) : ChainStatus
}

object OdoChain {
    const val GENESIS = "GENESIS"
    const val DISCONTINUITY_NOTE = "ODO 불연속"
    private const val MIN_GAP_KM = 0.1
    private const val EPS = 1e-9

    fun continuity(prevEndOdoKm: Double?, newStartOdoKm: Double): Continuity {
        if (prevEndOdoKm == null) return Continuity.Continuous
        val diff = newStartOdoKm - prevEndOdoKm
        return when {
            diff >= MIN_GAP_KM - EPS -> Continuity.Gap(prevEndOdoKm, newStartOdoKm)
            diff <= -MIN_GAP_KM + EPS -> Continuity.Discontinuity
            else -> Continuity.Continuous
        }
    }

    /** 해시 규칙 v1 (_dev/docs/04 3절). 필드 순서를 바꾸면 기존 장부 검증이 깨진다. */
    fun canonical(r: LedgerRecord): String = listOf(
        "v1", r.seq, r.kind, r.date, r.startedAt, r.endedAt, r.driverId ?: "",
        fmt1(r.startOdoKm), fmt1(r.endOdoKm), r.category, r.purpose ?: "", r.placeName ?: "",
        r.passengers, r.correctsSeq ?: "", r.note ?: "", r.prevHash,
    ).joinToString("|")

    fun hash(r: LedgerRecord): String =
        MessageDigest.getInstance("SHA-256").digest(canonical(r).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 직전 레코드 뒤에 붙일 수 있게 seq·prevHash·hash를 채운다. */
    fun seal(prev: LedgerRecord?, draft: LedgerRecord): LedgerRecord {
        val linked = draft.copy(seq = (prev?.seq ?: 0) + 1, prevHash = prev?.hash ?: GENESIS)
        return linked.copy(hash = hash(linked))
    }

    /**
     * 새 TRIP을 붙일 레코드 목록. 직전 도착 ODO와 차이가 있으면 GAP을 먼저 만든다.
     * tail = 체인 마지막(정정 포함), lastOdo = 마지막 TRIP·GAP.
     */
    fun appendTrip(tail: LedgerRecord?, lastOdo: LedgerRecord?, trip: LedgerRecord): List<LedgerRecord> {
        val c = continuity(lastOdo?.endOdoKm, trip.startOdoKm)
        val gap = if (c is Continuity.Gap && lastOdo != null) seal(
            tail,
            trip.copy(
                id = trip.id + "-gap", kind = RecordKind.GAP, category = Category.UNKNOWN,
                startedAt = lastOdo.endedAt, endedAt = trip.startedAt,
                startOdoKm = c.fromOdoKm, endOdoKm = c.toOdoKm,
                driverId = null, driverName = null, driverDept = null,
                purpose = null, placeName = null, passengers = 0, note = null,
            ),
        ) else null
        val t = if (c is Continuity.Discontinuity) trip.copy(note = DISCONTINUITY_NOTE) else trip
        return listOfNotNull(gap) + seal(gap ?: tail, t)
    }

    /** 해시 재계산 + prevHash 연결 + ODO 연속성. 처음 깨진 곳을 돌려준다. */
    fun verify(records: List<LedgerRecord>): ChainStatus {
        var prev: LedgerRecord? = null
        var prevOdoRecord: LedgerRecord? = null
        for (r in records.sortedBy { it.seq }) {
            if (r.prevHash != (prev?.hash ?: GENESIS) || r.hash != hash(r)) return ChainStatus.BrokenAt(r.seq)
            if (r.kind != RecordKind.CORRECTION) {
                if (continuity(prevOdoRecord?.endOdoKm, r.startOdoKm) is Continuity.Gap) return ChainStatus.BrokenAt(r.seq)
                prevOdoRecord = r
            }
            prev = r
        }
        val corrected = records.mapNotNull { it.correctsSeq }.toSet()
        val openGaps = records.count { it.kind == RecordKind.GAP && it.seq !in corrected }
        return if (openGaps == 0) ChainStatus.Ok else ChainStatus.GapCount(openGaps)
    }
}

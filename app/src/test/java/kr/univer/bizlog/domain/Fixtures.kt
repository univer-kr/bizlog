package kr.univer.bizlog.domain

import java.time.Instant
import java.time.LocalDate
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind

val T0: Instant = Instant.parse("2026-11-12T00:00:00Z")
fun t(minutes: Long): Instant = T0.plusSeconds(minutes * 60)

fun draft(
    start: Double, end: Double,
    category: Category = Category.BUSINESS,
    kind: RecordKind = RecordKind.TRIP,
    note: String? = null,
    date: LocalDate = LocalDate.of(2026, 11, 12),
) = LedgerRecord(
    seq = 0, id = "r$start", kind = kind, date = date,
    startedAt = T0, endedAt = t(30),
    driverId = if (kind == RecordKind.GAP) null else "A", driverName = if (kind == RecordKind.GAP) null else "사용자 A", driverDept = "영업 1팀",
    startOdoKm = start, endOdoKm = end, category = category, note = note,
)

/** 초안들을 순서대로 체인에 붙인다. */
fun chainOf(vararg drafts: LedgerRecord): List<LedgerRecord> =
    drafts.fold(emptyList()) { acc, d -> acc + OdoChain.seal(acc.lastOrNull(), d) }

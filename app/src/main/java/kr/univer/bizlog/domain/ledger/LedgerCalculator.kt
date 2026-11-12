package kr.univer.bizlog.domain.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.round1

/** 서식 ⑪ 총주행, ⑫ 업무용, ⑬ 업무사용비율(%) */
data class LedgerSummary(val totalKm: Double, val businessKm: Double, val businessRatioPercent: Double)

object LedgerCalculator {
    /** TRIP·GAP에 가장 최근 CORRECTION을 덮어 보여 줄 값. 원본 레코드는 바뀌지 않는다. */
    fun effective(records: List<LedgerRecord>): List<LedgerRecord> {
        val latestFix = records.filter { it.kind == RecordKind.CORRECTION }.sortedBy { it.seq }.associateBy { it.correctsSeq }
        return records.filter { it.kind != RecordKind.CORRECTION }.sortedBy { it.seq }.map { r ->
            val fix = latestFix[r.seq] ?: return@map r
            r.copy(category = fix.category, purpose = fix.purpose ?: r.purpose, note = fix.note ?: r.note)
        }
    }

    /** GAP은 총주행에 넣고, 사유로 업무가 정해지기 전까지 업무용에선 뺀다(보수적). */
    fun summarize(records: List<LedgerRecord>): LedgerSummary {
        val rows = effective(records)
        val total = round1(rows.sumOf { it.distanceKm })
        val business = round1(rows.filter { it.category == Category.COMMUTE || it.category == Category.BUSINESS }.sumOf { it.distanceKm })
        val ratio = if (total == 0.0) 0.0
        else BigDecimal.valueOf(business * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toDouble()
        return LedgerSummary(total, business, ratio)
    }
}

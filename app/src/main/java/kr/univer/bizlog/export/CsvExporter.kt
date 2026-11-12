package kr.univer.bizlog.export

import java.time.format.TextStyle
import java.util.Locale
import kr.univer.bizlog.domain.ledger.LedgerCalculator
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.fmt1

/** 서식 기본정보. 설정 화면에서 입력, 기본값 빈칸. */
data class CsvHeader(
    val year: Int,
    val companyName: String = "",
    val bizRegNo: String = "",
    val carModel: String = "",
    val plateNo: String = "",
)

/** 국세청 고시 별지 「업무용승용차 운행기록부」 항목 순서 CSV. UTF-8 BOM + CRLF(엑셀 호환). */
object CsvExporter {
    val BODY_COLUMNS = listOf(
        "③사용일자(요일)", "④사용자 부서", "④사용자 성명", "⑤주행 전 계기판의 거리(km)", "⑥주행 후 계기판의 거리(km)",
        "⑦주행거리(km)", "⑧출·퇴근용(km)", "⑨일반 업무용(km)", "⑩비고",
    )

    fun export(header: CsvHeader, records: List<LedgerRecord>): String {
        val year = records.filter { it.date.year == header.year }
        val rows = LedgerCalculator.effective(year)
        val sum = LedgerCalculator.summarize(year)
        val lines = buildList {
            add(listOf("업무용승용차 운행기록부"))
            add(listOf("사업연도", "법인명", "사업자등록번호", "①차종", "②자동차등록번호"))
            add(listOf("${header.year}", header.companyName, header.bizRegNo, header.carModel, header.plateNo))
            add(emptyList())
            add(BODY_COLUMNS)
            rows.forEach { add(row(it)) }
            add(emptyList())
            add(listOf("⑪사업연도 총주행 거리(km)", "⑫사업연도 업무용 사용거리(km)", "⑬업무사용비율"))
            add(listOf(fmt1(sum.totalKm), fmt1(sum.businessKm), "${fmt1(sum.businessRatioPercent)}%"))
        }
        return "﻿" + lines.joinToString("\r\n") { it.joinToString(",", transform = ::escape) } + "\r\n"
    }

    /** 운행기록부_<연도>_<차량번호 또는 VIN 끝 6자리>.csv */
    fun fileName(year: Int, plateNo: String?, vin: String?): String {
        val car = plateNo?.takeIf { it.isNotBlank() } ?: vin?.takeLast(6) ?: "차량"
        return "운행기록부_${year}_${car.replace(Regex("[^0-9A-Za-z가-힣_-]"), "")}.csv" // 파일명에 쓸 수 없는 문자 제거
    }

    private fun row(r: LedgerRecord): List<String> {
        val gap = r.kind == RecordKind.GAP
        val day = r.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        val notes = listOfNotNull(
            if (gap) (r.note?.let { "누락 구간(사유: $it)" } ?: "누락 구간") else r.note,
            r.purpose,
            r.passengers.takeIf { it > 0 }?.let { "동승 ${it}명" },
        )
        return listOf(
            "${r.date}($day)",
            r.driverDept.orEmpty().takeUnless { gap }.orEmpty(),
            if (gap) "미확인" else r.driverName.orEmpty(),
            fmt1(r.startOdoKm),
            fmt1(r.endOdoKm),
            fmt1(r.distanceKm),
            if (r.category == Category.COMMUTE) fmt1(r.distanceKm) else "",
            if (r.category == Category.BUSINESS) fmt1(r.distanceKm) else "",
            notes.joinToString(" / "),
        )
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

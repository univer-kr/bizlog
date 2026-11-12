package kr.univer.bizlog.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

enum class RecordKind { TRIP, GAP, CORRECTION }

/** 서식 ⑧ 출퇴근 / ⑨ 일반업무 / 비업무 / 미분류 */
enum class Category { COMMUTE, BUSINESS, PERSONAL, UNKNOWN }

/** 장부 한 줄. append-only — 고치지 말고 CORRECTION을 새로 붙인다. */
data class LedgerRecord(
    val seq: Long,
    val id: String,
    val kind: RecordKind,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant,
    val startOdoKm: Double,
    val endOdoKm: Double,
    val category: Category,
    val vehicleVin: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverDept: String? = null,
    val purpose: String? = null,
    val placeName: String? = null,
    val passengers: Int = 0,
    val correctsSeq: Long? = null,
    val note: String? = null,
    val prevHash: String = "",
    val hash: String = "",
) {
    val distanceKm: Double get() = round1(endOdoKm - startOdoKm)
}

/** 소수 1자리 반올림. Double.toString 기준이라 0.1 단위 값이 흔들리지 않는다. */
fun round1(v: Double): Double = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toDouble()

/** 소수 1자리 고정 문자열, Locale 무관. */
fun fmt1(v: Double): String = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString()

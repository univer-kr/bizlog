package kr.univer.bizlog.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind

/**
 * 시연용 장부(_dev/design/screen_3·4, _dev/docs/06).
 * 마지막 도착 ODO는 시연 대본의 Virtual Console 초기값 48,360.0에 맞춘다(시안은 48,359.2).
 * 11/13의 21.8 km 누락 구간은 여기 적지 않는다. appendTrip이 ODO 차이로 만든다.
 */
object DemoData {
    private val zone = ZoneId.of("Asia/Seoul")

    fun trips(): List<LedgerRecord> = listOf(
        trip("12", "08:32", "08:54", "사용자 A", "영업 1팀", 48213.4, 48225.9, Category.COMMUTE, place = "회사"),
        trip("12", "09:40", "10:10", "사용자 A", "영업 1팀", 48225.9, 48263.1, Category.BUSINESS,
            place = "C사 본사", purpose = "C사 신규 납품 상담", passengers = 2),
        trip("12", "19:10", "19:30", "사용자 C", null, 48263.1, 48270.0, Category.PERSONAL),
        trip("13", "09:20", "10:05", "사용자 A", "영업 1팀", 48291.8, 48330.4, Category.BUSINESS, place = "B사 물류센터"),
        trip("14", "14:00", "14:40", "사용자 C", null, 48330.4, 48360.0, Category.BUSINESS),
    )

    private fun trip(
        day: String, from: String, to: String, name: String, dept: String?, start: Double, end: Double,
        category: Category, place: String? = null, purpose: String? = null, passengers: Int = 0,
    ): LedgerRecord {
        val date = LocalDate.parse("2026-11-$day")
        fun at(hm: String) = LocalDateTime.of(date, java.time.LocalTime.parse(hm)).atZone(zone).toInstant()
        return LedgerRecord(
            seq = 0, id = "demo-$day-$from", kind = RecordKind.TRIP, date = date, startedAt = at(from), endedAt = at(to),
            startOdoKm = start, endOdoKm = end, category = category,
            driverId = name, driverName = name, driverDept = dept, placeName = place, purpose = purpose, passengers = passengers,
        )
    }
}

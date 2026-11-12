package kr.univer.bizlog.domain.classify

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kr.univer.bizlog.domain.model.Category

data class Place(val name: String?, val poiId: String? = null, val lat: Double? = null, val lon: Double? = null)

data class Classification(val category: Category, val askPurpose: Boolean, val storePlace: Boolean = true)

/** 용도 분류 규칙 (_dev/docs/04 5절). 위에서부터 먼저 맞는 규칙을 쓴다. */
class PurposeClassifier(
    private val commutePlaces: List<Place>,
    private val businessBookmarks: List<Place>,
    private val knownBusiness: List<Place>,
) {
    fun classify(arrival: Place?, privacy: Boolean): Classification = when {
        privacy -> Classification(Category.PERSONAL, askPurpose = false, storePlace = false)
        arrival == null -> Classification(Category.UNKNOWN, askPurpose = true)
        commutePlaces.any { same(it, arrival) } -> Classification(Category.COMMUTE, askPurpose = false)
        businessBookmarks.any { same(it, arrival) } || knownBusiness.any { same(it, arrival) } ->
            Classification(Category.BUSINESS, askPurpose = false)
        else -> Classification(Category.UNKNOWN, askPurpose = true)
    }

    /** POI ID → 이름 → 좌표 150 m 순으로 비교. */
    private fun same(a: Place, b: Place): Boolean = when {
        a.poiId != null && b.poiId != null -> a.poiId == b.poiId
        a.name != null && b.name != null -> a.name.trim().equals(b.name.trim(), ignoreCase = true)
        a.lat != null && a.lon != null && b.lat != null && b.lon != null -> meters(a.lat, a.lon, b.lat, b.lon) <= 150.0
        else -> false
    }

    private fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(h))
    }
}

/** 음성 답변 한 문장을 구분으로. 출퇴근, 개인 용무 단어가 없으면 업무로 본다(질문 자체가 업무 목적을 묻는다). */
fun categoryFromAnswer(answer: String): Category = when {
    Regex("출근|퇴근|집으로|귀가").containsMatchIn(answer) -> Category.COMMUTE
    Regex("개인|사적|가족|주말|비업무|볼일|쇼핑").containsMatchIn(answer) -> Category.PERSONAL
    else -> Category.BUSINESS
}

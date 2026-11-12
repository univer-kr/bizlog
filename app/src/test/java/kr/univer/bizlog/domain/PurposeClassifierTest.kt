package kr.univer.bizlog.domain

import kr.univer.bizlog.domain.classify.Classification
import kr.univer.bizlog.domain.classify.Place
import kr.univer.bizlog.domain.classify.PurposeClassifier
import kr.univer.bizlog.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class PurposeClassifierTest {
    private val c = PurposeClassifier(
        commutePlaces = listOf(Place("회사", lat = 37.5000, lon = 127.0000)),
        businessBookmarks = listOf(Place("B사 물류센터", poiId = "poi-b")),
        knownBusiness = listOf(Place("D사")),
    )

    @Test fun `U13 즐겨찾기 거래처 도착이면 일반업무, 질문 안 함`() {
        assertEquals(Classification(Category.BUSINESS, askPurpose = false), c.classify(Place("B사", poiId = "poi-b"), privacy = false))
    }

    @Test fun `U14 처음 방문지는 미분류 + 질문`() {
        assertEquals(Classification(Category.UNKNOWN, askPurpose = true), c.classify(Place("C사 본사"), privacy = false))
    }

    @Test fun `회사 좌표 150m 이내면 출퇴근`() {
        assertEquals(Category.COMMUTE, c.classify(Place(null, lat = 37.5010, lon = 127.0000), privacy = false).category)
    }

    @Test fun `이전에 업무로 답한 곳은 질문 안 함`() {
        assertEquals(Classification(Category.BUSINESS, askPurpose = false), c.classify(Place("D사"), privacy = false))
    }

    @Test fun `프라이버시 모드는 비업무, 장소 저장 안 함`() {
        assertEquals(Classification(Category.PERSONAL, askPurpose = false, storePlace = false), c.classify(Place("B사", poiId = "poi-b"), privacy = true))
    }
}

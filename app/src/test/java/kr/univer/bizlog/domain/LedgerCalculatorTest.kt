package kr.univer.bizlog.domain

import kr.univer.bizlog.domain.ledger.LedgerCalculator
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.RecordKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** U11 데이터: 출퇴근 12.5 + 업무 37.2 + 비업무 6.9 + GAP 21.8 */
fun u11Records() = chainOf(
    draft(48213.4, 48225.9, Category.COMMUTE),
    draft(48225.9, 48263.1, Category.BUSINESS),
    draft(48263.1, 48270.0, Category.PERSONAL),
    draft(48270.0, 48291.8, Category.UNKNOWN, RecordKind.GAP),
)

class LedgerCalculatorTest {
    @Test fun `U11 총 78_4, 업무 49_7, 비율 63_4`() {
        val s = LedgerCalculator.summarize(u11Records())
        assertEquals(78.4, s.totalKm, 0.0)
        assertEquals(49.7, s.businessKm, 0.0)
        assertEquals(63.4, s.businessRatioPercent, 0.0)
    }

    @Test fun `정정 레코드가 구분을 바꾼다 (원본은 그대로)`() {
        val base = u11Records()
        val fix = OdoChainFix.correction(base, correctsSeq = 3, category = Category.BUSINESS)
        val s = LedgerCalculator.summarize(base + fix)
        assertEquals(56.6, s.businessKm, 0.0)
        assertEquals(Category.PERSONAL, base[2].category)
    }

    @Test fun `기록이 없으면 비율 0`() {
        assertEquals(0.0, LedgerCalculator.summarize(emptyList()).businessRatioPercent, 0.0)
    }
}

private object OdoChainFix {
    fun correction(chain: List<kr.univer.bizlog.domain.model.LedgerRecord>, correctsSeq: Long, category: Category) =
        kr.univer.bizlog.domain.chain.OdoChain.seal(
            chain.last(),
            chain.first { it.seq == correctsSeq }.copy(kind = RecordKind.CORRECTION, correctsSeq = correctsSeq, category = category),
        )
}

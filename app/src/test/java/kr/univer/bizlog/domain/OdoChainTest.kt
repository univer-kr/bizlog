package kr.univer.bizlog.domain

import java.util.Locale
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.chain.Continuity
import kr.univer.bizlog.domain.chain.OdoChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdoChainTest {
    @Test fun `U5 직전 270_0, 새 출발 291_8 이면 21_8km 누락 구간`() {
        assertEquals(Continuity.Gap(270.0, 291.8), OdoChain.continuity(270.0, 291.8))
    }

    @Test fun `U6 차이 0_1km 미만이면 연속`() {
        assertEquals(Continuity.Continuous, OdoChain.continuity(270.0, 270.05))
    }

    @Test fun `U7 ODO가 줄면 불연속 플래그`() {
        assertEquals(Continuity.Discontinuity, OdoChain.continuity(270.0, 250.0))
    }

    private fun fiveRecords() = chainOf(
        draft(100.0, 110.0), draft(110.0, 120.0), draft(120.0, 130.0), draft(130.0, 140.0), draft(140.0, 150.0),
    )

    @Test fun `U8 정상 체인 5건은 OK`() {
        assertEquals(ChainStatus.Ok, OdoChain.verify(fiveRecords()))
    }

    @Test fun `U9 3번째 레코드 도착 ODO를 고치면 BROKEN_AT 3`() {
        val records = fiveRecords().toMutableList()
        records[2] = records[2].copy(endOdoKm = 135.0)
        assertEquals(ChainStatus.BrokenAt(3), OdoChain.verify(records))
    }

    @Test fun `레코드가 빠지면 깨짐`() {
        val records = fiveRecords().toMutableList().apply { removeAt(1) }
        assertEquals(ChainStatus.BrokenAt(3), OdoChain.verify(records))
    }

    @Test fun `누락 구간이 있으면 GapCount`() {
        val records = chainOf(draft(100.0, 110.0), draft(110.0, 130.0, kind = kr.univer.bizlog.domain.model.RecordKind.GAP), draft(130.0, 140.0))
        assertEquals(ChainStatus.GapCount(1), OdoChain.verify(records))
    }

    @Test fun `U10 같은 입력은 Locale과 무관하게 같은 해시`() {
        val r = chainOf(draft(270.0, 291.8))[0]
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(r.hash, OdoChain.hash(r))
            assertTrue(OdoChain.canonical(r).contains("|270.0|291.8|"))
        } finally {
            Locale.setDefault(default)
        }
        assertEquals(OdoChain.hash(r), OdoChain.hash(r.copy()))
        assertEquals(64, r.hash.length)
        assertEquals(OdoChain.GENESIS, r.prevHash)
    }
}

class AppendTripTest {
    private val prev = chainOf(draft(48360.0, 48368.0))

    @Test fun `E4 앱 밖에서 22km 움직였으면 GAP 다음 TRIP, 체인은 이어진다`() {
        val out = OdoChain.appendTrip(prev.last(), prev.last(), draft(48390.0, 48393.0))
        assertEquals(listOf(kr.univer.bizlog.domain.model.RecordKind.GAP, kr.univer.bizlog.domain.model.RecordKind.TRIP), out.map { it.kind })
        assertEquals(22.0, out[0].distanceKm, 0.0)
        assertEquals(null, out[0].driverName)
        assertEquals(ChainStatus.GapCount(1), OdoChain.verify(prev + out))
    }

    @Test fun `이어지면 TRIP 하나만`() {
        val out = OdoChain.appendTrip(prev.last(), prev.last(), draft(48368.0, 48371.0))
        assertEquals(1, out.size)
        assertEquals(ChainStatus.Ok, OdoChain.verify(prev + out))
    }

    @Test fun `ODO가 줄었으면 불연속 비고`() {
        val out = OdoChain.appendTrip(prev.last(), prev.last(), draft(100.0, 110.0))
        assertEquals(OdoChain.DISCONTINUITY_NOTE, out.single().note)
    }

    @Test fun `첫 트립은 GENESIS에 붙는다`() {
        assertEquals(OdoChain.GENESIS, OdoChain.appendTrip(null, null, draft(0.0, 3.0)).single().prevHash)
    }
}

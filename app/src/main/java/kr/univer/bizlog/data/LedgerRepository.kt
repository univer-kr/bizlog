package kr.univer.bizlog.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind

class LedgerRepository(private val db: AppDatabase) {
    private val dao = db.ledgerDao()

    val records: Flow<List<LedgerRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 트립 1건 저장. 직전 도착 ODO와 끊겨 있으면 GAP을 같은 트랜잭션에서 먼저 남긴다. */
    suspend fun appendTrip(trip: LedgerRecord): List<LedgerRecord> = db.withTransaction {
        val out = OdoChain.appendTrip(dao.tail()?.toDomain(), dao.lastOdoRecord()?.toDomain(), trip)
        dao.insertAll(out.map { it.toEntity() })
        out
    }

    /**
     * 구분 변경·누락 사유 입력. 원본은 두고 CORRECTION을 붙인다.
     * shown = 화면에 보이는(이미 정정이 반영된) 행 → 이전 정정 내용이 누적된다.
     */
    suspend fun appendCorrection(shown: LedgerRecord, category: Category = shown.category, note: String? = shown.note) =
        db.withTransaction {
            val fix = shown.copy(id = UUID.randomUUID().toString(), kind = RecordKind.CORRECTION, correctsSeq = shown.seq, category = category, note = note)
            dao.insertAll(listOf(OdoChain.seal(dao.tail()?.toDomain(), fix).toEntity()))
        }

    suspend fun reset() = dao.deleteAll()

    /** 장부를 비우고 시안·시연 대본 값으로 채운다. 누락 구간은 실제 체인 로직이 만든다. */
    suspend fun loadDemo() = db.withTransaction {
        dao.deleteAll()
        var tail: LedgerRecord? = null
        var lastOdo: LedgerRecord? = null
        for (trip in DemoData.trips()) {
            val out = OdoChain.appendTrip(tail, lastOdo, trip)
            dao.insertAll(out.map { it.toEntity() })
            tail = out.last()
            lastOdo = out.last()
        }
    }

    suspend fun tamperForDemo(): Int = dao.tamperForDemo()
}

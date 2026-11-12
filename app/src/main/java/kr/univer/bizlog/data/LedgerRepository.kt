package kr.univer.bizlog.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.LedgerRecord

class LedgerRepository(private val db: AppDatabase) {
    private val dao = db.ledgerDao()

    val records: Flow<List<LedgerRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 트립 1건 저장. 직전 도착 ODO와 끊겨 있으면 GAP을 같은 트랜잭션에서 먼저 남긴다. */
    suspend fun appendTrip(trip: LedgerRecord): List<LedgerRecord> = db.withTransaction {
        val out = OdoChain.appendTrip(dao.tail()?.toDomain(), dao.lastOdoRecord()?.toDomain(), trip)
        dao.insertAll(out.map { it.toEntity() })
        out
    }
}

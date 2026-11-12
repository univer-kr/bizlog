package kr.univer.bizlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** append-only: 일반 경로에 UPDATE는 없다. tamperForDemo만 예외(개발자 메뉴 시연용). */
@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<LedgerRecordEntity>)

    @Query("SELECT * FROM ledger_record ORDER BY seq")
    fun observeAll(): Flow<List<LedgerRecordEntity>>

    @Query("SELECT * FROM ledger_record ORDER BY seq DESC LIMIT 1")
    suspend fun tail(): LedgerRecordEntity?

    @Query("SELECT * FROM ledger_record WHERE kind != 'CORRECTION' ORDER BY seq DESC LIMIT 1")
    suspend fun lastOdoRecord(): LedgerRecordEntity?

    @Query("DELETE FROM ledger_record")
    suspend fun deleteAll()

    /** 시연용 변조: 첫 트립의 도착 ODO를 DB에서 직접 1 km 늘린다. 해시는 그대로라 검증이 깨진다. */
    @Query("UPDATE ledger_record SET endOdoKm = endOdoKm + 1.0 WHERE seq = (SELECT MIN(seq) FROM ledger_record WHERE kind = 'TRIP')")
    suspend fun tamperForDemo(): Int
}

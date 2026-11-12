package kr.univer.bizlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** append-only: UPDATE 쿼리를 두지 않는다. */
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
}

package kr.univer.bizlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind

/** 시각·날짜는 해시 canonical과 똑같은 ISO 문자열로 저장해야 왕복 후에도 해시가 맞는다. */
@Entity(tableName = "ledger_record")
data class LedgerRecordEntity(
    @PrimaryKey val seq: Long,
    val id: String,
    val kind: String,
    val vehicleVin: String?,
    val date: String,
    val startedAt: String,
    val endedAt: String,
    val driverId: String?,
    val driverName: String?,
    val driverDept: String?,
    val startOdoKm: Double,
    val endOdoKm: Double,
    val category: String,
    val purpose: String?,
    val placeName: String?,
    val passengers: Int,
    val correctsSeq: Long?,
    val note: String?,
    val prevHash: String,
    val hash: String,
)

fun LedgerRecord.toEntity() = LedgerRecordEntity(
    seq, id, kind.name, vehicleVin, date.toString(), startedAt.toString(), endedAt.toString(),
    driverId, driverName, driverDept, startOdoKm, endOdoKm, category.name,
    purpose, placeName, passengers, correctsSeq, note, prevHash, hash,
)

fun LedgerRecordEntity.toDomain() = LedgerRecord(
    seq = seq, id = id, kind = RecordKind.valueOf(kind), date = LocalDate.parse(date),
    startedAt = Instant.parse(startedAt), endedAt = Instant.parse(endedAt),
    startOdoKm = startOdoKm, endOdoKm = endOdoKm, category = Category.valueOf(category),
    vehicleVin = vehicleVin, driverId = driverId, driverName = driverName, driverDept = driverDept,
    purpose = purpose, placeName = placeName, passengers = passengers, correctsSeq = correctsSeq,
    note = note, prevHash = prevHash, hash = hash,
)

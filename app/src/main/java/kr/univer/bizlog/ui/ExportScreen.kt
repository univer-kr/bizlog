package kr.univer.bizlog.ui

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.ledger.LedgerCalculator
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.fmt1
import kr.univer.bizlog.export.CsvExporter

/** ④ 연말 정리·내보내기. 외부 전송 없음: 앱 저장소에 파일만 만든다(adb pull 가능). */
@Composable
fun ExportScreen(records: List<LedgerRecord>, chain: ChainStatus, settings: AppSettings) {
    val context = LocalContext.current
    val rows = remember(records) { LedgerCalculator.effective(records) }
    val year = rows.lastOrNull()?.date?.year ?: LocalDate.now().year
    // 소명 자료는 목적이 기록된 업무 트립을 우선 보여 준다.
    val trips = rows.filter { it.kind == RecordKind.TRIP }
    val evidence = trips.lastOrNull { it.category == Category.BUSINESS && it.purpose != null }
        ?: trips.lastOrNull { it.category == Category.BUSINESS } ?: trips.lastOrNull()
    var message by remember { mutableStateOf<String?>(null) }
    var showCost by remember { mutableStateOf(false) }

    fun exportCsv() {
        val name = CsvExporter.fileName(year, settings.plateNo, rows.lastOrNull { it.vehicleVin != null }?.vehicleVin)
        val bytes = CsvExporter.export(settings.csvHeader(year), records).toByteArray(Charsets.UTF_8)
        message = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                // 공용 다운로드 폴더: 파일 앱·adb(content read)로 꺼낼 수 있다. 권한 불필요.
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BizLog")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                "저장됨: Download/BizLog/$name  ($uri)"
            } else {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, name)
                file.writeBytes(bytes)
                "저장됨: ${file.absolutePath}"
            }
        }.getOrElse { "저장 실패: ${it.message}" }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        BzCard(Modifier.weight(1.35f).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("업무목적 소명 자료 · 트립 1건", color = Bz.ink, fontSize = Bz.title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (chain is ChainStatus.BrokenAt) BzChip("체인 불일치 #${chain.seq}", Bz.pink, Bz.pinkSoft)
                    else BzChip("체인 검증 통과", Bz.lime, Bz.limeSoft)
                }
                if (evidence == null) Text("아직 기록된 트립이 없습니다.", color = Bz.muted, fontSize = Bz.body)
                else EvidenceRows(evidence, records)
            }
        }
        BzCard(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Text("$year 사업연도 내보내기", color = Bz.ink, fontSize = Bz.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.heightIn(min = 4.dp))
                ExportButton("업무용승용차 운행기록부", "국세청 고시 서식 CSV") { exportCsv() }
                ExportButton("관련비용 명세서 입력값", "총주행 · 업무용 거리 · 비율") { showCost = !showCost }
                if (showCost) LedgerCalculator.summarize(records.filter { it.date.year == year }).let {
                    Text("⑪ 총주행 ${odo(it.totalKm)} km   ⑫ 업무용 ${odo(it.businessKm)} km   ⑬ ${fmt1(it.businessRatioPercent)}%", color = Bz.lime, fontSize = Bz.caption)
                }
                ExportButton("업무목적 소명 패키지", "트립별 PDF · 준비 중", enabled = false) {}
                ExportButton("한 번에 내보내기", "CSV", primary = true) { exportCsv() }
                message?.let { Text(it, color = Bz.muted, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun EvidenceRows(r: LedgerRecord, records: List<LedgerRecord>) {
    val zone = ZoneId.systemDefault()
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val date = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)
    val stored = records.first { it.seq == r.seq } // 무결성은 원본 레코드 기준
    val items = listOf(
        "일자·시간" to "${r.date.format(date)} ${r.startedAt.atZone(zone).format(hm)} ~ ${r.endedAt.atZone(zone).format(hm)}",
        "사용자" to listOfNotNull(r.driverName, r.driverDept).joinToString(" · ").ifEmpty { "—" },
        "계기판 거리" to "${odo(r.startOdoKm)} → ${odo(r.endOdoKm)} km (${fmt1(r.distanceKm)} km)",
        "방문지" to (r.placeName ?: "—"),
        "업무 목적" to (r.purpose ?: r.category.label()),
        "동승 인원" to "${r.passengers}명",
        "무결성" to "직전 기록 해시 ${stored.prevHash.take(4)}… → 이번 기록 ${stored.hash.take(4)}…",
    )
    items.forEach { (k, v) ->
        Row {
            Text(k, color = Bz.muted, fontSize = Bz.body, modifier = Modifier.width(200.dp))
            Text(v, color = if (k == "무결성") Bz.muted else Bz.ink, fontSize = Bz.body)
        }
    }
}

@Composable
private fun ExportButton(title: String, sub: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val fill = if (primary) Bz.lime else Bz.bg.copy(alpha = 0.6f)
    val fg = when { primary -> Bz.onLime; enabled -> Bz.ink; else -> Bz.dim }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Bz.touch).background(fill, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = fg, fontSize = Bz.body, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(sub, color = if (primary) Bz.onLime else Bz.muted, fontSize = 18.sp)
    }
}


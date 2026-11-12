package kr.univer.bizlog.ui

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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

/**
 * ④ 연말 정리. 모먼트: 실제로 내보낼 소명서를 종이 한 장으로 미리 보여 준다.
 * 외부 전송 없음. 기기의 다운로드 폴더에 파일만 만든다.
 */
@Composable
fun ExportScreen(records: List<LedgerRecord>, chain: ChainStatus, settings: AppSettings) {
    val context = LocalContext.current
    val rows = remember(records) { LedgerCalculator.effective(records) }
    val year = rows.lastOrNull()?.date?.year ?: LocalDate.now().year
    val trips = rows.filter { it.kind == RecordKind.TRIP }
    // 소명서 미리보기는 목적이 기록된 업무 트립을 우선 고른다.
    val evidence = trips.lastOrNull { it.category == Category.BUSINESS && it.purpose != null }
        ?: trips.lastOrNull { it.category == Category.BUSINESS } ?: trips.lastOrNull()
    var saved by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCost by remember { mutableStateOf(false) }

    fun exportCsv() {
        val name = CsvExporter.fileName(year, settings.plateNo, rows.lastOrNull { it.vehicleVin != null }?.vehicleVin)
        val bytes = CsvExporter.export(settings.csvHeader(year), records).toByteArray(Charsets.UTF_8)
        saved = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                // 공용 다운로드 폴더: 파일 앱과 adb(content read)로 꺼낼 수 있고 권한이 필요 없다.
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BizLog")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                "다운로드 폴더의 BizLog에 저장했어요" to name
            } else {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, name)
                file.writeBytes(bytes)
                "저장했어요" to file.absolutePath
            }
        }.getOrElse { "저장하지 못했어요" to (it.message ?: "") }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            T("소명 자료 미리보기", bzText(22.sp, Bz.secondary), Modifier.padding(start = 4.dp, bottom = 20.dp))
            if (evidence == null) Panel(Modifier.fillMaxWidth().height(480.dp)) {
                T("기록된 트립이 생기면 여기에 소명 자료가 만들어져요", bzText(24.sp, Bz.secondary), Modifier.align(Alignment.Center))
            } else Paper(evidence, records.first { it.seq == evidence.seq }, chain)
        }
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = 40.dp) {
            Column(Modifier.fillMaxSize()) {
                T("${year}년 사업연도", bzText(36.sp, Bz.ink, FontWeight.Medium))
                Spacer(Modifier.height(8.dp))
                T("국세청 고시 서식 그대로 내보내요", bzText(22.sp, Bz.secondary))
                Spacer(Modifier.height(32.dp))
                ActionRow(Icons.Outlined.List, "업무용승용차 운행기록부", "국세청 고시 서식, CSV", { exportCsv() })
                Hairline()
                ActionRow(Icons.Outlined.Info, "관련비용 명세서 입력값", "총주행, 업무용 거리, 비율", { showCost = !showCost })
                if (showCost) CostFigures(records.filter { it.date.year == year })
                Hairline()
                ActionRow(Icons.Outlined.DateRange, "업무목적 소명 패키지", "트립별 PDF", {}, enabled = false, trailing = "준비 중")
                Spacer(Modifier.weight(1f))
                saved?.let { (msg, file) ->
                    Stack(4.dp, Modifier.padding(bottom = 20.dp)) {
                        T(msg, bzText(22.sp, Bz.ink))
                        T(file, bzText(18.sp, Bz.muted), maxLines = 1)
                    }
                }
                PrimaryButton("한 번에 내보내기", { exportCsv() })
            }
        }
    }
}

@Composable
private fun CostFigures(records: List<LedgerRecord>) {
    val s = LedgerCalculator.summarize(records)
    Row(Modifier.fillMaxWidth().padding(start = 84.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        listOf("총주행" to "${odo(s.totalKm)}km", "업무용" to "${odo(s.businessKm)}km", "비율" to "${fmt1(s.businessRatioPercent)}%").forEach { (k, v) ->
            Stack(4.dp) {
                T(k, bzText(19.sp, Bz.muted))
                T(v, bzText(26.sp, Bz.ink, FontWeight.Medium, tnum = true))
            }
        }
    }
}

/** 종이 한 장. 어두운 화면 위의 유일한 밝은 물체. */
@Composable
private fun Paper(r: LedgerRecord, stored: LedgerRecord, chain: ChainStatus) {
    val zone = ZoneId.systemDefault()
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val date = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)
    val shape = RoundedCornerShape(20.dp)
    val ok = chain !is ChainStatus.BrokenAt
    Column(
        Modifier.fillMaxWidth().shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Bz.paper, shape).padding(horizontal = 48.dp, vertical = 44.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Stack(8.dp, Modifier.weight(1f)) {
                T("업무목적 소명 자료", bzText(20.sp, Bz.paperSecondary, FontWeight.Medium))
                T(r.purpose ?: "${r.category.label()} 운행", bzText(40.sp, Bz.paperInk, FontWeight.Medium, lineHeight = 48.sp))
                T(r.date.format(date), bzText(22.sp, Bz.paperSecondary))
            }
            Stamp(if (ok) "체인 검증 통과" else "체인 불일치", if (ok) Bz.stamp else Color(0xFFC0306B))
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bz.paperLine))
        val items = listOf(
            "운행 시간" to "${r.startedAt.atZone(zone).format(hm)} – ${r.endedAt.atZone(zone).format(hm)}",
            "사용자" to listOfNotNull(r.driverName, r.driverDept).joinToString(", ").ifEmpty { "미지정" },
            "계기판 거리" to "${odo(r.startOdoKm)} → ${odo(r.endOdoKm)}km",
            "주행거리" to "${fmt1(r.distanceKm)}km",
            "방문지" to (r.placeName ?: "기록 없음"),
            "동승 인원" to "${r.passengers}명",
        )
        items.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                T(k, bzText(22.sp, Bz.paperSecondary), Modifier.width(180.dp))
                T(v, bzText(24.sp, Bz.paperInk, tnum = true))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bz.paperLine))
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("무결성", bzText(22.sp, Bz.paperSecondary), Modifier.width(180.dp))
            val mono = bzText(22.sp, Bz.paperInk).copy(fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("앞 기록", bzText(22.sp, Bz.paperSecondary)); T(stored.prevHash.take(8), mono)
                T("→", bzText(22.sp, Bz.paperSecondary), Modifier.padding(horizontal = 6.dp))
                T("이 기록", bzText(22.sp, Bz.paperSecondary)); T(stored.hash.take(8), mono)
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.border(2.dp, color, shape).padding(horizontal = 16.dp, vertical = 8.dp)) {
        T(text, bzText(20.sp, color, FontWeight.SemiBold))
    }
}

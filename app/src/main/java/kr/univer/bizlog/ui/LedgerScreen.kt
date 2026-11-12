package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.ledger.LedgerCalculator
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.fmt1

private val weights = listOf(0.8f, 1.1f, 1.4f, 1.4f, 0.8f, 1.3f, 0.6f)
private val headers = listOf("일자", "사용자", "주행 전 ODO", "주행 후 ODO", "거리", "구분", "체인")
private val day = DateTimeFormatter.ofPattern("M/d")

fun Category.label() = when (this) {
    Category.COMMUTE -> "출·퇴근"
    Category.BUSINESS -> "일반 업무"
    Category.PERSONAL -> "비업무"
    Category.UNKNOWN -> "미분류"
}

/** ③ 운행기록부. 정차 중에만 열린다. 구분·누락 사유 변경은 CORRECTION append. */
@Composable
fun LedgerScreen(repo: LedgerRepository, records: List<LedgerRecord>, chain: ChainStatus) {
    val scope = rememberCoroutineScope()
    val rows = remember(records) { LedgerCalculator.effective(records) }
    val summary = remember(records) { LedgerCalculator.summarize(records) }
    val brokenFrom = (chain as? ChainStatus.BrokenAt)?.seq
    val corrected = remember(records) { records.mapNotNull { it.correctsSeq }.toSet() }
    val openGaps = rows.filter { it.kind == RecordKind.GAP && it.seq !in corrected }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        BzCard(Modifier.weight(1.9f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                TableRow { headers.forEachIndexed { i, h -> Cell(i) { Text(h, color = Bz.dim, fontSize = Bz.caption) } } }
                Divider()
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(rows, key = { it.seq }) { r ->
                        val gap = r.kind == RecordKind.GAP
                        val broken = brokenFrom != null && r.seq >= brokenFrom
                        TableRow(Modifier.background(if (gap) Bz.pinkSoft.copy(alpha = 0.6f) else Color.Transparent)) {
                            Cell(0) { Body(r.date.format(day)) }
                            Cell(1) { Body(if (gap) "미확인" else r.driverName ?: "—", if (gap) Bz.pink else Bz.ink) }
                            Cell(2) { Body(odo(r.startOdoKm)) }
                            Cell(3) { Body(odo(r.endOdoKm)) }
                            Cell(4) { Body(fmt1(r.distanceKm)) }
                            Cell(5) {
                                if (gap) BzChip(r.note?.let { "누락 · $it" } ?: "누락 구간", Bz.pink, Bz.pinkSoft)
                                else CategoryPicker(r.category) { c -> scope.launch { repo.appendCorrection(r, category = c) } }
                            }
                            Cell(6) {
                                when {
                                    broken -> Body("✕", Bz.pink)
                                    gap && r.seq !in corrected -> Body("!", Bz.pink)
                                    else -> Body("✓", Bz.lime)
                                }
                            }
                        }
                        Divider()
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("각 행은 직전 행의 해시를 품고 저장됩니다. 나중에 숫자를 고치면 그 뒤 체인이 모두 어긋나 표시됩니다.", color = Bz.dim, fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Bz.gap)) {
            BzCard(Modifier.fillMaxWidth()) {
                Column {
                    Label("올해 업무사용비율")
                    Text("${fmt1(summary.businessRatioPercent)}%", color = Bz.lime, fontSize = 104.sp, fontWeight = FontWeight.Bold)
                    Text("총주행 ${odo(summary.totalKm)} km 중 업무용 ${odo(summary.businessKm)} km", color = Bz.muted, fontSize = Bz.caption)
                }
            }
            if (brokenFrom != null) {
                BzCard(Modifier.fillMaxWidth(), border = Bz.pink, fill = Bz.pinkSoft.copy(alpha = 0.5f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("체인 불일치 #$brokenFrom", color = Bz.pink, fontSize = Bz.title, fontWeight = FontWeight.Bold)
                        Text("저장된 값과 해시가 맞지 않습니다.\n이 행 이후 기록은 사후 수정 의심으로 표시됩니다.", color = Bz.muted, fontSize = Bz.caption, lineHeight = 30.sp)
                    }
                }
            } else if (openGaps.isNotEmpty()) {
                val g = openGaps.last()
                BzCard(Modifier.fillMaxWidth(), border = Bz.pink.copy(alpha = 0.6f), fill = Bz.pinkSoft.copy(alpha = 0.5f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("누락 구간 ${openGaps.size}건", color = Bz.pink, fontSize = Bz.title, fontWeight = FontWeight.Bold)
                        Text("${g.date.format(day)} ${fmt1(g.distanceKm)} km는 앱 밖에서 움직인 거리입니다.\n정비 입고였다면 사유만 고르면 됩니다.", color = Bz.muted, fontSize = Bz.caption, lineHeight = 30.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (openGaps.isNotEmpty() && brokenFrom == null) ReasonButton { reason -> scope.launch { repo.appendCorrection(openGaps.last(), note = reason) } }
        }
    }
}

@Composable
private fun CategoryPicker(current: Category, onPick: (Category) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            current.label(), color = if (current == Category.UNKNOWN) Bz.dim else Bz.ink, fontSize = Bz.body,
            modifier = Modifier.clickable { open = true }.padding(vertical = 8.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Category.COMMUTE, Category.BUSINESS, Category.PERSONAL).forEach { c ->
                DropdownMenuItem(text = { Text(c.label(), fontSize = Bz.body) }, onClick = { open = false; if (c != current) onPick(c) },
                    modifier = Modifier.heightIn(min = Bz.touch))
            }
        }
    }
}

@Composable
private fun ReasonButton(onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Bz.touch).background(Bz.lime, RoundedCornerShape(Bz.cardRadius))
                .clickable { open = true }.padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("사유 선택하기", color = Bz.onLime, fontSize = Bz.body, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("정비 · 탁송 · 기타", color = Bz.onLime, fontSize = 18.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("정비", "탁송", "기타").forEach { r ->
                DropdownMenuItem(text = { Text(r, fontSize = Bz.body) }, onClick = { open = false; onPick(r) }, modifier = Modifier.heightIn(min = Bz.touch))
            }
        }
    }
}

@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
private fun RowScope.Cell(i: Int, content: @Composable () -> Unit) = Box(Modifier.weight(weights[i])) { content() }

@Composable
private fun Body(text: String, color: Color = Bz.ink) = Text(text, color = color, fontSize = Bz.body)

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Bz.line))

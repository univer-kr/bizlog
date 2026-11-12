package kr.univer.bizlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.launch
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.ledger.LedgerCalculator
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.LedgerRecord
import kr.univer.bizlog.domain.model.RecordKind
import kr.univer.bizlog.domain.model.fmt1
import kr.univer.bizlog.domain.model.round1

fun Category.label() = when (this) {
    Category.COMMUTE -> "출퇴근"
    Category.BUSINESS -> "일반 업무"
    Category.PERSONAL -> "비업무"
    Category.UNKNOWN -> "구분 선택"
}

fun LedgerRecord.dayLabel() = "${date.monthValue}월 ${date.dayOfMonth}일"

private val rowHeight = 92.dp
private val chainWidth = 40.dp

/** 체인 선 한 구간의 모양. 시그니처: 이어짐, 점선(누락), 끊김(변조). */
private enum class Seg { SOLID, GAP, GAP_RESOLVED, BROKEN, CUT }

/** ③ 운행 기록. 정차 중에만 열린다. 구분, 누락 사유는 원본을 두고 정정 기록을 덧붙인다. */
@Composable
fun LedgerScreen(repo: LedgerRepository, records: List<LedgerRecord>, chain: ChainStatus) {
    val scope = rememberCoroutineScope()
    val rows = remember(records) { LedgerCalculator.effective(records) }
    val corrected = remember(records) { records.mapNotNull { it.correctsSeq }.toSet() }
    val brokenFrom = (chain as? ChainStatus.BrokenAt)?.seq
    val openGaps = rows.filter { it.kind == RecordKind.GAP && it.seq !in corrected }
    val list = rememberLazyListState()
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) list.scrollToItem(rows.lastIndex) }

    fun isOpenGap(r: LedgerRecord) = r.kind == RecordKind.GAP && r.seq !in corrected
    fun isBroken(r: LedgerRecord) = brokenFrom != null && r.seq >= brokenFrom
    fun seg(i: Int): Seg {
        val a = rows[i - 1]; val b = rows[i]
        return when {
            brokenFrom != null && b.seq >= brokenFrom && a.seq < brokenFrom -> Seg.CUT
            isBroken(a) && isBroken(b) -> Seg.BROKEN
            isOpenGap(a) || isOpenGap(b) -> Seg.GAP
            a.kind == RecordKind.GAP || b.kind == RecordKind.GAP -> Seg.GAP_RESOLVED
            else -> Seg.SOLID
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        Panel(Modifier.weight(1.7f).fillMaxHeight(), padding = 0.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = Bz.pad, end = Bz.pad, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                    T("운행 기록", bzText(30.sp, Bz.ink, FontWeight.Medium))
                    Spacer(Modifier.width(16.dp))
                    T("${rows.size}건", bzText(22.sp, Bz.muted, tnum = true))
                }
                if (rows.isEmpty()) {
                    Empty(Modifier.weight(1f))
                } else {
                    TableRow(Modifier.height(56.dp)) {
                        Spacer(Modifier.width(chainWidth))
                        Head("날짜", 1.3f); Head("사용자", 1.1f); Head("주행 전", 1.25f, end = true); Head("주행 후", 1.25f, end = true)
                        Head("거리", 0.85f, end = true); Spacer(Modifier.width(32.dp)); Head("구분", 1.5f)
                    }
                    Hairline()
                    LazyColumn(Modifier.weight(1f), state = list) {
                        itemsIndexed(rows, key = { _, r -> r.seq }) { i, r ->
                            val gap = r.kind == RecordKind.GAP
                            val broken = isBroken(r)
                            TableRow(Modifier.height(rowHeight)) {
                                ChainCell(
                                    top = if (i > 0) seg(i) else null,
                                    bottom = if (i < rows.lastIndex) seg(i + 1) else null,
                                    node = when { broken -> Bz.pink; isOpenGap(r) -> Bz.pink; i == rows.lastIndex -> Bz.ink; else -> Bz.muted },
                                    hollow = gap,
                                )
                                Cell(1.3f) { T(r.dayLabel(), bzText(24.sp, Bz.secondary, tnum = true), maxLines = 1) }
                                Cell(1.1f) { T(if (gap) "확인 필요" else r.driverName ?: "미지정", bzText(24.sp, if (gap) Bz.muted else Bz.ink), maxLines = 1) }
                                Cell(1.25f, end = true) { T(odo(r.startOdoKm), bzText(24.sp, if (broken) Bz.pink else Bz.secondary, tnum = true)) }
                                Cell(1.25f, end = true) { T(odo(r.endOdoKm), bzText(24.sp, if (broken) Bz.pink else Bz.secondary, tnum = true)) }
                                Cell(0.85f, end = true) { T(fmt1(r.distanceKm), bzText(26.sp, Bz.ink, FontWeight.Medium, tnum = true)) }
                                Spacer(Modifier.width(32.dp))
                                Cell(1.5f) {
                                    when {
                                        isOpenGap(r) -> T("기록 없는 구간", bzText(23.sp, Bz.pink))
                                        gap -> T("${r.note ?: "사유"}로 확인", bzText(23.sp, Bz.secondary))
                                        else -> CategoryPicker(r.category) { c -> scope.launch { repo.appendCorrection(r, category = c) } }
                                    }
                                }
                            }
                            if (i < rows.lastIndex) Hairline(Modifier.padding(start = chainWidth + Bz.pad, end = Bz.pad))
                        }
                    }
                }
                Hairline()
                T(
                    "모든 기록은 바로 앞 기록의 해시를 이어 받아요. 나중에 숫자를 고치면 그 자리에서 체인이 끊겨요.",
                    bzText(19.sp, Bz.muted), Modifier.padding(horizontal = Bz.pad, vertical = 24.dp),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Bz.gap)) {
            RatioPanel(records)
            when {
                brokenFrom != null -> Notice(
                    "기록이 바뀐 흔적이 있어요",
                    "${brokenFrom}번째 기록의 숫자가 저장할 때의 해시와 맞지 않아요. 그 뒤 기록도 함께 의심 표시돼요.",
                )
                openGaps.isNotEmpty() -> {
                    val g = openGaps.first()
                    Notice(
                        "확인이 필요한 구간 ${openGaps.size}건",
                        "${g.dayLabel()} ${fmt1(g.distanceKm)}km는 앱이 꺼진 동안 움직인 거리예요. 정비나 탁송이었다면 사유를 남겨 주세요.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("정비", "탁송", "기타").forEach { reason ->
                                SecondaryButton(reason, { scope.launch { repo.appendCorrection(g, note = reason) } }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 업무사용비율 + 구분별 한 줄 막대. 업무용(출퇴근 + 일반 업무)만 라임. */
@Composable
private fun RatioPanel(all: List<LedgerRecord>) {
    val year = LocalDate.now().year
    val records = remember(all) { all.filter { it.date.year == year } } // "올해" = 이번 사업연도만
    val rows = remember(records) { LedgerCalculator.effective(records) }
    val summary = remember(records) { LedgerCalculator.summarize(records) }
    val corrected = remember(records) { records.mapNotNull { it.correctsSeq }.toSet() }
    fun km(c: Category) = round1(rows.filter { it.kind != RecordKind.GAP && it.category == c }.sumOf { it.distanceKm })
    fun gapKm(resolved: Boolean) = round1(rows.filter { it.kind == RecordKind.GAP && (it.seq in corrected) == resolved }.sumOf { it.distanceKm })
    val parts = listOf(
        Triple("일반 업무", km(Category.BUSINESS), Bz.lime),
        Triple("출퇴근", km(Category.COMMUTE), Bz.lime.copy(alpha = 0.45f)),
        Triple("비업무", km(Category.PERSONAL), Color(0xFF4A4A50)),
        Triple("미분류", km(Category.UNKNOWN), Color(0xFF34343A)),
        Triple("사유를 남긴 구간", gapKm(resolved = true), Color(0xFF3E3E44)),
        Triple("확인 필요", gapKm(resolved = false), Bz.pink.copy(alpha = 0.7f)),
    ).filter { it.second > 0 }

    Panel(Modifier.fillMaxWidth()) {
        Stack(24.dp) {
            Stack(4.dp) {
                T("올해 업무사용비율", bzText(22.sp, Bz.secondary))
                Row(verticalAlignment = Alignment.Bottom) {
                    T(fmt1(summary.businessRatioPercent), bzText(120.sp, Bz.lime, FontWeight.Medium, tnum = true, lineHeight = 124.sp))
                    T("%", bzText(56.sp, Bz.lime, FontWeight.Medium), Modifier.padding(start = 6.dp, bottom = 18.dp))
                }
            }
            if (parts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    parts.forEach { (_, v, c) -> Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(c)) }
                }
                Stack(12.dp) {
                    parts.forEach { (name, v, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(c)
                            T(name, bzText(22.sp, Bz.secondary), Modifier.padding(start = 12.dp).weight(1f))
                            T("${fmt1(v)}km", bzText(22.sp, Bz.ink, tnum = true))
                        }
                    }
                }
                Hairline()
                Row {
                    T("총주행", bzText(22.sp, Bz.secondary), Modifier.weight(1f))
                    T("${odo(summary.totalKm)}km", bzText(22.sp, Bz.ink, FontWeight.Medium, tnum = true))
                }
            }
        }
    }
}

@Composable
private fun Notice(title: String, body: String, actions: (@Composable () -> Unit)? = null) {
    Panel(Modifier.fillMaxWidth()) {
        Stack(16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dot(Bz.pink)
                T(title, bzText(26.sp, Bz.ink, FontWeight.Medium))
            }
            T(body, bzText(22.sp, Bz.secondary, lineHeight = 34.sp))
            actions?.let { Spacer(Modifier.height(4.dp)); it() }
        }
    }
}

@Composable
private fun Empty(modifier: Modifier) = Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Stack(12.dp) {
        T("아직 기록이 없어요", bzText(28.sp, Bz.ink, FontWeight.Medium))
        T("기어를 D로 바꾸고 출발하면 계기판 거리로 첫 기록이 생겨요", bzText(22.sp, Bz.secondary))
    }
}

@Composable
private fun CategoryPicker(current: Category, onPick: (Category) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val unset = current == Category.UNKNOWN
    Box {
        Pressable({ open = true }, modifier = Modifier.width(184.dp).height(Bz.touch).background(if (unset) Bz.raisedHi else Bz.raised, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxHeight().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(current.label(), bzText(23.sp, if (unset) Bz.ink else Bz.secondary, FontWeight.Medium), Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Bz.muted, modifier = Modifier.width(28.dp))
            }
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp), containerColor = Bz.raisedHi,
        ) {
            listOf(Category.BUSINESS, Category.COMMUTE, Category.PERSONAL).forEach { c ->
                DropdownMenuItem(
                    text = { T(c.label(), bzText(24.sp, if (c == current) Bz.ink else Bz.secondary, if (c == current) FontWeight.Medium else FontWeight.Normal)) },
                    onClick = { open = false; if (c != current) onPick(c) },
                    modifier = Modifier.height(Bz.touch).width(240.dp),
                )
            }
        }
    }
}

@Composable
private fun ChainCell(top: Seg?, bottom: Seg?, node: Color, hollow: Boolean) {
    Canvas(Modifier.width(chainWidth).fillMaxHeight()) {
        val x = size.width - 8.dp.toPx()
        val mid = size.height / 2
        top?.let { segment(it, x, 0f, mid) }
        bottom?.let { segment(it, x, mid, size.height) }
        val r = 7.dp.toPx()
        if (hollow) {
            drawCircle(Bz.card, r, Offset(x, mid))
            drawCircle(node, r, Offset(x, mid), style = Stroke(2.5.dp.toPx()))
        } else drawCircle(node, r, Offset(x, mid))
    }
}

private fun DrawScope.segment(s: Seg, x: Float, y0: Float, y1: Float) {
    val w = 2.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    when (s) {
        Seg.SOLID -> drawLine(Bz.muted.copy(alpha = 0.45f), Offset(x, y0), Offset(x, y1), w)
        Seg.GAP -> drawLine(Bz.pink.copy(alpha = 0.8f), Offset(x, y0), Offset(x, y1), w, pathEffect = dash)
        Seg.GAP_RESOLVED -> drawLine(Bz.muted.copy(alpha = 0.45f), Offset(x, y0), Offset(x, y1), w, pathEffect = dash)
        Seg.BROKEN -> drawLine(Bz.pink.copy(alpha = 0.5f), Offset(x, y0), Offset(x, y1), w)
        Seg.CUT -> Unit
    }
}

@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth().padding(end = Bz.pad), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
private fun RowScope.Head(text: String, weight: Float, end: Boolean = false) =
    T(text, bzText(20.sp, Bz.muted).copy(textAlign = if (end) TextAlign.End else TextAlign.Start), Modifier.weight(weight).padding(start = if (end) 0.dp else 24.dp))

@Composable
private fun RowScope.Cell(weight: Float, end: Boolean = false, content: @Composable () -> Unit) =
    Box(Modifier.weight(weight).padding(start = if (end) 0.dp else 24.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) { content() }

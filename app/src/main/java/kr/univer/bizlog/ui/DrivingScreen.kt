package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.model.round1
import kr.univer.bizlog.service.LiveState

/**
 * ① 주행 화면. 누를 곳이 없다(주행 중 입력 금지), 움직이는 장식도 없다.
 * 모먼트: 큰 트립 거리와 계기판처럼 칸에 든 누적 거리.
 */
@Composable
fun DrivingScreen(live: LiveState, settings: AppSettings, chain: ChainStatus) {
    var now by remember { mutableStateOf(Instant.now()) }
    // 주행 중 화면 변화를 줄이려고 분 단위로만 갱신(Pleos 주행 안전 가이드)
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(15_000) } }
    val tripKm = live.tripStartOdoKm?.let { start -> live.odometerKm?.let { round1(it - start) } } ?: 0.0
    val elapsed = live.tripStartedAt?.let { Duration.between(it, now) } ?: Duration.ZERO

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        Panel(Modifier.weight(1.55f).fillMaxHeight(), padding = 48.dp) {
            Column(Modifier.fillMaxSize()) {
                T("이번 트립", bzText(26.sp, Bz.secondary))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    T(String.format(Locale.US, "%.1f", tripKm), bzText(232.sp, Bz.sky, FontWeight.Medium, tnum = true, lineHeight = 232.sp))
                    T("km", bzText(60.sp, Bz.secondary, FontWeight.Medium), Modifier.padding(start = 16.dp, bottom = 36.dp))
                }
                Spacer(Modifier.weight(1f))
                T("계기판 누적 거리", bzText(20.sp, Bz.muted))
                Spacer(Modifier.height(16.dp))
                OdometerDrum(live.odometerKm)
                Spacer(Modifier.height(16.dp))
                T(live.tripStartOdoKm?.let { "출발할 때 ${odo(it)}km" } ?: "출발 거리를 기다리는 중", bzText(20.sp, Bz.muted, tnum = true))
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = 40.dp) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                InfoBlock("운전자") {
                    T(settings.driverName.ifBlank { "프로필 없음" }, bzText(44.sp, Bz.ink, FontWeight.Medium))
                    if (settings.driverDept.isNotBlank()) T(settings.driverDept, bzText(24.sp, Bz.secondary))
                }
                Hairline()
                InfoBlock("주행 시간") {
                    T(clock(elapsed), bzText(72.sp, Bz.ink, FontWeight.Medium, tnum = true))
                }
                Hairline()
                InfoBlock("장부") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dot(Bz.sky)
                        T("계기판 거리로 기록하고 있어요", bzText(26.sp, Bz.ink))
                    }
                    if (chain is ChainStatus.BrokenAt) T("${chain.seq}번째 기록이 바뀐 흔적이 있어요", bzText(22.sp, Bz.pink))
                    else T("앞 기록과 체인이 이어져 있어요", bzText(22.sp, Bz.secondary))
                }
                Hairline()
                T("주행 중에는 화면을 조작할 수 없어요", bzText(20.sp, Bz.muted))
            }
        }
    }
}

@Composable
private fun InfoBlock(label: String, content: @Composable () -> Unit) = Stack(8.dp) {
    T(label, bzText(20.sp, Bz.muted))
    content()
}

/** 기계식 계기판처럼 자리마다 칸. 앞자리 0은 흐리게, 소수 자리는 반전. */
@Composable
private fun OdometerDrum(km: Double?) {
    val digits = km?.let { String.format(Locale.US, "%08.1f", it) } ?: "------.-"
    val (whole, tenth) = digits.split(".").let { it[0] to it.getOrElse(1) { "-" } }
    val firstSignificant = whole.indexOfFirst { it != '0' }.let { if (it < 0) whole.length - 1 else it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        whole.forEachIndexed { i, c -> DrumCell(c.toString(), if (i < firstSignificant) Bz.muted.copy(alpha = 0.5f) else Bz.ink, Bz.raised) }
        DrumCell(tenth, Bz.page, Bz.ink)
        T("km", bzText(26.sp, Bz.secondary, FontWeight.Medium), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DrumCell(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) =
    Box(Modifier.size(width = 60.dp, height = 84.dp).background(bg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        T(text, bzText(48.sp, fg, FontWeight.Medium, tnum = true))
    }

private fun clock(d: Duration): String {
    val h = d.toHours(); val m = d.toMinutes() % 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}


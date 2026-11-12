package kr.univer.bizlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.domain.chain.ChainStatus
import kr.univer.bizlog.domain.model.round1
import kr.univer.bizlog.service.LiveState

/** ① 주행 화면. 터치할 요소가 없다(주행 중 입력 금지). 애니메이션 없음. */
@Composable
fun DrivingScreen(live: LiveState, settings: AppSettings, chain: ChainStatus) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1_000) } }
    val tripKm = live.tripStartOdoKm?.let { start -> live.odometerKm?.let { round1(it - start) } } ?: 0.0
    val elapsed = live.tripStartedAt?.let { Duration.between(it, now) } ?: Duration.ZERO

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        BzCard(Modifier.weight(1.15f).fillMaxHeight()) {
            Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("운전자 프로필")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(settings.driverName, color = Bz.ink, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text("  ${settings.driverDept}", color = Bz.muted, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BzChip("● 자동 기록 중", Bz.lime, Bz.limeSoft)
                    live.tripStartOdoKm?.let { BzChip("출발 ODO ${odo(it)} km", Bz.sky, Bz.skySoft) }
                    // 주행 중엔 무결성만. 누락 구간 처리는 정차 후 장부에서.
                    if (chain is ChainStatus.BrokenAt) BzChip("체인 불일치 #${chain.seq}", Bz.pink, Bz.pinkSoft)
                    else BzChip("ODO 체인 정상", Bz.ink, Bz.line)
                }
            }
        }
        BzCard(Modifier.weight(0.85f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                Label("이번 트립 · 계기판 기준")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(String.format(java.util.Locale.US, "%.1f", tripKm), color = Bz.sky, fontSize = 128.sp, fontWeight = FontWeight.Bold)
                    Text(" km", color = Bz.sky, fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 22.dp))
                }
                Spacer(Modifier.weight(1f))
                Label("주행 시간")
                Text(
                    "%02d:%02d".format(elapsed.toMinutes(), elapsed.seconds % 60),
                    color = Bz.ink, fontSize = 88.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text("주행 중에는 터치 입력을 받지 않습니다.\n필요한 안내는 음성으로만 전합니다.", color = Bz.muted, fontSize = Bz.caption, lineHeight = 32.sp)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

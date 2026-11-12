package kr.univer.bizlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.univer.bizlog.data.AppDatabase
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.fmt1
import kr.univer.bizlog.service.RecordingService

// shortcut: 임시 확인 화면. 시안 4종 화면(P0-4·5)으로 교체한다.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingService.start(this) // 화면이 보일 때만 포그라운드 서비스 시작이 허용된다(Android 14)
        val repo = LedgerRepository(AppDatabase.get(this))
        setContent { SignalScreen(repo) }
    }
}

@Composable
private fun SignalScreen(repo: LedgerRepository) {
    val live = RecordingService.state.collectAsState().value
    val records = remember(repo) { repo.records }.collectAsState(initial = emptyList()).value
    val ink = Color(0xFFF1F3F5)
    Column(
        Modifier.fillMaxSize().background(Color(0xFF14171B)).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ODO ${live.odometerKm?.let { "%,.1f km".format(it) } ?: "—"}", color = Color(0xFF38C6F6), fontSize = 64.sp)
        Text("기어 ${live.gear} · 주차 브레이크 ${if (live.parkingBrakeOn) "ON" else "OFF"}", color = Color(0xFFD6FD70), fontSize = 32.sp)
        Text(live.tripStartOdoKm?.let { "● 자동 기록 중 · 출발 ODO ${fmt1(it)}" } ?: "정차", color = ink, fontSize = 28.sp)
        Text("체인: ${OdoChain.verify(records)}", color = ink, fontSize = 24.sp)
        records.takeLast(6).forEach {
            Text("#${it.seq} ${it.kind} ${fmt1(it.startOdoKm)} → ${fmt1(it.endOdoKm)} (${fmt1(it.distanceKm)} km) ${it.hash.take(8)}", color = ink, fontSize = 22.sp)
        }
    }
}

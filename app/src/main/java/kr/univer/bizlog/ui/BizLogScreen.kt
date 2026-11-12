package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.gateway.pleos.uxRestricted
import kr.univer.bizlog.service.RecordingService

private enum class Tab(val title: String) { LEDGER("운행 기록"), EXPORT("연말 정리"), SETTINGS("설정") }

/** 주행 중(규제, 기어 D/R, 트립 진행)엔 주행 화면만. 정차 중에만 장부, 내보내기, 설정이 열린다. */
@Composable
fun BizLogScreen(repo: LedgerRepository, settings: AppSettings) {
    val context = LocalContext.current
    val live by RecordingService.state.collectAsState()
    val restricted by remember { uxRestricted(context.applicationContext) }.collectAsState(initial = false)
    val records by remember(repo) { repo.records }.collectAsState(initial = emptyList())
    val chain = remember(records) { OdoChain.verify(records) }
    val driving = restricted || live.gear == Gear.D || live.gear == Gear.R || live.tripStartOdoKm != null
    var tab by rememberSaveable { mutableStateOf(Tab.LEDGER) }
    var devTools by remember { mutableStateOf(settings.devTools) }
    var taps by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Bz.page)) {
        Row(
            Modifier.fillMaxWidth().height(104.dp).padding(horizontal = Bz.pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 시연 도구(데모 장부, 변조 시뮬레이션)는 숨김. 워드마크 다섯 번 = 켜고 끄기
            Pressable({ if (++taps >= 5) { taps = 0; devTools = !devTools; settings.devTools = devTools; tab = Tab.SETTINGS } }, enabled = !driving) {
                T("비즈로그", bzText(30.sp, Bz.ink, FontWeight.SemiBold))
            }
            Spacer(Modifier.width(40.dp))
            if (!driving && live.ask == null) Segmented(Tab.entries.map { it.title }, tab.ordinal) { tab = Tab.entries[it] }
            Spacer(Modifier.weight(1f))
            GearState(live.gear, driving)
        }
        Box(Modifier.fillMaxSize().padding(start = Bz.pad, end = Bz.pad, bottom = Bz.pad)) {
            val ask = live.ask
            if (driving) DrivingScreen(live, settings, chain)
            else if (ask != null) PurposeScreen(repo, ask)
            else when (tab) {
                Tab.LEDGER -> LedgerScreen(repo, records, chain)
                Tab.EXPORT -> ExportScreen(records, chain, settings)
                Tab.SETTINGS -> SettingsScreen(repo, settings, live, devTools)
            }
        }
    }
}

/** 화면 전환. 선택된 칸만 한 단계 밝은 표면. */
@Composable
private fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(18.dp)
    Row(Modifier.background(Bz.card, outer).border(1.dp, Bz.hairline, outer).padding(4.dp)) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Pressable({ onSelect(i) }, modifier = Modifier.height(Bz.touch).background(if (on) Bz.raisedHi else Bz.card, RoundedCornerShape(12.dp))) {
                T(label, bzText(23.sp, if (on) Bz.ink else Bz.secondary, FontWeight.Medium), Modifier.align(Alignment.Center).padding(horizontal = 28.dp))
            }
        }
    }
}

/** 계기판의 기어 표시처럼: 글자 칸 하나와 상태 문구. */
@Composable
private fun GearState(gear: Gear, driving: Boolean) {
    val letter = when {
        driving && gear == Gear.R -> "R"
        driving -> "D"
        gear == Gear.UNKNOWN -> "P"
        else -> gear.name
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        T(if (driving) "주행 중" else "정차 중", bzText(22.sp, Bz.secondary))
        Box(
            Modifier.size(64.dp).background(Bz.raised, RoundedCornerShape(Bz.radiusControl)).border(1.dp, Bz.hairline, RoundedCornerShape(Bz.radiusControl)),
            contentAlignment = Alignment.Center,
        ) { T(letter, bzText(32.sp, if (driving) Bz.sky else Bz.ink, FontWeight.SemiBold)) }
    }
}


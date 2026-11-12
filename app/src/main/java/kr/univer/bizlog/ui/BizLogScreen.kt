package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.chain.OdoChain
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.gateway.pleos.uxRestricted
import kr.univer.bizlog.service.RecordingService

private enum class Tab(val title: String) { LEDGER("운행기록부"), EXPORT("연말 정리"), SETTINGS("설정") }

/** 주행 중(규제·기어 D/R·트립 진행)이면 주행 화면만. 정차 중에만 장부·내보내기·설정이 열린다. */
@Composable
fun BizLogScreen(repo: LedgerRepository, settings: AppSettings) {
    val context = LocalContext.current
    val live by RecordingService.state.collectAsState()
    val restricted by remember { uxRestricted(context.applicationContext) }.collectAsState(initial = false)
    val records by remember(repo) { repo.records }.collectAsState(initial = emptyList())
    val chain = remember(records) { OdoChain.verify(records) }
    val driving = restricted || live.gear == Gear.D || live.gear == Gear.R || live.tripStartOdoKm != null
    var tab by rememberSaveable { mutableStateOf(Tab.LEDGER) }

    Column(Modifier.fillMaxSize().background(Bz.bg)) {
        TopBar(
            title = if (driving) "BizLog" else tab.title,
            gearChip = when {
                driving -> "${if (live.gear == Gear.R) "R" else "D"} 주행 중"
                tab == Tab.LEDGER -> "P 정차 시에만 열림"
                else -> "P 정차"
            },
            driving = driving,
            tabs = if (driving) null else ({
                Tab.entries.forEach { t ->
                    Text(
                        t.title, fontSize = Bz.body, color = if (t == tab) Bz.ink else Bz.dim,
                        fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { tab = t }.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }),
        )
        Box(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
            if (driving) DrivingScreen(live, settings, chain)
            else when (tab) {
                Tab.LEDGER -> LedgerScreen(repo, records, chain)
                Tab.EXPORT -> ExportScreen(records, chain, settings)
                Tab.SETTINGS -> SettingsScreen(repo, settings, live)
            }
        }
    }
}

@Composable
private fun TopBar(title: String, gearChip: String, driving: Boolean, tabs: (@Composable () -> Unit)?) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(10_000) } }
    Column {
        Row(Modifier.fillMaxWidth().height(Bz.barHeight + 16.dp).padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("비즈로그", color = Bz.ink, fontSize = Bz.title, fontWeight = FontWeight.Bold)
            Text(" · $title", color = Bz.muted, fontSize = Bz.title)
            Spacer(Modifier.weight(1f))
            tabs?.invoke()
            Spacer(Modifier.width(24.dp))
            if (driving) BzChip(gearChip, Bz.sky, Bz.skySoft, Bz.body) else BzChip(gearChip, Bz.lime, Bz.limeSoft, Bz.body)
            Spacer(Modifier.width(24.dp))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Bz.muted, fontSize = Bz.title)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bz.line))
    }
}

@Composable
fun Label(text: String) = Text(text, color = Bz.muted, fontSize = Bz.caption)

@Composable
fun Spaced(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }

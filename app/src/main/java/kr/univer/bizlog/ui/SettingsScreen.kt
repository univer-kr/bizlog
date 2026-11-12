package kr.univer.bizlog.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.service.LiveState

/** 설정: 운전자, 법인 정보(정차 중에만 입력) + 시연 도구. */
@Composable
fun SettingsScreen(repo: LedgerRepository, settings: AppSettings, live: LiveState, devTools: Boolean) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf<String?>(null) } // 지우는 동작은 두 번 눌러야 실행
    LaunchedEffect(armed) { if (armed != null) { delay(4_000); armed = null } }

    fun confirm(key: String, action: suspend () -> String) {
        if (armed != key) { armed = key; message = null; return }
        armed = null
        scope.launch { message = action() }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        Panel(Modifier.weight(1.1f).fillMaxHeight(), padding = 40.dp) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                T("운전자", bzText(30.sp, Bz.ink, FontWeight.Medium))
                Spacer(Modifier.height(8.dp))
                T("장부의 사용자 칸에 들어가요. 실명 대신 표시명을 써도 돼요", bzText(20.sp, Bz.secondary))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Field("표시명", settings.driverName, Modifier.weight(1f)) { settings.driverName = it }
                    Field("부서", settings.driverDept, Modifier.weight(1f)) { settings.driverDept = it }
                }
                Spacer(Modifier.height(56.dp))
                T("법인 정보", bzText(30.sp, Bz.ink, FontWeight.Medium))
                Spacer(Modifier.height(8.dp))
                T("운행기록부 서식의 머리글에 들어가요", bzText(20.sp, Bz.secondary))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Field("법인명", settings.companyName, Modifier.weight(1f)) { settings.companyName = it }
                    Field("사업자등록번호", settings.bizRegNo, Modifier.weight(1f)) { settings.bizRegNo = it }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Field("차종", settings.carModel, Modifier.weight(1f)) { settings.carModel = it }
                    Field("자동차등록번호", settings.plateNo, Modifier.weight(1f)) { settings.plateNo = it }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Bz.gap)) {
            if (devTools) Panel(Modifier.fillMaxWidth(), padding = 40.dp) {
                Column {
                    T("시연 도구", bzText(30.sp, Bz.ink, FontWeight.Medium))
                    Spacer(Modifier.height(16.dp))
                    ActionRow(
                        Icons.Outlined.Refresh, if (armed == "demo") "한 번 더 누르면 장부를 바꿔요" else "데모 장부 불러오기",
                        "지금 장부를 지우고 시안 기록으로 채워요", { confirm("demo") { repo.loadDemo(); "데모 장부를 불러왔어요" } },
                    )
                    Hairline()
                    ActionRow(
                        Icons.Outlined.Edit, "장부 변조 시뮬레이션", "첫 트립의 도착 거리를 저장소에서 직접 1km 늘려요",
                        { scope.launch { message = if (repo.tamperForDemo() > 0) "첫 트립을 고쳤어요. 운행 기록에서 체인을 확인해 보세요" else "고칠 트립이 없어요" } },
                    )
                    Hairline()
                    var demoVoice by remember { mutableStateOf(settings.demoVoice) }
                    ActionRow(
                        Icons.Outlined.PlayArrow, "녹음된 답변으로 음성 인식", "마이크 대신 \"거래처 신규 납품 상담\" 음성을 넣어요",
                        { demoVoice = !demoVoice; settings.demoVoice = demoVoice }, trailing = if (demoVoice) "켜짐" else "꺼짐",
                    )
                    Hairline()
                    ActionRow(
                        Icons.Outlined.Delete, if (armed == "reset") "한 번 더 누르면 모두 지워요" else "장부 모두 지우기",
                        "되돌릴 수 없어요", { confirm("reset") { repo.reset(); "장부를 모두 지웠어요" } }, titleColor = Bz.pink,
                    )
                    message?.let { T(it, bzText(22.sp, Bz.secondary), Modifier.padding(top = 16.dp)) }
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f), padding = 40.dp) {
                Column {
                    T("지금 차량 신호", bzText(30.sp, Bz.ink, FontWeight.Medium))
                    Spacer(Modifier.height(20.dp))
                    Signal("계기판 누적 거리", live.odometerKm?.let { "${odo(it)}km" } ?: "받는 중")
                    Signal("기어", if (live.gear == Gear.UNKNOWN) "받는 중" else live.gear.name)
                    Signal("주차 브레이크", if (live.parkingBrakeOn) "켜짐" else "꺼짐")
                    Signal("트립", live.tripStartOdoKm?.let { "기록 중, ${odo(it)}km에서 출발" } ?: "대기")
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, initial: String, modifier: Modifier = Modifier, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val shape = RoundedCornerShape(Bz.radiusControl)
    Stack(10.dp, modifier) {
        T(label, bzText(20.sp, Bz.secondary))
        BasicTextField(
            value = value, onValueChange = { value = it; onSave(it) }, singleLine = true,
            textStyle = bzText(24.sp, Bz.ink), cursorBrush = SolidColor(Bz.ink),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(Bz.touch).background(Bz.raised, shape).border(1.dp, Bz.hairline, shape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) T("입력 안 함", bzText(24.sp, Bz.muted))
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Signal(name: String, value: String) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        T(name, bzText(22.sp, Bz.secondary), Modifier.weight(1f))
        T(value, bzText(22.sp, Bz.ink, tnum = true))
    }
    Hairline()
}

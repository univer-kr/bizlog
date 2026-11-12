package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.model.fmt1
import kr.univer.bizlog.service.LiveState

/** 설정: 프로필·법인 정보(정차 중에만 입력) + 개발자 메뉴(시연용). */
@Composable
fun SettingsScreen(repo: LedgerRepository, settings: AppSettings, live: LiveState) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        BzCard(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("운전자 프로필")
                Field("표시명 (실명 대신 사용 가능)", settings.driverName) { settings.driverName = it }
                Field("부서", settings.driverDept) { settings.driverDept = it }
                Section("법인 정보 (운행기록부 서식 머리글)")
                Field("법인명", settings.companyName) { settings.companyName = it }
                Field("사업자등록번호", settings.bizRegNo) { settings.bizRegNo = it }
                Field("① 차종", settings.carModel) { settings.carModel = it }
                Field("② 자동차등록번호", settings.plateNo) { settings.plateNo = it }
            }
        }
        BzCard(Modifier.weight(1f).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("개발자 메뉴 · 시연용")
                DevButton("데모 데이터 불러오기", "장부를 지우고 시안 값(마지막 ODO 48,360.0)으로 채움") {
                    scope.launch { repo.loadDemo(); message = "데모 장부를 불러왔습니다." }
                }
                DevButton("장부 변조 시뮬레이션", "첫 트립의 도착 ODO를 DB에서 직접 +1 km") {
                    scope.launch { message = if (repo.tamperForDemo() > 0) "변조했습니다. 운행기록부에서 체인 불일치를 확인하세요." else "변조할 트립이 없습니다." }
                }
                DevButton("전체 초기화", "장부 전체 삭제", danger = true) {
                    scope.launch { repo.reset(); message = "장부를 비웠습니다." }
                }
                message?.let { Text(it, color = Bz.lime, fontSize = Bz.caption) }
                Section("현재 차량 신호")
                Signal("ODO", live.odometerKm?.let { "${odo(it)} km" } ?: "—")
                Signal("기어", live.gear.name)
                Signal("주차 브레이크", if (live.parkingBrakeOn) "ON" else "OFF")
                Signal("트립", live.tripStartOdoKm?.let { "기록 중 (출발 ${fmt1(it)})" } ?: "대기")
            }
        }
    }
}

@Composable
private fun Section(text: String) = Text(text, color = Bz.ink, fontSize = Bz.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Field(label: String, initial: String, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = value, onValueChange = { value = it; onSave(it) },
        label = { Text(label, fontSize = 16.sp) }, singleLine = true,
        textStyle = TextStyle(fontSize = Bz.caption, color = Bz.ink),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Bz.lime, unfocusedBorderColor = Bz.line, focusedLabelColor = Bz.lime, unfocusedLabelColor = Bz.muted, cursorColor = Bz.lime),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DevButton(title: String, sub: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Bz.touch).background(Bz.bg.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, color = if (danger) Bz.pink else Bz.ink, fontSize = Bz.body, fontWeight = FontWeight.Bold)
            Text(sub, color = Bz.muted, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Signal(name: String, value: String) = Row {
    Text(name, color = Bz.muted, fontSize = Bz.caption, modifier = Modifier.weight(1f))
    Text(value, color = Color(0xFF38C6F6), fontSize = Bz.caption)
}

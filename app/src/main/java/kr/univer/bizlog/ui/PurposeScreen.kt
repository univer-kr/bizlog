package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.domain.model.Category
import kr.univer.bizlog.domain.model.fmt1
import kr.univer.bizlog.service.PurposeAsk
import kr.univer.bizlog.service.PurposeAsk.Phase
import kr.univer.bizlog.service.RecordingService

/** ② 정차 직후 목적 질문. 한 번 묻고 한마디로 끝. 못 들으면 버튼 세 개로. */
@Composable
fun PurposeScreen(repo: LedgerRepository, ask: PurposeAsk) {
    val scope = rememberCoroutineScope()
    val trip = ask.trip
    LaunchedEffect(ask.phase) { if (ask.phase == Phase.RECORDED) { delay(12_000); RecordingService.dismissAsk() } }

    fun pick(c: Category) = scope.launch {
        repo.appendCorrection(trip, category = c, purpose = ask.answer ?: trip.purpose)
        RecordingService.dismissAsk()
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Bz.gap)) {
        Panel(Modifier.weight(1.55f).fillMaxHeight(), padding = 48.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dot(if (ask.phase == Phase.NOT_HEARD) Bz.muted else Bz.sky)
                    T(
                        when (ask.phase) {
                            Phase.ASKING -> "음성으로 묻는 중"
                            Phase.LISTENING -> "듣고 있어요"
                            Phase.RECORDED -> "답을 장부에 남겼어요"
                            Phase.NOT_HEARD -> "답을 듣지 못했어요. 버튼으로 골라 주세요"
                        },
                        bzText(24.sp, Bz.secondary),
                    )
                }
                Spacer(Modifier.weight(1f))
                T("이번 운행은\n어떤 업무였나요?", bzText(68.sp, Bz.ink, FontWeight.Medium, lineHeight = 84.sp))
                Spacer(Modifier.height(40.dp))
                if (ask.answer != null) {
                    T("“${ask.answer}”", bzText(44.sp, Bz.ink, lineHeight = 56.sp))
                    Spacer(Modifier.height(12.dp))
                    T("${ask.category?.label()}로 기록했어요. 다르면 아래에서 바꿀 수 있어요", bzText(24.sp, Bz.secondary))
                } else {
                    T("“거래처 납품 상담”처럼 짧게 답하면 돼요", bzText(26.sp, Bz.muted))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Category.BUSINESS, Category.COMMUTE, Category.PERSONAL).forEach { c ->
                        val on = ask.category == c
                        val shape = RoundedCornerShape(Bz.radiusControl)
                        Pressable(
                            { if (on) RecordingService.dismissAsk() else pick(c) },
                            modifier = Modifier.weight(1f).height(Bz.touch)
                                .background(if (on) Bz.ink else Bz.raised, shape).border(1.dp, Bz.hairline, shape),
                        ) {
                            T(c.label(), bzText(24.sp, if (on) Bz.page else Bz.ink, FontWeight.Medium), Modifier.align(Alignment.Center))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Pressable({ RecordingService.dismissAsk() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    T(if (ask.phase == Phase.RECORDED) "확인" else "나중에 장부에서 정하기", bzText(22.sp, Bz.secondary), Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = 40.dp) {
            val zone = ZoneId.systemDefault()
            val hm = DateTimeFormatter.ofPattern("HH:mm")
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                T("방금 끝난 운행", bzText(22.sp, Bz.secondary))
                Row(verticalAlignment = Alignment.Bottom) {
                    T(fmt1(trip.distanceKm), bzText(112.sp, Bz.ink, FontWeight.Medium, tnum = true, lineHeight = 116.sp))
                    T("km", bzText(40.sp, Bz.secondary, FontWeight.Medium), Modifier.padding(start = 12.dp, bottom = 16.dp))
                }
                Hairline()
                Fact("시간", "${trip.dayLabel()} ${trip.startedAt.atZone(zone).format(hm)} – ${trip.endedAt.atZone(zone).format(hm)}")
                Fact("계기판", "${odo(trip.startOdoKm)} → ${odo(trip.endOdoKm)}km")
                Fact("사용자", listOfNotNull(trip.driverName, trip.driverDept).joinToString(", ").ifEmpty { "미지정" })
            }
        }
    }
}

@Composable
private fun Fact(k: String, v: String) = Row(Modifier.fillMaxWidth()) {
    T(k, bzText(22.sp, Bz.muted), Modifier.weight(0.35f))
    T(v, bzText(24.sp, Bz.ink, tnum = true), Modifier.weight(1f))
}

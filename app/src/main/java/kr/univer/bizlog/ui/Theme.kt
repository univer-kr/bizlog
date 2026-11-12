package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kr.univer.bizlog.R

/**
 * 디자인 토큰. 차량 화면(160dpi, 팔 길이 거리)이라 웹 기준보다 한 단계 크다.
 * 표면은 무채색 그래파이트, 색은 의미에만: 라임 = 업무, 스카이 = 주행 중, 핑크 = 확인 필요.
 */
object Bz {
    // 무채색 단계
    val page = Color(0xFF111113)
    val card = Color(0xFF19191C)
    val raised = Color(0xFF232327)
    val raisedHi = Color(0xFF2E2E33)
    val hairline = Color(0x14EDEDED)
    val ink = Color(0xFFEDEDED)
    val secondary = Color(0xFFA1A1A6)
    val muted = Color(0xFF85858B)

    // 의미 색 (기획서와 같은 브랜드 값)
    val lime = Color(0xFFD6FD70)
    val sky = Color(0xFF38C6F6)
    val pink = Color(0xFFFF8CC1)

    // 종이(소명서 미리보기)
    val paper = Color(0xFFF4F2ED)
    val paperInk = Color(0xFF1D1C1A)
    val paperSecondary = Color(0xFF6E6A63)
    val paperLine = Color(0x1A1D1C1A)
    val stamp = Color(0xFF2E7D4F)

    val radiusPanel = 24.dp
    val radiusControl = 14.dp
    val pad = 32.dp
    val gap = 24.dp
    val touch = 76.dp

    val pretendard = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    )
}

/** 표 숫자는 고정폭(tnum). */
fun bzText(size: TextUnit, color: Color = Bz.ink, weight: FontWeight = FontWeight.Normal, tnum: Boolean = false, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = Bz.pretendard, fontSize = size, color = color, fontWeight = weight,
        fontFeatureSettings = if (tnum) "tnum" else null, lineHeight = lineHeight,
        letterSpacing = if (size.value >= 56) (-0.02 * size.value).sp else 0.sp,
        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase), // 한국어를 어절 단위로 줄바꿈
    )

/** 48,291.8 */
fun odo(v: Double): String = String.format(Locale.US, "%,.1f", v)

@Composable
fun T(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, style = style, modifier = modifier, maxLines = maxLines)

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = Bz.pad, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(Bz.radiusPanel)
    Box(modifier.background(Bz.card, shape).border(1.dp, Bz.hairline, shape).padding(padding), content = content)
}

/** 같은 그룹 안의 목록 행. 행 사이엔 hairline만. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Bz.hairline) =
    Box(modifier.fillMaxWidth().height(1.dp).background(color))

@Composable
fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).background(color, CircleShape))

/** 누르면 살짝 작아지는 표면. 색 변화와 0.98 축소만, 튀는 동작 없음. */
@Composable
fun Pressable(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.scale(if (pressed) 0.98f else 1f)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Bz.radiusControl)
    Pressable(onClick, modifier = modifier.fillMaxWidth().height(80.dp).background(Bz.ink, shape)) {
        T(text, bzText(26.sp, Bz.page, FontWeight.SemiBold), Modifier.align(Alignment.Center))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Bz.radiusControl)
    Pressable(onClick, modifier = modifier.height(Bz.touch).background(Bz.raised, shape).border(1.dp, Bz.hairline, shape)) {
        T(text, bzText(24.sp, Bz.ink, FontWeight.Medium), Modifier.align(Alignment.Center))
    }
}

/** 아이콘 칸 + 제목 + 설명 + 꼬리. 내보내기·시연 도구 목록 공용. */
@Composable
fun ActionRow(
    icon: ImageVector, title: String, detail: String, onClick: () -> Unit,
    enabled: Boolean = true, titleColor: Color = Bz.ink, trailing: String? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Pressable(onClick, enabled, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(104.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(64.dp).background(Bz.raised, RoundedCornerShape(Bz.radiusControl)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Bz.secondary.copy(alpha = alpha), modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(title, bzText(24.sp, titleColor.copy(alpha = alpha), FontWeight.Medium))
                T(detail, bzText(19.sp, Bz.secondary.copy(alpha = alpha)), maxLines = 1)
            }
            trailing?.let { T(it, bzText(19.sp, Bz.muted)) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = T(text, bzText(20.sp, Bz.secondary, FontWeight.Medium), modifier)

@Composable
fun Stack(gap: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), content = content)


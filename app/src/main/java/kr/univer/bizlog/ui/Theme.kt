package kr.univer.bizlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** 디자인 토큰 (_dev/docs/05 1절). 다크 고정. */
object Bz {
    val bg = Color(0xFF14171B)
    val panel = Color(0xFF1C2128)
    val line = Color(0xFF2A3039)
    val ink = Color(0xFFF1F3F5)
    val muted = Color(0xFFA3ABB5)
    val dim = Color(0xFF6B7380)
    val lime = Color(0xFFD6FD70)
    val sky = Color(0xFF38C6F6)
    val pink = Color(0xFFFF8CC1)
    val onLime = Color(0xFF14171B)
    val limeSoft = Color(0xFF2C3524)
    val skySoft = Color(0xFF1C3442)
    val pinkSoft = Color(0xFF3A2632)

    val cardRadius = 22.dp
    val barHeight = 64.dp
    val touch = 76.dp
    val gap = 24.dp

    val caption = 20.sp
    val body = 24.sp
    val title = 30.sp
}

/** 48,291.8 형식 */
fun odo(v: Double): String = String.format(Locale.US, "%,.1f", v)

@Composable
fun BzCard(modifier: Modifier = Modifier, border: Color = Bz.line, fill: Color = Bz.panel, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(Bz.cardRadius)
    Box(modifier.background(fill, shape).border(1.dp, border, shape).padding(36.dp), content = content)
}

@Composable
fun BzChip(text: String, fg: Color, bg: Color, size: TextUnit = Bz.caption, modifier: Modifier = Modifier) {
    Text(
        text, color = fg, fontSize = size, fontWeight = FontWeight.SemiBold,
        modifier = modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

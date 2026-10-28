package uz.oyna.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Oyna {
    val Bg = Color(0xFF0B0D12)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xCCFFFFFF)
    val Faint = Color(0x99FFFFFF)
    val Accent = Color(0xFF7AB8FF)
    val Danger = Color(0xFFFF6B6B)
    val Panel = Color(0xFF1C1F26)

    val labelShadow = Shadow(Color(0x99000000), Offset(0f, 1.5f), 4f)

    val iconLabel = TextStyle(
        color = Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = labelShadow,
    )
}

@Composable
fun OynaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Oyna.Accent,
            onPrimary = Color.Black,
            background = Oyna.Bg,
            surface = Oyna.Panel,
            onSurface = Oyna.Text,
            onBackground = Oyna.Text,
            surfaceContainerHigh = Oyna.Panel,
        ),
        content = content,
    )
}

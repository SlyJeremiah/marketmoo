package zw.marketmoo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Green = Color(0xFF1F7A3A)
val GreenDark = Color(0xFF155C2B)
val Gold = Color(0xFFFFE08A)
val Earth = Color(0xFF8B5E34)
val Cream = Color(0xFFFAF7F0)
val Danger = Color(0xFFC0392B)
val InfoBlue = Color(0xFF1E88E5)
val OfflineOrange = Color(0xFFE67E22)

private val Scheme = lightColorScheme(
    primary = Green, onPrimary = Color.White, primaryContainer = Color(0xFFE6F4EA), onPrimaryContainer = GreenDark,
    secondary = Earth, onSecondary = Color.White, secondaryContainer = Gold, onSecondaryContainer = GreenDark,
    tertiary = InfoBlue, background = Cream, surface = Color.White, surfaceVariant = Color(0xFFF2EEE3),
    error = Danger,
)

private val AppTypography = Typography(
    // 16 sp minimum for body text (Design Document 10.1)
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MarketMooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(14.dp)),
        content = content,
    )
}

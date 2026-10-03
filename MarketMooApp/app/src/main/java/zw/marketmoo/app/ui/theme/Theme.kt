package zw.marketmoo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: African sunrise over green pasture. Existing names are kept so older screens pick the new look up automatically.
val Green = Color(0xFF157A44)         // pasture
val GreenDark = Color(0xFF0B3D2E)     // deep forest
val Emerald = Color(0xFF1FB36B)       // fresh growth
val Mint = Color(0xFFD9F5E3)
val Gold = Color(0xFFFFC53D)          // sun
val Sunset = Color(0xFFFF7A3D)
val Rose = Color(0xFFE8566D)
val Earth = Color(0xFFB4683A)         // terracotta
val Sky = Color(0xFF7CC8F2)
val Cream = Color(0xFFFFFAF0)
val Ink = Color(0xFF14281D)
val Danger = Color(0xFFD64545)
val InfoBlue = Color(0xFF2B8CCB)
val OfflineOrange = Color(0xFFFF7A3D)

private val EarthColor = Earth
private val RoseColor = Rose

object Gradients {
    val Forest = Brush.linearGradient(listOf(GreenDark, Green, Emerald))
    val TopBar = Brush.horizontalGradient(listOf(GreenDark, Green))
    val Sun = Brush.linearGradient(listOf(Gold, Sunset))
    val Earth = Brush.linearGradient(listOf(Color(0xFFD98A4E), EarthColor))
    val Sky = Brush.linearGradient(listOf(Color(0xFF4FB3E8), InfoBlue))
    val Rose = Brush.linearGradient(listOf(Color(0xFFFF8A7A), RoseColor))
    val Dusk = Brush.linearGradient(listOf(Color(0xFF7B5CD6), Color(0xFF4B3FA8)))
    val Mint = Brush.linearGradient(listOf(Emerald, Color(0xFF0E8C6A)))
}

private val Scheme = lightColorScheme(
    primary = Green, onPrimary = Color.White, primaryContainer = Mint, onPrimaryContainer = GreenDark,
    secondary = Sunset, onSecondary = Color.White, secondaryContainer = Color(0xFFFFE9B0), onSecondaryContainer = Color(0xFF5A3300),
    tertiary = InfoBlue, onTertiary = Color.White,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF1EBDD), onSurfaceVariant = Color(0xFF4A5A50),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFDF8), surfaceContainer = Color(0xFFFBF6EA),
    surfaceContainerHigh = Color(0xFFFFFDF8), surfaceContainerHighest = Color.White,
    outline = Color(0xFFB9B2A0), error = Danger,
)

private val AppTypography = Typography(
    // 16 sp minimum for body text (Design Document 10.1)
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MarketMooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

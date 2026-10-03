package zw.marketmoo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import zw.marketmoo.app.ui.AppRoot
import zw.marketmoo.app.ui.components.cow
import zw.marketmoo.app.ui.theme.Emerald
import zw.marketmoo.app.ui.theme.Gold
import zw.marketmoo.app.ui.theme.GreenDark
import zw.marketmoo.app.ui.theme.MarketMooTheme
import zw.marketmoo.app.ui.theme.Sunset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sl = ServiceLocator.get(this)
        setContent {
            MarketMooTheme {
                var splash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(2100); splash = false }
                LaunchedEffect(Unit) { sl.loadBoundary() }
                Box(Modifier.fillMaxSize()) {
                    AppRoot(sl)
                    AnimatedVisibility(splash, exit = fadeOut(tween(450)) + slideOutVertically(tween(450)) { -it / 6 }) { Splash() }
                }
            }
        }
    }
}

/** Opening animation: the sun rises behind a cow, then the name springs in. */
@androidx.compose.runtime.Composable
private fun Splash() {
    val rise = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val walk = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { delay(500); name.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    LaunchedEffect(Unit) { walk.animateTo(1f, tween(2000)) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GreenDark, Color(0xFF157A44), Emerald))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Canvas(Modifier.size(width = 280.dp, height = 190.dp)) {
                val w = size.width
                val h = size.height
                val sun = Offset(w * 0.5f, h * (0.95f - 0.5f * rise.value))
                drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.6f), Color.Transparent), center = sun, radius = h * 0.8f), radius = h * 0.8f, center = sun)
                drawCircle(Gold, h * 0.3f, sun)
                drawCircle(Sunset.copy(alpha = 0.35f), h * 0.36f, sun)
                // pasture
                drawRoundRect(Color(0xFF0B5D3B), Offset(0f, h * 0.82f), androidx.compose.ui.geometry.Size(w, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(h * 0.09f))
                cow(w * (0.15f + 0.45f * walk.value), h * 0.86f, h * 0.55f, Color(0xFFFFFAF0), Color(0xFF5B3A24), kotlin.math.sin(walk.value * 24f))
            }
            Text(
                "MarketMoo", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * name.value; scaleY = 0.6f + 0.4f * name.value; alpha = name.value },
            )
            Text("Livestock markets, vets, water and weather", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, modifier = Modifier.graphicsLayer { alpha = name.value })
        }
    }
}

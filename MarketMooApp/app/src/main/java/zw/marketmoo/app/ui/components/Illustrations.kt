package zw.marketmoo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import zw.marketmoo.app.ui.theme.Gold
import zw.marketmoo.app.ui.theme.Sunset

private val SkyTopA = Color(0xFF2C8FD6)
private val SkyTopB = Color(0xFF6B4FB0)
private val SkyMidA = Color(0xFF8ED3F5)
private val SkyMidB = Color(0xFFFF9A6B)
private val HorizonA = Color(0xFFFFF1C9)
private val HorizonB = Color(0xFFFFC27A)

/**
 * Animated landscape drawn entirely in code (no image files, so it adds almost nothing to the APK and works offline):
 * the sky slowly shifts from morning blue to golden hour, the sun pulses, clouds drift, hills sway in parallax,
 * cattle walk across the pasture and birds fly by.
 */
@Composable
fun HeroScene(modifier: Modifier = Modifier, height: Dp = 260.dp, content: @Composable () -> Unit = {}) {
    val inf = rememberInfiniteTransition(label = "hero")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(80_000, easing = LinearEasing)), label = "drift")
    val walk by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(42_000, easing = LinearEasing)), label = "walk")
    val step by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "step")
    val pulse by inf.animateFloat(0.92f, 1.1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val sway by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val glow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
    val flap by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "flap")
    val birds by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "birds")

    Box(modifier.height(height).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(lerp(SkyTopA, SkyTopB, glow), lerp(SkyMidA, SkyMidB, glow), lerp(HorizonA, HorizonB, glow))))

            // sun with a soft halo and slowly turning rays
            val sun = Offset(w * 0.8f, h * 0.36f)
            val haloR = h * 0.5f * pulse
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f), Color.Transparent), center = sun, radius = haloR), radius = haloR, center = sun)
            repeat(14) { i ->
                rotate(degrees = i * (360f / 14f) + drift * 720f, pivot = sun) {
                    drawLine(Gold.copy(alpha = 0.55f), Offset(sun.x, sun.y - h * 0.16f), Offset(sun.x, sun.y - h * 0.16f - h * 0.07f * pulse), strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0), Gold), center = sun, radius = h * 0.13f), radius = h * 0.13f, center = sun)

            // clouds
            cloud(((drift * 1.0f + 0.10f) % 1.3f - 0.15f) * w, h * 0.16f, h * 0.075f, 0.85f)
            cloud(((drift * 0.7f + 0.55f) % 1.3f - 0.15f) * w, h * 0.30f, h * 0.06f, 0.65f)
            cloud(((drift * 1.3f + 0.80f) % 1.3f - 0.15f) * w, h * 0.09f, h * 0.05f, 0.55f)

            // birds
            for (i in 0 until 3) {
                val bx = ((birds * 1.2f + i * 0.13f) % 1.3f - 0.15f) * w
                val by = h * (0.22f + i * 0.045f) + sin((birds * 6f + i) * PI.toFloat()) * 6f
                bird(bx, by, h * 0.022f, flap, Color(0xFF1B2F2A).copy(alpha = 0.55f))
            }

            // parallax hills
            hill(w, h, h * 0.62f, h * 0.045f, 0.0f + sway * 0.25f, 1.3f, Color(0xFF58B07A).copy(alpha = 0.75f))
            hill(w, h, h * 0.70f, h * 0.05f, 1.7f - sway * 0.35f, 1.8f, Color(0xFF2E9A5E))
            // cattle between mid and front hills
            val cowY = h * 0.80f
            for (i in 0 until 3) {
                val s = h * (0.30f - i * 0.035f)
                val cx = ((walk * 1.35f + i * 0.34f) % 1.35f - 0.18f) * w
                cow(cx, cowY + i * h * 0.012f, s, if (i == 1) Color(0xFF7A4A2B) else Color(0xFFFFFAF0), if (i == 1) Color(0xFF4E2D17) else Color(0xFF5B3A24), step * (if (i == 1) -1f else 1f))
            }
            hill(w, h, h * 0.86f, h * 0.035f, 3.1f + sway * 0.4f, 2.4f, Color(0xFF0B5D3B))
            // grass tufts
            for (i in 0 until 16) {
                val gx = w * (i / 15f)
                drawLine(Color(0xFF0B3D2E), Offset(gx, h), Offset(gx + sway * 4f, h - h * 0.05f - (i % 3) * 5f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
            // gentle vignette so text stays readable
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.28f))))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { content() }
    }
}

private fun DrawScope.cloud(cx: Float, cy: Float, r: Float, alpha: Float) {
    val c = Color.White.copy(alpha = alpha)
    drawCircle(c, r * 1.0f, Offset(cx, cy))
    drawCircle(c, r * 0.8f, Offset(cx - r * 1.1f, cy + r * 0.2f))
    drawCircle(c, r * 0.9f, Offset(cx + r * 1.1f, cy + r * 0.15f))
    drawRoundRect(c, Offset(cx - r * 1.9f, cy + r * 0.2f), Size(r * 3.9f, r * 0.8f), CornerRadius(r * 0.4f))
}

private fun DrawScope.bird(x: Float, y: Float, s: Float, flap: Float, color: Color) {
    val lift = (flap - 0.5f) * s * 1.6f
    val p = Path().apply {
        moveTo(x - s * 1.4f, y - lift)
        quadraticTo(x - s * 0.6f, y - s * 0.9f + lift * 0.4f, x, y)
        quadraticTo(x + s * 0.6f, y - s * 0.9f + lift * 0.4f, x + s * 1.4f, y - lift)
    }
    drawPath(p, color, style = Stroke(width = s * 0.45f, cap = StrokeCap.Round))
}

private fun DrawScope.hill(w: Float, h: Float, baseY: Float, amp: Float, phase: Float, freq: Float, color: Color) {
    val p = Path().apply {
        moveTo(0f, h)
        lineTo(0f, baseY)
        var x = 0f
        while (x <= w + 12f) {
            lineTo(x, baseY + sin((x / w) * freq * 2f * PI.toFloat() + phase) * amp)
            x += 12f
        }
        lineTo(w, h)
        close()
    }
    drawPath(p, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.92f)), startY = baseY - amp, endY = h))
}

/** A cartoon cow facing right. [s] is its length in pixels; [legSwing] in -1..1 drives the walk cycle. */
fun DrawScope.cow(x: Float, groundY: Float, s: Float, body: Color, spots: Color, legSwing: Float) {
    val legW = s * 0.085f
    val legH = s * 0.30f
    val bodyH = s * 0.36f
    val bodyTop = groundY - legH - bodyH * 0.8f
    // shadow
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(x - s * 0.45f, groundY - s * 0.03f), Size(s * 0.95f, s * 0.09f))
    // legs (alternating)
    val hips = listOf(-0.30f to 1f, -0.18f to -1f, 0.16f to -1f, 0.28f to 1f)
    hips.forEach { (ox, dir) ->
        val pivot = Offset(x + s * ox, bodyTop + bodyH * 0.8f)
        rotate(degrees = legSwing * dir * 16f, pivot = pivot) {
            drawRoundRect(body.copy(alpha = 0.92f), Offset(pivot.x - legW / 2, pivot.y), Size(legW, legH), CornerRadius(legW * 0.4f))
            drawRect(Color(0xFF2B1B10), Offset(pivot.x - legW / 2, pivot.y + legH - legW * 0.8f), Size(legW, legW * 0.8f))
        }
    }
    // tail
    drawLine(spots, Offset(x - s * 0.40f, bodyTop + bodyH * 0.25f), Offset(x - s * 0.50f, bodyTop + bodyH * 0.85f + legSwing * 4f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
    // body
    drawRoundRect(body, Offset(x - s * 0.42f, bodyTop), Size(s * 0.80f, bodyH), CornerRadius(bodyH * 0.45f))
    drawCircle(spots, s * 0.07f, Offset(x - s * 0.16f, bodyTop + bodyH * 0.38f))
    drawCircle(spots, s * 0.05f, Offset(x + s * 0.10f, bodyTop + bodyH * 0.62f))
    drawCircle(spots, s * 0.04f, Offset(x - s * 0.30f, bodyTop + bodyH * 0.7f))
    // head
    val hx = x + s * 0.44f
    val hy = bodyTop + bodyH * 0.18f
    drawRoundRect(body, Offset(hx - s * 0.10f, hy - s * 0.02f), Size(s * 0.26f, s * 0.22f), CornerRadius(s * 0.09f))
    drawRoundRect(Color(0xFFF2B8A8), Offset(hx + s * 0.05f, hy + s * 0.09f), Size(s * 0.11f, s * 0.11f), CornerRadius(s * 0.05f))
    drawCircle(Color(0xFF1B1B1B), s * 0.018f, Offset(hx + s * 0.03f, hy + s * 0.05f))
    // horns and ear
    drawLine(Color(0xFFF1E3C4), Offset(hx - s * 0.04f, hy), Offset(hx - s * 0.08f, hy - s * 0.09f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
    drawOval(spots, Offset(hx - s * 0.16f, hy + s * 0.01f), Size(s * 0.10f, s * 0.05f))
}

/** Small floating blobs used behind headers: soft motion without images. */
@Composable
fun FloatingBubbles(modifier: Modifier = Modifier, color: Color = Color.White) {
    val inf = rememberInfiniteTransition(label = "bubbles")
    val a by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        drawCircle(color.copy(alpha = 0.10f), size.height * 0.9f, Offset(size.width * (0.85f - 0.08f * a), size.height * (0.1f + 0.2f * b)))
        drawCircle(color.copy(alpha = 0.08f), size.height * 0.6f, Offset(size.width * (0.15f + 0.1f * b), size.height * (0.95f - 0.25f * a)))
        drawCircle(Gold.copy(alpha = 0.18f), size.height * 0.28f, Offset(size.width * (0.62f + 0.05f * a), size.height * (0.25f + 0.1f * b)))
        drawCircle(Sunset.copy(alpha = 0.12f), size.height * 0.2f, Offset(size.width * (0.32f - 0.04f * b), size.height * (0.3f + 0.08f * a)))
    }
}

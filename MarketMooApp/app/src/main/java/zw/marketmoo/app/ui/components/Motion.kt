package zw.marketmoo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zw.marketmoo.app.ui.theme.Gradients

/** Fades and slides content in, staggered by [index], the first time it appears. */
@Composable
fun Entrance(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(70L * index); shown = true }
    AnimatedVisibility(
        visible = shown, modifier = modifier,
        enter = fadeIn(tween(420)) + slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) { it / 3 },
    ) { content() }
}

/** Number that counts up to [target] when first shown. */
@Composable
fun CountUp(target: Int, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge, color: Color = Color.Unspecified, suffix: String = "") {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(target) { go = true }
    val v by animateIntAsState(if (go) target else 0, tween(1400, easing = FastOutSlowInEasing), label = "count")
    Text("%,d".format(v) + suffix, modifier, style = style, color = color)
}

/** Click with a small squash-and-bounce so the interface feels alive. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    this.scale(s).clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Soft breathing glow for the main call to action. */
@Composable
fun pulseScale(): Float {
    val inf = rememberInfiniteTransition(label = "pulse")
    val s by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    return s
}

/** Rounded gradient header with drifting bubbles and a gently floating icon. Used at the top of every screen. */
@Composable
fun GradientHeader(
    title: String, subtitle: String? = null, icon: ImageVector, modifier: Modifier = Modifier,
    brush: Brush = Gradients.Forest,
) {
    val inf = rememberInfiniteTransition(label = "hdr")
    val bob by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(brush)) {
        FloatingBubbles(Modifier.matchParentSize())
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).graphicsLayer { translationY = bob }.clip(CircleShape).background(Color.White.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.Medium)
            }
        }
    }
}

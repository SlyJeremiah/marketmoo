package zw.marketmoo.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.ui.components.CountUp
import zw.marketmoo.app.ui.components.Entrance
import zw.marketmoo.app.ui.components.HeroScene
import zw.marketmoo.app.ui.components.bounceClick
import zw.marketmoo.app.ui.components.pulseScale
import zw.marketmoo.app.ui.theme.Emerald
import zw.marketmoo.app.ui.theme.Gold
import zw.marketmoo.app.ui.theme.Gradients
import zw.marketmoo.app.ui.theme.GreenDark
import zw.marketmoo.app.ui.theme.OfflineOrange
import zw.marketmoo.app.ui.theme.Sunset

private val tips = listOf(
    "Dip cattle every week in the rainy season (November to April) to keep January disease away.",
    "Never open the carcass of an animal that died suddenly. Call the Department of Veterinary Services: it could be anthrax.",
    "Check your farm pin on Farm Insights: it shows the nearest water and market, even with no signal.",
    "Keep a record of every vaccination. Lenders ask for herd history, and so do buyers.",
    "Pool your goats with other farmers to meet a buyer's volume and get a better price.",
    "Turn on Data Saver in Account to protect your airtime on mobile data.",
)

@Composable
fun HomeScreen(sl: ServiceLocator, online: Boolean, onOpen: (String) -> Unit) {
    val pin = sl.pin
    val profile = sl.session.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 1. Animated landscape hero
        HeroScene(Modifier.fillMaxWidth(), height = 270.dp) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mhoroi · Sawubona", color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Your livestock hub", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                Text("Markets, vets, water and weather. Works without a signal.", color = Color.White.copy(alpha = 0.95f), fontSize = 15.sp)
            }
        }

        // 2. Status card overlapping the hero
        Entrance(0, Modifier.padding(horizontal = 16.dp).offset(y = (-34).dp)) {
            StatusCard(online = online, signedIn = sl.session.isSignedIn, phone = profile?.phone, hasPin = pin != null, boundaryHa = sl.boundary?.areaHa,
                onSignIn = { onOpen("signin") }, onPin = { onOpen("map") })
        }

        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-18).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Entrance(1) { Text("What do you need today?", style = MaterialTheme.typography.titleLarge, color = GreenDark) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Entrance(2, Modifier.weight(1f)) { ActionTile("Farm Insights", "Markets, vets, water", Icons.Filled.Analytics, Gradients.Mint, { onOpen("insights") }) }
                Entrance(3, Modifier.weight(1f)) { ActionTile("My Records", "Health, feed, sales", Icons.Filled.EditNote, Gradients.Earth, { onOpen("records") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Entrance(4, Modifier.weight(1f)) { ActionTile("Sell livestock", "List your animals", Icons.Filled.Sell, Gradients.Sun, { onOpen("market") }) }
                Entrance(5, Modifier.weight(1f)) { ActionTile("Disease alerts", "Notices and reports", Icons.Filled.Warning, Gradients.Rose, { onOpen("report") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Entrance(6, Modifier.weight(1f)) { ActionTile("Learn", "Guides and advisor", Icons.Filled.MenuBook, Gradients.Sky, { onOpen("learn") }) }
                Entrance(7, Modifier.weight(1f)) { ActionTile("Sync status", "What is saved", Icons.Filled.Sync, Gradients.Dusk, { onOpen("sync") }) }
            }

            Entrance(8) { TipCard() }

            Entrance(9) { Text("Pilot districts", style = MaterialTheme.typography.titleLarge, color = GreenDark) }
            Entrance(10) { DistrictStrip(sl) }
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(online: Boolean, signedIn: Boolean, phone: String?, hasPin: Boolean, boundaryHa: Double?, onSignIn: () -> Unit, onPin: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "dot")
    val dot by inf.animateFloat(0.7f, 1.4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dotScale")
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), elevation = CardDefaults.cardElevation(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(14.dp).scale(dot).background((if (online) Emerald else OfflineOrange).copy(alpha = 0.3f), CircleShape))
                    Box(Modifier.size(8.dp).background(if (online) Emerald else OfflineOrange, CircleShape))
                }
                Text(if (online) "Online" else "Offline: everything still works", fontWeight = FontWeight.Bold, color = if (online) Color(0xFF0E7C46) else Color(0xFFB85300))
                Box(Modifier.weight(1f))
                Text(if (signedIn) (phone ?: "Signed in") else "Guest", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5E6B63))
            }
            if (!signedIn) {
                val s = pulseScale()
                Row(
                    Modifier.fillMaxWidth().scale(s).clip(RoundedCornerShape(18.dp)).background(Gradients.Sun).bounceClick(onSignIn).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Login, null, tint = Color(0xFF3B2300))
                    Column(Modifier.weight(1f)) {
                        Text("Sign in to sync", fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B2300), fontSize = 17.sp)
                        Text("Your records stay safe on this phone either way", color = Color(0xFF5A3300), fontSize = 13.sp)
                    }
                }
            } else if (!hasPin) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Gradients.Mint).bounceClick(onPin).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Place, null, tint = Color.White)
                    Column(Modifier.weight(1f)) {
                        Text("Mark your farm boundary", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 17.sp)
                        Text("Draw it on the map or upload a shapefile", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            } else {
                Text(if (boundaryHa != null) "Farm boundary saved: %.1f hectares. Open Farm Insights for your markets, water and risks.".format(boundaryHa) else "Farm pin set. Open Farm Insights for your markets, water and risks.", color = Color(0xFF3D4A42))
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: ImageVector, brush: Brush, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "tile")
    val spin by inf.animateFloat(-8f, 8f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "spin")
    Box(
        modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(24.dp)).background(brush).bounceClick(onClick),
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.20f), modifier = Modifier.size(104.dp).align(Alignment.BottomEnd).offset(x = 22.dp, y = 22.dp).rotate(spin))
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 20.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TipCard() {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(6500); i = (i + 1) % tips.size } }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFF1C9), Color(0xFFFFD79A))))) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) { Text("!", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFF5A3300)) }
            Column {
                Text("Farm tip", fontWeight = FontWeight.ExtraBold, color = Color(0xFF8A4B00), fontSize = 13.sp, letterSpacing = 1.sp)
                AnimatedContent(
                    targetState = i, label = "tip",
                    transitionSpec = { (fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) togetherWith (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 3 }) },
                ) { idx -> Text(tips[idx], color = Color(0xFF3B2A12), fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Start) }
            }
        }
    }
}

@Composable
private fun DistrictStrip(sl: ServiceLocator) {
    val items = listOf(
        Triple("Mhondoro-Ngezi", "wetter, mixed farming", listOf(Color(0xFF1FB36B), Color(0xFF0E7C46))),
        Triple("Gwanda", "ranching country", listOf(Color(0xFFFFB04D), Color(0xFFE2762B))),
        Triple("Beitbridge", "hot and dry border", listOf(Color(0xFFE8566D), Color(0xFFB02A4B))),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (name, note, colors) ->
            val cells = remember(name) { runCatching { sl.packs.cellCount(name) }.getOrDefault(0) }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(colors)).padding(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 17.sp)
                    Text(note, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    Box(Modifier.height(6.dp))
                    CountUp(cells, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("km² analysed", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
    }
}

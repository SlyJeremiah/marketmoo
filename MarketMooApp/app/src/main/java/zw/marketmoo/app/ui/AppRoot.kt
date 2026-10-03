package zw.marketmoo.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.ui.account.AccountScreen
import zw.marketmoo.app.ui.auth.SignInScreen
import zw.marketmoo.app.ui.help.HelpScreen
import zw.marketmoo.app.ui.report.ReportScreen
import zw.marketmoo.app.ui.home.HomeScreen
import zw.marketmoo.app.ui.insights.InsightsScreen
import zw.marketmoo.app.ui.learn.LearnScreen
import zw.marketmoo.app.ui.map.MapScreen
import zw.marketmoo.app.ui.market.MarketScreen
import zw.marketmoo.app.ui.records.RecordsScreen
import zw.marketmoo.app.ui.sync.SyncStatusScreen
import zw.marketmoo.app.ui.theme.Green
import zw.marketmoo.app.ui.theme.InfoBlue
import zw.marketmoo.app.ui.theme.OfflineOrange

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("market", "Market", Icons.Filled.Storefront),
    Tab("map", "Map", Icons.Filled.Map),
    Tab("learn", "Learn", Icons.Filled.MenuBook),
    Tab("help", "Help", Icons.Filled.SupportAgent),
)

@Composable
fun rememberOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(isOnline(ctx)) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = isOnline(ctx) }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return state
}

private fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(sl: ServiceLocator) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val online by rememberOnline()
    val pending by sl.db.sync().observePendingCount().collectAsState(initial = 0)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MarketMoo") },
                actions = {
                    ConnectionChip(online, pending) { nav.navigate("sync") }
                    IconButton(onClick = { nav.navigate("account") }) { Icon(Icons.Filled.AccountCircle, contentDescription = "Account", tint = Color.White) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Green, titleContentColor = Color.White),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding).fillMaxSize()) {
            composable("home") { HomeScreen(sl, onOpen = { nav.navigate(it) }) }
            composable("signin") { SignInScreen(sl, onDone = { nav.popBackStack() }) }
            composable("account") { AccountScreen(sl, onSignIn = { nav.navigate("signin") }) }
            composable("report") { ReportScreen(sl, onOpenMap = { nav.navigate("map") }) }
            composable("market") { MarketScreen(sl) }
            composable("map") { MapScreen(sl, onInsights = { nav.navigate("insights") }) }
            composable("learn") { LearnScreen(onReport = { nav.navigate("report") }) }
            composable("help") { HelpScreen() }
            composable("insights") { InsightsScreen(sl, onOpenMap = { nav.navigate("map") }) }
            composable("records") { RecordsScreen(sl) }
            composable("sync") { SyncStatusScreen(sl, online) }
        }
    }
}

@Composable
private fun ConnectionChip(online: Boolean, pending: Int, onClick: () -> Unit) {
    val color = if (online) InfoBlue else OfflineOrange
    Row(
        Modifier.padding(end = 12.dp).clickable(onClick = onClick).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            (if (online) "Online" else "Offline") + if (pending > 0) " · $pending pending" else "",
            color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}

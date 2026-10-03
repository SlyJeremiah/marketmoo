package zw.marketmoo.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zw.marketmoo.app.ServiceLocator

@Composable
fun HomeScreen(sl: ServiceLocator, onOpen: (String) -> Unit) {
    val pin = sl.pin
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Welcome to MarketMoo", style = MaterialTheme.typography.titleLarge)
        if (!sl.session.isSignedIn) Tile("Sign in to sync", "Your records and listings are safe on this phone. Sign in with your phone number so they also reach the server.", Icons.Filled.Sync) { onOpen("signin") }
        Text(
            "Your livestock hub: works without a signal. Records and listings are saved on this phone first and sync when you are connected.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Tile("Farm Insights", if (pin == null) "Set your farm pin on the Map, then see markets, vets, water and disease risk." else "Pin set at %.3f, %.3f".format(pin.lat, pin.lon), Icons.Filled.Analytics) { onOpen("insights") }
        Tile("My Records", "Health, breeding, feed, sales and expenses. Works offline.", Icons.Filled.EditNote) { onOpen("records") }
        Tile("Sell livestock", "Create a listing (saved locally, synced later).", Icons.Filled.Sell) { onOpen("market") }
        Tile("Sync status", "See what is Pending, retry, and the data-saver switch.", Icons.Filled.Sync) { onOpen("sync") }
        Text("Planned for the next build: weather, finance matching, Market Hub pools, outbreak reporting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun Tile(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

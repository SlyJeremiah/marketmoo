package zw.marketmoo.app.ui.sync

import zw.marketmoo.app.ui.components.GradientHeader
import zw.marketmoo.app.ui.theme.Gradients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zw.marketmoo.app.BuildConfig
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.sync.SyncWorker
import zw.marketmoo.app.ui.records.StatusBadge

@Composable
fun SyncStatusScreen(sl: ServiceLocator, online: Boolean) {
    val ctx = LocalContext.current
    val ops by sl.db.sync().observeRecent().collectAsState(initial = emptyList())
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
        item { GradientHeader("Sync status", "See what is saved and what is waiting", Icons.Filled.Sync, brush = Gradients.Dusk) }
        item {
            Text(
                (if (online) "Online. " else "Offline. ") +
                    if (BuildConfig.API_BASE_URL.isBlank()) "No server is configured in this build, so items stay Pending." else "Server: ${BuildConfig.API_BASE_URL}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Button(onClick = { SyncWorker.enqueue(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Sync now") } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Demo sync (simulated server)", style = MaterialTheme.typography.titleMedium)
                    Text("For presentations only: marks items Synced without contacting any server.", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = sl.settings.demoSync, onCheckedChange = { sl.settings.updateDemoSync(it); if (it) SyncWorker.enqueue(ctx) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Data Saver", style = MaterialTheme.typography.titleMedium)
                    Text("Skips photos and background refresh to save mobile data.", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = sl.settings.dataSaver, onCheckedChange = { sl.settings.updateDataSaver(it) })
            }
        }
        item { Text("Recent operations", style = MaterialTheme.typography.titleMedium) }
        if (ops.isEmpty()) item { Text("Nothing queued.", style = MaterialTheme.typography.bodyMedium) }
        items(ops, key = { it.id }) { op ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${op.entity} · attempts ${op.attempts}", style = MaterialTheme.typography.bodyMedium)
                    if (op.lastError.isNotBlank()) Text(op.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    StatusBadge(op.status)
                }
            }
        }
    }
}

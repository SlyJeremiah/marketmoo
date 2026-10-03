package zw.marketmoo.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.net.ApiResult
import zw.marketmoo.app.data.pack.PackUpdater

private val roles = listOf("farmer", "buyer", "vet")
private val districts = listOf("mhondoro-ngezi" to "Mhondoro-Ngezi", "gwanda" to "Gwanda", "beitbridge" to "Beitbridge", "other" to "Other")

@Composable
fun AccountScreen(sl: ServiceLocator, onSignIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = sl.session.profile
    var role by remember(profile) { mutableStateOf(profile?.role ?: "farmer") }
    var district by remember(profile) { mutableStateOf(profile?.district ?: "other") }
    var url by remember { mutableStateOf(sl.settings.serverUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var packMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", style = MaterialTheme.typography.titleLarge)
        if (profile == null) {
            Text("You are not signed in. Records and listings are saved on this phone and will sync after you sign in.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in with phone number") }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.phone + if (profile.verified) "  (verified)" else "", style = MaterialTheme.typography.titleMedium)
                    Text("I am a", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(roles) { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.replaceFirstChar(Char::uppercase)) }) } }
                    Text("My district", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(districts) { (k, v) -> FilterChip(selected = district == k, onClick = { district = k }, label = { Text(v) }) } }
                    Button(onClick = {
                        scope.launch {
                            when (val r = sl.api.updateProfile(role, district, profile.language)) {
                                is ApiResult.Ok -> { sl.session.cache(r.value); status = "Saved." }
                                is ApiResult.Failure -> status = if (r.offline) "No connection. Try again when online." else r.message
                            }
                        }
                    }) { Text("Save profile") }
                }
            }
        }

        Text("Server", style = MaterialTheme.typography.titleMedium)
        Text("Emulator: http://10.0.2.2:8000. On a real phone use your server address (HTTPS), for example the Render URL.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(url, { url = it }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            sl.settings.updateServerUrl(url)
            scope.launch { status = if (sl.api.ping()) "Server reachable." else "Server not reachable (it may be waking up: try again in a minute)." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save and test connection") }

        Text("Data packs", style = MaterialTheme.typography.titleMedium)
        sl.packs.districts.forEach { d -> Text("$d: version ${sl.packs.version(d)}", style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = {
            packMsg = "Checking..."
            scope.launch {
                when (val c = PackUpdater.check(sl)) {
                    is ApiResult.Failure -> packMsg = if (c.offline) "No connection." else c.message
                    is ApiResult.Ok -> {
                        if (c.value.isEmpty()) { packMsg = "Packs are up to date."; return@launch }
                        var allOk = true
                        for (u in c.value) {
                            packMsg = "Downloading ${u.info.district} (${u.info.size / 1024} KB)..."
                            val ok = withContext(Dispatchers.IO) { PackUpdater.download(ctx, sl, u) { } }
                            allOk = allOk && ok
                        }
                        packMsg = if (allOk) "Packs updated." else "Download interrupted. Try again: it resumes where it stopped."
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Check for pack updates") }
        packMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

        if (profile != null) {
            OutlinedButton(onClick = { sl.session.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            Button(onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                Text("Delete my account and data on the server")
            }
        }
        OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) { Text("Erase everything stored on this phone") }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete your account?") },
        text = { Text("This removes your account, listings, records and reports from the server. It cannot be undone. Data on this phone is not touched.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    when (val r = sl.api.deleteAccount()) {
                        is ApiResult.Ok -> { sl.session.signOut(); status = "Account deleted." }
                        is ApiResult.Failure -> status = if (r.offline) "No connection. Try again when online." else r.message
                    }
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Erase data on this phone?") },
        text = { Text("Records, listings and queued changes stored here are deleted. Anything already synced stays on the server.") },
        confirmButton = {
            TextButton(onClick = {
                confirmWipe = false
                scope.launch { withContext(Dispatchers.IO) { sl.db.clearAllTables() }; status = "Local data erased." }
            }) { Text("Erase") }
        },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
    )
}

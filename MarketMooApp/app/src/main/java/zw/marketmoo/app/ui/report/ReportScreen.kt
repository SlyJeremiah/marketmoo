package zw.marketmoo.app.ui.report

import zw.marketmoo.app.ui.components.GradientHeader
import zw.marketmoo.app.ui.theme.Gradients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.net.ApiResult
import zw.marketmoo.app.data.net.OutbreakDto

private val species = listOf("Cattle", "Goats", "Sheep", "Pigs", "Poultry")

@Composable
fun ReportScreen(sl: ServiceLocator, onOpenMap: () -> Unit) {
    val scope = rememberCoroutineScope()
    var notices by remember { mutableStateOf<List<OutbreakDto>?>(null) }
    var noticeError by remember { mutableStateOf<String?>(null) }
    var sp by remember { mutableStateOf("Cattle") }
    var suspected by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var desc by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val pin = sl.pin

    LaunchedEffect(Unit) {
        when (val r = sl.api.activeOutbreaks()) {
            is ApiResult.Ok -> notices = r.value
            is ApiResult.Failure -> noticeError = if (r.offline) "Offline: active notices cannot be loaded now. Check your area on Farm Insights (works offline)." else r.message
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientHeader("Disease alerts", "Active notices and suspected-outbreak reports", Icons.Filled.Warning, brush = Gradients.Rose)
        Text("Active notices", style = MaterialTheme.typography.titleMedium)
        noticeError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
        notices?.let { list ->
            if (list.isEmpty()) Text("No active notices.", style = MaterialTheme.typography.bodyMedium)
            list.forEach { n ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${n.disease}: ${n.district}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text("Since ${n.started}. Control zone ${n.controlKm.toInt()} km, surveillance ${n.surveillanceKm.toInt()} km. ${n.summary}", style = MaterialTheme.typography.bodyMedium)
                        if (n.source.isNotBlank()) Text("Source: ${n.source}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text("Report a suspected outbreak", style = MaterialTheme.typography.titleMedium)
        Text("For anthrax, African Swine Fever or foot-and-mouth disease, call the Department of Veterinary Services at once. This report is checked before anything is published, and farms are never named.", style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(species) { s -> FilterChip(selected = sp == s, onClick = { sp = s }, label = { Text(s) }) } }
        OutlinedTextField(suspected, { suspected = it }, label = { Text("What do you suspect? (for example anthrax)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text("Animals affected") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(desc, { desc = it }, label = { Text("What did you see?") }, modifier = Modifier.fillMaxWidth())
        if (pin == null) {
            Text("Mark your farm on the map so the report has a location.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text("Open map") }
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val n = count.toIntOrNull()
            when {
                pin == null -> err = "Set your farm pin first."
                suspected.isBlank() || n == null || n < 1 -> err = "Say what you suspect and how many animals."
                else -> {
                    err = null
                    scope.launch { sl.repo.addOutbreakReport(sp, suspected.trim(), n, pin.lat, pin.lon, desc.trim()); saved = true }
                    suspected = ""; desc = ""
                }
            }
        }) { Text("Save report") }
        if (saved) Text("Saved on this phone. It is sent when you are signed in and online.", color = MaterialTheme.colorScheme.primary)
    }
}

package zw.marketmoo.app.ui.records

import zw.marketmoo.app.ui.components.GradientHeader
import zw.marketmoo.app.ui.theme.Gradients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.local.SyncStatus
import zw.marketmoo.app.ui.theme.Danger
import zw.marketmoo.app.ui.theme.Green
import zw.marketmoo.app.ui.theme.OfflineOrange

private val types = listOf("Health / Vaccination", "Breeding", "Feed / Water", "Sale", "Expense", "Mortality")

@Composable
fun RecordsScreen(sl: ServiceLocator) {
    val scope = rememberCoroutineScope()
    val records by sl.repo.records.collectAsState(initial = emptyList())
    var type by remember { mutableStateOf(types[0]) }
    var animal by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
        item { GradientHeader("Farm Records", "Saved on your phone first, even with no signal", Icons.Filled.EditNote, brush = Gradients.Earth) }
        item { Text("Saved on this phone first (encrypted). Works with no signal.", style = MaterialTheme.typography.bodyMedium) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
            }
        }
        item { OutlinedTextField(animal, { animal = it }, label = { Text("Animal or group (e.g. Cow A12)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(cost, { cost = it }, label = { Text("Cost in USD (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Column {
                error?.let { Text(it, color = Danger) }
                Button(onClick = {
                    val parsed = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                    when {
                        animal.isBlank() -> error = "Enter the animal or group."
                        parsed == null -> error = "Date must look like 2026-10-02."
                        else -> {
                            error = null
                            scope.launch { sl.repo.addRecord(type, animal.trim(), parsed.toString(), cost.toDoubleOrNull(), notes.trim()) }
                            animal = ""; cost = ""; notes = ""
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save record") }
            }
        }
        item { Text("Saved records", style = MaterialTheme.typography.titleMedium) }
        if (records.isEmpty()) item { Text("No records yet.", style = MaterialTheme.typography.bodyMedium) }
        items(records, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(r.type, style = MaterialTheme.typography.titleMedium, color = Green)
                        Text("${r.animal} · ${r.date}", style = MaterialTheme.typography.bodyMedium)
                        if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodyMedium)
                        StatusBadge(r.status)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        r.cost?.let { Text("$%.2f".format(it), style = MaterialTheme.typography.titleMedium) }
                        IconButton(onClick = { scope.launch { sl.repo.deleteRecord(r.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Delete record", tint = Danger) }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        SyncStatus.SYNCED.name -> "Synced" to Green
        SyncStatus.FAILED.name -> "Needs attention" to Danger
        else -> "Pending" to OfflineOrange
    }
    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp).background(color, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
}

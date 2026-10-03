package zw.marketmoo.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class Entry(val title: String, val keywords: List<String>, val body: String, val sources: List<String>)

private fun load(ctx: android.content.Context): Pair<List<Entry>, List<Entry>> {
    val root = JSONObject(ctx.assets.open("kb.json").bufferedReader().use { it.readText() })
    fun parse(key: String) = (0 until root.getJSONArray(key).length()).map { i ->
        val o = root.getJSONArray(key).getJSONObject(i)
        Entry(
            o.getString("title"),
            o.optJSONArray("keywords")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } } ?: emptyList(),
            o.getString("body"),
            o.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }
    return parse("advisor") to parse("guides")
}

@Composable
fun LearnScreen(onReport: () -> Unit) {
    val ctx = LocalContext.current
    val (advisor, guides) = remember { load(ctx) }
    var tab by remember { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pest and disease") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Guides") })
        }
        if (tab == 0) Advisor(advisor, onReport) else Guides(guides)
    }
}

@Composable
private fun Advisor(kb: List<Entry>, onReport: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<Entry?>(null) }
    var asked by remember { mutableStateOf("") }
    fun ask(text: String) {
        asked = text
        val low = text.lowercase()
        answer = kb.map { e -> e to e.keywords.filter { low.contains(it) }.sumOf { it.length } }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("Disease notices and report an outbreak") } }
        item { Text("Works offline. General information only: always confirm treatment with a licensed vet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(kb.take(8)) { e -> AssistChip(onClick = { q = e.title; ask(e.title) }, label = { Text(e.title) }) }
            }
        }
        item { OutlinedTextField(q, { q = it }, label = { Text("Ask about a pest, symptom or disease") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Button(onClick = { ask(q) }, modifier = Modifier.fillMaxWidth()) { Text("Ask") } }
        if (asked.isNotBlank()) item {
            val a = answer
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a == null) Text("I do not have an answer for that yet. Try: January disease, ticks, Newcastle, African Swine Fever, foot rot, anthrax. For anything urgent, call a vet or the Department of Veterinary Services.", style = MaterialTheme.typography.bodyLarge)
                    else {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(a.body, style = MaterialTheme.typography.bodyLarge)
                        if (a.sources.isNotEmpty()) Text("Sources: " + a.sources.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Draft content: needs veterinary review before field use.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Guides(guides: List<Entry>) {
    var open by remember { mutableStateOf<Int?>(null) }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(guides.size) { i ->
            Card(onClick = { open = if (open == i) null else i }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(guides[i].title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (open == i) {
                        Text(guides[i].body, style = MaterialTheme.typography.bodyLarge)
                        Text("Draft content: needs veterinary review.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

package zw.marketmoo.app.ui.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class Expert(val name: String, val role: String, val area: String, val phone: String)

/** DEMO entries copied from the HTML prototype. They are not real contacts; replace with verified directory data. */
private val experts = listOf(
    Expert("Dr. Tendai Mutasa", "Veterinarian", "Harare", "+263772000001"),
    Expert("Mrs. Chipo Nyathi", "Extension Officer", "Bulawayo", "+263772000002"),
    Expert("Mr. Farai Dube", "Animal Health Technician", "Masvingo", "+263772000003"),
    Expert("Dr. Rudo Moyo", "Veterinarian", "Midlands", "+263772000004"),
)

@Composable
fun HelpScreen() {
    val ctx = LocalContext.current
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Vets and experts", style = MaterialTheme.typography.titleLarge) }
        item {
            Text(
                "Demo entries only (not real people). In the pilot this list is sorted by distance and every expert is verified. " +
                    "For suspected anthrax, African Swine Fever or foot-and-mouth disease, contact the Department of Veterinary Services at once.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }
        items(experts) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.name + " (demo)", style = MaterialTheme.typography.titleMedium)
                    Text("${e.role} · ${e.area}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${e.phone}"))) }) { Text("Call") }
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${e.phone}"))) }) { Text("SMS") }
                    }
                }
            }
        }
    }
}

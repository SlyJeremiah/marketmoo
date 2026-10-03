package zw.marketmoo.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zw.marketmoo.app.FarmPin
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.pack.Cell

private val suit = listOf("n/a", "very low", "low", "moderate", "high", "very high")
private val tick = listOf("low", "moderate", "high")

/** The four spatial analyses for the farm pin, read from the district pack. No network, no routing on the phone. */
@Composable
fun InsightsScreen(sl: ServiceLocator, onOpenMap: () -> Unit) {
    val pin = sl.pin
    val cell: Cell? = remember(pin) { pin?.let { sl.packs.lookup(it.lat, it.lon) } }
    val vet = remember(pin) { pin?.let { sl.packs.nearestFeature(it.lat, it.lon, "DVS") ?: sl.packs.nearestFeature(it.lat, it.lon, "vet") } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Farm Insights", style = MaterialTheme.typography.titleLarge)
        if (pin == null) {
            Text("Set your farm pin first. Tap the map, or pick a starting point:", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text("Open map and tap my farm") }
            sl.packs.districts.forEach { d ->
                OutlinedButton(onClick = {
                    sl.packs.features(d, "DVS").firstOrNull()?.let { sl.pin = FarmPin(it.lat, it.lon) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Demo pin: main town of $d") }
            }
            return@Column
        }
        Text("Pin: %.4f, %.4f".format(pin.lat, pin.lon), style = MaterialTheme.typography.bodyMedium)
        if (cell == null) {
            Text("This pin is outside the three pilot districts (Mhondoro-Ngezi, Gwanda, Beitbridge), so there is no data pack for it.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { sl.pin = null }) { Text("Clear pin") }
            return@Column
        }
        Text("District: ${cell.district} (grid cell %.1f km from the pin)".format(cell.distanceToPinKm), style = MaterialTheme.typography.bodyMedium)

        InsightCard(
            "1  Market access",
            if (cell.minutesToMajorMarket == null) "No road route found to a major market from this cell."
            else "Nearest major market: ${cell.nearestMajorMarket}, about ${cell.minutesToMajorMarket} minutes by road (assumed truck speeds)." +
                (cell.minutesToLocalPoint?.let { " Nearest local trading point: $it minutes." } ?: ""),
            "Access index ${cell.marketAccessIndex ?: "n/a"} of 100 (percentile within the three districts).",
        )
        val covText = listOf("Covered (30 minutes or less)", "Partly covered (30 to 60 minutes)", "Underserved (over 60 minutes)")[cell.coverage.coerceIn(0, 2)]
        InsightCard(
            "2  Vet and service access",
            vet?.let { "Nearest service point: ${it.first.name}, about %.0f km away in a straight line. Road time to the nearest service point: ${cell.minutesToService?.let { m -> "$m minutes" } ?: "over 120 minutes"}.".format(it.second) }
                ?: "No service point in the pack.",
            "$covText. Service points are PROXIES (assumed district office); real vet and dip-tank data are not yet loaded.",
        )
        InsightCard(
            "3  Water and grazing",
            "Reliable water in the dry season: ${cell.waterDryKm?.let { "%.1f km".format(it) } ?: "unknown"}. Grazing suitability: ${suit[cell.suitDry.coerceIn(0, 5)]} in the dry season, ${suit[cell.suitWet.coerceIn(0, 5)]} in the wet season.",
            if (cell.district == "Mhondoro-Ngezi") "Water distances for Mhondoro-Ngezi use satellite water only (OpenStreetMap water features were not retrieved)." else "Based on OpenStreetMap water points, satellite surface water, rainfall, vegetation, slope and land cover.",
        )
        InsightCard(
            "4  Disease risk",
            "Tick-borne disease suitability from rainfall: ${tick[cell.tickClass.coerceIn(0, 2)]}. " +
                (if (cell.inSurveillanceRing) "Inside the 40 km surveillance ring of the reported FMD event." else "Outside the reported FMD event rings (January 2026, Mangwe)."),
            if (cell.district == "Gwanda" || cell.district == "Beitbridge") "This district is in the May 2026 FMD vaccination campaign area: confirm movement rules with the Department of Veterinary Services before buying or selling cattle." else "Confirm any movement rules with the Department of Veterinary Services.",
        )
        OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text("Show on map") }
        OutlinedButton(onClick = { sl.pin = null }, modifier = Modifier.fillMaxWidth()) { Text("Clear pin") }
        Text("Pack ${sl.packs.version(cell.district)}. School-trial data from open sources; see the Design Document, Section 7.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
private fun InsightCard(title: String, main: String, note: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(main, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

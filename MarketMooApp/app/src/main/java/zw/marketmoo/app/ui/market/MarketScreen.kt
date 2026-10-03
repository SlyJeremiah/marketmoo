package zw.marketmoo.app.ui.market

import zw.marketmoo.app.ui.components.GradientHeader
import zw.marketmoo.app.ui.theme.Gradients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.net.ApiResult
import zw.marketmoo.app.data.net.ListingDto
import zw.marketmoo.app.data.net.PoolDto
import zw.marketmoo.app.ui.records.StatusBadge
import zw.marketmoo.app.util.Geo

private val species = listOf("Cattle", "Goats", "Sheep", "Pigs", "Poultry")

@Composable
fun MarketScreen(sl: ServiceLocator) {
    var tab by remember { mutableStateOf(0) }
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Scaffold(floatingActionButton = { if (tab != 2) ExtendedFloatingActionButton(onClick = { showForm = true }) { Text("Sell livestock") } }) { pad ->
        Column(Modifier.padding(pad)) {
            GradientHeader("Market", "Buy, sell and pool your livestock", Icons.Filled.Storefront, modifier = Modifier.padding(16.dp), brush = Gradients.Sun)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Browse") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mine") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Pools") })
            }
            when (tab) {
                0 -> FeedTab(sl)
                1 -> MineTab(sl)
                else -> PoolsTab(sl)
            }
        }
    }
    if (showForm) SellDialog(sl, onDismiss = { showForm = false }, onSave = { v ->
        val pin = sl.pin!!
        scope.launch { sl.repo.addListing(v.species, v.breed, v.sex, v.age, v.qty, v.price, v.ward, pin.lat, pin.lon, v.phone, v.photo) }
        showForm = false
        tab = 1
    })
    @Suppress("UNUSED_EXPRESSION") ctx
}

@Composable
private fun FeedTab(sl: ServiceLocator) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    var feed by remember { mutableStateOf<List<ListingDto>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val pin = sl.pin
    LaunchedEffect(filter, pin, sl.session.token) {
        loading = true
        when (val r = sl.api.listings(pin?.lat, pin?.lon, filter, sl.settings.dataSaver)) {
            is ApiResult.Ok -> { feed = r.value; message = if (r.value.isEmpty()) "No live listings match yet." else null }
            is ApiResult.Failure -> message = if (r.offline) "Offline or server not reachable. Showing sample listings." else r.message
        }
        loading = false
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(species) { s -> FilterChip(selected = filter == s, onClick = { filter = if (filter == s) null else s }, label = { Text(s) }) }
            }
        }
        item { Text(if (pin == null) "Set your farm pin on the Map to sort by distance." else "Nearest first from your pin.", style = MaterialTheme.typography.bodyMedium) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) } }
        val live = feed.orEmpty()
        items(live, key = { it.id }) { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    l.photoUrl?.let { RemoteImage(sl, it) }
                    Text("${l.species.replaceFirstChar(Char::uppercase)} · ${l.breed} ×${l.qty}" + if (l.verified) "  ✓ verified" else "", style = MaterialTheme.typography.titleMedium)
                    Text("$${"%.0f".format(l.priceUsd)} each · ${l.sex}, ${l.ageMonths} months · ${l.ward}" + (l.distanceKm?.let { " · $it km" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${l.phone}"))) }) { Text("Call") }
                        OutlinedButton(onClick = {
                            val msg = Uri.encode("Hello, I am interested in your ${l.species} (${l.breed}) at $${"%.0f".format(l.priceUsd)}. Sent via MarketMoo")
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${l.phone.filter { it.isDigit() }}?text=$msg")))
                        }) { Text("WhatsApp") }
                    }
                }
            }
        }
        if (feed == null || live.isEmpty()) {
            item { Text("Sample listings (demo data, not real sellers)", style = MaterialTheme.typography.titleMedium) }
            items(demoListings.filter { filter == null || it.species == filter }) { d -> DemoCard(d) }
        }
    }
}

@Composable
private fun RemoteImage(sl: ServiceLocator, url: String) {
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = sl.api.downloadBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    img?.let { Image(it, contentDescription = "Listing photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp)) }
}

private data class Demo(val title: String, val species: String, val price: Int, val district: String, val sex: String, val age: String, val phone: String, val seller: String)

/** SAMPLE data from the HTML prototype so the screen is not empty offline. Never shown once real listings exist. */
private val demoListings = listOf(
    Demo("Brahman bull", "Cattle", 850, "Harare", "Male", "24 months", "+263771000001", "John Moyo (sample)"),
    Demo("Boer goats x5", "Goats", 420, "Matabeleland South", "Mixed", "18 months", "+263771000002", "Mary Chirwa (sample)"),
    Demo("Large White pigs x3", "Pigs", 310, "Mashonaland West", "Mixed", "8 months", "+263771000003", "Peter Ncube (sample)"),
    Demo("Dorper sheep x4", "Sheep", 380, "Matabeleland North", "Mixed", "14 months", "+263771000005", "Samuel Dube (sample)"),
)

@Composable
private fun DemoCard(d: Demo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium)
            Text("$${d.price} · ${d.sex}, ${d.age} · ${d.district}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MineTab(sl: ServiceLocator) {
    val own by sl.repo.listings.collectAsState(initial = emptyList())
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item { Text("Listings you created (saved on this phone; they appear to buyers after a manager approves them).", style = MaterialTheme.typography.bodyMedium) }
        if (!sl.session.isSignedIn) item { Text("Sign in (Account) so these sync.", color = MaterialTheme.colorScheme.error) }
        if (own.isEmpty()) item { Text("None yet. Tap Sell livestock.", style = MaterialTheme.typography.bodyMedium) }
        items(own, key = { it.id }) { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${l.species} · ${l.breed} ×${l.qty}", style = MaterialTheme.typography.titleMedium)
                    Text("$${"%.0f".format(l.priceUsd)} each · ${l.sex}, ${l.ageMonths} months · ${l.ward}", style = MaterialTheme.typography.bodyMedium)
                    Text("Public position (blurred to about 1 km): %.2f, %.2f".format(Geo.blurToKm(l.lat), Geo.blurToKm(l.lon)), style = MaterialTheme.typography.bodyMedium)
                    if (l.photoPath != null) Text("Photo waiting to upload", style = MaterialTheme.typography.bodyMedium)
                    StatusBadge(l.status)
                }
            }
        }
    }
}

@Composable
private fun PoolsTab(sl: ServiceLocator) {
    val scope = rememberCoroutineScope()
    var pools by remember { mutableStateOf<List<PoolDto>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf<PoolDto?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        when (val r = sl.api.pools()) {
            is ApiResult.Ok -> { pools = r.value; message = if (r.value.isEmpty()) "No open pools." else null }
            is ApiResult.Failure -> message = if (r.offline) "Offline: pools cannot be loaded now." else r.message
        }
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item { Text("Pool your animals with other farmers to meet a buyer's volume. Pool entries marked SAMPLE are demo data.", style = MaterialTheme.typography.bodyMedium) }
        message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.secondary) } }
        items(pools.orEmpty(), key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text("${p.committed} of ${p.target} ${p.species} (${p.progressPct}%) · deadline ${p.deadline}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { p.progressPct / 100f }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { if (sl.session.isSignedIn) joining = p else message = "Sign in (Account) to join a pool." }) { Text("Join this pool") }
                }
            }
        }
    }
    joining?.let { p ->
        var qty by remember { mutableStateOf("") }
        var age by remember { mutableStateOf("") }
        var ready by remember { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { joining = null },
            title = { Text("Join ${p.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("How many animals") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Average age in months") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(ready, { ready = it }, label = { Text("Ready from (YYYY-MM-DD)") }, singleLine = true)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val q = qty.toIntOrNull(); val a = age.toIntOrNull(); val d = runCatching { LocalDate.parse(ready) }.getOrNull()
                    if (q == null || q < 1 || a == null || d == null) { err = "Check the numbers and the date."; return@Button }
                    scope.launch {
                        when (val r = sl.api.commitToPool(p.id, q, a, d.toString())) {
                            is ApiResult.Ok -> { joining = null; message = "You joined the pool."; reload++ }
                            is ApiResult.Failure -> err = if (r.offline) "No connection. Try again when online." else r.message // 409: pool already closed
                        }
                    }
                }) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { joining = null }) { Text("Cancel") } },
        )
    }
}

private class SellValues(val species: String, val breed: String, val sex: String, val age: Int, val qty: Int, val price: Double, val ward: String, val phone: String, val photo: Uri?)

@Composable
private fun SellDialog(sl: ServiceLocator, onDismiss: () -> Unit, onSave: (SellValues) -> Unit) {
    var sp by remember { mutableStateOf("Cattle") }
    var breed by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Mixed") }
    var age by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var ward by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(sl.session.profile?.phone ?: "") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    val pin = sl.pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell livestock") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pin == null) Text("Mark your farm boundary (or drop a pin) on the Map first, so buyers can see your area (blurred to about 1 km). Your outline is never shown.", color = MaterialTheme.colorScheme.error)
                else Text(if (sl.boundary != null) "Using the centre of your farm boundary" else "Farm pin: %.3f, %.3f".format(pin.lat, pin.lon), style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(species) { s -> FilterChip(selected = sp == s, onClick = { sp = s }, label = { Text(s) }) } }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("Male", "Female", "Mixed")) { s -> FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(s) }) } }
                OutlinedTextField(breed, { breed = it }, label = { Text("Breed") }, singleLine = true)
                OutlinedTextField(age, { age = it }, label = { Text("Age in months") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(price, { price = it }, label = { Text("Price per animal (USD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(ward, { ward = it }, label = { Text("Ward or village") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Contact phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (photo == null) "Add a photo (optional)" else "Photo chosen: change") }
                Text("Photos are shrunk on your phone and uploaded later" + if (sl.settings.dataSaver) " on Wi-Fi (Data Saver is on)." else ".", style = MaterialTheme.typography.bodyMedium)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = age.toIntOrNull(); val q = qty.toIntOrNull(); val p = price.toDoubleOrNull()
                when {
                    pin == null -> err = "Mark your farm boundary on the Map first."
                    breed.isBlank() || ward.isBlank() || phone.isBlank() -> err = "Fill breed, ward and phone."
                    a == null || q == null || q < 1 || p == null || p <= 0 -> err = "Check age, quantity and price."
                    else -> onSave(SellValues(sp, breed.trim(), sex, a, q, p, ward.trim(), phone.trim(), photo))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

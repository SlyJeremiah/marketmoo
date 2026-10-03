package zw.marketmoo.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import zw.marketmoo.app.data.Repository
import zw.marketmoo.app.data.local.AppDatabase
import zw.marketmoo.app.data.net.Api
import zw.marketmoo.app.data.net.Profile
import zw.marketmoo.app.data.pack.PackRepository
import androidx.compose.runtime.mutableStateListOf
import zw.marketmoo.app.util.FarmShape
import zw.marketmoo.app.util.LatLon
import zw.marketmoo.app.security.TokenStore

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("marketmoo_settings", Context.MODE_PRIVATE)
    var dataSaver by mutableStateOf(prefs.getBoolean("data_saver", true))
        private set
    var demoSync by mutableStateOf(prefs.getBoolean("demo_sync", false))
        private set
    /** Base URL of the MarketMoo API (the live trial server by default). */
    var serverUrl by mutableStateOf(prefs.getString("server_url", null) ?: BuildConfig.API_BASE_URL.ifBlank { "https://marketmoo-api.onrender.com" })
        private set

    fun updateDataSaver(v: Boolean) { dataSaver = v; prefs.edit().putBoolean("data_saver", v).apply() }
    fun updateDemoSync(v: Boolean) { demoSync = v; prefs.edit().putBoolean("demo_sync", v).apply() }
    fun updateServerUrl(v: String) { serverUrl = v.trim().trimEnd('/'); prefs.edit().putString("server_url", serverUrl).apply() }
}

data class FarmPin(val lat: Double, val lon: Double)

/** Who is signed in. The token is kept encrypted by [TokenStore]; the profile is cached in memory and refreshed from the server. */
class Session(context: Context) {
    private val store = TokenStore(context)
    private val prefs = context.getSharedPreferences("marketmoo_session", Context.MODE_PRIVATE)
    var token by mutableStateOf(store.load())
        private set
    var profile by mutableStateOf<Profile?>(
        prefs.getString("phone", null)?.let { Profile(it, prefs.getString("role", "farmer")!!, prefs.getString("language", "en")!!, prefs.getString("district", "other")!!, prefs.getBoolean("verified", false)) }
    )
        private set
    val isSignedIn get() = token != null

    fun signIn(token: String, p: Profile) { store.save(token); this.token = token; cache(p) }
    fun cache(p: Profile) {
        profile = p
        prefs.edit().putString("phone", p.phone).putString("role", p.role).putString("language", p.language).putString("district", p.district).putBoolean("verified", p.verified).apply()
    }
    fun signOut() { store.clear(); token = null; profile = null; prefs.edit().clear().apply() }
}

/** Tiny manual dependency container (Hilt is deliberately not used to keep the APK small). */
class ServiceLocator private constructor(context: Context) {
    val db: AppDatabase = AppDatabase.create(context)
    val settings = Settings(context)
    val session = Session(context)
    val api = Api(baseUrl = { settings.serverUrl }, token = { session.token })
    val repo = Repository(context, db)
    val packs = PackRepository(context)
    var pin by mutableStateOf<FarmPin?>(null)

    /** The saved farm outline, if any. The pin above is its centre, so every insight keeps working. */
    var boundary by mutableStateOf<FarmShape?>(null)
        private set

    /** Map drawing mode: corners tapped so far (kept here so they survive leaving the map screen). */
    var drawing by mutableStateOf(false)
    val draft = mutableStateListOf<LatLon>()

    suspend fun loadBoundary() {
        repo.loadBoundary()?.let { boundary = it; if (pin == null) pin = it.centroid.let { c -> FarmPin(c.lat, c.lon) } }
    }

    suspend fun saveBoundary(shape: FarmShape, source: String) {
        repo.saveBoundary(shape, source)
        boundary = shape
        pin = shape.centroid.let { FarmPin(it.lat, it.lon) }
    }

    suspend fun removeBoundary() {
        repo.deleteBoundary()
        boundary = null
    }

    companion object {
        @Volatile private var instance: ServiceLocator? = null
        fun get(context: Context): ServiceLocator =
            instance ?: synchronized(this) { instance ?: ServiceLocator(context.applicationContext).also { instance = it } }
    }
}

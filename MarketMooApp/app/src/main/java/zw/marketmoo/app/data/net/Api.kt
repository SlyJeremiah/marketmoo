package zw.marketmoo.app.data.net

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Result of an API call. Network trouble is a normal case for this app, so it is a value, not an exception. */
sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String) : ApiResult<Nothing>() {
        val offline get() = code == 0
        val unauthorized get() = code == 401 || code == 403
    }
}

data class Profile(val phone: String, val role: String, val language: String, val district: String, val verified: Boolean)
data class OtpResponse(val devCode: String?)
data class LoginResponse(val token: String, val profile: Profile, val created: Boolean)
data class ListingDto(
    val id: String, val species: String, val breed: String, val sex: String, val ageMonths: Int, val qty: Int, val priceUsd: Double,
    val ward: String, val lat: Double, val lon: Double, val phone: String, val verified: Boolean, val distanceKm: Double?, val photoUrl: String?,
)
data class PoolDto(val id: Int, val title: String, val species: String, val target: Int, val committed: Int, val progressPct: Int, val deadline: String)
data class OutbreakDto(val disease: String, val district: String, val started: String, val summary: String, val source: String, val controlKm: Double, val surveillanceKm: Double)
data class PackInfo(val district: String, val version: String, val size: Long, val sha256: String, val url: String)
data class SyncResult(val id: String, val status: String, val error: String?)
data class PresignDto(val key: String, val uploadUrl: String)

/**
 * Minimal JSON-over-HTTPS client built on HttpURLConnection (no extra libraries, keeps the APK small).
 * Timeouts are long on purpose: free-tier servers can take tens of seconds to wake up.
 */
class Api(private val baseUrl: () -> String, private val token: () -> String?) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val c = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("Accept", "application/json")
            if (auth) token()?.let { c.setRequestProperty("Authorization", "Token $it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) ApiResult.Ok(if (text.isBlank()) JSONObject() else JSONObject(text))
            else ApiResult.Failure(code, runCatching { JSONObject(text).optString("detail") }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP $code")
        } catch (e: Exception) {
            ApiResult.Failure(0, e.message ?: "No connection")
        }
    }

    private fun <T> ApiResult<JSONObject>.map(f: (JSONObject) -> T): ApiResult<T> = when (this) {
        is ApiResult.Ok -> try { ApiResult.Ok(f(value)) } catch (e: Exception) { ApiResult.Failure(-1, "Unexpected response") }
        is ApiResult.Failure -> this
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(baseUrl().trimEnd('/') + "/v1/ping").openConnection() as HttpURLConnection
            c.connectTimeout = 3_000; c.readTimeout = 3_000
            c.responseCode == 200
        } catch (e: Exception) { false }
    }

    suspend fun otpRequest(phone: String) =
        call("POST", "/v1/auth/otp/request", JSONObject().put("phone", phone), auth = false).map { OtpResponse(it.optString("dev_code").ifBlank { null }) }

    suspend fun otpVerify(phone: String, code: String, consent: Boolean) =
        call("POST", "/v1/auth/otp/verify", JSONObject().put("phone", phone).put("code", code).put("consent", consent), auth = false)
            .map { LoginResponse(it.getString("token"), profile(it.getJSONObject("profile")), it.optBoolean("created")) }

    suspend fun passwordLogin(phone: String, password: String) =
        call("POST", "/v1/auth/login", JSONObject().put("phone", phone).put("password", password), auth = false)
            .map { LoginResponse(it.getString("token"), profile(it.getJSONObject("profile")), false) }

    suspend fun changePassword(newPassword: String, oldPassword: String?): ApiResult<Unit> =
        call("POST", "/v1/auth/password", JSONObject().put("new_password", newPassword).apply { if (!oldPassword.isNullOrEmpty()) put("old_password", oldPassword) }).map { }

    suspend fun me() = call("GET", "/v1/auth/me").map { profile(it) }

    suspend fun updateProfile(role: String, district: String, language: String) =
        call("PATCH", "/v1/auth/me", JSONObject().put("role", role).put("district", district).put("language", language)).map { profile(it) }

    suspend fun deleteAccount(): ApiResult<Unit> = call("DELETE", "/v1/auth/me").map { }

    suspend fun listings(lat: Double?, lon: Double?, species: String?, dataSaver: Boolean) = call(
        "GET",
        "/v1/listings?" + listOfNotNull(
            if (lat != null && lon != null) "near=$lat,$lon&radius=300" else null,
            species?.let { "species=${it.lowercase()}" },
            if (dataSaver) "data_saver=1" else null,
        ).joinToString("&"),
        auth = false,
    ).map { j ->
        val a = j.getJSONArray("results")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            ListingDto(o.getString("id"), o.getString("species"), o.getString("breed"), o.optString("sex"), o.getInt("age_months"), o.getInt("qty"),
                o.getString("price_usd").toDouble(), o.optString("ward"), o.getDouble("public_lat"), o.getDouble("public_lon"), o.optString("phone"),
                o.optBoolean("seller_verified"), if (o.isNull("distance_km")) null else o.getDouble("distance_km"), if (o.isNull("photo_url")) null else o.getString("photo_url"))
        }
    }

    suspend fun pools() = call("GET", "/v1/pools", auth = false).map { j ->
        val a = j.getJSONArray("results")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PoolDto(o.getInt("id"), o.getString("title"), o.getString("species"), o.getInt("target_qty"), o.getInt("committed"), o.getInt("progress_pct"), o.getString("deadline"))
        }
    }

    suspend fun commitToPool(id: Int, qty: Int, ageMonths: Int, readyDate: String) =
        call("POST", "/v1/pools/$id/commitments", JSONObject().put("qty", qty).put("age_months", ageMonths).put("ready_date", readyDate)).map { }

    suspend fun activeOutbreaks() = call("GET", "/v1/outbreaks/active", auth = false).map { j ->
        val a = j.getJSONArray("results")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            OutbreakDto(o.getString("disease"), o.getString("district"), o.getString("started_on"), o.optString("summary"), o.optString("source_url"),
                o.getDouble("control_radius_km"), o.getDouble("surveillance_radius_km"))
        }
    }

    suspend fun packManifest() = call("GET", "/v1/packs/manifest", auth = false).map { j ->
        val a = j.getJSONArray("packs")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PackInfo(o.getString("district"), o.getString("version"), o.getLong("size_bytes"), o.getString("sha256"), o.getString("url"))
        }
    }

    suspend fun presignPhoto(listingId: String) =
        call("POST", "/v1/photos/presign", JSONObject().put("listing_id", listingId)).map { PresignDto(it.getString("key"), it.getString("upload_url")) }

    /** Pushes operations; the server answers per operation (applied, duplicate or rejected with a reason). */
    suspend fun sync(ops: List<Triple<String, String, Pair<String, String>>>): ApiResult<List<SyncResult>> {
        val arr = JSONArray()
        ops.forEach { (id, entity, p) -> arr.put(JSONObject().put("id", id).put("entity", entity).put("entity_id", p.first).put("payload", JSONObject(p.second))) }
        return call("POST", "/v1/sync", JSONObject().put("ops", arr)).map { j ->
            val a = j.getJSONArray("results")
            (0 until a.length()).map { i -> val o = a.getJSONObject(i); SyncResult(o.optString("id"), o.getString("status"), o.optString("error").ifBlank { null }) }
        }
    }

    /** PUT image bytes straight to the presigned object-storage URL (the API never touches the bytes). */
    suspend fun uploadPhoto(url: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "PUT"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 60_000
            c.setRequestProperty("Content-Type", "image/webp")
            c.outputStream.use { it.write(bytes) }
            c.responseCode in 200..299
        } catch (e: Exception) { false }
    }

    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000
            if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) { null }
    }

    /** Resumable download: appends to [partial] from its current size using an HTTP Range request. */
    suspend fun downloadResumable(url: String, partial: File, total: Long, onProgress: (Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val have = if (partial.exists()) partial.length() else 0L
            if (have >= total) return@withContext true
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000
            if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
            val code = c.responseCode
            if (code != 200 && code != 206) return@withContext false
            val append = code == 206 // 200 means the server ignored Range: start over
            var written = if (append) have else 0L
            c.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(written)
                    }
                }
            }
            partial.length() >= total
        } catch (e: Exception) { false }
    }

    private fun profile(o: JSONObject) = Profile(o.getString("phone"), o.getString("role"), o.optString("language", "en"), o.optString("district", "other"), o.optBoolean("verified"))
}

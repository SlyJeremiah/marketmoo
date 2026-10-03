package zw.marketmoo.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import org.json.JSONObject
import zw.marketmoo.app.data.local.AppDatabase
import zw.marketmoo.app.data.local.ListingEntity
import zw.marketmoo.app.data.local.RecordEntity
import zw.marketmoo.app.data.local.SyncOp
import zw.marketmoo.app.data.sync.SyncWorker
import zw.marketmoo.app.util.Geo

/** Local-first writes: save to the encrypted DB, enqueue an operation, ask WorkManager to sync when connected. */
class Repository(private val context: Context, private val db: AppDatabase) {
    val records = db.records().observeAll()
    val listings = db.listings().observeAll()

    suspend fun addRecord(type: String, animal: String, date: String, cost: Double?, notes: String) {
        val r = RecordEntity(UUID.randomUUID().toString(), type, animal, date, cost, notes)
        db.records().upsert(r)
        val payload = JSONObject().put("type", type).put("animal", animal).put("date", date)
            .put("cost", cost ?: JSONObject.NULL).put("notes", notes).toString()
        db.sync().enqueue(SyncOp(UUID.randomUUID().toString(), "record", r.id, payload))
        SyncWorker.enqueue(context)
    }

    suspend fun deleteRecord(id: String) = db.records().delete(id)

    suspend fun addListing(
        species: String, breed: String, sex: String, age: Int, qty: Int, price: Double,
        ward: String, lat: Double, lon: Double, phone: String, photo: Uri?,
    ) {
        val id = UUID.randomUUID().toString()
        val photoPath = photo?.let { shrinkPhoto(it, id) }
        val l = ListingEntity(id, species, breed, sex, age, qty, price, ward, lat, lon, phone, photoPath)
        db.listings().upsert(l)
        // The public position is blurred to about 1 km before it leaves the phone.
        val payload = JSONObject().put("species", species).put("breed", breed).put("sex", sex).put("age_months", age)
            .put("qty", qty).put("price_usd", price).put("ward", ward)
            .put("public_lat", Geo.blurToKm(lat)).put("public_lon", Geo.blurToKm(lon)).toString()
        db.sync().enqueue(SyncOp(UUID.randomUUID().toString(), "listing", l.id, payload))
        SyncWorker.enqueue(context)
    }

    /** A suspected outbreak is queued like any other write; it only becomes public after a manager verifies it with DVS. */
    suspend fun addOutbreakReport(species: String, suspected: String, count: Int, lat: Double, lon: Double, description: String) {
        val payload = JSONObject().put("species", species).put("suspected", suspected).put("count", count)
            .put("lat", lat).put("lon", lon).put("description", description).toString()
        db.sync().enqueue(SyncOp(UUID.randomUUID().toString(), "outbreak_report", UUID.randomUUID().toString(), payload))
        SyncWorker.enqueue(context)
    }

    /** Resize on the device (about 800 px, WebP, small) so uploads stay cheap on weak connections. */
    private fun shrinkPhoto(uri: Uri, id: String): String? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1600) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        bmp?.let {
            val scale = 800f / maxOf(it.width, it.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true) else it
            val f = File(context.filesDir, "photos/$id.webp").also { p -> p.parentFile?.mkdirs() }
            f.outputStream().use { o ->
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                out.compress(fmt, 70, o)
            }
            f.path
        }
    } catch (e: Exception) { null }
}

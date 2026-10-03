package zw.marketmoo.app.data.pack

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import zw.marketmoo.app.util.Geo

data class Cell(
    val id: Int, val district: String, val lon: Double, val lat: Double,
    val minutesToMajorMarket: Int?, val nearestMajorMarket: String, val minutesToLocalPoint: Int?, val marketAccessIndex: Int?,
    val minutesToService: Int?, val coverage: Int, val waterDryKm: Double?, val suitDry: Int, val suitWet: Int,
    val tickClass: Int, val inSurveillanceRing: Boolean, val distanceToPinKm: Double,
)

data class Feature(val kind: String, val name: String, val lon: Double, val lat: Double, val weight: Double, val source: String)

/**
 * Reads the per-district SQLite packs (built by the GIS pipeline). Everything here is a local lookup:
 * no routing or raster work happens on the phone.
 */
class PackRepository(private val context: Context) {
    val districts = listOf("Mhondoro-Ngezi", "Gwanda", "Beitbridge")
    private val dbs = HashMap<String, SQLiteDatabase>()

    @Synchronized
    private fun open(district: String): SQLiteDatabase = dbs.getOrPut(district) {
        val out = File(context.filesDir, "packs/pack_$district.sqlite")
        if (!out.exists()) {
            out.parentFile?.mkdirs()
            context.assets.open("packs/pack_$district.sqlite").use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /** Close a cached connection so a freshly downloaded pack file is picked up on the next lookup. */
    @Synchronized
    fun close(district: String) { dbs.remove(district)?.close() }

    fun version(district: String): String =
        open(district).rawQuery("select v from meta where k='pack_version'", null).use { if (it.moveToFirst()) it.getString(0) else "?" }

    /** Nearest 1 km grid cell to the pin within [maxKm] across all district packs, or null if the pin is outside the pilot area. */
    fun lookup(lat: Double, lon: Double, maxKm: Double = 2.5): Cell? {
        val d = maxKm / 111.0 * 1.3
        var best: Cell? = null
        for (district in districts) {
            open(district).rawQuery(
                "select id,lon,lat,t_major,m_major,t_local,mai,t_service,cover,water_dry_km,suit_dry,suit_wet,tick,in_zone from cells where lat between ? and ? and lon between ? and ?",
                arrayOf((lat - d).toString(), (lat + d).toString(), (lon - d).toString(), (lon + d).toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val cLon = c.getDouble(1)
                    val cLat = c.getDouble(2)
                    val dist = Geo.haversineKm(lat, lon, cLat, cLon)
                    val current = best
                    if (dist <= maxKm && (current == null || dist < current.distanceToPinKm)) {
                        best = Cell(
                            c.getInt(0), district, cLon, cLat,
                            c.intOrNull(3), c.getString(4) ?: "", c.intOrNull(5), c.intOrNull(6), c.intOrNull(7), c.getInt(8),
                            if (c.isNull(9)) null else c.getDouble(9), c.getInt(10), c.getInt(11), c.getInt(12), c.getInt(13) == 1, dist,
                        )
                    }
                }
            }
        }
        return best
    }

    fun features(district: String, kindPrefix: String? = null): List<Feature> {
        val sql = "select kind,name,lon,lat,weight,source from features" + if (kindPrefix != null) " where kind like ?" else ""
        val args = if (kindPrefix != null) arrayOf("$kindPrefix%") else null
        val list = ArrayList<Feature>()
        open(district).rawQuery(sql, args).use { c ->
            while (c.moveToNext()) list.add(Feature(c.getString(0), c.getString(1) ?: "", c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getString(5) ?: ""))
        }
        return list
    }

    fun nearestFeature(lat: Double, lon: Double, kindPrefix: String): Pair<Feature, Double>? =
        districts.flatMap { features(it, kindPrefix) }
            .map { it to Geo.haversineKm(lat, lon, it.lat, it.lon) }
            .minByOrNull { it.second }

    /** All grid cells of a district as lightweight points for the map layer: lon, lat, minutes to major market (-1 if unknown). */
    fun mapPoints(district: String): List<Triple<Double, Double, Int>> {
        val out = ArrayList<Triple<Double, Double, Int>>()
        open(district).rawQuery("select lon,lat,coalesce(t_major,-1) from cells", null).use { c ->
            while (c.moveToNext()) out.add(Triple(c.getDouble(0), c.getDouble(1), c.getInt(2)))
        }
        return out
    }

    private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
}

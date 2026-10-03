package zw.marketmoo.app.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** Universal Transverse Mercator on the WGS 84 ellipsoid (Krueger series, millimetre accuracy at farm scale). */
object Utm {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val N = F / (2 - F)
    private val A_HAT = A / (1 + N) * (1 + N * N / 4 + N.pow(4) / 64)
    private val ALPHA = doubleArrayOf(
        N / 2 - 2 * N * N / 3 + 5 * N.pow(3) / 16,
        13 * N * N / 48 - 3 * N.pow(3) / 5,
        61 * N.pow(3) / 240,
    )
    private val BETA = doubleArrayOf(
        N / 2 - 2 * N * N / 3 + 37 * N.pow(3) / 96,
        N * N / 48 + N.pow(3) / 15,
        17 * N.pow(3) / 480,
    )
    private val DELTA = doubleArrayOf(
        2 * N - 2 * N * N / 3 - 2 * N.pow(3),
        7 * N * N / 3 - 8 * N.pow(3) / 5,
        56 * N.pow(3) / 15,
    )

    fun centralMeridian(zone: Int) = (zone * 6 - 183).toDouble()

    /** Easting and northing in metres for a WGS 84 point. */
    fun forward(lat: Double, lon: Double, zone: Int, south: Boolean): Pair<Double, Double> {
        val phi = Math.toRadians(lat)
        val lam = Math.toRadians(lon - centralMeridian(zone))
        val t = sinh(atanh(sin(phi)) - 2 * sqrt(N) / (1 + N) * atanh(2 * sqrt(N) / (1 + N) * sin(phi)))
        val xi = atan(t / cos(lam))
        val eta = atanh(sin(lam) / sqrt(1 + t * t))
        var e = eta
        var n = xi
        for (j in 1..3) {
            e += ALPHA[j - 1] * cos(2 * j * xi) * kotlin.math.sinh(2 * j * eta)
            n += ALPHA[j - 1] * sin(2 * j * xi) * kotlin.math.cosh(2 * j * eta)
        }
        return (500000.0 + K0 * A_HAT * e) to (K0 * A_HAT * n + if (south) 10_000_000.0 else 0.0)
    }

    fun inverse(easting: Double, northing: Double, zone: Int, south: Boolean): LatLon {
        val x = easting - 500000.0
        val y = if (south) northing - 10_000_000.0 else northing
        val xi = y / (K0 * A_HAT)
        val eta = x / (K0 * A_HAT)
        var xi0 = xi
        var eta0 = eta
        for (j in 1..3) {
            xi0 -= BETA[j - 1] * sin(2 * j * xi) * kotlin.math.cosh(2 * j * eta)
            eta0 -= BETA[j - 1] * cos(2 * j * xi) * kotlin.math.sinh(2 * j * eta)
        }
        val chi = kotlin.math.asin(sin(xi0) / kotlin.math.cosh(eta0))
        var phi = chi
        for (j in 1..3) phi += DELTA[j - 1] * sin(2 * j * chi)
        val lam = atan(sinh(eta0) / cos(xi0))
        return LatLon(Math.toDegrees(phi), centralMeridian(zone) + Math.toDegrees(lam))
    }

    private fun atanh(v: Double) = 0.5 * kotlin.math.ln((1 + v) / (1 - v))
}

/**
 * Reads the outer rings of polygons from a zipped ESRI shapefile (.shp, with .prj for the coordinate system).
 * Works fully offline. Supports geographic coordinates and UTM (WGS 84 and Arc 1950 zones, which is what Zimbabwe
 * surveys normally use). Anything it cannot place with confidence is refused with a message that says what to do.
 */
object ShapefileImport {
    sealed class Result {
        data class Ok(val shape: FarmShape, val notes: List<String>) : Result()
        data class Error(val message: String) : Result()
    }

    private const val MAX_ZIP_BYTES = 25L * 1024 * 1024   // total uncompressed, guards against zip bombs
    private const val MAX_ENTRIES = 60
    private const val MAX_RINGS = 50

    fun fromZip(input: InputStream): Result {
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { zis ->
                var total = 0L
                var count = 0
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (++count > MAX_ENTRIES) return Result.Error("The zip has too many files. Zip only the shapefile parts (.shp, .shx, .dbf, .prj).")
                    if (e.isDirectory) continue
                    val name = e.name.substringAfterLast('/').substringAfterLast('\\').lowercase()
                    val ext = name.substringAfterLast('.', "")
                    val keep = ext == "shp" || ext == "prj"
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = zis.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ZIP_BYTES) return Result.Error("The zip is too large (over 25 MB when unpacked). Keep only the farm boundary layer.")
                        if (keep) buf.write(chunk, 0, n)
                    }
                    if (keep) files[name] = buf.toByteArray()
                }
            }
        } catch (e: Exception) {
            return Result.Error("This file is not a readable zip archive.")
        }
        val shpNames = files.keys.filter { it.endsWith(".shp") }
        if (shpNames.isEmpty()) return Result.Error("No .shp file found inside the zip. Zip the shapefile parts (.shp, .shx, .dbf, .prj) directly.")
        val notes = ArrayList<String>()
        if (shpNames.size > 1) notes += "The zip holds ${shpNames.size} shapefiles; using ${shpNames.first()}."
        val shpName = shpNames.first()
        val prj = files[shpName.removeSuffix(".shp") + ".prj"] ?: files.entries.firstOrNull { it.key.endsWith(".prj") }?.value
        return parse(files.getValue(shpName), prj?.toString(Charsets.UTF_8), notes)
    }

    fun parse(shp: ByteArray, prjText: String?, notes: MutableList<String> = ArrayList()): Result {
        if (shp.size < 100) return Result.Error("The .shp file is too short to be valid.")
        val bb = ByteBuffer.wrap(shp)
        if (bb.order(ByteOrder.BIG_ENDIAN).getInt(0) != 9994) return Result.Error("This is not an ESRI shapefile (.shp).")
        bb.order(ByteOrder.LITTLE_ENDIAN)
        val fileType = bb.getInt(32)
        when (fileType) {
            5, 15, 25 -> {}
            1, 8, 11, 18, 21, 28 -> return Result.Error("This layer holds points, not polygons. Export your farm boundary as a polygon layer.")
            3, 13, 23 -> return Result.Error("This layer holds lines, not polygons. Export your farm boundary as a polygon layer.")
            else -> return Result.Error("Unsupported shapefile type ($fileType). A polygon layer is needed.")
        }
        val raw = ArrayList<List<Pair<Double, Double>>>()
        var pos = 100
        var holes = 0
        while (pos + 8 <= shp.size) {
            val contentWords = ByteBuffer.wrap(shp, pos + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val start = pos + 8
            val end = start + contentWords * 2
            if (contentWords < 0 || end > shp.size) return Result.Error("The .shp file is damaged (a record runs past the end of the file).")
            pos = end
            if (contentWords < 2) continue
            val r = ByteBuffer.wrap(shp, start, end - start).slice().order(ByteOrder.LITTLE_ENDIAN)
            val type = r.getInt(0)
            if (type == 0) continue // null shape
            if (type !in intArrayOf(5, 15, 25)) return Result.Error("A record is not a polygon (type $type).")
            val numParts = r.getInt(36)
            val numPoints = r.getInt(40)
            if (numParts < 0 || numPoints < 0 || numPoints > 2_000_000 || 44 + 4 * numParts + 16L * numPoints > end - start) return Result.Error("The .shp file is damaged (inconsistent polygon sizes).")
            val parts = IntArray(numParts) { r.getInt(44 + 4 * it) }
            val ptsBase = 44 + 4 * numParts
            for (pi in 0 until numParts) {
                val from = parts[pi]
                val to = if (pi + 1 < numParts) parts[pi + 1] else numPoints
                if (from < 0 || to > numPoints || to - from < 4) continue
                val ring = (from until to).map { k -> r.getDouble(ptsBase + 16 * k) to r.getDouble(ptsBase + 16 * k + 8) }
                // shapefile rule: outer rings run clockwise, holes counter-clockwise
                if (signedArea(ring) > 0) { holes++ } else raw += ring
            }
            if (raw.size > MAX_RINGS) return Result.Error("The layer has more than $MAX_RINGS polygons. Keep only your farm.")
        }
        // some exporters write outer rings anticlockwise: if nothing is clockwise, accept all rings as outer rings
        val rings: List<List<Pair<Double, Double>>> = if (raw.isEmpty() && holes > 0) {
            holes = 0
            notes += "The polygon rings run anticlockwise; treated as outer boundaries."
            readAllRings(shp)
        } else raw
        if (rings.isEmpty()) return Result.Error("No polygons found in this shapefile.")
        if (holes > 0) notes += "$holes inner ring(s) (holes) were ignored: a farm boundary is the outer edge."

        val crs = detectCrs(prjText, rings, notes) ?: return Result.Error(crsMessage(prjText))
        val outRings = rings.map { ring ->
            val pts = ring.map { (x, y) -> crs(x, y) }.let { if (it.size > 1 && it.first() == it.last()) it.dropLast(1) else it }
            Geometry.simplify(pts)
        }
        for (ring in outRings) for (p in ring) {
            if (p.lat !in -23.0..-15.0 || p.lon !in 24.0..34.0) return Result.Error("After conversion the boundary falls outside Zimbabwe. The layer's coordinate system is probably not what its .prj says. Export it again in WGS 84 (EPSG:4326).")
        }
        if (outRings.any { it.size < 3 }) return Result.Error("A polygon has fewer than 3 usable points.")
        return Result.Ok(FarmShape(outRings), notes)
    }

    private fun readAllRings(shp: ByteArray): List<List<Pair<Double, Double>>> {
        val out = ArrayList<List<Pair<Double, Double>>>()
        var pos = 100
        while (pos + 8 <= shp.size) {
            val words = ByteBuffer.wrap(shp, pos + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val start = pos + 8; val end = start + words * 2
            if (words < 2 || end > shp.size) { pos = end; continue }
            pos = end
            val r = ByteBuffer.wrap(shp, start, end - start).slice().order(ByteOrder.LITTLE_ENDIAN)
            if (r.getInt(0) !in intArrayOf(5, 15, 25)) continue
            val np = r.getInt(36); val n = r.getInt(40)
            val parts = IntArray(np) { r.getInt(44 + 4 * it) }
            val base = 44 + 4 * np
            for (pi in 0 until np) {
                val from = parts[pi]; val to = if (pi + 1 < np) parts[pi + 1] else n
                if (to - from >= 4) out += (from until to).map { k -> r.getDouble(base + 16 * k) to r.getDouble(base + 16 * k + 8) }
            }
        }
        return out
    }

    private fun signedArea(ring: List<Pair<Double, Double>>): Double {
        var a = 0.0
        for (i in 0 until ring.size - 1) a += ring[i].first * ring[i + 1].second - ring[i + 1].first * ring[i].second
        return a / 2.0
    }

    /** Returns a function from file coordinates (x, y) to WGS 84, or null if the coordinate system is not understood. */
    private fun detectCrs(prj: String?, rings: List<List<Pair<Double, Double>>>, notes: MutableList<String>): ((Double, Double) -> LatLon)? {
        val first = rings.first().first()
        val looksGeographic = rings.all { r -> r.all { abs(it.first) <= 180.0 && abs(it.second) <= 90.0 } }
        val text = prj?.trim().orEmpty()
        val upper = text.uppercase()
        if (text.isNotEmpty() && upper.startsWith("PROJCS")) {
            // UTM by name, for example WGS_1984_UTM_Zone_35S or Arc_1950_UTM_Zone_36S
            Regex("UTM[_ ]ZONE[_ ]?(\\d{1,2})\\s*([NS])?").find(upper)?.let { m ->
                val zone = m.groupValues[1].toInt()
                val south = m.groupValues[2].ifEmpty { if (upper.contains("FALSE_NORTHING\",10000000") || upper.contains("FALSE_NORTHING\", 10000000")) "S" else "N" } == "S"
                if (zone in 1..60) {
                    if (upper.contains("ARC") || upper.contains("HARTEBEESTHOEK")) notes += "Arc 1950 / Hartebeesthoek94 coordinates were treated as WGS 84 (differences are within about 100 m). For legal-grade work, export in WGS 84."
                    return { x, y -> Utm.inverse(x, y, zone, south) }
                }
            }
            // generic transverse Mercator that is really UTM
            val cm = Regex("CENTRAL_MERIDIAN\",\\s*(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toDoubleOrNull()
            val fe = Regex("FALSE_EASTING\",\\s*(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toDoubleOrNull()
            val fn = Regex("FALSE_NORTHING\",\\s*(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toDoubleOrNull()
            val sk = Regex("SCALE_FACTOR\",\\s*(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toDoubleOrNull()
            if (cm != null && fe == 500000.0 && sk != null && abs(sk - 0.9996) < 1e-9 && (fn == 0.0 || fn == 10_000_000.0) && (cm + 183) % 6 == 0.0) {
                val zone = ((cm + 183) / 6).toInt()
                return { x, y -> Utm.inverse(x, y, zone, fn == 10_000_000.0) }
            }
            return null
        }
        if (text.isNotEmpty() && upper.startsWith("GEOGCS") && looksGeographic) {
            if (!upper.contains("WGS") && !upper.contains("4326")) notes += "The layer's datum is not WGS 84; coordinates were used as they are (differences are small at farm scale)."
            return { x, y -> LatLon(y, x) }
        }
        if (text.isEmpty() && looksGeographic) {
            notes += "No .prj file: coordinates look like longitude and latitude, so they were used as WGS 84."
            return { x, y -> LatLon(y, x) }
        }
        @Suppress("UNUSED_VARIABLE") val unused = first
        return null
    }

    private fun crsMessage(prj: String?): String =
        if (prj.isNullOrBlank()) "This shapefile has no .prj file and its coordinates are not longitude and latitude, so the coordinate system cannot be known. Include the .prj in the zip, or export in WGS 84 (EPSG:4326)."
        else "The coordinate system in this shapefile is not supported (${prj.take(60).replace('\n', ' ')}...). Export the boundary in WGS 84 (EPSG:4326) or UTM zone 35S or 36S and try again."
}

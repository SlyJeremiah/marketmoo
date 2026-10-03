package zw.marketmoo.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zw.marketmoo.app.util.FarmShape
import zw.marketmoo.app.util.Geometry
import zw.marketmoo.app.util.LatLon
import zw.marketmoo.app.util.ShapefileImport
import zw.marketmoo.app.util.Utm

/** A 100 m by 100 m square near Gwanda (one hectare), corners clockwise as a shapefile outer ring would be. */
private const val LAT0 = -20.93
private const val LON0 = 29.0
private val DLAT = 100 / 110_574.0
private val DLON = 100 / (111_320.0 * cos(Math.toRadians(LAT0)))
private val SQUARE = listOf(LatLon(LAT0, LON0), LatLon(LAT0 + DLAT, LON0), LatLon(LAT0 + DLAT, LON0 + DLON), LatLon(LAT0, LON0 + DLON))

private fun shp(type: Int = 5, rings: List<List<Pair<Double, Double>>>): ByteArray {
    val pts = rings.sumOf { it.size }
    val content = 4 + 32 + 4 + 4 + 4 * rings.size + 16 * pts
    val total = 100 + 8 + content
    val b = ByteBuffer.allocate(total)
    b.order(ByteOrder.BIG_ENDIAN).putInt(9994).position(24)
    b.putInt(total / 2)
    b.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(type)
    b.position(100)
    b.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(content / 2)
    b.order(ByteOrder.LITTLE_ENDIAN).putInt(type)
    val xs = rings.flatten().map { it.first }; val ys = rings.flatten().map { it.second }
    b.putDouble(xs.min()).putDouble(ys.min()).putDouble(xs.max()).putDouble(ys.max())
    b.putInt(rings.size).putInt(pts)
    var start = 0
    rings.forEach { b.putInt(start); start += it.size }
    rings.flatten().forEach { b.putDouble(it.first).putDouble(it.second) }
    return b.array()
}

private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> files.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
    return out.toByteArray()
}

private fun closed(ring: List<LatLon>) = (ring + ring.first()).map { it.lon to it.lat }
private const val WGS = "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"

class GeometryTest {
    @Test fun squareIsOneHectare() = assertEquals(1.0, Geometry.areaHectares(SQUARE), 0.03)

    @Test fun centroidOfSquareIsItsMiddle() {
        val c = FarmShape(listOf(SQUARE)).centroid
        assertEquals(LAT0 + DLAT / 2, c.lat, 1e-6)
        assertEquals(LON0 + DLON / 2, c.lon, 1e-6)
    }

    @Test fun bowTieCrossesItselfButSquareDoesNot() {
        val bow = listOf(LatLon(0.0, 0.0), LatLon(0.001, 0.001), LatLon(0.001, 0.0), LatLon(0.0, 0.001)).map { LatLon(-20.9 + it.lat, 29.0 + it.lon) }
        assertTrue(Geometry.selfIntersects(bow))
        assertFalse(Geometry.selfIntersects(SQUARE))
    }

    @Test fun simplifyDropsCollinearPointsButKeepsCorners() {
        val edge = (1..20).map { LatLon(LAT0, LON0 + DLON * it / 20) }
        val ring = listOf(LatLon(LAT0, LON0)) + edge + listOf(LatLon(LAT0 + DLAT, LON0 + DLON), LatLon(LAT0 + DLAT, LON0))
        val s = Geometry.simplify(ring)
        assertTrue("was ${s.size}", s.size <= 5)
        assertEquals(1.0, Geometry.areaHectares(s), 0.03)
    }

    @Test fun simplifyCapsVertexCount() {
        val circle = (0 until 3000).map { val a = it * 2 * Math.PI / 3000; LatLon(LAT0 + 0.003 * kotlin.math.sin(a), LON0 + 0.003 * kotlin.math.cos(a)) }
        assertTrue(Geometry.simplify(circle, maxPoints = 400).size <= 400)
    }

    // GeoJSON conversion uses org.json, which is only available on a device (the JVM test stubs are empty); the server tests cover the same format.
    @Test fun twoRingsAddUp() {
        val shape = FarmShape(listOf(SQUARE, SQUARE.map { LatLon(it.lat - 0.01, it.lon) }))
        assertEquals(2.0, shape.areaHa, 0.06)
        assertEquals(8, shape.vertexCount)
        assertEquals(LAT0 - 0.005 + DLAT / 2, shape.centroid.lat, 2e-4)
    }
}

class UtmTest {
    // reference values computed with PROJ (pyproj) for EPSG:32631 and EPSG:32735
    @Test fun matchesProjForNorthernZone() {
        val (e, n) = Utm.forward(48.8584, 2.2945, 31, false)
        assertEquals(448252.0014, e, 0.01)
        assertEquals(5411954.9099, n, 0.01)
    }

    @Test fun matchesProjForGwandaInZone35South() {
        val (e, n) = Utm.forward(-20.93, 29.0, 35, true)
        assertEquals(707986.0570, e, 0.01)
        assertEquals(7684302.3062, n, 0.01)
    }

    @Test fun roundTripInZimbabweZonesBothSides() {
        for ((lat, lon, zone) in listOf(Triple(-20.93, 29.0, 35), Triple(-22.2, 30.0, 35), Triple(-18.5, 30.2, 36), Triple(-22.3, 31.1, 36))) {
            val (e, n) = Utm.forward(lat, lon, zone, true)
            val back = Utm.inverse(e, n, zone, true)
            assertEquals(lat, back.lat, 1e-7)
            assertEquals(lon, back.lon, 1e-7)
        }
    }
}

class ShapefileImportTest {
    private fun ok(r: ShapefileImport.Result) = (r as? ShapefileImport.Result.Ok) ?: error("expected Ok but got $r")
    private fun err(r: ShapefileImport.Result) = (r as? ShapefileImport.Result.Error)?.message ?: error("expected Error but got $r")

    @Test fun geographicShapefileInZip() {
        val z = zip("farm.shp" to shp(rings = listOf(closed(SQUARE))), "farm.prj" to WGS.toByteArray(), "farm.dbf" to ByteArray(10))
        val r = ok(ShapefileImport.fromZip(ByteArrayInputStream(z)))
        assertEquals(1, r.shape.rings.size)
        assertEquals(1.0, r.shape.areaHa, 0.03)
        assertEquals(LAT0 + DLAT / 2, r.shape.centroid.lat, 1e-5)
    }

    @Test fun utmShapefileIsConvertedToLatLon() {
        val utm = closed(SQUARE).map { (lon, lat) -> Utm.forward(lat, lon, 35, true) }
        val prj = "PROJCS[\"WGS_1984_UTM_Zone_35S\",GEOGCS[\"GCS_WGS_1984\"],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],PARAMETER[\"False_Northing\",10000000.0],PARAMETER[\"Central_Meridian\",27.0],PARAMETER[\"Scale_Factor\",0.9996]]"
        val r = ok(ShapefileImport.fromZip(ByteArrayInputStream(zip("sub/dir/Farm.SHP" to shp(rings = listOf(utm)), "sub/dir/Farm.PRJ" to prj.toByteArray()))))
        assertEquals(1.0, r.shape.areaHa, 0.03)
        assertEquals(LON0 + DLON / 2, r.shape.centroid.lon, 1e-5)
    }

    @Test fun arc1950UtmIsAcceptedWithAnAccuracyNote() {
        val utm = closed(SQUARE).map { (lon, lat) -> Utm.forward(lat, lon, 35, true) }
        val prj = "PROJCS[\"Arc_1950_UTM_Zone_35S\",GEOGCS[\"GCS_Arc_1950\"]]"
        val r = ok(ShapefileImport.fromZip(ByteArrayInputStream(zip("a.shp" to shp(rings = listOf(utm)), "a.prj" to prj.toByteArray()))))
        assertTrue(r.notes.any { it.contains("Arc 1950") })
    }

    @Test fun holesAreIgnoredAndMentioned() {
        val outer = closed(SQUARE)                                    // clockwise from the bottom-left going up first? make sure orientation is clockwise
        val clockwise = if (signed(outer) > 0) outer.reversed() else outer
        val hole = closed(SQUARE.map { LatLon(LAT0 + (it.lat - LAT0) * 0.4 + DLAT * 0.3, LON0 + (it.lon - LON0) * 0.4 + DLON * 0.3) })
        val ccw = if (signed(hole) < 0) hole.reversed() else hole
        val r = ok(ShapefileImport.fromZip(ByteArrayInputStream(zip("f.shp" to shp(rings = listOf(clockwise, ccw)), "f.prj" to WGS.toByteArray()))))
        assertEquals(1, r.shape.rings.size)
        assertEquals(1.0, r.shape.areaHa, 0.03)
        assertTrue(r.notes.any { it.contains("hole") })
    }

    private fun signed(r: List<Pair<Double, Double>>): Double { var a = 0.0; for (i in 0 until r.size - 1) a += r[i].first * r[i + 1].second - r[i + 1].first * r[i].second; return a / 2 }

    @Test fun projectedCoordinatesWithoutPrjAreRefused() {
        val msg = err(ShapefileImport.fromZip(ByteArrayInputStream(zip("a.shp" to shp(rings = listOf(listOf(300000.0 to 7700000.0, 300100.0 to 7700000.0, 300100.0 to 7699900.0, 300000.0 to 7700000.0)))))))
        assertTrue(msg, msg.contains(".prj"))
    }

    @Test fun geographicCoordinatesWithoutPrjAreAcceptedWithNote() {
        val r = ok(ShapefileImport.fromZip(ByteArrayInputStream(zip("a.shp" to shp(rings = listOf(closed(SQUARE)))))))
        assertTrue(r.notes.any { it.contains("No .prj") })
    }

    @Test fun unsupportedProjectionIsRefusedWithAdvice() {
        val prj = "PROJCS[\"Lambert_Conformal_Conic\",PROJECTION[\"Lambert_Conformal_Conic\"]]"
        val msg = err(ShapefileImport.fromZip(ByteArrayInputStream(zip("a.shp" to shp(rings = listOf(listOf(300000.0 to 7700000.0, 300100.0 to 7700000.0, 300100.0 to 7699900.0, 300000.0 to 7700000.0))), "a.prj" to prj.toByteArray()))))
        assertTrue(msg, msg.contains("WGS 84"))
    }

    @Test fun pointsAndLinesAreRefused() {
        assertTrue(err(ShapefileImport.parse(shp(type = 5, rings = listOf(closed(SQUARE))).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 1) }, WGS)).contains("points"))
        assertTrue(err(ShapefileImport.parse(shp(type = 5, rings = listOf(closed(SQUARE))).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 3) }, WGS)).contains("lines"))
    }

    @Test fun boundaryOutsideZimbabweIsRefused() {
        val london = listOf(-0.10 to 51.5, -0.09 to 51.5, -0.09 to 51.51, -0.10 to 51.5)
        assertTrue(err(ShapefileImport.fromZip(ByteArrayInputStream(zip("a.shp" to shp(rings = listOf(london)), "a.prj" to WGS.toByteArray())))).contains("outside Zimbabwe"))
    }

    @Test fun notAZipAndZipWithoutShpAreRefused() {
        assertTrue(err(ShapefileImport.fromZip(ByteArrayInputStream("hello".toByteArray()))).contains("zip"))
        assertTrue(err(ShapefileImport.fromZip(ByteArrayInputStream(zip("notes.txt" to "x".toByteArray())))).contains("No .shp"))
    }

    @Test fun damagedShapefileIsRefusedNotCrashed() {
        val good = shp(rings = listOf(closed(SQUARE)))
        val cut = good.copyOf(good.size - 40)
        assertTrue(err(ShapefileImport.parse(cut, WGS)).contains("damaged"))
        assertTrue(err(ShapefileImport.parse(ByteArray(20), WGS)).contains("too short"))
        assertTrue(abs(1.0) > 0)
    }

    @Test fun zipBombIsStopped() {
        val big = ByteArray(30 * 1024 * 1024)
        val msg = err(ShapefileImport.fromZip(ByteArrayInputStream(zip("big.dat" to big))))
        assertTrue(msg, msg.contains("too large"))
    }
}

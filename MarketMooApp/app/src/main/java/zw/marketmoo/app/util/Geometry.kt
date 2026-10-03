package zw.marketmoo.app.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

data class LatLon(val lat: Double, val lon: Double)

/** A farm outline: one or more outer rings (no holes), each an open list of corners in WGS 84. */
data class FarmShape(val rings: List<List<LatLon>>) {
    val areaHa: Double get() = rings.sumOf { Geometry.areaHectares(it) }
    val centroid: LatLon get() = Geometry.centroid(rings)
    val vertexCount: Int get() = rings.sumOf { it.size }
}

object Geometry {
    private const val M_PER_DEG_LAT = 110_574.0
    private fun mPerDegLon(lat: Double) = 111_320.0 * cos(Math.toRadians(lat))

    /** Corners projected to local metres (x east, y north) around the first corner. */
    private fun local(ring: List<LatLon>): List<Pair<Double, Double>> {
        val lat0 = ring.map { it.lat }.average()
        val lon0 = ring.map { it.lon }.average()
        val kx = mPerDegLon(lat0)
        return ring.map { ((it.lon - lon0) * kx) to ((it.lat - lat0) * M_PER_DEG_LAT) }
    }

    fun areaHectares(ring: List<LatLon>): Double {
        if (ring.size < 3) return 0.0
        val p = local(ring)
        var a = 0.0
        for (i in p.indices) {
            val (x1, y1) = p[i]
            val (x2, y2) = p[(i + 1) % p.size]
            a += x1 * y2 - x2 * y1
        }
        return abs(a) / 2.0 / 10_000.0
    }

    /** Area-weighted centre of all rings (planar in local metres, fine at farm scale). */
    fun centroid(rings: List<List<LatLon>>): LatLon {
        var total = 0.0; var wLat = 0.0; var wLon = 0.0
        for (ring in rings) {
            if (ring.size < 3) continue
            val lat0 = ring.map { it.lat }.average(); val lon0 = ring.map { it.lon }.average()
            val kx = mPerDegLon(lat0)
            val p = local(ring)
            var a = 0.0; var cx = 0.0; var cy = 0.0
            for (i in p.indices) {
                val (x1, y1) = p[i]; val (x2, y2) = p[(i + 1) % p.size]
                val cross = x1 * y2 - x2 * y1
                a += cross; cx += (x1 + x2) * cross; cy += (y1 + y2) * cross
            }
            a /= 2.0
            if (abs(a) < 1e-6) continue
            cx /= 6.0 * a; cy /= 6.0 * a
            val w = abs(a)
            total += w; wLat += (lat0 + cy / M_PER_DEG_LAT) * w; wLon += (lon0 + cx / kx) * w
        }
        if (total == 0.0) {
            val all = rings.flatten()
            return LatLon(all.map { it.lat }.average(), all.map { it.lon }.average())
        }
        return LatLon(wLat / total, wLon / total)
    }

    /** True if any two non-neighbouring edges cross: a bow-tie or figure-eight is not a farm outline. */
    fun selfIntersects(ring: List<LatLon>): Boolean {
        val n = ring.size
        if (n < 4) return false
        val p = local(ring)
        for (i in 0 until n) {
            val a1 = p[i]; val a2 = p[(i + 1) % n]
            for (j in i + 1 until n) {
                if (j == i + 1 || (i == 0 && j == n - 1)) continue
                val b1 = p[j]; val b2 = p[(j + 1) % n]
                if (segmentsCross(a1, a2, b1, b2)) return true
            }
        }
        return false
    }

    private fun orient(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double =
        (b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)

    private fun segmentsCross(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>): Boolean {
        val o1 = orient(a, b, c); val o2 = orient(a, b, d); val o3 = orient(c, d, a); val o4 = orient(c, d, b)
        return (o1 * o2 < 0) && (o3 * o4 < 0)
    }

    /** Douglas-Peucker in local metres: keeps the shape within [toleranceM] while capping the vertex count. */
    fun simplify(ring: List<LatLon>, toleranceM: Double = 1.5, maxPoints: Int = 400): List<LatLon> {
        var tol = toleranceM
        var out = dp(ring, tol)
        var guard = 0
        while (out.size > maxPoints && guard++ < 15) { tol *= 1.7; out = dp(out, tol) }
        return out
    }

    private fun dp(ring: List<LatLon>, tol: Double): List<LatLon> {
        if (ring.size <= 3) return ring
        val p = local(ring)
        val keep = BooleanArray(p.size)
        keep[0] = true; keep[p.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to p.size - 1)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0.0; var idx = -1
            for (i in s + 1 until e) { val d = distToSegment(p[i], p[s], p[e]); if (d > maxD) { maxD = d; idx = i } }
            if (idx >= 0 && maxD > tol) { keep[idx] = true; stack.add(s to idx); stack.add(idx to e) }
        }
        val res = ring.filterIndexed { i, _ -> keep[i] }
        return if (res.size >= 3) res else ring
    }

    private fun distToSegment(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = max(0.0, minOf(1.0, ((p.first - a.first) * dx + (p.second - a.second) * dy) / len2))
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }

    fun toGeoJson(shape: FarmShape): JSONObject {
        val polys = JSONArray()
        for (ring in shape.rings) {
            val r = JSONArray()
            (ring + ring.first()).forEach { r.put(JSONArray().put(it.lon).put(it.lat)) }
            polys.put(JSONArray().put(r))
        }
        return JSONObject().put("type", "MultiPolygon").put("coordinates", polys)
    }

    fun fromGeoJson(j: JSONObject): FarmShape {
        val polys = j.getJSONArray("coordinates")
        val rings = (0 until polys.length()).map { i ->
            val ring = polys.getJSONArray(i).getJSONArray(0)
            val pts = (0 until ring.length()).map { k -> val c = ring.getJSONArray(k); LatLon(c.getDouble(1), c.getDouble(0)) }
            if (pts.size > 1 && pts.first() == pts.last()) pts.dropLast(1) else pts
        }
        return FarmShape(rings)
    }
}

package zw.marketmoo.app.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** Blur a coordinate to roughly a 1 km grid so exact farm locations are never shown publicly. */
    fun blurToKm(value: Double, km: Double = 1.0): Double {
        val step = km / 111.32
        return Math.round(value / step) * step
    }

    fun metersPerDegreeLon(lat: Double): Double = 111_320.0 * cos(lat * PI / 180.0)
}

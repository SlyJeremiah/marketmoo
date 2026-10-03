package zw.marketmoo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zw.marketmoo.app.util.Geo

class GeoTest {
    @Test
    fun haversineGwandaToBeitbridgeIsAboutOneHundredAndSeventyKm() {
        // straight-line distance; the road is longer (about 190 km)
        val d = Geo.haversineKm(-20.93, 29.0, -22.2, 29.99)
        assertTrue("was $d", d in 150.0..190.0)
    }

    @Test
    fun blurMovesPositionByAtMostAboutOneKilometre() {
        val lat = -20.93412
        val blurred = Geo.blurToKm(lat)
        assertTrue(Math.abs(lat - blurred) * 111.32 <= 0.75)
    }

    @Test
    fun blurIsIdempotent() {
        val once = Geo.blurToKm(29.123456)
        assertEquals(once, Geo.blurToKm(once), 1e-9)
    }
}

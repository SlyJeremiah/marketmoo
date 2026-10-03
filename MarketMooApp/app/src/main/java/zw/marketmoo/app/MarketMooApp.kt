package zw.marketmoo.app

import android.app.Application
import org.maplibre.android.MapLibre

class MarketMooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}

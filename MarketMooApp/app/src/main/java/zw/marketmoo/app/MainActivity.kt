package zw.marketmoo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import zw.marketmoo.app.ui.AppRoot
import zw.marketmoo.app.ui.theme.MarketMooTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sl = ServiceLocator.get(this)
        setContent { MarketMooTheme { AppRoot(sl) } }
    }
}

package eu.junak.baton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.baton.ui.navigation.BatonApp
import eu.junak.baton.ui.theme.BatonTheme

/** Single-activity host. The whole UI is Compose under [BatonApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatonTheme {
                BatonApp()
            }
        }
    }
}

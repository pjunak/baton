package eu.junak.baton

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.baton.feature.update.Updater
import eu.junak.baton.ui.navigation.BatonApp
import eu.junak.baton.ui.theme.BatonTheme
import javax.inject.Inject

/** Single-activity host. The whole UI is Compose under [BatonApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updater: Updater

    // Notifications back the phone-as-speaker foreground service; request once (Android 13+).
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        // Silent launch-time update check: on a newer release it lights the
        // Settings-tab badge; otherwise it stays invisible (see checkOnLaunch).
        updater.checkOnLaunch()
        setContent {
            BatonTheme {
                BatonApp()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

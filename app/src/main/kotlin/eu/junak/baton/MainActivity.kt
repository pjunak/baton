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

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            /* best-effort: denied features report their normal connection/notification errors */
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestRuntimePermissions()
        // Silent launch-time update check: on a newer release it lights the
        // Settings-tab badge; otherwise it stays invisible (see checkOnLaunch).
        updater.checkOnLaunch()
        setContent {
            BatonTheme {
                BatonApp()
            }
        }
    }

    private fun maybeRequestRuntimePermissions() {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Android 17 (API 37) blocks direct LAN sockets by default for apps
            // targeting 37. Baton needs broad access for user-entered server URLs.
            if (Build.VERSION.SDK_INT >= 37 &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }

        if (missing.isNotEmpty()) {
            requestRuntimePermissions.launch(missing.toTypedArray())
        }
    }
}

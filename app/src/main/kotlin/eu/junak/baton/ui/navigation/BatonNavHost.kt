package eu.junak.baton.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.network.ServerConfig
import eu.junak.baton.ui.main.MainScreen
import eu.junak.baton.ui.setup.SetupScreen
import javax.inject.Inject

object Routes {
    const val SETUP = "setup"
    const val MAIN = "main"
}

/** Picks the start screen: the main shell if a server is already configured,
 *  otherwise the setup wizard. */
@HiltViewModel
class AppViewModel @Inject constructor(
    serverConfig: ServerConfig,
) : ViewModel() {
    val startDestination: String =
        if (serverConfig.isConfigured) Routes.MAIN else Routes.SETUP
}

@Composable
fun BatonApp(appViewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = appViewModel.startDestination,
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onConnected = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                onSignedOut = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}

package eu.junak.baton.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.sync.SyncClient
import eu.junak.baton.ui.console.ConsoleScreen
import eu.junak.baton.ui.library.LibraryScreen
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

private enum class MainTab(val label: String, val icon: ImageVector) {
    CONSOLE("Console", Icons.Filled.PlayCircle),
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    SESSION("Session", Icons.Filled.GraphicEq),
    DEVICES("Devices", Icons.Filled.Speaker),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** App-wide error frames (from the WebSocket), surfaced as a snackbar. */
@HiltViewModel
class MainViewModel @Inject constructor(syncClient: SyncClient) : ViewModel() {
    val errors: SharedFlow<String> = syncClient.errors
}

@Composable
fun MainScreen(
    onSignedOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (MainTab.entries[tabIndex]) {
                MainTab.CONSOLE -> ConsoleScreen(onSignedOut = onSignedOut)
                MainTab.LIBRARY -> LibraryScreen()
                MainTab.SESSION -> PlaceholderTab("Session", "Modes, cues, soundboards, EQ — coming soon.")
                MainTab.DEVICES -> PlaceholderTab("Devices", "Outputs and this phone as a speaker — coming soon.")
                MainTab.SETTINGS -> PlaceholderTab("Settings", "Server, account, updates — coming soon.")
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

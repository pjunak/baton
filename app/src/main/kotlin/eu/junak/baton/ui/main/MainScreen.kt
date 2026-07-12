package eu.junak.baton.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import eu.junak.baton.feature.update.UpdateState
import eu.junak.baton.feature.update.Updater
import eu.junak.baton.ui.console.ConsoleScreen
import eu.junak.baton.ui.library.LibraryScreen
import eu.junak.baton.ui.session.SessionScreen
import eu.junak.baton.ui.settings.SettingsScreen
import eu.junak.baton.ui.theme.ActiveAccent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private enum class MainTab(val label: String, val icon: ImageVector) {
    CONSOLE("Console", Icons.Filled.PlayCircle),
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    SESSION("Session", Icons.Filled.GraphicEq),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** App shell state: WS error frames (snackbar) + the global play/pause docked in the nav bar. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncClient: SyncClient,
    updater: Updater,
) : ViewModel() {
    val errors: SharedFlow<String> = syncClient.errors

    data class PlayState(val isPlaying: Boolean = false, val connected: Boolean = false)

    val playState: StateFlow<PlayState> =
        combine(syncClient.state, syncClient.status) { state, status ->
            PlayState(
                // Interrupt overrides ambient on the outputs — reflect it so the icon matches.
                isPlaying = state?.interrupt != null || state?.isPlaying == true,
                connected = status == ConnectionStatus.CONNECTED,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayState())

    /** Lights the Settings-tab badge while an update is available / in flight
     *  (fed by the silent launch-time check or a manual one). */
    val updateAvailable: StateFlow<Boolean> =
        updater.state
            .map {
                it is UpdateState.Available || it is UpdateState.Downloading || it is UpdateState.ReadyToInstall
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun playPause() {
        val state = syncClient.state.value
        val playing = state?.interrupt != null || state?.isPlaying == true
        syncClient.send(if (playing) Action.Pause else Action.Resume)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun MainScreen(
    onSignedOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val playState by viewModel.playState.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        bottomBar = {
            DockedNavBar(
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
                isPlaying = playState.isPlaying,
                enabled = playState.connected,
                onPlayPause = viewModel::playPause,
                settingsBadge = updateAvailable,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (MainTab.entries[tabIndex.coerceIn(0, MainTab.entries.lastIndex)]) {
                MainTab.CONSOLE -> ConsoleScreen()
                MainTab.LIBRARY -> LibraryScreen()
                MainTab.SESSION -> SessionScreen()
                MainTab.SETTINGS -> SettingsScreen(onSignedOut = onSignedOut)
            }
        }
    }
}

/**
 * Bottom nav with a center gap; the global play/pause rests in that gap, straddling the tab row
 * (its bottom inside the bar, its top poking up toward the Console's transport controls).
 */
@Composable
private fun DockedNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    isPlaying: Boolean,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    settingsBadge: Boolean,
) {
    Box(Modifier.fillMaxWidth()) {
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(MainTab.CONSOLE, selectedIndex == 0) { onSelect(0) }
                NavItem(MainTab.LIBRARY, selectedIndex == 1) { onSelect(1) }
                Spacer(Modifier.width(72.dp)) // gap the play button rests in
                NavItem(MainTab.SESSION, selectedIndex == 2) { onSelect(2) }
                NavItem(MainTab.SETTINGS, selectedIndex == 3, badge = settingsBadge) { onSelect(3) }
            }
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -32.dp) // half the button height up: its center lands on the bar's top edge
                .size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: MainTab,
    selected: Boolean,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (selected) ActiveAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BadgedBox(badge = { if (badge) Badge() }) {
            Icon(tab.icon, contentDescription = tab.label, tint = color)
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

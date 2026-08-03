package eu.junak.baton.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.BuildConfig
import eu.junak.baton.R
import eu.junak.baton.feature.update.UpdateState
import java.io.File

private enum class SettingsTab {
    GENERAL,
    PLAYBACK,
    UPDATES,
}

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(SettingsTab.GENERAL.ordinal) }
    val tabs = remember { SettingsTab.entries }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab.ordinal,
                    onClick = { selectedTab = tab.ordinal },
                    text = {
                        Text(
                            when (tab) {
                                SettingsTab.GENERAL -> stringResource(R.string.settings_tab_general)
                                SettingsTab.PLAYBACK -> stringResource(R.string.settings_tab_playback)
                                SettingsTab.UPDATES -> stringResource(R.string.settings_tab_updates)
                            },
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tabs[selectedTab.coerceIn(0, tabs.lastIndex)]) {
                SettingsTab.GENERAL -> GeneralSettings(
                    username = ui.username,
                    serverUrl = ui.serverUrl,
                    signingOut = ui.signingOut,
                    onOpenServer = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    },
                    onSignOut = { viewModel.signOut(onSignedOut) },
                )

                SettingsTab.PLAYBACK -> PlaybackSettings(
                    keepConsoleAwake = ui.keepConsoleAwake,
                    onKeepConsoleAwakeChanged = viewModel::setKeepConsoleAwake,
                )

                SettingsTab.UPDATES -> UpdatesSettings(
                    state = updateState,
                    onCheck = viewModel::checkForUpdate,
                    onDownload = viewModel::downloadUpdate,
                    onInstall = viewModel::installUpdate,
                )
            }
        }
    }
}

@Composable
private fun GeneralSettings(
    username: String?,
    serverUrl: String?,
    signingOut: Boolean,
    onOpenServer: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    SettingsPage {
        SectionHeader(stringResource(R.string.settings_section_account))
        ListItem(
            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
            headlineContent = { Text(username ?: "—") },
            supportingContent = { Text(stringResource(R.string.settings_signed_in)) },
        )
        OutlinedButton(
            onClick = onSignOut,
            enabled = !signingOut,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_sign_out))
        }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.settings_section_server))
        ListItem(
            leadingContent = { Icon(Icons.Filled.Dns, contentDescription = null) },
            headlineContent = {
                Text(
                    text = serverUrl ?: stringResource(R.string.settings_server_not_configured),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = { Text(stringResource(R.string.settings_connected_server)) },
        )
        serverUrl?.let { url ->
            OutlinedButton(
                onClick = { onOpenServer(url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_open_web_app))
            }
        }
    }
}

@Composable
private fun PlaybackSettings(
    keepConsoleAwake: Boolean,
    onKeepConsoleAwakeChanged: (Boolean) -> Unit,
) {
    SettingsPage {
        SectionHeader(stringResource(R.string.settings_section_console))
        ListItem(
            modifier = Modifier.toggleable(
                value = keepConsoleAwake,
                role = Role.Switch,
                onValueChange = onKeepConsoleAwakeChanged,
            ),
            leadingContent = { Icon(Icons.Filled.ScreenLockPortrait, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.settings_keep_console_awake)) },
            supportingContent = { Text(stringResource(R.string.settings_keep_console_awake_summary)) },
            trailingContent = {
                Switch(
                    checked = keepConsoleAwake,
                    onCheckedChange = null,
                )
            },
        )

        HorizontalDivider()
        SectionHeader(stringResource(R.string.settings_section_background_audio))
        ListItem(
            leadingContent = { Icon(Icons.Filled.Headphones, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.settings_background_audio_title)) },
            supportingContent = { Text(stringResource(R.string.settings_background_audio_summary)) },
        )
    }
}

@Composable
private fun UpdatesSettings(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (UpdateState.Available) -> Unit,
    onInstall: (File) -> Unit,
) {
    SettingsPage {
        SectionHeader(stringResource(R.string.settings_section_about))
        ListItem(
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            headlineContent = {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            },
            supportingContent = { Text(stringResource(R.string.settings_update_source)) },
        )
        UpdateSection(state, onCheck, onDownload, onInstall)
    }
}

@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** Renders the current [UpdateState] as a check button, progress, or install prompt. */
@Composable
private fun UpdateSection(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (UpdateState.Available) -> Unit,
    onInstall: (File) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        when (state) {
            UpdateState.Idle ->
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_check_updates))
                }

            UpdateState.Checking ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_checking_updates))
                }

            UpdateState.UpToDate -> {
                Text(
                    stringResource(R.string.settings_up_to_date),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_check_again))
                }
            }

            is UpdateState.Available -> {
                Text(
                    stringResource(R.string.settings_update_available, state.version),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.notes?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onDownload(state) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_download_install))
                }
            }

            is UpdateState.Downloading -> {
                Text(stringResource(R.string.settings_downloading, (state.progress * 100).toInt()))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }

            is UpdateState.ReadyToInstall -> {
                Text(stringResource(R.string.settings_downloaded, state.version))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onInstall(state.apk) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_install))
                }
            }

            is UpdateState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_try_again))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

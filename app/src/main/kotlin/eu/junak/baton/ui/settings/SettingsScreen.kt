package eu.junak.baton.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.BuildConfig
import eu.junak.baton.feature.update.UpdateState
import java.io.File

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader("Account")
        ListItem(
            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
            headlineContent = { Text(ui.username ?: "—") },
            supportingContent = { Text("Signed in") },
        )
        OutlinedButton(
            onClick = { viewModel.signOut(onSignedOut) },
            enabled = !ui.signingOut,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }

        HorizontalDivider()

        SectionHeader("Server")
        ListItem(
            leadingContent = { Icon(Icons.Filled.Dns, contentDescription = null) },
            headlineContent = {
                Text(
                    text = ui.serverUrl ?: "Not configured",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = { Text("Connected server") },
        )
        ui.serverUrl?.let { url ->
            OutlinedButton(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open web app (for authoring)")
            }
        }

        HorizontalDivider()

        SectionHeader("About")
        ListItem(
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            headlineContent = { Text("Baton ${BuildConfig.VERSION_NAME}") },
            supportingContent = { Text("Checks GitHub for a newer release") },
        )
        UpdateSection(
            state = updateState,
            onCheck = viewModel::checkForUpdate,
            onDownload = viewModel::downloadUpdate,
            onInstall = viewModel::installUpdate,
        )
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
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }

            UpdateState.Checking ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking…")
                }

            UpdateState.UpToDate -> {
                Text("You're on the latest version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
            }

            is UpdateState.Available -> {
                Text("Update available — v${state.version}", style = MaterialTheme.typography.titleMedium)
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
                    Text("Download & install")
                }
            }

            is UpdateState.Downloading -> {
                Text("Downloading… ${(state.progress * 100).toInt()}%")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }

            is UpdateState.ReadyToInstall -> {
                Text("Downloaded v${state.version}.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onInstall(state.apk) }, modifier = Modifier.fillMaxWidth()) { Text("Install") }
            }

            is UpdateState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
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

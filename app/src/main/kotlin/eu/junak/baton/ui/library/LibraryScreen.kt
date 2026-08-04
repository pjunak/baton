package eu.junak.baton.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.R
import eu.junak.baton.core.model.Track
import eu.junak.baton.ui.components.TrackArtwork

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.library_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            ui.loading -> Centered { CircularProgressIndicator() }

            ui.error != null -> Centered {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            ui.searchResults != null -> {
                val results = ui.searchResults.orEmpty()
                if (results.isEmpty()) {
                    Centered { Text(stringResource(R.string.library_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "s:${it.id}" }) { track ->
                            TrackRow(track, viewModel.coverUrl(track.id), { viewModel.playTrack(track) }, { viewModel.enqueue(track) }, { viewModel.playInterrupt(track) })
                        }
                    }
                }
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (ui.path.isNotEmpty() || ui.folders.isNotEmpty() || ui.tracks.isNotEmpty()) {
                    item {
                        FolderActions(
                            path = ui.path,
                            canPlay = ui.folders.isNotEmpty() || ui.tracks.isNotEmpty(),
                            onUp = viewModel::goUp,
                            onPlay = viewModel::playCurrentFolder,
                        )
                    }
                }
                if (ui.folders.isNotEmpty()) {
                    item { LibrarySectionHeader(stringResource(R.string.library_folders)) }
                }
                items(ui.folders, key = { "f:${it.path}" }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.library_track_count,
                                    folder.trackCount,
                                    folder.trackCount,
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.openFolder(folder) },
                    )
                }
                if (ui.tracks.isNotEmpty()) {
                    item { LibrarySectionHeader(stringResource(R.string.library_tracks)) }
                }
                items(ui.tracks, key = { "t:${it.id}" }) { track ->
                    TrackRow(track, viewModel.coverUrl(track.id), { viewModel.playTrack(track) }, { viewModel.enqueue(track) }, { viewModel.playInterrupt(track) })
                }
                if (ui.folders.isEmpty() && ui.tracks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.library_folder_empty),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderActions(path: String, canPlay: Boolean, onUp: () -> Unit, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.library_folder_actions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (path.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.library_path, path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (path.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onUp,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                        Text(stringResource(R.string.library_up), Modifier.padding(start = 8.dp))
                    }
                }
                if (canPlay) {
                    FilledTonalButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.library_play_folder), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    coverUrl: String?,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onInterrupt: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val playLabel = stringResource(R.string.library_play_track, track.effectiveTitle)
    val optionsLabel = stringResource(R.string.library_track_options, track.effectiveTitle)
    Box {
        ListItem(
            leadingContent = { TrackArtwork(coverUrl, Modifier.size(44.dp), corner = 6.dp) },
            headlineContent = { Text(track.effectiveTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                IconButton(onClick = onEnqueue) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.library_add_queue))
                }
            },
            // Tap plays now; long-press opens the fuller action menu (queue / interrupt).
            modifier = Modifier.combinedClickable(
                onClickLabel = playLabel,
                onClick = onPlay,
                onLongClickLabel = optionsLabel,
                onLongClick = { menuOpen = true },
            ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_play_now)) },
                onClick = {
                    menuOpen = false
                    onPlay()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_add_queue)) },
                onClick = {
                    menuOpen = false
                    onEnqueue()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_play_interrupt)) },
                onClick = {
                    menuOpen = false
                    onInterrupt()
                },
                leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

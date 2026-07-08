package eu.junak.baton.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.core.model.Track
import eu.junak.baton.ui.components.TrackArtwork

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search library") },
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
                    Centered { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "s:${it.id}" }) { track ->
                            TrackRow(track, viewModel.coverUrl(track.id), { viewModel.playTrack(track) }, { viewModel.enqueue(track) }, { viewModel.playInterrupt(track) })
                        }
                    }
                }
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (ui.path.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Up") },
                            supportingContent = { Text("/${ui.path}") },
                            leadingContent = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.goUp() },
                        )
                    }
                }
                if (ui.folders.isNotEmpty() || ui.tracks.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Play this folder") },
                            leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.playCurrentFolder() },
                        )
                    }
                }
                items(ui.folders, key = { "f:${it.path}" }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text("${folder.trackCount} tracks") },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.openFolder(folder) },
                    )
                }
                items(ui.tracks, key = { "t:${it.id}" }) { track ->
                    TrackRow(track, viewModel.coverUrl(track.id), { viewModel.playTrack(track) }, { viewModel.enqueue(track) }, { viewModel.playInterrupt(track) })
                }
                if (ui.folders.isEmpty() && ui.tracks.isEmpty()) {
                    item {
                        Text(
                            text = "This folder is empty",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
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
    Box {
        ListItem(
            leadingContent = { TrackArtwork(coverUrl, Modifier.size(44.dp), corner = 6.dp) },
            headlineContent = { Text(track.effectiveTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                IconButton(onClick = onEnqueue) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to queue")
                }
            },
            // Tap plays now; long-press opens the fuller action menu (queue / interrupt).
            modifier = Modifier.combinedClickable(onClick = onPlay, onLongClick = { menuOpen = true }),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Play now") },
                onClick = {
                    menuOpen = false
                    onPlay()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = {
                    menuOpen = false
                    onEnqueue()
                },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Play as interrupt") },
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

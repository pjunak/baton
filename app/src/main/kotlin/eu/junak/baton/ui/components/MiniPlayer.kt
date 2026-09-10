package eu.junak.baton.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.junak.baton.R
import eu.junak.baton.core.model.Track
import eu.junak.baton.ui.theme.BatonSpacing

/** A dedicated swipe target, separate from Library rows and all playback sliders. */
@Composable
fun MiniPlayer(track: Track, coverUrl: String?, onOpen: () -> Unit) {
    val open by rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.console_open), onClick = onOpen)
                .pointerInput(threshold) {
                    var distance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { distance = 0f },
                        onVerticalDrag = { change, amount -> change.consume(); distance += amount },
                        onDragEnd = { if (distance < -threshold) open(); distance = 0f },
                        onDragCancel = { distance = 0f },
                    )
                }.padding(horizontal = BatonSpacing.Medium, vertical = BatonSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(coverUrl, Modifier.size(40.dp), corner = 6.dp)
            Column(Modifier.weight(1f).padding(horizontal = BatonSpacing.Medium)) {
                Text(track.effectiveTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    track.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
        }
    }
}

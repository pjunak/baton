package eu.junak.baton.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.ui.devices.DevicesViewModel.DeviceRow

/**
 * Output-device picker, shown as a sheet pulled down from the top of the Console
 * (its trigger is the speaker icon up there). Lists every connected output — this
 * phone included — toggles which are active, and trims per-device volume.
 */
@Composable
fun DevicePicker(viewModel: DevicesViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Output devices",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (ui.devices.isEmpty()) {
            Text(
                text = if (ui.connected) "No devices connected" else "Connecting…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            ui.devices.forEach { device ->
                DeviceCard(
                    device = device,
                    enabled = ui.connected,
                    onToggle = { viewModel.toggleOutput(device.deviceId, it) },
                    onVolume = { viewModel.setDeviceVolume(device.deviceId, it) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceRow,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = deviceIcon(device.name, device.isThisDevice),
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(shortDeviceLabel(device.name), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = deviceSubtitle(device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = device.isActiveOutput, onCheckedChange = onToggle, enabled = enabled)
        }
        if (device.isActiveOutput) {
            DeviceVolume(device.volume, enabled, onVolume)
        }
    }
}

/**
 * Minimalist volume slider: a thin rounded track with a small playhead dot, flanked by
 * muted volume glyphs. Drag anywhere on it, or tap to jump. Mirrors the Console seek line
 * rather than the chunky default Material slider, for a cleaner look in the device sheet.
 */
@Composable
private fun DeviceVolume(volume: Float, enabled: Boolean, onVolume: (Float) -> Unit) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val frac = (dragFrac ?: volume).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val fillColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbOffset = with(LocalDensity.current) { (widthPx * frac).toDp() } - 7.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.VolumeDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = iconTint)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(24.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(enabled, widthPx) {
                    if (!enabled || widthPx <= 0) return@pointerInput
                    detectTapGestures { offset -> onVolume((offset.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled, widthPx) {
                    if (!enabled || widthPx <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFrac = (offset.x / widthPx).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFrac?.let { onVolume(it) }
                            dragFrac = null
                        },
                        onDragCancel = { dragFrac = null },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(trackColor),
            )
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(fillColor),
            )
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(fillColor),
            )
        }
        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp), tint = iconTint)
    }
}

private fun deviceSubtitle(device: DeviceRow): String {
    val role = if (device.isActiveOutput) "Active output" else "Idle"
    return if (device.isThisDevice) "This device · $role" else role
}

/**
 * Pick a device-type glyph from the (possibly user-set) device name — a PC for
 * computers, a TV for televisions, a phone/tablet for mobiles — instead of the old
 * one-size-fits-all speaker. Falls back to a speaker for genuinely unknown outputs.
 */
private fun deviceIcon(name: String, isThisDevice: Boolean): ImageVector {
    val n = name.lowercase()
    return when {
        "tv" in n || "television" in n -> Icons.Filled.Tv
        isThisDevice -> Icons.Filled.Smartphone
        "ipad" in n || "tablet" in n -> Icons.Filled.Tablet
        "iphone" in n || "phone" in n || "mobile" in n -> Icons.Filled.Smartphone
        "pc" in n || "computer" in n || "desktop" in n || "laptop" in n ||
            "windows" in n || "mac" in n || "linux" in n -> Icons.Filled.Computer
        else -> Icons.Filled.Speaker
    }
}

/**
 * Shorten a device label by swapping the verbose OS name for its emoji
 * ("Windows PC · Edge" → "🪟 PC · Edge"). Mirrors the music web client so both
 * remotes read the same; names without an OS keyword pass through untouched.
 */
private fun shortDeviceLabel(name: String): String =
    name
        .replace(Regex("windows", RegexOption.IGNORE_CASE), "🪟")
        .replace(Regex("macintosh|mac os x", RegexOption.IGNORE_CASE), "🍎")
        .replace(Regex("\\bmac\\b", RegexOption.IGNORE_CASE), "🍎")
        .replace(Regex("\\blinux\\b|x11", RegexOption.IGNORE_CASE), "🐧")
        .replace(Regex("android", RegexOption.IGNORE_CASE), "🤖")
        .replace(Regex("\\biphone\\b|\\bipad\\b", RegexOption.IGNORE_CASE), "🍎")
        .trim()

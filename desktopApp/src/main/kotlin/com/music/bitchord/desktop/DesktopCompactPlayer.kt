package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import kotlin.math.roundToLong

internal fun compactPlaybackTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

/** The playhead is read here, so its ticks don't recompose navigation or the whole page. */
@Composable
internal fun DesktopCompactPlayer(
    song: Song,
    position: PlaybackPosition,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    canSeek: Boolean,
    onExpand: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    var scrub by remember(song.videoId) { mutableStateOf<Float?>(null) }
    val elapsed = position.positionMs.coerceAtLeast(0L).let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
    val fraction = scrub ?: if (durationMs > 0L) elapsed.toFloat() / durationMs else 0f
    val shown = if (scrub != null) (fraction * durationMs).roundToLong() else elapsed
    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().clip(shape).desktopBarGlass(shape)
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Open now playing", onClick = onExpand).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DesktopArtwork(song.thumbnailUrl, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), px = 80)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onPrevious, enabled = previousEnabled) {
                Icon(Icons.Rounded.SkipPrevious, "Previous track")
            }
            IconButton(onClick = onPlayPause) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = DesktopAccent, strokeWidth = 2.dp)
                else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play", tint = DesktopAccent)
            }
            IconButton(onClick = onNext, enabled = nextEnabled) { Icon(Icons.Rounded.SkipNext, "Next track") }
        }
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { onSeek((it * durationMs).roundToLong().coerceIn(0L, durationMs.coerceAtLeast(0L))) }
                scrub = null
            },
            enabled = durationMs > 0L && canSeek,
            modifier = Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Seek" },
            colors = SliderDefaults.colors(thumbColor = DesktopAccent, activeTrackColor = DesktopAccent,
                inactiveTrackColor = DesktopSecondary.copy(alpha = 0.25f)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(compactPlaybackTime(shown), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
            Text(if (durationMs > 0L) "−${compactPlaybackTime(durationMs - shown)}" else "--:--",
                color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

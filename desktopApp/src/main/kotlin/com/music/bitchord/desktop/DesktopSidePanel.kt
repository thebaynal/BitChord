package com.music.bitchord.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

internal enum class DesktopSidePanel { LYRICS, QUEUE }

/** A modal drawer on small windows; a bounded, resizable column on larger ones. */
@Composable
internal fun DesktopSidePanelColumn(
    panel: DesktopSidePanel?,
    onClose: () -> Unit,
    compact: Boolean,
    maxWidth: Dp,
    onPanelChange: (DesktopSidePanel) -> Unit,
    autoFollow: Boolean = true,
    onAutoFollowChange: (Boolean) -> Unit = {},
    upcomingCount: Int = 0,
    content: @Composable (DesktopSidePanel) -> Unit,
) {
    var shown by remember { mutableStateOf(panel) }
    if (panel != null) shown = panel
    val persistence = remember { DesktopPersistence() }
    var width by remember {
        mutableFloatStateOf(persistence.string("side_panel_width", "360").toFloatOrNull()?.coerceIn(280f, 520f) ?: 360f)
    }
    val density = LocalDensity.current.density
    val bounded = width.coerceIn(280f, maxWidth.value.coerceAtLeast(280f)).dp
    val pane: @Composable () -> Unit = {
        val which = shown ?: DesktopSidePanel.QUEUE
        Column(Modifier.fillMaxSize().background(DesktopSurface.copy(alpha = 0.98f))) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DesktopSidePanel.entries.forEach { destination ->
                    TextButton(onClick = { onPanelChange(destination) }) {
                        Text(if (destination == DesktopSidePanel.QUEUE) "Queue" else "Lyrics",
                            color = if (destination == which) DesktopAccent else DesktopSecondary)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "Close panel", tint = DesktopSecondary)
                }
            }
            if (which == DesktopSidePanel.LYRICS) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (autoFollow) "Following the song" else "Read at your pace",
                        modifier = Modifier.weight(1f), color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                    FilterChip(selected = autoFollow, onClick = { onAutoFollowChange(!autoFollow) },
                        label = { Text("Follow") }, colors = desktopChipColors())
                }
            } else {
                Text("$upcomingCount up next", Modifier.padding(start = 16.dp, bottom = 6.dp),
                    color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider(color = DesktopDivider)
            Crossfade(which, animationSpec = tween(160), label = "sidePanel", modifier = Modifier.weight(1f)) { page ->
                Box(Modifier.fillMaxSize().padding(horizontal = if (page == DesktopSidePanel.LYRICS) 16.dp else 4.dp,
                    vertical = 8.dp)) { content(page) }
            }
        }
    }
    if (compact) {
        AnimatedVisibility(visible = panel != null, modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(DesktopScrim)
                    .clickable(onClickLabel = "Dismiss side panel", onClick = onClose)
                    .semantics { contentDescription = "Dismiss side panel" })
                Box(Modifier.align(Alignment.CenterEnd).width(maxWidth).fillMaxHeight()) { pane() }
            }
        }
    } else {
        AnimatedVisibility(visible = panel != null,
            enter = expandHorizontally(tween(180), expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) + fadeOut()) {
            Row(Modifier.width(bounded).fillMaxHeight()) {
                Box(Modifier.width(8.dp).fillMaxHeight()
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                    .draggable(orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> width = (width - delta / density).coerceIn(280f, maxWidth.value.coerceAtLeast(280f)) },
                        onDragStopped = { persistence.saveString("side_panel_width", width.toString()) })
                    .semantics {
                        contentDescription = "Resize side panel"
                        progressBarRangeInfo = ProgressBarRangeInfo(bounded.value, 280f..maxWidth.value.coerceAtLeast(280f))
                        setProgress { value ->
                            width = value.coerceIn(280f, maxWidth.value.coerceAtLeast(280f))
                            persistence.saveString("side_panel_width", width.toString())
                            true
                        }
                    }.focusable().onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                            width = (width + if (event.key == Key.DirectionLeft) 16f else -16f)
                                .coerceIn(280f, maxWidth.value.coerceAtLeast(280f))
                            persistence.saveString("side_panel_width", width.toString())
                            true
                        } else false
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(2.dp).height(36.dp).background(DesktopDivider))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { pane() }
            }
        }
    }
}

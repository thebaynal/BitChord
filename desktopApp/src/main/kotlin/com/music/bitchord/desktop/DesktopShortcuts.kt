package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.unit.dp

internal enum class DesktopShortcut { SEARCH, PLAY_PAUSE, PREVIOUS, NEXT, VOLUME_UP, VOLUME_DOWN, QUEUE, LYRICS, HELP }

/** Editing owns Space and cursor/volume chords; search and help remain reachable everywhere. */
internal fun desktopShortcut(event: KeyEvent, editing: Boolean): DesktopShortcut? {
    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) return null
    if (event.isCtrlPressed) {
        if (event.key == Key.L) return DesktopShortcut.SEARCH
        if (event.key == Key.Slash) return DesktopShortcut.HELP
        if (editing) return null
        return when (event.key) {
            Key.DirectionLeft -> DesktopShortcut.PREVIOUS
            Key.DirectionRight -> DesktopShortcut.NEXT
            Key.DirectionUp -> DesktopShortcut.VOLUME_UP
            Key.DirectionDown -> DesktopShortcut.VOLUME_DOWN
            Key.Q -> DesktopShortcut.QUEUE
            Key.J -> DesktopShortcut.LYRICS
            else -> null
        }
    }
    return if (!editing && event.key == Key.Spacebar) DesktopShortcut.PLAY_PAUSE else null
}

/** Track actual platform text-input sessions, including fields in shared pages and dialogs. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopShortcutScope(content: @Composable (editing: Boolean) -> Unit) {
    var sessions by remember { mutableIntStateOf(0) }
    val interceptor = remember {
        PlatformTextInputInterceptor { request, next ->
            sessions++
            try { next.startInputMethod(request) } finally { sessions-- }
        }
    }
    InterceptPlatformTextInput(interceptor) { content(sessions > 0) }
}

@Composable
internal fun DesktopShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Ctrl+L" to "Search",
                    "Space" to "Play / pause",
                    "Ctrl+← / →" to "Previous / next track",
                    "Ctrl+↑ / ↓" to "Volume up / down",
                    "Ctrl+Q" to "Queue",
                    "Ctrl+J" to "Lyrics",
                    "Ctrl+/" to "Show shortcuts",
                    "Esc" to "Close panel / leave text field",
                ).forEach { (keys, action) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(keys, color = DesktopAccent, style = MaterialTheme.typography.bodySmall)
                        Text(action, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Playback shortcuts stay inactive while you type. Queue and lyrics need a selected track.",
                    color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Window-level delivery also works after a focused control disappears during resizing. */
internal class DesktopKeyboardDispatcher {
    var handler: (KeyEvent) -> Boolean = { false }
    fun dispatch(event: KeyEvent): Boolean = handler(event)
}
internal val LocalDesktopKeyboardDispatcher = staticCompositionLocalOf<DesktopKeyboardDispatcher?> { null }

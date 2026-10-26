@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.music.bitchord.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.*

class DesktopShortcutsTest {
    private fun key(code: Key, ctrl: Boolean = false, shift: Boolean = false,
        alt: Boolean = false, type: KeyEventType = KeyEventType.KeyDown) =
        KeyEvent(key = code, type = type, isCtrlPressed = ctrl, isShiftPressed = shift, isAltPressed = alt)

    @Test fun textEditingKeepsSpacesAndNavigation() {
        assertNull(desktopShortcut(key(Key.Spacebar), editing = true))
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Q, Key.J).forEach {
            assertNull(desktopShortcut(key(it, ctrl = true), editing = true))
        }
        assertEquals(DesktopShortcut.SEARCH, desktopShortcut(key(Key.L, ctrl = true), editing = true))
    }

    @Test fun shortcutsAreHandledOnceOnPressAndRespectOtherModifiers() {
        assertEquals(DesktopShortcut.PLAY_PAUSE, desktopShortcut(key(Key.Spacebar), false))
        assertNull(desktopShortcut(key(Key.Spacebar, type = KeyEventType.KeyUp), false))
        assertNull(desktopShortcut(key(Key.Spacebar, shift = true), false))
        assertNull(desktopShortcut(key(Key.DirectionLeft, alt = true), false))
        assertEquals(DesktopShortcut.NEXT, desktopShortcut(key(Key.DirectionRight, ctrl = true), false))
        assertEquals(DesktopShortcut.HELP, desktopShortcut(key(Key.Slash, ctrl = true), true))
    }

    @Test fun playbackTimeHandlesUnknownAndLongTracks() {
        assertEquals("0:00", compactPlaybackTime(-1))
        assertEquals("1:05", compactPlaybackTime(65_999))
        assertEquals("120:00", compactPlaybackTime(7_200_000))
    }
}

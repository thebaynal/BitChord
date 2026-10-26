@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.music.bitchord.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.*
import kotlinx.coroutines.*
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class DesktopUsabilityUiTest {
    private val song = Song("ui-fixture", "Beyond the journey", "A quiet evening", null, durationText = "3:00")

    @Composable
    private fun FixtureTheme(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = desktopColorScheme(), typography = desktopTypography()) {
            CompositionLocalProvider(LocalContentColor provides DesktopText, content = content)
        }
    }

    private class UiScene(context: CoroutineContext) : AutoCloseable {
        private val job = SupervisorJob()
        private val recomposer = FrameRecomposer(context + job)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(900, 600))
        val keyboardDispatcher = DesktopKeyboardDispatcher()
        private var clock = 0L
        suspend fun frames(count: Int = 24) {
            repeat(count) {
                yield()
                Snapshot.sendApplyNotifications()
                clock += 16_666_667L
                recomposer.performFrame(clock)
                scene.measureAndLayout()
            }
        }
        fun nodes(): List<SemanticsNode> {
            val owner = scene.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(scene)
            val layers = scene.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(scene) as List<*>
            val owners = listOf(owner) + layers.map {
                it!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(it)
            }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return owners.flatMap {
                val semantics = it.javaClass.getMethod("getSemanticsOwner").invoke(it) as SemanticsOwner
                descendants(semantics.unmergedRootSemanticsNode)
            }
        }
        fun text(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == value } == true }
        fun described(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true }
        fun click(node: SemanticsNode) {
            var target = node
            while (!target.config.contains(SemanticsActions.OnClick)) target = checkNotNull(target.parent)
            check(target.config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun key(key: Key, ctrl: Boolean = false): Boolean {
            val event = KeyEvent(key = key, type = KeyEventType.KeyDown, isCtrlPressed = ctrl)
            return keyboardDispatcher.dispatch(event) || scene.sendKeyEvent(event)
        }
        fun save(name: String) {
            val size = checkNotNull(scene.size)
            val directory = File(System.getProperty("bitchord.ui.screenshots", "build/reports/usability-ui")).apply { mkdirs() }
            Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
                scene.draw(surface.canvas.asComposeCanvas())
                surface.makeImageSnapshot().use { image -> File(directory, "$name.png").writeBytes(image.encodeToData()!!.bytes) }
            }
        }
        override fun close() { scene.close(); recomposer.close(); job.cancel() }
    }

    @Test fun searchAndHelpRemainReachableAcrossWindowSizes() = runBlocking {
        AppUi.install(DesktopAppUiHost)
        PlayerPlatform.install(DesktopPlayerHost)
        UiScene(coroutineContext).use { ui ->
            ui.scene.setContent {
                CompositionLocalProvider(LocalDesktopKeyboardDispatcher provides ui.keyboardDispatcher) { BitChordDesktopApp() }
            }
            ui.frames()
            ui.click(ui.described("Search"))
            ui.frames()
            val field = ui.nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config.getOrNull(SemanticsProperties.Focused) == true)
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Frieren"))
            ui.frames()
            for (width in listOf(900, 979, 980, 1220, 900)) {
                ui.scene.size = IntSize(width, 600)
                ui.frames()
                val input = ui.nodes().single { it.config.contains(SemanticsActions.SetText) }
                assertEquals("Frieren", input.config[SemanticsProperties.EditableText].text)
                assertTrue(input.boundsInRoot.width > 50 && input.boundsInRoot.right <= width)
            }
            assertTrue(ui.key(Key.L, ctrl = true))
            ui.frames()
            assertTrue(ui.nodes().single { it.config.contains(SemanticsActions.SetText) }.config[SemanticsProperties.Focused])
            ui.click(ui.described("Keyboard shortcuts"))
            ui.frames()
            ui.text("Keyboard shortcuts")
            ui.save("keyboard-shortcuts")
            ui.click(ui.text("Done"))
            ui.frames()
        }
    }

    @Test fun compactTransportSeeksAndKeepsButtonActionsSeparate() = runBlocking {
        val position = PlaybackPosition().apply { report(30_000) }
        var previous = 0; var play = 0; var next = 0; var expanded = 0; var seek = -1L
        var duration by mutableLongStateOf(180_000)
        UiScene(coroutineContext).use { ui ->
            ui.scene.setContent {
                FixtureTheme {
                    Box(Modifier.fillMaxSize()) {
                        DesktopCompactPlayer(song, position, duration, false, false, true, true, true,
                            onExpand = { expanded++ }, onPrevious = { previous++ }, onPlayPause = { play++ },
                            onNext = { next++ }, onSeek = { seek = it })
                    }
                }
            }
            ui.frames()
            ui.text("0:30"); ui.text("−2:30")
            ui.click(ui.described("Previous track")); ui.click(ui.described("Play")); ui.click(ui.described("Next track"))
            assertEquals(listOf(1, 1, 1, 0), listOf(previous, play, next, expanded))
            assertTrue(ui.described("Seek").config[SemanticsActions.SetProgress].action!!.invoke(0.5f))
            ui.frames()
            assertEquals(90_000L, seek)
            duration = 0
            ui.frames()
            assertTrue(ui.described("Seek").config.contains(SemanticsProperties.Disabled))
            ui.text("--:--")
            Unit
        }
    }

    @Test fun panelsOverlayCompactWindowsResizeWideOnesAndRespectLyricsFollow() = runBlocking {
        PlayerPlatform.install(DesktopPlayerHost)
        DesktopPersistence().saveString("side_panel_width", "360")
        var panel by mutableStateOf<DesktopSidePanel?>(DesktopSidePanel.QUEUE)
        var follow by mutableStateOf(true)
        val position = PlaybackPosition().apply { report(30_000) }
        val lines = List(80) { LyricLine(it * 3_000L, "Line $it — beyond the journey") }
        UiScene(coroutineContext).use { ui ->
            ui.scene.setContent {
                FixtureTheme {
                    DesktopFrame(containerColor = DesktopBackground,
                        backdrop = { DesktopPageBackdrop(null, it) },
                        topBar = { Text("FRIEREN", Modifier.padding(16.dp)) },
                        sidebar = {}, bottomBar = {},
                        trailing = { compact, width ->
                            DesktopSidePanelColumn(panel, { panel = null }, compact, width,
                                onPanelChange = { panel = it }, autoFollow = follow, onAutoFollowChange = { follow = it }, upcomingCount = 2) {
                                if (it == DesktopSidePanel.QUEUE) {
                                    QueueSidePanel(listOf(song, song.copy(videoId = "second", title = "A new chapter"),
                                        song.copy(videoId = "third", title = "On the road")), 0, false, {}, {}, { _, _ -> }, {})
                                } else {
                                    LyricsSidePanel(song, false, position, lines, null, emptyMap(), {}, false, {}, autoFollow = follow)
                                }
                            }
                        },
                    ) { padding, compact ->
                        Column(Modifier.fillMaxSize().padding(padding).semantics { contentDescription = "Main content" }) {
                            Text("Your library", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
                            if (compact) DesktopCompactPlayer(song, position, 180_000, false, false, true, true, true, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
            ui.frames(60)
            assertEquals(900f, ui.described("Main content").boundsInRoot.width, 1f)
            ui.described("Dismiss side panel")
            assertTrue(ui.nodes().none { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Resize side panel") == true })
            ui.save("compact-queue-drawer")
            ui.click(ui.described("Dismiss side panel")); ui.frames()
            assertNull(panel)
            panel = DesktopSidePanel.QUEUE
            ui.scene.size = IntSize(1220, 780)
            ui.frames(60)
            val before = ui.described("Main content").boundsInRoot.width
            ui.described("Resize side panel").config[SemanticsActions.SetProgress].action!!.invoke(480f)
            ui.frames()
            assertTrue(ui.described("Main content").boundsInRoot.width < before - 80)
            assertTrue(ui.described("Main content").boundsInRoot.width >= 420)
            ui.save("wide-queue-panel")
            ui.click(ui.text("Lyrics")); ui.frames(90)
            fun scroll(): Float = ui.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }
                .first { it.maxValue() > 0 }.value()
            ui.click(ui.text("Follow")); ui.frames()
            assertFalse(follow)
            val reading = scroll()
            position.report(150_000); position.seeks++
            ui.frames(120)
            assertEquals(reading, scroll(), 2f)
            ui.click(ui.text("Follow")); ui.frames(120)
            assertTrue(follow)
            assertTrue(scroll() > reading + 100)
            ui.save("lyrics-follow-panel")
        }
    }
}

package com.jrees.wowcompanion

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FpsCounterTest {
    private val events = mutableListOf<Pair<Int, Boolean>>()
    private val held = mutableSetOf<Int>()
    private val messages = mutableListOf<String>()
    private val oldSink = InputSink.sink
    private val oldUp = LauncherHooks.gameUp
    private val oldStart = LauncherHooks.gameStartSec
    private val oldMode = PlayModes.current
    private val oldPause = InputSink.pause
    private val oldStatus = GameInput.onStatus
    private val oldRestarting = SetupBridge.restarting
    private val endField = GameLife::class.java.getDeclaredField("endedAt").apply { isAccessible = true }
    private val oldEnded = endField.getLong(null)
    private val local = object : InputSink.Sink {
        override fun pressedKeys(): Set<Int> = held.toSet()
        override fun key(xKeycode: Int, down: Boolean) {
            events += xKeycode to down
            if (down) held += xKeycode else held -= xKeycode
        }
        override fun tap(fx: Float, fy: Float) { fail("FPS must not send an add-on tap too") }
    }

    @Before fun liveLocal() {
        PlayModes.current = PlayMode.LOCAL
        LauncherHooks.gameUp = { true }
        LauncherHooks.gameStartSec = System.currentTimeMillis() / 1000L
        endField.setLong(null, 0L)
        SetupBridge.restarting = false
        TapSinks.wire(PlayMode.LOCAL, local, null)
        InputSink.pause = { assertEquals(InputSink.KEY_HOLD_MS, it) }
        GameInput.onStatus = { messages += it }
    }

    @After fun restore() {
        InputSink.sink = oldSink
        LauncherHooks.gameUp = oldUp
        LauncherHooks.gameStartSec = oldStart
        PlayModes.current = oldMode
        InputSink.pause = oldPause
        GameInput.onStatus = oldStatus
        SetupBridge.restarting = oldRestarting
        endField.setLong(null, oldEnded)
    }

    private fun expected() = listOf(37 to true, 27 to true, 27 to false, 37 to false)

    @Test fun missingAndStaleAddonCapabilitiesStillRouteExactlyOneNativeChord() {
        for ((current, cap) in listOf(true to null, true to false, false to true)) {
            val hub = JSONObject()
            cap?.let { hub.put("fpsToggle", it) }
            GameUi.onHub(JSONObject().put("hub", hub), current)
            events.clear()
            assertTrue(GameUi.toggleFramerate())
            assertEquals(expected(), events)
            assertTrue(held.isEmpty())
        }
    }

    @Test fun heldLeftOrRightCtrlIsNotReleasedOrPressedAgain() {
        for (ctrl in listOf(37, 105)) {
            held.clear(); held += ctrl; events.clear()
            assertTrue(GameUi.toggleFramerate())
            assertEquals(listOf(27 to true, 27 to false), events)
            assertEquals(setOf(ctrl), held)
        }
    }

    @Test fun otherHeldModifiersAndRRefuseWithoutChangingAnyKey() {
        for (key in listOf(27, 50, 62, 64, 108, 133, 134)) {
            held.clear(); held += key
            assertNotNull(FpsCounter.unavailable())
            assertFalse(GameUi.toggleFramerate())
            assertTrue(events.isEmpty())
            assertEquals(setOf(key), held)
            assertTrue(messages.last().startsWith("Release"))
        }
    }

    @Test fun stoppedEndedRestartingDisconnectedAndStreamRoutesNeverSend() {
        fun refused() {
            val reason = FpsCounter.unavailable()
            assertNotNull(reason)
            assertFalse(GameUi.toggleFramerate())
            assertEquals(reason, messages.last())
            assertTrue(events.isEmpty())
            val row = PerformanceTileModel.lines(PerformanceTileModel.LIMIT,
                PerformanceTileModel.State(fpsUnavailable = reason)).first { it.key == PerformanceTileModel.L_GAME_FPS }
            assertEquals(setOf(0), row.off)
            assertEquals(reason, row.value)
        }
        LauncherHooks.gameUp = { false }; refused(); LauncherHooks.gameUp = { true }
        endField.setLong(null, System.currentTimeMillis() + 1000L); refused(); endField.setLong(null, 0L)
        SetupBridge.restarting = true; refused(); SetupBridge.restarting = false
        InputSink.sink = null; refused()
        InputSink.sink = object : InputSink.Sink {
            override fun key(xKeycode: Int, down: Boolean) { fail("unknown keyboard route") }
            override fun tap(fx: Float, fy: Float) { fail("unknown tap route") }
        }; refused()
        TapSinks.wire(PlayMode.LOCAL, local, null)
        PlayModes.current = PlayMode.STREAM; refused()
    }

    @Test fun interruptedPressReleasesOnlyKeysItOwns() {
        InputSink.pause = { throw InterruptedException("test") }
        assertFalse(GameUi.toggleFramerate())
        assertEquals(expected(), events)
        assertTrue(held.isEmpty())
        assertTrue(messages.last().contains("failed"))
    }

    @Test fun bottomPerformanceActionRoutesOnReleaseAndUnavailableButtonHasNoCursorStop() {
        val app = RuntimeEnvironment.getApplication()
        Settings.init(app)
        val view = PerformanceTiles(app, SettingsView(app) {})
        fun layout() {
            view.measure(View.MeasureSpec.makeMeasureSpec(1240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST))
            view.layout(0, 0, 1240, view.measuredHeight)
        }
        fun touch(box: RectF, action: Int) {
            val event = MotionEvent.obtain(0, 40, action, box.centerX(), box.centerY(), 0)
            view.onTouchEvent(event); event.recycle()
        }
        layout()
        val tile = view.tileBox(PerformanceTileModel.LIMIT)!!
        touch(tile, MotionEvent.ACTION_DOWN); touch(tile, MotionEvent.ACTION_UP); layout()
        val rows = PerformanceTileModel.lines(PerformanceTileModel.LIMIT, view.state())
        val fps = rows.first { it.key == PerformanceTileModel.L_GAME_FPS }
        assertEquals(PerformanceTileModel.Kind.ACTION, fps.kind)
        assertTrue(fps.off.isEmpty()); assertNull(fps.value)
        val before = rows.takeWhile { it.key != fps.key }.sumOf { it.words.size - it.off.size }
        val button = view.focusTargets()[view.tileIds().size + before]
        touch(button, MotionEvent.ACTION_DOWN)
        assertTrue(events.isEmpty())
        touch(button, MotionEvent.ACTION_UP)
        assertEquals(expected(), events)
        val enabledStops = view.focusTargets().size
        InputSink.sink = null
        assertEquals(enabledStops - 1, view.focusTargets().size)
        events.clear()
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP)
        assertTrue(events.isEmpty())
    }
}

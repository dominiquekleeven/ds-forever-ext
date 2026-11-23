package com.jrees.wowcompanion

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class GameFpsTest {
    @After fun clear() = GameFps.reset()

    @Test fun onlyFreshCurrentGameSamplesAreUsed() {
        GameFps.note(39.5, true, 100)
        assertEquals(39.5f, GameFps.read(1100)!!, 0f)
        assertNull(GameFps.read(3101))
        assertNull(GameFps.read(99))
        GameFps.note(60.0, false, 3200)
        assertNull(GameFps.read(3200))
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 10001.0)) {
            GameFps.note(v, true, 4000)
            assertNull(GameFps.read(4000))
        }
        GameFps.note(40.0, true, 5000)
        GameFps.reset()
        assertNull(GameFps.read(5000))
    }

    @Test fun labelsKeepGameAndOutputSeparateAndNeverInventMissingValues() {
        assertEquals("Game FPS 40.0", GameFps.label("Game", 40f))
        assertEquals("Output FPS 119.5", GameFps.label("Output", 119.5f))
        assertEquals("Output FPS unavailable", GameFps.label("Output", null))
        assertEquals("Game FPS unavailable", GameFps.label("Game", Float.NaN))
    }

    @Test fun performanceOffersAnExplicitToggleWithoutChangingTheFrameLimit() {
        val s = PerformanceTileModel.State(limit = 40)
        val lines = PerformanceTileModel.lines(PerformanceTileModel.LIMIT, s)
        val toggle = lines.single { it.key == PerformanceTileModel.L_GAME_FPS }
        assertEquals(listOf("Toggle"), toggle.words)
        assertEquals("WoW FPS counter (Ctrl+R)", toggle.label)
        assertEquals(PerformanceTileModel.Kind.ACTION, toggle.kind)
        assertEquals(40, s.limit)
    }
}

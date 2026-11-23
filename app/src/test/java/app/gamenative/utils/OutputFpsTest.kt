package app.gamenative.utils

import org.junit.Assert.*
import org.junit.Test

class OutputFpsTest {
    @Test fun presentsAreMeasuredAndResetOnStopsGapsAndCounterRestart() {
        val fps = OutputFps()
        assertNull(fps.sample(100, 1000))
        assertEquals(120f, fps.sample(220, 2000)!!, 0f)
        assertNull(fps.sample(220, 3000))
        assertNull(fps.sample(1, 4000))
        assertEquals(60f, fps.sample(61, 5000)!!, 0f)
        assertNull(fps.sample(300, 9000))
        fps.reset()
        assertNull(fps.sample(420, 10000))
    }

    @Test fun layerOutputIsIndependentOfBaseAndMustBeFreshAndValid() {
        assertEquals(119.5f, OutputFps.parse("base=40\nfps=119.5\n", 1000, 2000)!!, 0f)
        assertNull(OutputFps.parse("base=40\n", 1000, 2000))
        assertNull(OutputFps.parse("fps=120", 1000, 3001))
        assertNull(OutputFps.parse("fps=120", 3000, 2000))
        for (v in listOf("NaN", "Infinity", "-1", "0", "bad"))
            assertNull(OutputFps.parse("fps=$v", 1000, 2000))
    }
}

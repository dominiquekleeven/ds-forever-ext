package app.gamenative.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.gamenative.ui.data.PerformanceHudConfig
import app.gamenative.ui.data.PerformanceHudSize
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceHudViewTest {
    private fun hud(config: PerformanceHudConfig = PerformanceHudConfig()) =
        PerformanceHudView(RuntimeEnvironment.getApplication(), { 60f }, config, { 30f })

    private fun snapshot() = HudSnapshot(
        fpsValue = 60f, cpuValue = 28f, gpuValue = 42f,
        fps = "Output FPS 60 (presents)", gameFps = "Game FPS 30",
        cpu = "CPU 28%", gpu = "GPU 42%", ram = "RAM 4.2GB", battery = "BAT 75%",
        power = "PWR 5.2W", runtime = "LEFT 3:20", batteryTemp = "BAT TEMP 32°C",
        clock = "TIME 12:34", cpuTemp = "CPU TEMP 45°C", gpuTemp = "GPU TEMP 42°C",
        fan = null, tune = null,
    )

    private fun render(hud: PerformanceHudView, snapshot: HudSnapshot = snapshot()) {
        PerformanceHudView::class.java.getDeclaredMethod("renderSnapshot", HudSnapshot::class.java)
            .apply { isAccessible = true }.invoke(hud, snapshot)
    }

    private fun layout(hud: View, width: Int, height: Int = 1080) {
        hud.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
        )
        hud.layout(0, 0, hud.measuredWidth, hud.measuredHeight)
    }

    private fun children(group: ViewGroup) = (0 until group.childCount).map(group::getChildAt)
    private fun cells(hud: PerformanceHudView) = children(hud.getChildAt(0) as ViewGroup)
    private fun text(cell: View) = (cell as ViewGroup).getChildAt(0) as TextView

    private fun assertInside(group: ViewGroup) {
        children(group).filter { it.visibility != View.GONE }.forEach {
            assertTrue("left: $it", it.left >= 0)
            assertTrue("right: $it", it.right <= group.width)
            assertTrue("bottom: $it", it.bottom <= group.height)
            if (it is ViewGroup) assertInside(it)
        }
    }

    @Test fun landscapeUsesOneShallowHorizontalStrip() {
        val hud = hud()
        render(hud)
        layout(hud, 1920)
        assertEquals(1, hud.childCount)
        val cells = cells(hud)
        assertTrue(cells.size > 5)
        assertEquals(1, cells.map { it.top }.distinct().size)
        assertTrue(cells.zipWithNext().all { (a, b) -> b.left > a.right })
        assertTrue(hud.height < 80)
        assertInside(hud)
        assertTrue(cells.all { text(it).layout.getEllipsisCount(0) == 0 })
        System.getenv("PERFORMANCE_HUD_SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            hud.draw(Canvas(bitmap))
            File(dir, "horizontal-hud-1920x1080.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun allMetricsAndGraphsWrapAtLargeFontWithoutLeavingBounds() {
        val context = RuntimeEnvironment.getApplication()
        val config = android.content.res.Configuration(context.resources.configuration).apply { fontScale = 2f }
        val hud = PerformanceHudView(context.createConfigurationContext(config), { 60f },
            PerformanceHudConfig(
                size = PerformanceHudSize.LARGE,
                showBatteryRuntime = true, showBatteryTemperature = true, showClockTime = true,
                showFrameRateGraph = true, showCpuUsageGraph = true, showGpuUsageGraph = true,
            ))
        render(hud)
        layout(hud, 320, 1920)
        assertTrue(cells(hud).map { it.top }.distinct().size > 1)
        assertInside(hud)
        // Android 14 applies nonlinear font scaling; retain the actual 13sp LARGE size.
        val expectedTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 13f, hud.resources.displayMetrics,
        )
        cells(hud).forEach { assertEquals(expectedTextPx, text(it).textSize, 0.01f) }
        // A rotation gives the same cells more width; it does not reset their settings.
        layout(hud, 1920)
        assertInside(hud)
        assertEquals(12, cells(hud).size)
    }

    @Test fun enabledGraphsStayInlineAndRespectGraphOnlyChoices() {
        val hud = hud(PerformanceHudConfig(showFrameRateGraph = true, showCpuUsageGraph = true, showGpuUsageGraph = true))
        render(hud)
        layout(hud, 1920)
        val graphCells = cells(hud).filter { (it as ViewGroup).childCount == 2 }
        assertEquals(3, graphCells.size)
        graphCells.forEach { cell ->
            val graph = (cell as ViewGroup).getChildAt(1)
            assertEquals(View.VISIBLE, graph.visibility)
            assertTrue(graph.left >= text(cell).right)
            assertTrue(graph.width > 0)
        }
        hud.setConfig(PerformanceHudConfig(showFrameRate = false, showFrameRateGraph = true))
        layout(hud, 1920)
        assertEquals(View.GONE, text(cells(hud).first()).visibility)
        assertEquals(View.VISIBLE, (cells(hud).first() as ViewGroup).getChildAt(1).visibility)
        assertInside(hud)
    }

    @Test fun unavailableUpdatesKeepFpsIdentityAndReuseAttachedCells() {
        val hud = hud()
        render(hud)
        layout(hud, 1920)
        val firstCell = cells(hud).first()
        render(hud, snapshot().copy(fpsValue = null, fps = "Output FPS unavailable (presents)", gameFps = "Game FPS unavailable"))
        layout(hud, 1920)
        assertSame(firstCell, cells(hud).first())
        assertEquals("Output FPS unavailable (presents)", text(cells(hud)[0]).text.toString())
        assertEquals("Game FPS unavailable", text(cells(hud)[1]).text.toString())
        render(hud, snapshot().copy(cpu = null))
        layout(hud, 1920)
        assertFalse(cells(hud).any { text(it).text.startsWith("CPU ") && !text(it).text.startsWith("CPU TEMP") })
        assertInside(hud)
    }
}

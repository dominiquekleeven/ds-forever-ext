package com.jrees.wowcompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

/** Native Skia checks on synthetic world geometry. Optional CI pictures: BOTTOM_ROUTE_SHOTS_DIR. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class BottomRouteShotsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val gold = MarkerArt.PIN_GOLD
    private val blue = Color.rgb(143, 184, 232)
    private fun doc() = JSONObject().put("type", "spfroute").put("v", "sr1").put("on", true).put("c", 0).put("leg", 2)
        .put("p", JSONArray(listOf(500, 1000, 500, 900, 400, 900, 400, 800))).put("n", JSONArray(listOf(500, 900)))
    private fun map() = JSONObject().put("ok", true).put("mapID", 42).put("zone", "Synthetic route map")
        .put("x", 0.5).put("y", 0.5).put("wx", 500).put("wy", 1000).put("cont", 0).put("facing", 0)
        .put("rect", JSONObject().put("c", 0).put("t", 1000).put("b", 0).put("l", 2000).put("r", 0))

    private fun render(v: View): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1000, 700)
        return Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }
    private fun count(b: Bitmap, color: Int): Int {
        val p = IntArray(b.width * b.height); b.getPixels(p, 0, b.width, 0, 0, b.width, b.height)
        return p.count { it == color }
    }
    private fun shot(b: Bitmap, name: String) {
        System.getenv("BOTTOM_ROUTE_SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun assertRoute(baseline: Bitmap, active: Bitmap) {
        assertTrue("gold breadcrumbs/next waypoint", count(active, gold) > count(baseline, gold) + 20)
        assertTrue("blue forward cue", count(active, blue) > count(baseline, blue) + 3)
    }

    @Test fun compactHomeAndExpandedModuleRenderAndExpireWithoutPolestar() {
        Settings.init(app)
        for (compact in listOf(true, false)) {
            val v = MapView(app).apply { this.compact = compact; update(map(), 0) }
            val baseline = render(v)
            v.spfRoute.onDoc(doc(), SystemClock.elapsedRealtime())
            val active = render(v)
            assertRoute(baseline, active)
            shot(active, if (compact) "01_home_compact" else "02_home_expanded")
            v.spfRoute.clear()
            assertEquals(count(baseline, gold), count(render(v), gold))
            v.spfRoute.onDoc(doc(), SystemClock.elapsedRealtime())
            ShadowSystemClock.advanceBy(Duration.ofMillis(SpfRoute.STALE_MS + 1))
            assertEquals("stale route gone", count(baseline, gold), count(render(v), gold))
        }
    }

    @Test fun fullMapRoomUsesItsWorldProjectionAndClearsOnWrongContinent() {
        Settings.init(app)
        val v = WorldMapRoomView(app)
        v.model.maps[42] = WorldMapModel.MapInfo(42, "Synthetic route map", 3, 0, 1002, 668, emptyList(),
            world = WorldMapModel.World(0, 1000.0, 0.0, 2000.0, 0.0))
        WorldMapRoomView::class.java.getDeclaredField("artReady").apply { isAccessible = true }.setBoolean(v, true)
        v.update(map())
        val baseline = render(v)
        v.spfRoute.onDoc(doc(), SystemClock.elapsedRealtime())
        val active = render(v)
        assertRoute(baseline, active)
        shot(active, "03_full_map_room")
        v.updatePos(map().put("cont", 1))
        assertEquals("a different continent never borrows this route", count(baseline, gold), count(render(v), gold))
        v.updatePos(map())
        assertRoute(baseline, render(v))
        v.spfRoute.onDoc(JSONObject().put("v", "sr1").put("on", false), SystemClock.elapsedRealtime())
        assertEquals(count(baseline, gold), count(render(v), gold))
        v.backdropDone(lowMemory = true)
    }

    @Test fun worldAxesZoomRotationClipAndLiveProgressUseRealNativePixels() {
        val route = SpfRoute().apply { onDoc(doc(), 100) }
        val field = RectF(50f, 50f, 450f, 450f)
        val w = WorldMapModel.World(0, 1000.0, 0.0, 2000.0, 0.0)
        val painter = SpfRoutePaint()
        for (angle in listOf(0f, 90f)) for (zoom in listOf(1f, 2f)) {
            val b = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.rotate(angle, 250f, 250f)
            val walk = route.live(100, 500.0, 970.0, 0)!!
            painter.draw(c, walk, field, 100) { wx, wy ->
                // World Y is horizontal; world X is vertical. Keep the player's origin at the centre.
                floatArrayOf(250f + ((w.nx(wy) - 0.5) * 2000 * zoom).toFloat(),
                    250f + ((w.ny(wx) - 0.5) * 1000 * zoom).toFloat())
            }
            assertTrue(count(b, gold) > 30); assertTrue(count(b, blue) > 3)
            // No route behind the live 30-yard progress point. Axis and rotation are independently checked.
            val oldX = if (angle == 0f) 260 else 250
            val oldY = if (angle == 0f) 250 else 260
            assertEquals("no line back to old start", Color.TRANSPARENT, b.getPixel(oldX, oldY))
            for (x in 0 until 500) for (y in listOf(0, 49, 450, 499)) assertEquals(Color.TRANSPARENT, b.getPixel(x, y))
            shot(b, "04_geometry_${angle.toInt()}_${zoom.toInt()}")
        }
    }
}

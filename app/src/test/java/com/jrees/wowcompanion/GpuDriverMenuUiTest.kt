package com.jrees.wowcompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GpuDriverMenuUiTest {
    private val service = object : GpuDriverMenu.Service {
        override var state = GpuDriverMenu.State(adreno = true)
        override var enabled = true
        val saves = ArrayList<String?>()
        override fun refresh() {}
        override fun select(id: String?) { saves += id; state = state.copy(selected = id) }
        override fun importZip() { state = state.copy(busy = true) }
    }

    @Before fun setup() {
        Tr.init(RuntimeEnvironment.getApplication())
        System.getenv("TRADE_ART_ROOT")?.let { root ->
            ArtHarvest::class.java.getDeclaredField("rootDir").apply { isAccessible = true }.set(null, File(root))
        }
        GpuDriverMenu.service = service
        StartMenu.open = true
    }
    @After fun cleanup() { StartMenu.open = false; GpuDriverMenu.service = null }

    private fun draw(name: String, game: Boolean, small: Boolean = false): List<Int> {
        val bitmap = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Theme.ink)
        val area = if (small) RectF(120f, 160f, 1120f, 740f)
            else if (game) RectF(120f, StartView.GAP, 1120f, 1080f - StartView.ROW_DOWN - StartView.BUTTON_H - StartView.GAP)
            else RectF(ChooserPanel.MARGIN, ChooserPanel.MARGIN + 110f, 1240f - ChooserPanel.MARGIN,
                1080f - ChooserPanel.MARGIN - ChooserPanel.BUTTON_H - ChooserPanel.GAP * 2)
        val hits = ArrayList<Pair<RectF, Int>>()
        StartMenu.draw(canvas, area, -1, hits, RectF(40f, 940f, 260f, 1040f), game)
        for ((rect, id) in hits) if (id != 12) assertTrue("$name: $id leaves $area: $rect",
            rect.left >= area.left - 0.5f && rect.right <= area.right + 0.5f && rect.top >= area.top - 0.5f && rect.bottom <= area.bottom + 0.5f)
        System.getenv("TRADE_SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
        return hits.map { it.second }
    }

    @Test fun bothMenusFitAndExposeDriverInsideMenuThenUseTheNativePanel() {
        for (game in listOf(false, true)) for (small in listOf(false, true)) {
            assertTrue(StartMenu.GPU_DRIVER in draw("gpu_menu_${game}_$small", game, small))
            StartMenu.press(StartMenu.GPU_DRIVER)
            val hits = draw("gpu_panel_${game}_$small", game, small)
            assertTrue(GpuDriverMenu.ROW0 in hits)
            assertTrue(GpuDriverMenu.ROW0 + 1 in hits)
            assertTrue(GpuDriverMenu.IMPORT in hits)
            assertTrue(GpuDriverMenu.BACK in hits)
            StartMenu.back()
        }
    }

    @Test fun manyLongNamesBusyAndErrorsStayWithinTheModuleInEveryTheme() {
        service.state = service.state.copy(installed = (0 until 35).map {
            GpuDriverMenu.Driver("legacy-$it", "Qualcomm Adreno Turnip ".repeat(9))
        }, selected = "legacy-34")
        for (theme in Theme.presets) Theme.preview(theme) {
            GpuDriverMenu.show()
            draw("gpu_long_${theme.id}", false)
            draw("gpu_long_small_${theme.id}", true, true)
            service.state = service.state.copy(busy = true, message = "Import failed: ".repeat(40))
            val hits = draw("gpu_error_${theme.id}", false, true)
            assertFalse(GpuDriverMenu.IMPORT in hits)
            assertFalse(hits.any { it >= GpuDriverMenu.ROW0 })
            assertTrue(GpuDriverMenu.BACK in hits)
            service.state = service.state.copy(busy = false)
            GpuDriverMenu.close()
        }
    }

    @Test fun waitingScreenPadRoutesToSubmenuInsteadOfSetupOrGameAndCancelDoesNotSave() {
        GpuDriverMenu.show()
        assertTrue(SetupBridge.holdsGamePad())
        assertTrue(StartKeys.route(KeyEvent.KEYCODE_DPAD_DOWN, true, true))
        assertEquals(GpuDriverMenu.ROW0 + 1, StartMenu.focusId())
        assertTrue(service.saves.isEmpty())
        assertTrue(StartKeys.route(KeyEvent.KEYCODE_BUTTON_B, true, true))
        assertTrue(StartMenu.open)
        assertFalse(GpuDriverMenu.open)
        assertTrue(service.saves.isEmpty())
        GpuDriverMenu.show()
        StartKeys.route(KeyEvent.KEYCODE_DPAD_DOWN, true, true)
        StartKeys.route(KeyEvent.KEYCODE_BUTTON_A, true, true)
        assertEquals(listOf("System"), service.saves)
    }

    @Test fun waitingScreenTouchRequiresACompleteTapAndBackKeepsTheSavedChoice() {
        val context = RuntimeEnvironment.getApplication()
        val view = StartView(context)
        view.layout(0, 0, 1240, 1080)
        view.menuOpen = true
        StartMenu.press(StartMenu.GPU_DRIVER)
        val bitmap = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val hits = ArrayList<Pair<RectF, Int>>()
        StartMenu.draw(Canvas(bitmap), RectF(120f, StartView.GAP, 1120f,
            1080f - StartView.ROW_DOWN - StartView.BUTTON_H - StartView.GAP), -1, hits, RectF(), true)
        fun event(action: Int, id: Int) {
            val rect = hits.first { it.second == id }.first
            MotionEvent.obtain(0, 0, action, rect.centerX(), rect.centerY(), 0).let { e ->
                view.onTouchEvent(e); e.recycle()
            }
        }
        event(MotionEvent.ACTION_DOWN, GpuDriverMenu.ROW0)
        event(MotionEvent.ACTION_UP, GpuDriverMenu.ROW0 + 1)
        assertTrue(service.saves.isEmpty())
        event(MotionEvent.ACTION_DOWN, GpuDriverMenu.ROW0 + 1)
        event(MotionEvent.ACTION_UP, GpuDriverMenu.ROW0 + 1)
        assertEquals(listOf("System"), service.saves)
        event(MotionEvent.ACTION_DOWN, GpuDriverMenu.BACK)
        event(MotionEvent.ACTION_UP, GpuDriverMenu.BACK)
        assertFalse(GpuDriverMenu.open)
        assertTrue(StartMenu.open)
        assertEquals("System", service.state.selected)
        bitmap.recycle()
    }

    @Test fun topScreenHasNoStandaloneDriverButtonOrMaterialDialogAndHasOnlyOneOwner() {
        val root = listOf(File("."), File("..")).first { File(it, "app/src/main/java").isDirectory }
        fun source(path: String) = File(root, "app/src/main/java/$path").readText()
        val wow = source("app/gamenative/ui/screen/wow/WoWForeverScreen.kt")
        assertFalse(wow.contains("WowGpuDriverButton"))
        assertTrue(wow.contains("GpuDriverMenu.holdsLaunch"))
        val controller = source("app/gamenative/ui/screen/wow/WowGpuDriverMenu.kt")
        assertFalse(controller.contains("AlertDialog"))
        assertTrue(controller.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(controller.contains("GpuDriverMenu.service === controller"))
        val launcher = source("app/gamenative/ui/PluviaMain.kt")
        assertEquals(1, Regex("WowGpuDriverMenu\\(\\)").findAll(launcher).count())
    }
}

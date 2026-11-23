package com.jrees.wowcompanion

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GpuDriverMenuTest {
    private class Fake : GpuDriverMenu.Service {
        override var state = GpuDriverMenu.State(adreno = true)
        override var enabled = true
        val saves = ArrayList<String?>()
        var imports = 0
        var refreshes = 0
        override fun refresh() { refreshes++ }
        override fun select(id: String?) { saves += id; state = state.copy(selected = id) }
        override fun importZip() { imports++; state = state.copy(busy = true) }
    }
    private val service = Fake()
    @Before fun setup() { GpuDriverMenu.service = service; StartMenu.open = true }
    @After fun cleanup() { StartMenu.open = false; GpuDriverMenu.service = null; GpuDriverMenu.launching = false }

    @Test fun menuEntryBackAndFocusNeverSaveWhileBrowsing() {
        StartMenu.press(StartMenu.GPU_DRIVER)
        assertTrue(StartMenu.submenuOpen)
        assertTrue(GpuDriverMenu.holdsLaunch)
        assertEquals(1, service.refreshes)
        assertEquals(GpuDriverMenu.ROW0, StartMenu.focusId())
        StartMenu.pickerMove(0, 1)
        assertTrue(service.saves.isEmpty())
        assertTrue(StartMenu.back())
        assertTrue(StartMenu.open)
        assertFalse(GpuDriverMenu.holdsLaunch)
        assertTrue(service.saves.isEmpty())
        StartMenu.press(StartMenu.GPU_DRIVER)
        assertEquals(GpuDriverMenu.ROW0, StartMenu.focusId())
    }

    @Test fun touchAndPadApplyOnlyExplicitChoicesIncludingResetAndLegacyId() {
        val legacy = "Turnip-custom-legacy"
        service.state = service.state.copy(installed = listOf(GpuDriverMenu.Driver(legacy, "Old Turnip")), selected = legacy)
        StartMenu.press(StartMenu.GPU_DRIVER)
        assertEquals(GpuDriverMenu.ROW0 + 2, StartMenu.focusId())
        StartMenu.pickerMove(0, -1)
        StartMenu.pickerA()
        assertEquals(listOf("System"), service.saves)
        StartMenu.press(GpuDriverMenu.ROW0)
        assertEquals(listOf("System", null), service.saves)
        assertEquals(legacy, service.state.installed.single().id)
    }

    @Test fun manyDriversArePagedAndEveryDriverIsReachableWithoutSaving() {
        service.state = service.state.copy(installed = (0 until 40).map { GpuDriverMenu.Driver("driver-$it", "Long driver ".repeat(16)) })
        GpuDriverMenu.show()
        assertEquals(14, GpuDriverMenu.pageCount)
        repeat(41) { GpuDriverMenu.move(0, 1) }
        assertEquals(GpuDriverMenu.ROW0 + 41, GpuDriverMenu.focus)
        assertEquals(13, GpuDriverMenu.page)
        assertTrue(service.saves.isEmpty())
        GpuDriverMenu.move(0, 1)
        assertEquals(GpuDriverMenu.IMPORT, GpuDriverMenu.focus)
        GpuDriverMenu.move(1, 0)
        assertEquals(GpuDriverMenu.BACK, GpuDriverMenu.focus)
        GpuDriverMenu.press(GpuDriverMenu.focus)
        assertFalse(GpuDriverMenu.open)
    }

    @Test fun disabledBusyErrorsAndCancellationPreserveSelectionAndAllowBack() {
        service.state = service.state.copy(selected = "missing-old-driver", adreno = false,
            installed = listOf(GpuDriverMenu.Driver("new", "New driver")))
        GpuDriverMenu.show()
        assertEquals("Saved driver unavailable", GpuDriverMenu.state.selectedLabel)
        assertFalse(GpuDriverMenu.enabled(GpuDriverMenu.IMPORT))
        assertFalse(GpuDriverMenu.enabled(GpuDriverMenu.ROW0 + 2))
        assertTrue(GpuDriverMenu.enabled(GpuDriverMenu.ROW0))
        assertTrue(GpuDriverMenu.enabled(GpuDriverMenu.ROW0 + 1))
        service.state = service.state.copy(adreno = true)
        GpuDriverMenu.press(GpuDriverMenu.IMPORT)
        assertEquals(1, service.imports)
        assertFalse(GpuDriverMenu.enabled(GpuDriverMenu.ROW0))
        GpuDriverMenu.press(GpuDriverMenu.ROW0)
        GpuDriverMenu.press(GpuDriverMenu.BACK)
        assertFalse(GpuDriverMenu.open)
        assertTrue(GpuDriverMenu.holdsLaunch) // OS picker/import still owns the pending launch.
        service.state = service.state.copy(busy = false, message = "Import failed. Previous choice is unchanged.")
        assertFalse(GpuDriverMenu.holdsLaunch)
        assertEquals("missing-old-driver", service.state.selected)
        assertTrue(service.saves.isEmpty())
        service.enabled = false
        GpuDriverMenu.show()
        assertFalse(GpuDriverMenu.enabled(GpuDriverMenu.ROW0))
        assertTrue(GpuDriverMenu.enabled(GpuDriverMenu.BACK))
    }

    @Test fun closingTheMenuReleasesItsPanelAndNoServiceIsNotADanglingAction() {
        GpuDriverMenu.show()
        StartMenu.open = false
        assertFalse(GpuDriverMenu.open)
        GpuDriverMenu.service = null
        GpuDriverMenu.show()
        assertFalse(GpuDriverMenu.open)
    }
}

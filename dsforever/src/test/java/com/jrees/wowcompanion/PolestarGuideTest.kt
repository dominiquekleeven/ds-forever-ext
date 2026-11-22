package com.jrees.wowcompanion

import org.junit.Assert.*
import org.junit.Test

/** Retired callers and old preferences must never bring Polestar's guide back. */
class PolestarGuideTest {
    @Test fun retiredChoicesCannotStartTheGuide() {
        for (old in listOf(null, "on", "off", "polestar", "safepath", "questtrail")) {
            assertEquals("vanilla", PolestarGuide.mode(old))
            assertFalse(PolestarGuide.ask(old))
            assertEquals(false, PolestarGuide.wanted(old))
        }
        PolestarGuide.set(true)
        Settings.questTracking = "polestar"
        assertEquals("vanilla", Settings.questTracking)
        assertEquals("off", Settings.polestarGuide)
        assertEquals(listOf("vanilla"), Settings.QUEST_TRACKING)
        assertFalse(ThirdPartyAddons.coreWanted(ThirdPartyAddons.POLESTAR))
        assertFalse(ThirdPartyAddons.prefetch(ThirdPartyAddons.POLESTAR, "core"))
    }

    @Test fun obsoleteLauncherInputDoesNotOfferPolestar() {
        val ready = SetupFlow.Inputs(path = "/g", folderFound = true, art = SetupFlow.Art.READY,
            loginSaved = true, addonsInstalled = true, polestarAsk = true)
        assertNull(SetupFlow.screen(ready).prompt)
        assertTrue(SetupFlow.screen(ready).play!!.enabled)
    }
}

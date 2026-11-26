package com.jrees.wowcompanion

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source boundary checks complement tools/test_ltrt_stancebar.py's real Lua 5.1 clicks. */
class LtRtStanceBarTest {
    private fun addon(folder: String, name: String): String {
        val base = "companion/app/src/main/assets/companion/addons/$folder/$name.lua"
        return listOf(File(base), File("../$base")).first { it.isFile }.readText()
    }

    @Test fun routerFollowsOnlyRelevantLayoutAndControllerEvents() {
        val s = addon("WowCompanionSpells", "PadMap")
        for (event in listOf("GAMEPAD_STANCE_BAR_OVERRIDE_CHANGED", "GAMEPAD_POSSESS_BAR_OVERRIDE_CHANGED",
            "GAME_PAD_CONNECTED", "GAME_PAD_DISCONNECTED", "GAME_PAD_ACTIVE_CHANGED", "GAME_PAD_CONFIGS_CHANGED"))
            assertTrue(event, event in s)
        assertTrue("R.trWired == nil or R.trWired == R.trNow()" in s)
        assertTrue("if inCombat() then R.rewireFight = true; return end" in s)
        assertTrue("if R.padRelink then" in s && "pcall(OV.pm.link)" in s)
        assertTrue("not R.layoutCv" in s)
    }

    @Test fun displayedHeldLayerDropsCachedIndicesOnControllerChanges() {
        val s = addon("WowCompanion", "ActionBar")
        for (event in listOf("GAME_PAD_CONNECTED", "GAME_PAD_DISCONNECTED", "GAME_PAD_ACTIVE_CHANGED", "GAME_PAD_CONFIGS_CHANGED"))
            assertTrue(event, event in s)
        assertTrue("for _, e in ipairs(NB.PAD_EV) do pcall(f.RegisterEvent, f, e) end" in s)
        assertTrue("if NB.PAD_SET[e] then NB.idx = nil; return end" in s)
    }
}

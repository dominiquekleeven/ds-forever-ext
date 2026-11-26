package com.jrees.wowcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source checks complement the app's real-view GC test; they do not assert full host collectibility. */
class HomeListenerWiringTest {
    private val src = listOf("../companion/app/src/main/java/com/jrees/wowcompanion", "companion/app/src/main/java/com/jrees/wowcompanion")
        .map { File(it) }.first { it.isDirectory }

    @Test fun theHostsOwnStaticListenersHoldItWeakly() {
        val host = File(src, "CompanionHost.kt").readText()
        val registry = "(HomeSettings|HomeLayoutStore|RadialMeta|DiagLog)"
        val all = Regex("\\b$registry\\.onChange\\b").findAll(host).toList()
        val weak = Regex("\\b$registry\\.onChange\\(weakly\\(this\\) \\{ h ->").findAll(host).toList()
        assertEquals(setOf("HomeSettings", "HomeLayoutStore", "RadialMeta", "DiagLog"), all.map { it.groupValues[1] }.toSet())
        // Ext has its own listener set, not upstream's top-castbar/swing additions.
        assertEquals("every registration is weak, without pinning upstream's count", all.size, weak.size)
        assertTrue(host.contains("HomeLayoutStore.onChange(weakly(this) { h -> h.ui.post { if (!h.shell.hostGone()) h.lastDpad?.let { h.mirrorCheck(it); h.topCheck(it) } } })"))
        assertTrue(host.contains("h.pauseOverTell(h.lastPadMap, \"the setting\")"))
        assertTrue(host.contains("if (!h.shell.hostGone()) h.petBTell(\"the setting\")"))
    }

    @Test fun theScopedViewsUseWeakCallbacksAndKeepTheirSettingsWork() {
        val registrations = mapOf("HomeView" to listOf("HomeSettings.onChange", "HomeLayoutStore.onOwner", "GameUi.onChange", "ChatFilter.onChange"),
            "MapView" to listOf("Settings.onChange"), "CastView" to listOf("ItemCd.onChange"),
            "CastStripView" to listOf("ItemCd.onChange"), "ChatView" to listOf("ChatFilter.onChange", "TalkKeep.onChange"))
        for ((name, calls) in registrations) {
            val text = File(src, "$name.kt").readText()
            for (call in calls) {
                assertTrue("$name: $call still registered", text.contains("$call(weakly(this) { v ->"))
                assertFalse("$name: no strong $call", Regex(Regex.escape(call) + "\\s*\\{").containsMatchIn(text))
            }
        }
        val home = File(src, "HomeView.kt").readText()
        assertTrue(home.contains("v.post { v.homeSettingsChanged() }"))
        assertTrue(home.contains("if (flow.setRest(restWanted())) changed()"))
        assertTrue(home.contains("v.editOwnerCheck(\"the character changed\")"))
        assertTrue(File(src, "MapView.kt").readText().contains("v -> v.applySettings()"))
    }

    @Test fun theDisconnectHoldIsOnlyDiagnosticAndIsResetAndFed() {
        val host = File(src, "CompanionHost.kt").readText()
        val login = File(src, "NativeLogin.kt").readText()
        assertTrue(host.contains("outOfWorld = NativeLogin.outOfWorldHold"))
        assertTrue(login.contains("val outOfWorldHold: Boolean get() = outOfWorld && !worldAgain.back"))
        assertTrue(login.contains("outOfWorld = true\n        worldAgain.reset()"))
        assertTrue(login.contains("if (outOfWorld) worldAgain.second(rx)"))
        assertEquals("traffic recovery never changes login policy", 3, Regex("worldAgain\\.").findAll(login).count())
    }
}

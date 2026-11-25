package com.jrees.wowcompanion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AuctionTooltipTest {
    @Test fun rendererReceivesTheActualListingStatsAndSellPrice() {
        val tip = javaClass.getResourceAsStream("/auction-tooltip.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        val body = TipStack.lines(tip.getJSONArray("lines"), true)
        assertEquals("ahrow 1", tip.getString("key"))
        assertEquals(7001L, tip.getLong("auction"))
        assertEquals(listOf("87 Armor", "+5 Strength", "Requires Level 10", "Sell Price:"), body.map { it.t })
        assertEquals("Mail", body[0].r)
        assertEquals(123L, body[3].money)
        assertEquals("00ff00", tip.getJSONArray("lines").getJSONObject(2).getString("c"))
        assertEquals("ff0000", tip.getJSONArray("lines").getJSONObject(3).getString("c"))
    }

    @Test fun touchRequestsReadOnlyListingStatsAndRejectsAnOlderAuctionAtTheSameIndex() {
        val root = listOf(File("."), File("..")).first {
            File(it, "companion/app/src/main/java/com/jrees/wowcompanion/InteractView.kt").isFile
        }
        val view = File(root, "companion/app/src/main/java/com/jrees/wowcompanion/InteractView.kt").readText()
        assertTrue(view.contains("hit.startsWith(\"sel:ahrow \") -> { selected = hit.substring(4); look(selected!!) }"))
        assertTrue(view.contains("e?.o?.optString(\"lookRow\") == \"ibuy\""))
        assertTrue(view.contains("TapBridge.tap(context, d.optJSONObject(\"pad\"), row, col)"))
        assertTrue(view.contains("auction == lookedAuction"))
        assertTrue(view.contains("entries().firstOrNull { it.cmd == cmd }?.o?.optLong(\"auction\", -1) != t.optLong(\"auction\", -1)"))
    }
}

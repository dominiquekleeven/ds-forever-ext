package com.jrees.wowcompanion

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AuctionBuyCellTest {
    @Test fun itemSelectionUsesThePublishedRowAndConfirmationStaysSeparate() {
        for (i in 1..40) assertEquals("iqty", InteractRows.of("ahrow $i", "iqty"))
        assertEquals("iact", InteractRows.of("ahbuy", "iqty"))
        assertEquals("ibuy", InteractRows.of("ahres 1", "iqty"))
        assertEquals("ibuy", InteractRows.of("ahrow 1")) // old add-on document
        assertEquals("ibuy", InteractRows.of("ahrow 1", "unknown"))
        assertEquals("ibuy", InteractRows.of("buy 1 42", "iqty"))
        assertEquals("isell", InteractRows.of("ahfav", "iqty"))
    }

    @Test fun viewKeepsTheSelectedListingAndDoesNotEnableAMissingCell() {
        val root = listOf(File("."), File("..")).first {
            File(it, "companion/app/src/main/java/com/jrees/wowcompanion/InteractView.kt").isFile
        }
        val view = File(root, "companion/app/src/main/java/com/jrees/wowcompanion/InteractView.kt").readText()
        assertTrue(view.contains("val pe = pick?.let { entry(it) }"))
        assertTrue(view.contains("val price = pe?.o?.optLong(\"price\", 0) ?: 0L"))
        assertTrue(view.contains("pe != null && pe.slot >= 0 && price > 0 && !pe.o.optBoolean(\"mine\") && price <= purse"))
        assertTrue(view.contains("InteractRows.of(cmd, doc?.optJSONObject(\"auction\")?.optJSONObject(\"item\")?.optString(\"rowTap\"))"))
        assertTrue(view.contains("entry(if (item.optString(\"rowTap\") == \"iqty\") \"ahbuy\" else \"ahrow \${buy.optInt(\"row\")}\")"))
    }
}

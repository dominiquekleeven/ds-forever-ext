package com.jrees.wowcompanion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** i167: the auction house's Sell grid (AhSellGrid) - the add-on's rows (Interact.lua AH.gridRows), folds, a scroll that never jumps */
class AhSellGridTest {
    private fun item(bag: Int, s: Int, id: Int) = JSONObject().put("bag", bag).put("s", s).put("id", id)
    private fun group(bag: Int, n: Int, shut: Boolean = false) = JSONObject().put("bag", bag).put("n", n).put("shut", shut)
    private val bags = listOf(item(0, 1, 10), item(0, 2, 11), item(0, 3, 12), item(0, 4, 13), item(0, 5, 14), item(1, 1, 20))

    @Test fun headersThenItemsFourARow() {
        val cells = AhSellGrid.layout(bags, listOf(group(0, 5), group(1, 1)), 4, 330f)
        assertEquals(listOf("ahsfold 0", "ahsitem 0 1 10", "ahsitem 0 2 11", "ahsitem 0 3 12", "ahsitem 0 4 13", "ahsitem 0 5 14",
            "ahsfold 1", "ahsitem 1 1 20"), cells.map { it.cmd })
        assertTrue(cells[0].head && !cells[1].head)
        assertEquals("the fifth item starts the second row", cells[1].box.left, cells[5].box.left, 0.01f)
        assertTrue(cells[5].box.top > cells[1].box.top)
        assertEquals("the four in a row fit the panel", 330f - AhSellGrid.EDGE, cells[4].box.right, 0.5f)
        assertTrue("the next bag's header below the last row", cells[6].box.top > cells[5].box.bottom)
    }

    @Test fun aFoldedBagShowsItsHeaderOnly() {
        val cells = AhSellGrid.layout(bags, listOf(group(0, 5, shut = true), group(1, 1)), 4, 330f)
        assertEquals(listOf("ahsfold 0", "ahsfold 1", "ahsitem 1 1 20"), cells.map { it.cmd })
        assertTrue(AhSellGrid.height(cells) < AhSellGrid.height(AhSellGrid.layout(bags, listOf(group(0, 5), group(1, 1)), 4, 330f)))
    }

    @Test fun theScrollFollowsTheCursorAndOtherwiseStays() {
        val b = AhSellGrid.Box(8f, 500f, 80f, 572f)
        assertEquals("in view: unchanged (a post never sends it to the top)", 300f, AhSellGrid.follow(b, 300f, 400f), 0.01f)
        assertEquals("below: just enough", 576f - 200f, AhSellGrid.follow(b, 0f, 200f), 0.01f)
        assertEquals("above: just enough", 496f, AhSellGrid.follow(b, 700f, 200f), 0.01f)
    }

    // issue 590, e46b123a + AH-only 9b769805: refused tiles and controller scroll.
    @Test fun theRefusedItemsShowGreyedAfterTheSellableOnes() {
        val no = listOf(item(0, 6, 30).put("no", "bound"), item(0, 7, 31).put("no", "quest"), item(2, 1, 40).put("no", "damaged"))
        val groups = listOf(group(0, 5).put("x", 2), group(1, 1), group(2, 0).put("x", 1))
        val cells = AhSellGrid.layout(bags, groups, 4, 330f, no)
        assertEquals(listOf("ahsfold 0", "ahsitem 0 1 10", "ahsitem 0 2 11", "ahsitem 0 3 12", "ahsitem 0 4 13", "ahsitem 0 5 14",
            "ahsno 0 6 30", "ahsno 0 7 31", "ahsfold 1", "ahsitem 1 1 20", "ahsfold 2", "ahsno 2 1 40"), cells.map { it.cmd })
        assertEquals(listOf(null, null, null, null, null, null, "bound", "quest", null, null, null, "damaged"), cells.map { it.no })
        assertEquals(cells[2].box.left, cells[6].box.left, 0.01f)
        assertEquals(cells[5].box.top, cells[6].box.top, 0.01f)
        assertTrue(cells.filter { it.no != null }.none { it.head || it.cmd.startsWith("ahsitem ") })
        assertEquals(listOf("ahsfold 0", "ahsfold 1", "ahsitem 1 1 20", "ahsfold 2", "ahsno 2 1 40"),
            AhSellGrid.layout(bags, listOf(group(0, 5, shut = true), group(1, 1), group(2, 0)), 4, 330f, no).map { it.cmd })
        assertEquals("Bound", AhSellGrid.noWord("bound")); assertEquals("Quest", AhSellGrid.noWord("quest"))
        assertEquals("Damaged", AhSellGrid.noWord("damaged")); assertEquals("Can't sell", AhSellGrid.noWord("no"))
        assertEquals("Can't sell", AhSellGrid.noWord("new code"))
        assertEquals("The auction house won't take this", AhSellGrid.noWhy("no"))
        assertEquals("5 · 2 can't sell", AhSellGrid.headCount(groups[0]))
        assertEquals("1", AhSellGrid.headCount(groups[1]))
        val dir = listOf("../companion/app/src/main/assets/companion/i18n", "companion/app/src/main/assets/companion/i18n")
            .map { java.io.File(it) }.first { it.isDirectory }
        for (lang in listOf("es", "fr")) {
            val bytes = java.io.File(dir, "$lang.json").readBytes()
            val text = bytes.toString(Charsets.UTF_8)
            assertTrue("$lang: all line endings remain CRLF", !text.replace("\r\n", "").contains('\n') && !text.replace("\r\n", "").contains('\r'))
            Tr.load(lang, text)
            for (code in listOf("bound", "quest", "damaged", "no")) {
                assertTrue("$code $lang", Tr.t(AhSellGrid.noWord(code), lang) != AhSellGrid.noWord(code))
                assertTrue("$code why $lang", Tr.t(AhSellGrid.noWhy(code), lang) != AhSellGrid.noWhy(code))
            }
        }
        assertEquals("5 · 2 no se venden", Tr.t("5 · 2 can't sell", "es"))
        assertEquals("5 · 2 invendables", Tr.t("5 · 2 can't sell", "fr"))
    }

    @Test fun aScrollBarOnlyWhenTheGridIsTallerThanItsPanel() {
        assertEquals(null, AhSellGrid.bar(0f, 300f, 400f))
        assertEquals(0f to 0.5f, AhSellGrid.bar(0f, 800f, 400f))
        assertEquals(0.5f to 1f, AhSellGrid.bar(400f, 800f, 400f))
        val cells = AhSellGrid.layout(bags, listOf(group(0, 5), group(1, 1)), 4, 330f - AhSellGrid.BAR_W)
        assertTrue(cells.all { it.box.right <= 330f - AhSellGrid.BAR_W })
    }

    @Test fun theCursorBringsItsBagsGreyedTilesIntoView() {
        val no = (6..13).map { item(0, it, 30 + it).put("no", "bound") } + listOf(item(2, 1, 40).put("no", "no"))
        val groups = listOf(group(0, 5).put("x", 8), group(1, 1), group(2, 0).put("x", 1))
        val cells = AhSellGrid.layout(bags, groups, 4, 330f, no)
        val first = cells.indexOfFirst { it.cmd == "ahsitem 0 1 10" }
        val last = cells.indexOfFirst { it.cmd == "ahsitem 0 5 14" }
        val lastNo = cells.last { it.cmd.startsWith("ahsno 0 ") }
        assertEquals(cells[first].box, AhSellGrid.followBox(cells, first, 2000f))
        val fb = AhSellGrid.followBox(cells, last, 2000f)
        assertEquals(lastNo.box.bottom, fb.bottom, 0.01f)
        assertEquals(cells[last].box.top, fb.top, 0.01f)
        val view = lastNo.box.bottom - cells[last].box.top + 16f
        assertEquals("old follow did not scroll", 0f, AhSellGrid.follow(cells[last].box, 0f, view), 0.01f)
        val s = AhSellGrid.follow(AhSellGrid.followBox(cells, last, view), 0f, view)
        assertTrue(lastNo.box.bottom <= s + view)
        assertTrue(cells[last].box.top >= s)
        val tight = AhSellGrid.followBox(cells, last, 150f)
        assertTrue(tight.bottom <= cells[last].box.top + 150f && AhSellGrid.follow(tight, 0f, 150f) <= cells[last].box.top)
        val h2 = cells.indexOfFirst { it.cmd == "ahsfold 2" }
        assertEquals(cells.last().box.bottom, AhSellGrid.followBox(cells, h2, 2000f).bottom, 0.01f)
        assertEquals(cells[0].box, AhSellGrid.followBox(cells, 0, 2000f))
    }

    @Test fun startIsPostOnlyWithAnItemInTheSellForm() {
        assertTrue(AhSellGrid.ownsStart(padHeld = true, tab = "sell", hasItem = true))
        assertFalse("no item: Start is the pause wheel as everywhere", AhSellGrid.ownsStart(true, "sell", false))
        assertFalse("the Buy tab", AhSellGrid.ownsStart(true, "browse", true))
        assertFalse("the pad not held (the game's)", AhSellGrid.ownsStart(false, "sell", true))
    }

    @Test fun theKeyboardsBDoesNotReopenItsField() {
        assertTrue("a document still naming the field B closed a moment ago", InteractFieldKbd.justClosed("ahspg", "ahspg", 200L))
        assertFalse("another field opens", InteractFieldKbd.justClosed("ahsps", "ahspg", 200L))
        assertFalse("after the guard", InteractFieldKbd.justClosed("ahspg", "ahspg", InteractFieldKbd.REOPEN_GUARD_MS))
        assertFalse("nothing closed", InteractFieldKbd.justClosed("ahspg", null, 0L))
    }

    @Test fun theRedSearchCommitsTheAuctionHousesBoxes() {
        assertTrue(InteractFieldKbd.searchCommits("ahtext"))
        assertTrue(InteractFieldKbd.searchCommits("ahmin"))
        assertFalse("a price box is not the search's", InteractFieldKbd.searchCommits("ahspg"))
        assertFalse(InteractFieldKbd.searchCommits(null))
    }
}

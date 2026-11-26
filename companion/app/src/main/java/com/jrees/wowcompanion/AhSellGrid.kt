package com.jrees.wowcompanion

import org.json.JSONObject
import kotlin.math.max

/**
 * i167 (the user 2026-11-19: "Instead of an item list let's show a grid with collapsible bags"): the auction house's Sell grid, laid out
 * exactly as the add-on's cursor walks it (Interact.lua AH.gridRows): per bag a header (its fold), then - unless folded - its sellable
 * items [cols] a row. Boxes are in the view's own coordinates (y from the top of the content, before the scroll). Pure (AhSellGridTest).
 */
object AhSellGrid {
    const val COLS = 4
    const val HEAD_H = 46f
    const val GAP = 6f
    const val EDGE = 8f

    /** a box in the view's content coordinates (no android.graphics here: the JVM tests run it) */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Refused tiles have a reason, but never a sell command or a controller stop. */
    class Cell(val cmd: String, val box: Box, val o: JSONObject, val head: Boolean, val no: String? = null)

    const val BAR_W = 16f

    fun noWord(code: String): String = when (code) {
        "bound" -> "Bound"; "quest" -> "Quest"; "damaged" -> "Damaged"; else -> "Can't sell"
    }

    fun noWhy(code: String): String = when (code) {
        "bound" -> "Soulbound: the auction house won't take it"
        "quest" -> "A quest item: the auction house won't take it"
        "damaged" -> "Damaged: repair it to sell it"
        else -> "The auction house won't take this"
    }

    fun headCount(g: JSONObject): String = g.optInt("n", 0).let { n -> g.optInt("x", 0).let { x -> if (x > 0) "$n · $x can't sell" else "$n" } }

    /** The scroll bar's visible fraction; null when the whole grid fits. */
    fun bar(scroll: Float, content: Float, viewH: Float): Pair<Float, Float>? =
        if (content <= viewH + 2f || content <= 0f) null else (scroll / content).coerceIn(0f, 1f) to ((scroll + viewH) / content).coerceIn(0f, 1f)

    fun cmdOf(b: JSONObject) = "ahsitem ${b.optInt("bag")} ${b.optInt("s")} ${b.optInt("id")}"

    fun tile(cols: Int, width: Float): Float = (width - 2 * EDGE - (cols - 1) * GAP) / cols

    fun layout(bags: List<JSONObject>, groups: List<JSONObject>, cols0: Int, width: Float, no: List<JSONObject> = emptyList()): List<Cell> {
        val cols = cols0.coerceIn(1, 8)
        val t = tile(cols, width)
        val out = ArrayList<Cell>()
        var y = 4f
        for (g in groups) {
            val bag = g.optInt("bag")
            out.add(Cell("ahsfold $bag", Box(EDGE, y, width - EDGE, y + HEAD_H), g, true))
            y += HEAD_H + 4f
            if (g.optBoolean("shut")) continue
            val its = bags.filter { it.optInt("bag") == bag }
            val refused = no.filter { it.optInt("bag") == bag }
            for ((k, b) in (its + refused).withIndex()) {
                val x = EDGE + (k % cols) * (t + GAP)
                val ty = y + (k / cols) * (t + GAP)
                val why = if (k >= its.size) b.optString("no", "no").ifEmpty { "no" } else null
                out.add(Cell(if (why != null) "ahsno ${cmdOf(b).removePrefix("ahsitem ")}" else cmdOf(b), Box(x, ty, x + t, ty + t), b, false, why))
            }
            y += ((its.size + refused.size + cols - 1) / cols) * (t + GAP) + 6f
        }
        return out
    }

    /** i168: Start is the Sell form's Post (never the pause wheel) while the add-on holds the pad, the Sell tab is open and an item is in it */
    fun ownsStart(padHeld: Boolean, tab: String, hasItem: Boolean): Boolean = padHeld && tab == "sell" && hasItem

    fun height(cells: List<Cell>): Float = (cells.maxOfOrNull { it.box.bottom } ?: 0f) + 8f

    /** Follow refused tiles after the bag's last sellable row, without losing the cursor's own top. */
    fun followBox(cells: List<Cell>, ci: Int, viewH: Float): Box {
        val c = cells[ci]
        var bottom = c.box.bottom
        for (k in ci + 1 until cells.size) {
            val n = cells[k]
            if (n.head) break
            if (n.no == null) { if (n.box.top > c.box.top) return c.box else continue }
            bottom = max(bottom, n.box.bottom)
        }
        return Box(c.box.left, c.box.top, c.box.right, bottom.coerceAtMost(c.box.top + viewH - 8f).coerceAtLeast(c.box.bottom))
    }

    /** the scroll that keeps [box] in a view [viewH] tall, moved as little as possible from [scroll] (never back to the top) */
    fun follow(box: Box, scroll: Float, viewH: Float): Float = when {
        box.top - 4f < scroll -> max(0f, box.top - 4f)
        box.bottom + 4f > scroll + viewH -> box.bottom + 4f - viewH
        else -> scroll
    }
}

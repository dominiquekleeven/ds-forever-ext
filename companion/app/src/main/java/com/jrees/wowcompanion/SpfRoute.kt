package com.jrees.wowcompanion

import org.json.JSONObject
import kotlin.math.abs

/** Destination only. The legacy class name is retained for the two map callers; sr1 routes are rejected. */
class SpfRoute {
    class Goal(val continent: Int, val x: Double, val y: Double)
    private var goal: Goal? = null
    private var at = Long.MIN_VALUE / 2

    fun clear() { goal = null; at = Long.MIN_VALUE / 2 }

    fun onDoc(d: JSONObject, now: Long) {
        clear()
        if (d.optString("v") != "dst1" || d.opt("on") != true) return
        val c = (d.opt("c") as? Number)?.toDouble() ?: return
        val x = (d.opt("x") as? Number)?.toDouble() ?: return
        val y = (d.opt("y") as? Number)?.toDouble() ?: return
        if (!c.isFinite() || c % 1 != 0.0 || c !in 0.0..100000.0 ||
            !x.isFinite() || !y.isFinite() || abs(x) > WORLD_LIMIT || abs(y) > WORLD_LIMIT) return
        goal = Goal(c.toInt(), x, y); at = now
    }

    fun current(now: Long, playerContinent: Int): Goal? = goal?.takeIf {
        now >= at && now - at <= STALE_MS && playerContinent == it.continent
    }

    fun live(now: Long, wx: Double, wy: Double, playerContinent: Int): Goal? =
        if (wx.isFinite() && wy.isFinite()) current(now, playerContinent) else null

    companion object {
        const val STALE_MS = 8000L
        const val WORLD_LIMIT = 100000.0
    }
}

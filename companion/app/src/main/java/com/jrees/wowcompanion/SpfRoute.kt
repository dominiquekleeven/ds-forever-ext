package com.jrees.wowcompanion

import org.json.JSONObject
import kotlin.math.abs

/** Read-only ShortestPathForever guidance. No navigation commands or Polestar state. */
class SpfRoute {
    class Walk(val continent: Int, val points: DoubleArray, val next: DoubleArray, val leg: Int)
    private var walk: Walk? = null
    private var at = Long.MIN_VALUE / 2
    private var progress = 0.0

    fun clear() { walk = null; progress = 0.0; at = Long.MIN_VALUE / 2 }

    /** sr1: one current walk, never connected to a later transport or continent. Fail closed. */
    fun onDoc(d: JSONObject, now: Long) {
        val old = walk
        fun invalid() { clear() }
        if (d.optString("v") != "sr1" || d.opt("on") != true) return invalid()
        fun integer(k: String, min: Int, max: Int): Int? {
            val n = d.opt(k) as? Number ?: return null
            val v = n.toDouble()
            return v.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= min && it <= max }?.toInt()
        }
        val cont = integer("c", 0, 100000) ?: return invalid()
        val leg = integer("leg", 1, 4096) ?: return invalid()
        val p = d.optJSONArray("p") ?: return invalid()
        val n = d.optJSONArray("n") ?: return invalid()
        if (p.length() !in 4..MAX_POINTS * 2 || p.length() % 2 != 0 || n.length() != 2) return invalid()
        fun values(a: org.json.JSONArray): DoubleArray? {
            val out = DoubleArray(a.length())
            for (i in out.indices) {
                val v = (a.opt(i) as? Number)?.toDouble() ?: return null
                if (!v.isFinite() || abs(v) > WORLD_LIMIT) return null
                out[i] = v
            }
            return out
        }
        val points = values(p) ?: return invalid()
        val next = values(n) ?: return invalid()
        // The public Guidance index makes the second exported point the current bend.
        if (next[0] != points[2] || next[1] != points[3]) return invalid()
        if (old == null || old.continent != cont || old.leg != leg || !old.points.contentEquals(points)) progress = 0.0
        walk = Walk(cont, points, next, leg); at = now
    }

    fun current(now: Long, playerContinent: Int): Walk? = walk?.takeIf {
        now >= at && now - at <= STALE_MS && playerContinent == it.continent
    }

    /** Only trim the API's current segment. Do not jump across a nearby loop or backtrack on a heartbeat. */
    fun live(now: Long, wx: Double, wy: Double, playerContinent: Int): Walk? {
        val w = current(now, playerContinent) ?: return null
        if (!wx.isFinite() || !wy.isFinite()) return null
        val p = w.points
        val dx = p[2] - p[0]; val dy = p[3] - p[1]; val l2 = dx * dx + dy * dy
        val t = if (l2 > 0) (((wx - p[0]) * dx + (wy - p[1]) * dy) / l2).coerceIn(0.0, 1.0) else 1.0
        progress = maxOf(progress, t)
        val out = p.copyOf()
        out[0] = p[0] + progress * dx; out[1] = p[1] + progress * dy
        return Walk(w.continent, out, w.next, w.leg)
    }

    companion object {
        const val MAX_POINTS = 240
        const val STALE_MS = 8000L
        const val WORLD_LIMIT = 100000.0
    }
}

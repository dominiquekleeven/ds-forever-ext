package com.jrees.wowcompanion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpfRouteTest {
    private fun doc(p: List<Number> = listOf(0, 0, 100, 0, 100, 100)) = JSONObject()
        .put("type", "spfroute").put("v", "sr1").put("on", true).put("c", 0).put("leg", 2)
        .put("p", JSONArray(p)).put("n", JSONArray(p.subList(2, 4)))

    @Test fun validatesAndFailsClosedWithoutLeavingAnOldLine() {
        val r = SpfRoute()
        val bad = listOf(doc().put("c", -1), doc().put("c", 0.5), doc().put("c", "0"), doc().put("leg", 0),
            doc().put("v", "future"), doc().put("on", false), doc().put("p", JSONArray(listOf(0, 0, 1))),
            doc().put("p", JSONArray(List(482) { 0 })), doc().put("p", JSONArray(listOf(0, "NaN", 100, 0))),
            doc().put("p", JSONArray(listOf(0, 0, 100001, 0))), doc().put("n", JSONArray(listOf(101, 0))))
        for (d in bad) {
            r.onDoc(doc(), 100)
            assertNotNull(r.current(100, 0))
            r.onDoc(d, 101)
            assertNull(d.toString(), r.current(101, 0))
        }
    }

    @Test fun freshnessContinentAndSessionClear() {
        val r = SpfRoute(); r.onDoc(doc(), 100)
        assertNotNull(r.current(100 + SpfRoute.STALE_MS, 0))
        assertNull(r.current(101 + SpfRoute.STALE_MS, 0))
        assertNull(r.current(99, 0)); assertNull(r.current(100, 1))
        assertNull(r.live(100, Double.NaN, 0.0, 0))
        assertNull(r.live(100, 0.0, Double.POSITIVE_INFINITY, 0))
        r.clear(); assertNull(r.current(100, 0))
    }

    @Test fun progressDoesNotGoBackOnResendOrJumpAcrossLoopsAndReplansReset() {
        val r = SpfRoute(); val d = doc(listOf(0, 0, 100, 0, 0, 1, 100, 1))
        r.onDoc(d, 100)
        assertEquals(60.0, r.live(100, 60.0, 0.9, 0)!!.points[0], 0.001)
        r.onDoc(d, 200) // identical heartbeat, not a new plan
        assertEquals(60.0, r.live(200, 20.0, 0.0, 0)!!.points[0], 0.001)
        // Same endpoints, different middle bend: a genuinely new plan.
        r.onDoc(doc(listOf(0, 0, 80, 0, 0, 1, 100, 1)), 300)
        assertEquals(20.0, r.live(300, 20.0, 0.0, 0)!!.points[0], 0.001)
        r.onDoc(doc(listOf(100, 0, 100, 100, 200, 100)).put("leg", 3), 400)
        assertArrayEquals(doubleArrayOf(100.0, 50.0, 100.0, 100.0, 200.0, 100.0),
            r.live(400, 100.0, 50.0, 0)!!.points, 0.001)
        // Zones on the same continent share world coordinates. A new continent must not borrow the old route.
        assertNull(r.live(401, 100.0, 50.0, 1))
    }
}

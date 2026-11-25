package com.jrees.wowcompanion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpfRouteTest {
    private fun doc() = JSONObject().put("type", "destination").put("v", "dst1").put("on", true)
        .put("c", 0).put("x", 100).put("y", 200)

    @Test fun validatesAndRejectsOldWalkingGeometryWithoutLeavingStaleMarker() {
        val r = SpfRoute()
        val bad = listOf(doc().put("c", -1), doc().put("c", 0.5), doc().put("c", "0"),
            doc().put("v", "sr1"), doc().put("on", false), doc().put("x", "NaN"),
            doc().put("x", 100001), doc().apply { remove("y") })
        for (d in bad) {
            r.onDoc(doc(), 100); assertNotNull(r.current(100, 0))
            r.onDoc(d, 101); assertNull(d.toString(), r.current(101, 0))
        }
    }

    @Test fun freshnessContinentAndSessionClear() {
        val r = SpfRoute(); r.onDoc(doc(), 100)
        assertNotNull(r.current(100 + SpfRoute.STALE_MS, 0))
        assertNull(r.current(101 + SpfRoute.STALE_MS, 0))
        assertNull(r.current(99, 0)); assertNull(r.current(100, 1))
        assertNull(r.live(100, Double.NaN, 0.0, 0))
        r.clear(); assertNull(r.current(100, 0))
    }

    @Test fun oneGoalNeverMovesWithPlayerOrCreatesConnector() {
        val r = SpfRoute(); r.onDoc(doc(), 100)
        assertEquals(100.0, r.live(100, 60.0, 0.9, 0)!!.x, 0.001)
        assertEquals(200.0, r.live(100, -500.0, 90.0, 0)!!.y, 0.001)
        r.onDoc(doc().put("x", 300), 200)
        assertEquals(300.0, r.current(200, 0)!!.x, 0.001)
    }
}

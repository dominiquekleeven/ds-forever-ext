package com.jrees.wowcompanion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Upstream 0d05f993: read game state and send only the explicit opposite-state cell. */
class ProfessionsGatherTest {
    private val M = ProfessionsModel
    private fun overview(trk: Int? = 0, on: Int? = 2, off: Int? = 3, row: Boolean = true): ProfessionsModel.Overview {
        val b = JSONObject().put("n", "Find Herbs")
        trk?.let { b.put("trk", it) }; on?.let { b.put("ton", it) }; off?.let { b.put("toff", it) }
        val p = JSONObject().put("id", 182).put("pos", 1).put("name", "Herbalism").put("btn", JSONArray().put(b))
        val rows = JSONObject(); if (row) rows.put(M.ROW_OPEN, 3)
        return M.parseOverview(JSONObject().put("list", JSONArray().put(p)).put("pad", JSONObject().put("rows", rows)))
    }

    @Test fun explicitStatesNotToggle() {
        val ov = overview(); val b = ov.list[0].btn[0]
        assertTrue(b.tracking); assertFalse(b.on); assertTrue(M.gatherLive(ov, b))
        val tap = M.gatherTap(ov, b, true)!!
        assertEquals(M.ROW_OPEN, tap.row); assertEquals(2, tap.col)
        assertNull(M.gatherTap(ov, b, false))
        val next = overview(trk = 1); val active = next.list[0].btn[0]
        assertTrue(active.on); assertEquals(3, M.gatherTap(next, active, false)!!.col)
        assertNull(M.gatherTap(next, active, true))
    }

    @Test fun oldAddonUnlearnedAndUncachedStayInert() {
        for (ov in listOf(overview(trk = null, on = null, off = null), overview(on = null, off = null), overview(row = false))) {
            val b = ov.list[0].btn[0]
            assertFalse(M.gatherLive(ov, b)); assertNull(M.gatherTap(ov, b, true))
        }
        assertNull(M.gatherTap(null, null, true))
        assertFalse(ProfessionsModel.Btn("Fishing", 1, false, false).tracking)
        assertFalse(ProfessionsModel.Btn("Smelting", 1, true, true).tracking)
    }

    @Test fun nativeMinimapOnly() {
        assertEquals("Dots show on the top screen's minimap, not on our map.", M.GATHER_NOTE)
        assertEquals("Settings > Top screen > Minimap: On or Tracking", M.GATHER_WHERE)
    }
}

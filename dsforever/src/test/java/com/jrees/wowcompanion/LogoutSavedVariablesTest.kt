package com.jrees.wowcompanion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LogoutSavedVariablesTest {
    private val variable = "WowCompanionDB"

    @Test fun delayedLogoutDocumentKeepsCaptureTimeNotLaterReceiptTime() {
        val msg = JSONObject().put("var", variable).put("lua", "{[\"switch\"]=false}")
            .put("n", 1).put("savedAt", 1_000L)
        val backup = SvKeep.parse(msg, 3_000L)!!
        assertEquals(1_000L, backup.savedAt)
        val result = SvKeep.fold(mapOf("switch" to true), 2_000L,
            mapOf("switch" to false), backup.savedAt, mapOf("switch" to "initial"), emptySet())
        assertEquals(true, result.table["switch"])
    }

    @Test fun newerNativeLogoutDeletionIsNotResurrectedByStaleBackup() {
        val base: Map<Any, Any> = mapOf("switch" to true, "other" to 1L)
        val result = SvKeep.fold(mapOf("other" to 1L), 2_000L,
            mapOf("switch" to false, "other" to 1L), 1_000L, base, emptySet())
        assertFalse(result.table.containsKey("switch"))
        assertEquals(1L, result.table["other"])
        // A real newer kept addition remains available for the existing sync path.
        val fresh = SvKeep.fold(emptyMap(), 1_000L, mapOf("new" to true), 2_000L, emptyMap(), emptySet())
        assertEquals(true, fresh.table["new"])
    }

    @Test fun legacyDocumentsAndCurrentValidationRemainCompatible() {
        val msg = JSONObject().put("var", variable).put("lua", "{}").put("n", 0)
        assertEquals(3_000L, SvKeep.parse(msg, 3_000L)!!.savedAt)
        assertNull(SvKeep.parse(JSONObject(msg.toString()).put("var", "OtherAddon"), 3_000L))
        assertNull(SvKeep.parse(JSONObject(msg.toString()).put("lua", "not a table"), 3_000L))
        assertNull(SvKeep.parse(JSONObject(msg.toString()).put("savedAt", -1L), 3_000L))
        assertEquals(3_000L, SvKeep.parse(msg.put("savedAt", 9_000L), 3_000L)!!.savedAt)
    }
}

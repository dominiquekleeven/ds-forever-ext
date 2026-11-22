package com.jrees.wowcompanion

import org.junit.Assert.*
import org.junit.Test

class ExtUpdateTest {
    @Test fun forkVersionsKeepTheExistingAssetPrefixAndSortByExtRevision() {
        assertTrue(AppUpdate.ASSET.matches("DS-Forever-1.0.37-ext.1.apk"))
        assertTrue(AppUpdate.compare("1.0.37-ext.2", "1.0.37-ext.1") > 0)
        assertTrue(AppUpdate.compare("1.0.38-ext.1", "1.0.37-ext.9") > 0)
        assertEquals(0, AppUpdate.compare("1.0.37-ext.1", "1.0.37-ext.1"))
        assertEquals("dominiquekleeven/ds-forever-ext", ProblemReport.REPO)
        val json = """{"tag_name":"v1.0.37-ext.2","assets":[{"name":"DS-Forever-1.0.37-ext.2.apk","size":123,"browser_download_url":"https://example.com/test.apk"}]}"""
        assertTrue(AppUpdate.parse(json, "1.0.37-ext.1") is AppUpdate.Check.Available)
        assertTrue(AppUpdate.parse(json, "1.0.37-ext.2") is AppUpdate.Check.UpToDate)
        assertTrue(AppUpdate.parseExtReleases("[$json]", "1.0.37-ext.1") is AppUpdate.Check.Available)
        assertTrue(AppUpdate.parseExtReleases("[$json]", "1.0.37-ext.2") is AppUpdate.Check.UpToDate)
        assertTrue(AppUpdate.parseExtReleases("[]", "1.0.37-ext.1") is AppUpdate.Check.Skipped)
        assertTrue(AppUpdate.parseExtReleases("[{\"tag_name\":\"v1.0.37-ext.2\",\"draft\":true}]", "1.0.37-ext.1") is AppUpdate.Check.Skipped)
    }
}

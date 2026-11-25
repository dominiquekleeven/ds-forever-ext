package com.jrees.wowcompanion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Official archives are local test inputs, never repository/APK assets. */
class NavigationStartupTest {
    private lateinit var tmp: File
    private val all = ThirdPartyAddons.ALL
    private val running = DependencyAddons.gameRunning
    private val a = ThirdPartyAddons.TOMTOM
    private val choices = hashMapOf<String, Boolean>()
    private val game get() = File(tmp, "game")
    private val addons get() = File(game, "Interface/AddOns")
    private val addon get() = File(addons, "TomTom")
    private val privateDir get() = File(tmp, "private")
    private val archive get() = File(System.getenv("TOMTOM_ZIP") ?: "/tmp/opencode/TomTom-v4.4.1-release.zip")

    private fun start() = ThirdPartyAddons.start(object : ThirdPartyAddons.Store {
        override fun get(k: String, d: Boolean) = choices[k] ?: d
        override fun put(k: String, v: Boolean) { choices[k] = v }
    }, privateDir)

    @Before fun up() {
        tmp = Files.createTempDirectory("navigation-startup").toFile()
        ThirdPartyAddons.ALL = listOf(a)
        ThirdPartyAddons.noFetch = true
        DependencyAddons.gameRunning = { false }
        start()
    }

    @After fun down() {
        ThirdPartyAddons.ALL = all
        ThirdPartyAddons.noFetch = false
        DependencyAddons.gameRunning = running
        tmp.deleteRecursively()
    }

    private fun record() {
        privateDir.mkdirs()
        File(privateDir, "state.json").writeText(JSONObject().put("tomtom/core", JSONObject()
            .put("in", a.core.tag).put("folders", JSONArray(a.core.folders))).toString())
        start()
    }

    private fun syntheticInstall() {
        File(addon, "TomTom.toc").apply {
            parentFile.mkdirs()
            writeText("## Interface: 120100, 16001\n## Version: v4.4.1-release\ncore/waypoints.lua\n")
        }
        File(addon, "core/waypoints.lua").apply { parentFile.mkdirs(); writeText("-- test core") }
        record()
    }

    private fun cacheArchive() {
        File(privateDir, "dl/${a.core.asset}").apply { parentFile.mkdirs(); archive.copyTo(this, overwrite = true) }
        File(privateDir, "state.json").writeText(JSONObject().put("tomtom/core", JSONObject().put("zip", a.core.tag)).toString())
        start()
    }

    @Test fun installsByDefaultOnceAndOptOutPersists() {
        assertTrue(ThirdPartyAddons.coreWanted(a))
        ThirdPartyAddons.setCore(a, false)
        start()
        assertFalse(ThirdPartyAddons.coreWanted(a))
        ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertFalse(addon.exists())
        assertFalse(ThirdPartyAddons.coreWanted(a))
    }

    @Test fun eachStartupEnablesOnlyWantedNavigationAndUninstallPreservesSharedFiles() {
        syntheticInstall()
        val txt = File(game, "WTF/Account/FAKE/AddOns.txt").apply { parentFile.mkdirs() }
        val before = "TomTom: disabled\r\nQuestie: disabled\r\nQuestieDB: disabled\r\nOther: disabled\r\n"
        txt.writeText(before)
        val keep = listOf("Questie/user.txt", "QuestieDB/user.txt", "Other/user.txt").map { n ->
            File(addons, n).apply { parentFile.mkdirs(); writeText(n) }
        } + File(game, "WTF/Account/FAKE/SavedVariables/ShortestPathForever.lua").apply {
            parentFile.mkdirs(); writeText("keep player settings")
        }
        val line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("TomTom dependencies installed and enabled"))
        assertEquals(before.replace("TomTom: disabled", "TomTom: enabled"), txt.readText())
        assertEquals(before, txt.parentFile.listFiles()!!.single { it.name.startsWith("AddOns.txt.dsf-before-") }.readText())
        assertEquals(ThirdPartyAddons.Kind.INSTALLED, ThirdPartyAddons.status(a, "core").kind)
        val expected = keep.associateWith { it.readText() }
        ThirdPartyAddons.setCore(a, false)
        start()
        ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertFalse(addon.exists())
        assertTrue(File(game, "Interface/${DependencyAddons.BACKUP}").listFiles()!!.any { it.name.startsWith("TomTom-") })
        for ((file, text) in expected) assertEquals(file.path, text, file.readText())
        assertFalse(ThirdPartyAddons.coreWanted(a))
    }

    @Test fun missingForeverTocFolderOrLoadFileCannotReportReady() {
        syntheticInstall()
        File(addon, "core/waypoints.lua").delete()
        assertNotNull(ThirdPartyAddons.tomTomProblem(addon))
        var line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("TomTom not ready"))
        assertFalse(ThirdPartyAddons.coreInstalled(a))
        syntheticInstall()
        File(addon, "TomTom.toc").renameTo(File(addon, "TomTom_Vanilla.toc"))
        ThirdPartyAddons.repairMissing(game)
        assertFalse(ThirdPartyAddons.coreInstalled(a))
        assertTrue(File(addon, "TomTom_Vanilla.toc").isFile)
        syntheticInstall()
        addon.deleteRecursively()
        line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("not ready"))
        assertNotEquals(ThirdPartyAddons.Kind.INSTALLED, ThirdPartyAddons.status(a, "core").kind)
    }

    @Test fun actualPinnedArchiveInstallsAndRepairsWithoutTouchingUnknownFiles() {
        assumeTrue("provide TOMTOM_ZIP", archive.isFile)
        assertEquals(a.core.bytes, archive.length())
        assertEquals(a.core.sha256, DependencyAddons.sha256(archive))
        ZipFile(archive).use { z ->
            assertEquals(setOf("TomTom"), z.entries().asSequence().map { it.name.substringBefore('/') }.toSet())
        }
        val keep = File(addons, "Other/user.txt").apply { parentFile.mkdirs(); writeText("unknown addon") }
        cacheArchive()
        var line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("TomTom v4.4.1-release installed"))
        assertTrue(line, line.contains("dependencies installed and enabled"))
        assertNull(ThirdPartyAddons.tomTomProblem(addon))
        assertEquals(a.core.folders, ThirdPartyAddons.folders(a, "core"))
        assertTrue(File(addon, "Images/Modern/LICENSE.txt").isFile)
        assertTrue(File(addon, "README.md").isFile)
        File(addon, "TomTom.toc").delete()
        File(addon, "user-marker.txt").writeText("preserve old copy")
        ThirdPartyAddons.repairMissing(game)
        assertFalse(ThirdPartyAddons.coreInstalled(a))
        cacheArchive()
        line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("dependencies installed and enabled"))
        assertNull(ThirdPartyAddons.tomTomProblem(addon))
        val backup = File(game, "Interface/${DependencyAddons.BACKUP}").listFiles()!!.single { it.name.startsWith("TomTom-") }
        assertEquals("preserve old copy", File(backup, "user-marker.txt").readText())
        assertEquals("unknown addon", keep.readText())
    }

    @Test fun upgradeRetiresOnlyRecordedSpfWithProtectedMoveAndKeepsSavedVariables() {
        val old = File(addons, "ShortestPathForever/player.txt").apply { parentFile.mkdirs(); writeText("old addon") }
        val saved = File(game, "WTF/Account/FAKE/SavedVariables/ShortestPathForever.lua").apply {
            parentFile.mkdirs(); writeText("settings")
        }
        val other = File(addons, "Other/user.txt").apply { parentFile.mkdirs(); writeText("other") }
        // Unrecorded user installations are not removed.
        assertFalse(ThirdPartyAddons.retireShortestPath(game))
        privateDir.mkdirs()
        File(privateDir, "state.json").writeText(JSONObject().put("shortestpath/core", JSONObject()
            .put("in", "v1.45.3").put("folders", JSONArray(listOf("ShortestPathForever")))).toString())
        start()
        DependencyAddons.gameRunning = { true }
        assertTrue(runCatching { ThirdPartyAddons.retireShortestPath(game) }.isFailure)
        assertTrue(old.isFile)
        DependencyAddons.gameRunning = { false }
        assertTrue(ThirdPartyAddons.retireShortestPath(game))
        assertFalse(old.exists())
        assertEquals("old addon", File(game, "Interface/AddOns-disabled-dsforever/ShortestPathForever/player.txt").readText())
        assertEquals("settings", saved.readText()); assertEquals("other", other.readText())
        assertFalse(ThirdPartyAddons.retireShortestPath(game))
        assertFalse(choices["shortestpath.core"] ?: true)
    }
}

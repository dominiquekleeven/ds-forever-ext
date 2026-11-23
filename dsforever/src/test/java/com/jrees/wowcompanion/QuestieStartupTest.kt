package com.jrees.wowcompanion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.zip.ZipFile

/** No device, account files, or redistributed upstream assets. Official archives are optional local test inputs. */
class QuestieStartupTest {
    private lateinit var tmp: File
    private lateinit var server: ServerSocket
    private val realAll = ThirdPartyAddons.ALL
    private val realApi = DependencyAddons.api
    private val realRunning = DependencyAddons.gameRunning
    private val choices = hashMapOf("questie.replacement1122" to true, "questie.core" to true)
    private val game get() = File(tmp, "game")
    private val addons get() = File(game, "Interface/AddOns")
    private val privateDir get() = File(tmp, "private")
    private val cache get() = File(tmp, "cache")
    private val state get() = File(tmp, "deps.json")
    private val bundle get() = File(System.getenv("QUESTIE_BUNDLE_ZIP") ?: "/tmp/opencode/questie-12.12.0.zip")
    private val dbZip get() = File(System.getenv("QUESTIEDB_FOREVER_ZIP") ?: "/tmp/opencode/questiedb-1.1.4-forever.zip")

    private fun start() = ThirdPartyAddons.start(object : ThirdPartyAddons.Store {
        override fun get(k: String, d: Boolean) = choices[k] ?: d
        override fun put(k: String, v: Boolean) { choices[k] = v }
    }, privateDir)

    @Before fun up() {
        tmp = Files.createTempDirectory("questie-startup").toFile()
        DependencyAddons.gameRunning = { false }
        ThirdPartyAddons.noFetch = true
        ThirdPartyAddons.ALL = listOf(ThirdPartyAddons.QUESTIE)
        server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        // Discovery and downloads fail immediately; fallback must use only the verified local private cache.
        Thread {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                s.use {
                    val r = it.getInputStream().bufferedReader()
                    while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }
                    it.getOutputStream().write("HTTP/1.0 503 Offline\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
            }
        }.apply { isDaemon = true }.start()
        DependencyAddons.api = "http://127.0.0.1:${server.localPort}"
        start()
    }

    @After fun down() {
        server.close()
        tmp.deleteRecursively()
        ThirdPartyAddons.ALL = realAll
        ThirdPartyAddons.noFetch = false
        DependencyAddons.api = realApi
        DependencyAddons.gameRunning = realRunning
    }

    private fun recordedQuestie() {
        privateDir.mkdirs()
        File(addons, "Questie/Questie_Forever.toc").apply {
            parentFile.mkdirs()
            writeText("## Interface: 16001\n## Version: 12.12.0\n## X-QuestieDB-Contract: 4\n")
        }
        File(privateDir, "state.json").writeText(JSONObject().put("questie/core", JSONObject()
            .put("in", ThirdPartyAddons.QUESTIE.core.tag).put("folders", JSONArray(listOf("Questie")))).toString())
        start()
    }

    private fun compatibleDb() {
        File(addons, "QuestieDB/QuestieDB_Forever.toc").apply {
            parentFile.mkdirs(); writeText("## Interface: 16001\n## Version: 1.1.4\n")
        }
        File(addons, "QuestieDB/src/config.lua").apply {
            parentFile.mkdirs(); writeText("config.contractVersion = 4\nconfig.minSupportedContract = 1\n")
        }
    }

    private fun keptFiles(): Pair<File, File> {
        val other = File(addons, "Other/user.txt").apply { parentFile.mkdirs(); writeText("unchanged addon") }
        val sv = File(game, "WTF/Account/FAKE/SavedVariables/Questie.lua").apply {
            parentFile.mkdirs(); writeText("unchanged progress")
        }
        return other to sv
    }

    @Test fun recordedVersionStillEnablesBothAndBacksUpExactBytes() {
        recordedQuestie(); compatibleDb()
        val (other, sv) = keptFiles()
        val txt = File(game, "WTF/Account/FAKE/AddOns.txt")
        val before = "Questie: disabled\r\nQuestieDB: disabled\r\nOther: disabled\r\n"
        txt.writeText(before)
        val line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("dependencies installed and enabled"))
        assertEquals("Questie: enabled\r\nQuestieDB: enabled\r\nOther: disabled\r\n", txt.readText())
        assertEquals(before, txt.parentFile.listFiles()!!.single { it.name.startsWith("AddOns.txt.dsf-before-questie-enable-") }.readText())
        assertEquals("unchanged addon", other.readText()); assertEquals("unchanged progress", sv.readText())
        // Questie off must not remove the shared companion database.
        choices["questie.core"] = false
        ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertNull(DependencyAddons.questieDbProblem(File(addons, "QuestieDB")))
    }

    @Test fun missingDbOfflineCannotReportReadyAndFailedChecksAreNotCached() {
        recordedQuestie()
        val line = DependencyAddons.run(game, cache, state)
        assertTrue(line, line.contains("Questie not ready"))
        assertEquals(ThirdPartyAddons.Kind.FAILED, ThirdPartyAddons.status(ThirdPartyAddons.QUESTIE, "core").kind)
        val firstAt = JSONObject(state.readText()).getJSONObject("QuestieDB").getLong("at")
        // Even a recent rule-3 failed result must not suppress repair.
        val next = DependencyAddons.run(game, cache, state, firstAt + 1)
        assertTrue(next, next.contains("QuestieDB skipped"))
        assertTrue(next, next.contains("Questie not ready"))
    }

    @Test fun wrongClientOrContractIsNotUsableEvenWhenVersionIsNew() {
        recordedQuestie(); compatibleDb()
        val config = File(addons, "QuestieDB/src/config.lua")
        config.writeText("config.contractVersion = 3\nconfig.minSupportedContract = 1\n")
        assertNotNull(DependencyAddons.questieDbProblem(File(addons, "QuestieDB")))
        assertTrue(DependencyAddons.run(game, cache, state).contains("Questie not ready"))
        assertTrue("failed download preserves old files", config.isFile)
        compatibleDb()
        File(addons, "QuestieDB/QuestieDB_Forever.toc").renameTo(File(addons, "QuestieDB/QuestieDB_Vanilla.toc"))
        assertNotNull(DependencyAddons.questieDbProblem(File(addons, "QuestieDB")))
    }

    @Test fun verifiedStandaloneCacheRepairsMissingDbAndMigratesOldFailureOffline() {
        assumeTrue("provide the official QuestieDB 1.1.4 Forever archive", dbZip.isFile)
        recordedQuestie()
        val (other, sv) = keptFiles()
        File(cache, "deps/QuestieDB-1.1.4.zip").apply { parentFile.mkdirs(); dbZip.copyTo(this) }
        state.writeText(JSONObject().put("QuestieDB", JSONObject().put("at", System.currentTimeMillis())
            .put("rule", 2).put("installed", "").put("line", "QuestieDB skipped (offline)")).toString())
        val line = DependencyAddons.run(game, cache, state)
        assertTrue(line, line.contains("QuestieDB 1.1.4 installed"))
        assertTrue(line, line.contains("dependencies installed and enabled"))
        assertNull(DependencyAddons.questieDbProblem(File(addons, "QuestieDB")))
        assertEquals("unchanged addon", other.readText()); assertEquals("unchanged progress", sv.readText())
        // A damaged/older contract with a recorded new version is repaired, with its entire copy backed up.
        File(addons, "QuestieDB/src/config.lua").writeText("config.contractVersion = 3\nconfig.minSupportedContract = 1\n")
        File(addons, "QuestieDB/user-marker.txt").writeText("keep in backup")
        assertTrue(DependencyAddons.run(game, cache, state).contains("dependencies installed and enabled"))
        val old = File(game, "Interface/${DependencyAddons.BACKUP}").listFiles()!!.single { it.name.startsWith("QuestieDB-") }
        assertEquals("keep in backup", File(old, "user-marker.txt").readText())
    }

    @Test fun actualPinnedBundleUnpacksAndMovesWithoutTouchingSharedDb() {
        assumeTrue("provide the official pinned Questie bundle", bundle.isFile)
        val p = ThirdPartyAddons.QUESTIE.core
        assertEquals(p.bytes, bundle.length()); assertEquals(p.sha256, DependencyAddons.sha256(bundle))
        ZipFile(bundle).use { z ->
            fun text(n: String) = z.getInputStream(z.getEntry(n)).bufferedReader().use { it.readText() }
            val toc = text("Questie/Questie_Forever.toc")
            assertTrue(toc.contains("## X-QuestieDB-Contract: 4"))
            assertTrue(toc.contains("## Interface: 16001"))
            assertTrue(text("Questie/Modules/VersionCheckDB.lua").contains("LibQuestieDB.RequireContract(required)"))
            assertTrue(text("Questie/Modules/QuestieInit.lua").contains("_StopStartup(\"startup.provider-contract\""))
            assertTrue(z.entries().asSequence().sumOf { it.size } < 256L * 1024 * 1024)
        }
        compatibleDb()
        val (other, sv) = keptFiles()
        File(addons, "QuestieDB/user-marker.txt").writeText("shared database")
        File(privateDir, "dl/${p.asset}").apply { parentFile.mkdirs(); bundle.copyTo(this) }
        File(privateDir, "state.json").writeText(JSONObject().put("questie/core", JSONObject().put("zip", p.tag)).toString())
        start()
        val line = ThirdPartyAddons.applyAtStart(game, fetchSmall = false)
        assertTrue(line, line.contains("Questie 12.12.0 installed"))
        assertTrue(line, line.contains("dependencies installed and enabled"))
        assertEquals(listOf("Questie"), ThirdPartyAddons.folders(ThirdPartyAddons.QUESTIE, "core"))
        assertEquals("shared database", File(addons, "QuestieDB/user-marker.txt").readText())
        assertFalse(File(addons, "QuestieTrace").exists())
        assertTrue(File(addons, "Questie/LICENSE.md").isFile)
        assertEquals("unchanged addon", other.readText()); assertEquals("unchanged progress", sv.readText())
        File(addons, "Questie/Questie_Forever.toc").delete()
        ThirdPartyAddons.repairMissing(game)
        assertFalse("a generic TOC cannot hide a missing Forever TOC", ThirdPartyAddons.coreInstalled(ThirdPartyAddons.QUESTIE))
        assertTrue("repair only invalidates the record, not user files", File(addons, "Questie/Questie.toc").isFile)
    }
}

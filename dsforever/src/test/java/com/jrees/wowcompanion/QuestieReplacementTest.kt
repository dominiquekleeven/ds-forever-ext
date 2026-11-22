package com.jrees.wowcompanion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QuestieReplacementTest {
    private lateinit var tmp: File
    private val choices = HashMap<String, Boolean>()
    private val all = ThirdPartyAddons.ALL
    private val running = DependencyAddons.gameRunning
    private val game get() = File(tmp, "game")
    private val addons get() = File(game, "Interface/AddOns")
    private val privateDir get() = File(tmp, "private")
    private fun start() = ThirdPartyAddons.start(object : ThirdPartyAddons.Store {
        override fun get(k: String, d: Boolean) = choices[k] ?: d
        override fun put(k: String, v: Boolean) { choices[k] = v }
    }, privateDir)

    @Before fun up() {
        tmp = Files.createTempDirectory("questie-replacement").toFile()
        ThirdPartyAddons.noFetch = true
        DependencyAddons.gameRunning = { false }
    }
    @After fun down() {
        tmp.deleteRecursively()
        ThirdPartyAddons.ALL = all
        ThirdPartyAddons.noFetch = false
        DependencyAddons.gameRunning = running
    }

    @Test fun officialPinAndMenuHaveNoGuideStepClaims() {
        val a = ThirdPartyAddons.QUESTIE
        assertEquals("Questie/Questie", a.repo)
        assertEquals("bundle/v12.12.0+v1.1.4", a.core.tag)
        assertEquals(88_661_277L, a.core.bytes)
        assertEquals("116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f", a.core.sha256)
        assertEquals(listOf("Questie"), a.core.folders)
        assertTrue(ThirdPartyAddons.ALL.any { it.id == "questie" })
        assertFalse(ThirdPartyAddons.ALL.any { it.id == "polestar" })
        assertTrue(SettingsTabs.shown(SettingsTabs.Block.ADDON_QUESTIE))
        assertFalse(SettingsTabs.shown(SettingsTabs.Block.ADDON_POLESTAR))
        assertFalse(SettingsTabs.shown(SettingsTabs.Block.POLESTAR_TOP))
        assertFalse(SettingsTabs.shown(SettingsTabs.Block.QUEST_TRACKING))
        assertEquals("Questie settings", a.settings)
        assertFalse(a.base)
    }

    @Test fun replacementIsWantedOnceButLaterUninstallIsKept() {
        start()
        assertTrue(ThirdPartyAddons.coreWanted(ThirdPartyAddons.QUESTIE))
        ThirdPartyAddons.setCore(ThirdPartyAddons.QUESTIE, false)
        start()
        assertFalse(ThirdPartyAddons.coreWanted(ThirdPartyAddons.QUESTIE))
    }

    @Test fun onlyRecordedPolestarFoldersMoveAndSavedVariablesStay() {
        for (name in listOf("Polestar", "Polestar_Nav", "Polestar_UserGuide", "Questie", "QuestieDB", "Other")) {
            File(addons, name).mkdirs()
            File(addons, "$name/keep.txt").writeText(name)
        }
        val sv = File(game, "WTF/Account/FAKE/SavedVariables/Polestar.lua").apply {
            parentFile.mkdirs(); writeText("keep progress")
        }
        privateDir.mkdirs()
        File(privateDir, "state.json").writeText(JSONObject().put("polestar/core", JSONObject()
            .put("in", "v0.2.5").put("folders", JSONArray(listOf("Polestar", "Polestar_Nav")))).toString())
        start()
        DependencyAddons.gameRunning = { true }
        assertThrows(Exception::class.java) { ThirdPartyAddons.retirePolestar(game) }
        assertTrue(File(addons, "Polestar").exists())
        DependencyAddons.gameRunning = { false }
        assertTrue(ThirdPartyAddons.retirePolestar(game))
        for (name in listOf("Polestar", "Polestar_Nav")) {
            assertFalse(File(addons, name).exists())
            assertEquals(name, File(game, "Interface/AddOns-disabled-dsforever/$name/keep.txt").readText())
        }
        for (name in listOf("Polestar_UserGuide", "Questie", "QuestieDB", "Other")) assertTrue(File(addons, "$name/keep.txt").exists())
        assertEquals("keep progress", sv.readText())
        assertFalse(ThirdPartyAddons.retirePolestar(game))
    }

    @Test fun installationEnableTargetsOnlyQuestieAndItsDatabase() {
        val before = "Questie: disabled\r\nQuestieDB: disabled\r\nPolestar: disabled\r\nOther: disabled\r\n"
        val (after, count) = LauncherSetup.switchOn(before, setOf("Questie", "QuestieDB"))
        assertEquals(2, count)
        assertEquals("Questie: enabled\r\nQuestieDB: enabled\r\nPolestar: disabled\r\nOther: disabled\r\n", after)
    }

    @Test fun copiedGameFoldersRetireNamedPolestarWithoutDeletingUserFiles() {
        start()
        for (name in listOf("Polestar", "Polestar_Nav", "Polestar_UserGuide")) {
            File(addons, "$name/keep.txt").apply { parentFile.mkdirs(); writeText(name) }
        }
        assertTrue(ThirdPartyAddons.retirePolestar(game))
        assertEquals("Polestar", File(game, "Interface/AddOns-disabled-dsforever/Polestar/keep.txt").readText())
        assertEquals("Polestar_Nav", File(game, "Interface/AddOns-disabled-dsforever/Polestar_Nav/keep.txt").readText())
        assertTrue(File(addons, "Polestar_UserGuide/keep.txt").exists())
        assertFalse(ThirdPartyAddons.retirePolestar(game))
    }
}

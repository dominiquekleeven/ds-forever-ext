package app.gamenative.ui.screen.wow

import com.jrees.wowcompanion.ArtHarvest
import com.jrees.wowcompanion.GameFiles
import com.jrees.wowcompanion.IconRequirements
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtStartupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var game: File
    private lateinit var stamp: File
    private var oldStamp: ByteArray? = null
    private var oldPath: String? = null
    private val saved = linkedMapOf<String, Any?>()
    private val build = "11111111111111111111111111111111"

    private fun field(name: String) = ArtHarvest::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun put(name: String, value: Any?) { field(name).set(ArtHarvest, value) }

    @Before fun prepare() {
        for (name in listOf("state", "worker", "rerun", "stopping", "parkedForGame")) saved[name] = field(name).get(ArtHarvest)
        check((saved["worker"] as? Thread)?.isAlive != true)
        put("state", ArtHarvest.State.Idle); put("worker", null); put("rerun", false); put("stopping", false); put("parkedForGame", false)
        val prefs = context.getSharedPreferences(GameFiles.PREFS, 0)
        oldPath = prefs.getString(GameFiles.KEY_GAME_PATH, null)
        game = Files.createTempDirectory("dsf-art-startup-").toFile()
        File(game, "_forever_").mkdirs()
        File(game, "_forever_/Wow-arm64.exe").writeText("fixture")
        File(game, ".build.info").writeText("Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|Product!STRING:0\nus|1|$build|wowf\n")
        prefs.edit().putString(GameFiles.KEY_GAME_PATH, game.path).commit()
        assertEquals(build, GameFiles.activeBuild(game)?.second)
        stamp = File(ArtHarvest.root(context), "stamp.json")
        oldStamp = stamp.takeIf { it.isFile }?.readBytes()
        val data = JSONObject()
        for (step in ArtHarvest.STEPS.filter { it.beforePlay }) {
            val entry = JSONObject().put("build", build).put("v", step.version)
            if (step.id == "icons") entry.put("inputs", IconRequirements.inputKey(context, build))
            data.put(step.id, entry)
        }
        stamp.writeText(data.toString())
    }

    @After fun restore() {
        for ((name, value) in saved) put(name, value)
        if (::stamp.isInitialized) oldStamp?.let { stamp.writeBytes(it) } ?: stamp.delete()
        context.getSharedPreferences(GameFiles.PREFS, 0).edit().putString(GameFiles.KEY_GAME_PATH, oldPath).commit()
        if (::game.isInitialized) game.deleteRecursively()
    }

    @Test fun readyRequiredArtDoesNotStartAnOptionalWorker() = runBlocking {
        assertTrue(ArtHarvest.pending(context, beforePlay = false).isNotEmpty())
        val messages = arrayListOf<String>()
        assertTrue(withTimeout(5_000) { awaitArtHarvest(context) { messages += it } })
        assertNull(field("worker").get(ArtHarvest))
        assertTrue(messages.isEmpty())
    }

    @Test fun missingRequiredStampCannotTakeTheShortcut() {
        stamp.writeText("{}")
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
    }

    @Test fun changedGameBuildCannotTakeTheShortcut() {
        File(game, ".build.info").writeText(File(game, ".build.info").readText().replace(build, "22222222222222222222222222222222"))
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
    }

    @Test fun missingGameBuildCannotTakeTheShortcut() {
        File(game, ".build.info").delete()
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
    }

    @Test fun requiredFailureCannotTakeTheShortcut() {
        put("state", ArtHarvest.State.Failed("required fixture failure"))
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
    }

    @Test fun restartAndUpdateMustStillUseTheNormalWait() {
        put("rerun", true)
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
        put("rerun", false); put("stopping", true)
        assertFalse(ArtHarvest.canSkipHarvestAtPlay(context))
    }

    @Test fun anExistingOptionalWorkerIsNotStoppedOrForgottenByTheShortcut() {
        val release = CountDownLatch(1)
        val existing = Thread { release.await() }
        existing.start()
        try {
            put("worker", existing)
            assertTrue(ArtHarvest.canSkipHarvestAtPlay(context))
            assertSame(existing, field("worker").get(ArtHarvest))
            assertFalse(ArtHarvest.letGoForPlay(waitMs = 0L))
        } finally {
            release.countDown(); existing.join(5_000)
        }
        assertFalse(existing.isAlive)
        assertTrue(ArtHarvest.letGoForPlay(waitMs = 0L))
    }

    @Test fun launchStillWaitsForOpenFilesAndKeepsFreshWineProfiles() {
        val source = listOf("src/main/java", "app/src/main/java").map { File(it) }.first { it.isDirectory }
        val screen = File(source, "app/gamenative/ui/screen/wow/WoWForeverScreen.kt").readText()
        val launch = screen.substring(screen.indexOf("fun launchGame("), screen.indexOf("LifecycleResumeEffect(gamePath)"))
        assertTrue(launch.indexOf("awaitArtHarvest(context)") < launch.indexOf("LauncherHooks.holdForPlay()"))
        assertTrue(launch.indexOf("LauncherHooks.holdForPlay()") < launch.indexOf("ArtHarvest.letGoForPlay()"))
        assertTrue(launch.indexOf("ArtHarvest.letGoForPlay()") < launch.indexOf("launchEvent(\"preparing the container"))
        val install = screen.substring(screen.indexOf("private fun installBundledComponents("), screen.indexOf("private fun installDriver("))
        assertFalse(install.contains("ContentsManager(context).syncContents()"))
        assertTrue(install.contains("ensureBionicLib"))
        val xserver = File(source, "app/gamenative/ui/screen/xserver/XServerScreen.kt").readText()
        assertTrue(xserver.substringAfter("private fun setupXEnvironment(").contains("contentsManager.syncContents()"))
    }
}

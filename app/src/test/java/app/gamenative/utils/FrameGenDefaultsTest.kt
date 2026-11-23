package app.gamenative.utils

import android.content.Context
import com.jrees.wowcompanion.FrameGen
import com.jrees.wowcompanion.ResetPlan
import com.winlator.container.Container
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Android preferences, synthetic PE fixtures only. No device, driver or commercial DLL is needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameGenDefaultsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("framegen", Context.MODE_PRIVATE)
    private val backupKey = "defaults30x2Ext2Before"

    private fun forgetProcess() {
        for (name in listOf("prefs", "dir", "launched", "launchedLimit"))
            FrameGen::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }

    @Before fun clean() {
        forgetProcess()
        prefs.edit().clear().commit()
        File(app.filesDir, "framegen/Lossless.dll").delete()
    }

    @After fun finish() {
        File(app.filesDir, "framegen/Lossless.dll").delete()
        prefs.edit().clear().commit()
        forgetProcess()
    }

    private fun container() = Container("wow_forever").apply {
        rootDir = Files.createTempDirectory(File("build/framegen-tests").apply { mkdirs() }.toPath(), "container").toFile()
        containerVariant = Container.BIONIC
    }

    private fun importFixture() {
        assertTrue(FrameGen.importFrom(ByteArrayInputStream(FrameGenTest.peBytes(true))).startsWith("Lossless.dll saved"))
    }

    @Test fun freshDefaultsAreDesiredOn2xButMissingDllIsOffAndCappedAtThirty() {
        FrameGen.init(app)
        assertTrue(FrameGen.enabled)
        assertEquals(2, FrameGen.multiplier)
        assertEquals(30, FrameGen.fpsLimit)
        assertEquals("{}", prefs.getString(backupKey, null))
        assertEquals(FrameGen.OFF, FrameGen.current())
        val c = container()
        LsfgVkManager.applyDsForever(c)
        assertEquals("true", c.getExtra("fpsLimiterEnabled"))
        assertEquals("30", c.getExtra("fpsLimiterTarget"))
        assertEquals("false", c.getExtra(LsfgVkManager.EXTRA_ARMED))
        assertFalse(LsfgVkManager.isArmed(c))
        assertEquals(30, LsfgVkManager.fpsLimit(c))
        assertFalse(FrameGen.importFrom(ByteArrayInputStream(FrameGenTest.peBytes(false))).startsWith("Lossless.dll saved"))
        assertFalse(FrameGen.hasDll())
        importFixture()
        assertTrue(FrameGen.current().on)
        LsfgVkManager.applyDsForever(c)
        assertEquals("true", c.getExtra(LsfgVkManager.EXTRA_ARMED))
        assertEquals("2", c.getExtra(LsfgVkManager.EXTRA_MULTIPLIER))
        assertEquals(30, LsfgVkManager.fpsLimit(c))
    }

    @Test fun upgradeReplacesOldPendingCapAndSavesOriginalStateOnlyOnce() {
        prefs.edit().putBoolean("fpsLimit108", true).putInt("fpsLimitContainer", 60).putInt("fpsLimitAsk", 40)
            .putBoolean("fpsLimitNote", false).putBoolean("enabled", false).putInt("multiplier", 3)
            .putFloat("flowScale", 0.55f).putBoolean("performance", false).commit()
        FrameGen.init(app)
        val before = prefs.getString(backupKey, null)!!
        val old = JSONObject(before)
        assertEquals(40, old.getInt("fpsLimitAsk"))
        assertEquals(60, old.getInt("fpsLimitContainer"))
        assertTrue(old.getBoolean("fpsLimit108"))
        assertFalse(old.getBoolean("fpsLimitNote"))
        assertFalse(old.getBoolean("enabled"))
        assertEquals(3, old.getInt("multiplier"))
        assertEquals(0.55f, FrameGen.flowScale, 0.001f)
        assertFalse(FrameGen.performance)
        assertTrue(FrameGen.enabled)
        assertEquals(2, FrameGen.multiplier)
        assertEquals(30, FrameGen.fpsLimit)
        assertFalse(FrameGen.migrateLimit { fail("legacy migration must not overwrite ext.2") })
        val c = container().apply {
            putExtra("fpsLimiterEnabled", "true"); putExtra("fpsLimiterTarget", "40")
            putExtra(LsfgVkManager.EXTRA_MULTIPLIER, "3")
            putExtra(LsfgVkManager.EXTRA_BACKEND, LsfgVkManager.BACKEND_LEGACY)
        }
        importFixture()
        LsfgVkManager.applyDsForever(c)
        assertEquals("30", c.getExtra("fpsLimiterTarget"))
        assertEquals("2", c.getExtra(LsfgVkManager.EXTRA_MULTIPLIER))
        assertEquals("0.55", c.getExtra(LsfgVkManager.EXTRA_FLOW_SCALE))
        assertEquals("false", c.getExtra(LsfgVkManager.EXTRA_PERFORMANCE_MODE))
        assertEquals(LsfgVkManager.BACKEND_LEGACY, LsfgVkManager.backend(c))
        FrameGen.enabled = false
        FrameGen.multiplier = 3
        FrameGen.askLimit(40)
        repeat(2) { FrameGen.init(app) }
        forgetProcess() // A new process / later APK reads the same private preferences.
        FrameGen.init(app)
        assertFalse(FrameGen.enabled)
        assertEquals(3, FrameGen.multiplier)
        assertEquals(40, FrameGen.fpsLimit)
        LsfgVkManager.applyDsForever(c)
        assertEquals("40", c.getExtra("fpsLimiterTarget"))
        assertEquals("false", c.getExtra(LsfgVkManager.EXTRA_ARMED))
        FrameGen.askLimit(0)
        forgetProcess(); FrameGen.init(app)
        LsfgVkManager.applyDsForever(c)
        assertEquals("false", c.getExtra("fpsLimiterEnabled"))
        assertEquals(before, prefs.getString(backupKey, null))
    }

    @Test fun recreatedContainersUseDefaultsButExplicitContainerPicksSurvive() {
        FrameGen.init(app)
        LsfgVkManager.applyDsForever(container()) // consumes the one-time pending default
        val fresh = container()
        LsfgVkManager.applyDsForever(fresh)
        assertEquals("30", fresh.getExtra("fpsLimiterTarget"))
        val explicit = container().apply { putExtra("fpsLimiterEnabled", "false"); putExtra("fpsLimiterTarget", "40") }
        LsfgVkManager.applyDsForever(explicit)
        assertEquals("false", explicit.getExtra("fpsLimiterEnabled"))
        assertEquals("40", explicit.getExtra("fpsLimiterTarget"))
        LsfgVkManager.applyDsForever(container().also {
            // The last observed explicit cap must not become an unconfigured container's default.
            assertEquals(0, FrameGen.fpsLimit)
        })
        assertEquals(30, FrameGen.fpsLimit)
    }

    @Test fun nativeAndLegacyLaunchConfigurationsKeepThirtyAsBaseAndTwoAsMultiplier() {
        FrameGen.init(app)
        importFixture()
        for (backend in listOf(LsfgVkManager.BACKEND_NATIVE, LsfgVkManager.BACKEND_LEGACY)) {
            val c = container().apply { putExtra(LsfgVkManager.EXTRA_BACKEND, backend) }
            LsfgVkManager.applyDsForever(c)
            assertTrue(LsfgVkManager.isArmed(c))
            assertEquals(30, LsfgVkManager.fpsLimit(c))
            assertEquals(2, LsfgVkManager.multiplier(c))
            File(c.rootDir, ".local/share/lsfg-vk/Lossless.dll").apply {
                parentFile.mkdirs(); writeBytes(FrameGenTest.peBytes(true))
            }
            assertTrue(LsfgVkManager.writeConfig(c))
            val config = File(c.rootDir, ".config/lsfg-vk/conf.toml").readText()
            assertTrue(config, config.contains("fps_limit = 30"))
            assertTrue(config, config.contains("multiplier = ${if (backend == LsfgVkManager.BACKEND_NATIVE) 1 else 2}"))
            assertEquals(60f, LsfgVkManager.nativeOutputTarget(LsfgVkManager.fpsLimit(c), LsfgVkManager.multiplier(c), 120f), 0f)
        }
    }

    @Test fun settingsResetKeepsMigrationAndRecoveryButRestoresNewDefaults() {
        FrameGen.init(app)
        val before = prefs.getString(backupKey, null)
        FrameGen.enabled = false; FrameGen.multiplier = 3; FrameGen.askLimit(40)
        val keys = ResetPlan.prefKeys("framegen", prefs.all.keys, setOf(ResetPlan.Kind.SETTINGS), "", false)
        prefs.edit().also { e -> keys.forEach { e.remove(it) } }.commit()
        FrameGen.resetDone()
        forgetProcess(); FrameGen.init(app)
        assertTrue(prefs.getBoolean("defaults30x2Ext2", false))
        assertEquals(before, prefs.getString(backupKey, null))
        assertTrue(FrameGen.enabled)
        assertEquals(2, FrameGen.multiplier)
        assertEquals(30, FrameGen.fpsLimit)
        assertFalse(FrameGen.migrateLimit { fail("reset must not restore legacy cap") })
        val c = container().apply { putExtra("fpsLimiterEnabled", "true"); putExtra("fpsLimiterTarget", "40") }
        LsfgVkManager.applyDsForever(c)
        assertEquals("30", c.getExtra("fpsLimiterTarget"))
    }
}

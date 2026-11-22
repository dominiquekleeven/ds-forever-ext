package app.gamenative.ui.screen.wow

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class WowGpuDriversTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val root get() = File(context.filesDir, "contents/adrenotools")

    @Before fun clean() {
        root.deleteRecursively()
        context.getSharedPreferences("wow_gpu_drivers", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun meta(name: String = "Mesa Turnip Test", minApi: Int = 26, library: String = "libvulkan_freedreno.so") = JSONObject()
        .put("schemaVersion", 1).put("name", name).put("minApi", minApi)
        .put("libraryName", library).toString().toByteArray()

    private fun elf() = ByteArray(64).apply {
        this[0] = 0x7f; this[1] = 'E'.code.toByte(); this[2] = 'L'.code.toByte(); this[3] = 'F'.code.toByte()
        this[4] = 2; this[5] = 1; this[16] = 3; this[18] = 0xb7.toByte()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun validZip() = zip("meta.json" to meta(), "libvulkan_freedreno.so" to elf())

    private fun reject(input: ByteArrayInputStream) {
        try {
            WowGpuDrivers.importZip(root, input, 34)
            fail("Invalid driver ZIP was accepted")
        } catch (_: IllegalStateException) {
            assertTrue("Staging files must be removed", root.listFiles().orEmpty().none { it.name.startsWith(".import-") })
        }
    }

    @Test fun importedChoicePersistsAcrossLaunchesAndResetUsesBundledProfile() {
        val driver = WowGpuDrivers.importZip(context, validZip())
        assertEquals("Mesa Turnip Test", driver.label)
        assertEquals(driver, WowGpuDrivers.installed(context).single())
        WowGpuDrivers.select(context, driver.id)
        assertEquals(driver.id, WowGpuDrivers.selected(context))
        assertEquals(driver.id, WowGpuDrivers.resolve(context, "bundled-a6xx"))
        assertEquals(driver.id, WowGpuDrivers.resolve(context, "bundled-a8xx"))
        WowGpuDrivers.select(context, null)
        assertEquals("bundled-a8xx", WowGpuDrivers.resolve(context, "bundled-a8xx"))
        assertTrue(File(root, driver.id).exists())
    }

    @Test fun sameNamedImportsCannotOverwriteEachOtherOrBundledFiles() {
        val bundled = File(root, "Turnip-WoW-scheduler-test").apply { mkdirs() }
        val sentinel = File(bundled, "meta.json").apply { writeText("bundled unchanged") }
        val first = WowGpuDrivers.importZip(context, validZip())
        val second = WowGpuDrivers.importZip(context, validZip())
        assertNotEquals(first.id, second.id)
        assertEquals(2, WowGpuDrivers.installed(context).size)
        assertEquals("bundled unchanged", sentinel.readText())
    }

    @Test fun traversalAndMetadataLibraryPathsAreRejected() {
        reject(zip("../escaped-driver" to elf(), "meta.json" to meta()))
        reject(zip("/absolute" to elf()))
        reject(zip("..\\escaped-driver" to elf()))
        reject(zip("meta.json" to meta(library = "../outside.so"), "libvulkan_freedreno.so" to elf()))
        assertFalse(File(root.parentFile, "escaped-driver").exists())
    }

    @Test fun missingMetadataLibraryWrongAbiAndNewerAndroidAreRejected() {
        reject(zip("libvulkan_freedreno.so" to elf()))
        reject(zip("meta.json" to meta()))
        reject(zip("meta.json" to meta(minApi = 35), "libvulkan_freedreno.so" to elf()))
        reject(zip("meta.json" to meta(), "libvulkan_freedreno.so" to elf().apply { this[18] = 0x3e }))
        reject(zip("meta.json" to meta(name = "Other Vulkan driver"), "libvulkan_freedreno.so" to elf()))
    }

    @Test fun tooManyEntriesAndOversizedMetadataAreRejected() {
        reject(zip(*Array(257) { "file-$it" to byteArrayOf() }))
        reject(zip("meta.json" to ByteArray(65537) { ' '.code.toByte() }))
    }

    @Test fun failedImportKeepsPreviousSelectionAndMissingDriverRequiresExplicitReset() {
        val driver = WowGpuDrivers.importZip(context, validZip())
        WowGpuDrivers.select(context, driver.id)
        reject(zip("meta.json" to meta()))
        assertEquals(driver.id, WowGpuDrivers.resolve(context, "bundled"))
        File(root, driver.id).deleteRecursively()
        try {
            WowGpuDrivers.resolve(context, "bundled")
            fail("Missing driver should not silently switch the saved choice")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("Bundled default"))
        }
        WowGpuDrivers.select(context, null)
        assertEquals("bundled", WowGpuDrivers.resolve(context, "bundled"))
    }
}

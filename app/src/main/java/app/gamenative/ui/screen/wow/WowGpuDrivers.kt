package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** App-private drivers only. Import never replaces an installed driver or the bundled defaults. */
internal object WowGpuDrivers {
    private const val PREFS = "wow_gpu_drivers"
    private const val KEY = "selected"
    private const val PREFIX = "Turnip-custom-"
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val MAX_ENTRIES = 256

    data class Driver(val id: String, val label: String)

    private fun root(context: Context) = File(context.filesDir, "contents/adrenotools")

    fun selected(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun select(context: Context, id: String?) {
        require(id == null || installed(context).any { it.id == id }) { "Driver is no longer installed. Import it again." }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY) else putString(KEY, id)
        }.commit()) { "Could not save the GPU driver choice." }
    }

    fun installed(context: Context): List<Driver> = root(context).listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith(PREFIX) }
        .mapNotNull { dir -> runCatching { Driver(dir.name, validate(dir, Build.VERSION.SDK_INT)) }.getOrNull() }
        .sortedBy { it.label }

    // Resolve at launch, not composition: every restart uses the last saved choice.
    fun resolve(context: Context, bundled: String): String {
        val id = selected(context) ?: return bundled
        check(installed(context).any { it.id == id }) {
            "Saved GPU driver is missing or invalid. Open GPU driver and reset to Bundled default, or import it again."
        }
        return id
    }

    fun importZip(context: Context, input: InputStream): Driver = importZip(root(context), input, Build.VERSION.SDK_INT)

    internal fun importZip(root: File, input: InputStream, sdk: Int): Driver {
        check(root.isDirectory || root.mkdirs()) { "Cannot create the driver directory." }
        val stage = File(root, ".import-${UUID.randomUUID()}")
        check(stage.mkdir()) { "Cannot stage the driver ZIP." }
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var total = 0L
                var count = 0
                val seen = HashSet<String>()
                val buffer = ByteArray(8192)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check(++count <= MAX_ENTRIES) { "Driver ZIP contains too many entries." }
                    val name = entry.name
                    check(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name &&
                        name.split('/').none { it == ".." || it == "." }) { "Unsafe path in driver ZIP." }
                    val target = File(stage, name)
                    check(target.canonicalPath.startsWith(stage.canonicalPath + File.separator) && seen.add(target.canonicalPath)) {
                        "Unsafe or duplicate path in driver ZIP."
                    }
                    if (entry.isDirectory) {
                        check(target.isDirectory || target.mkdirs()) { "Cannot extract driver directory." }
                    } else {
                        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot extract driver directory." }
                        target.outputStream().use { output ->
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                total += n
                                check(total <= MAX_BYTES) { "Driver ZIP exceeds the 256 MiB unpacked limit." }
                                if (name == "meta.json") check(target.length() + n <= 65536) { "Driver metadata is too large." }
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val label = validate(stage, sdk)
            val id = PREFIX + UUID.randomUUID()
            check(stage.renameTo(File(root, id))) { "Could not save the imported driver." }
            return Driver(id, label)
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun validate(dir: File, sdk: Int): String {
        val meta = File(dir, "meta.json")
        check(meta.isFile && meta.length() in 1..65536) { "ZIP needs meta.json at its root." }
        val json = JSONObject(meta.readText())
        val name = json.getString("name").trim()
        check(name.isNotEmpty() && name.length <= 200 && name.contains("turnip", ignoreCase = true)) {
            "Import a Turnip Adrenotools driver ZIP."
        }
        check(json.getInt("schemaVersion") == 1) { "Unsupported driver metadata schema." }
        check(json.getInt("minApi") in 1..sdk) { "Driver requires a newer Android version." }
        val library = json.getString("libraryName")
        check(Regex("[A-Za-z0-9_.-]+\\.so").matches(library)) { "Invalid driver library name." }
        val file = File(dir, library)
        check(file.isFile && file.canonicalFile.parentFile == dir.canonicalFile) { "Driver library is missing." }
        val header = ByteArray(64)
        val read = file.inputStream().use { input ->
            var n = 0
            while (n < header.size) {
                val got = input.read(header, n, header.size - n)
                if (got < 0) break
                n += got
            }
            n
        }
        check(read == 64 && header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() &&
            header[16] == 3.toByte() && header[17] == 0.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()) { "Driver must be an ARM64 Android shared library." }
        return name
    }
}

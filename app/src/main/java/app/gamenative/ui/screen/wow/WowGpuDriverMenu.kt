package app.gamenative.ui.screen.wow

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.jrees.wowcompanion.GpuDriverMenu
import com.jrees.wowcompanion.LauncherHooks
import com.winlator.core.GPUInformation
import kotlinx.coroutines.*

/** One owner above the chooser/local split. This composable draws nothing. */
@Composable
internal fun WowGpuDriverMenu() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    val controller = remember(context) { WowGpuDriverController(context, scope, picking) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        controller.picked(uri)
    }
    SideEffect {
        controller.pick = {
            picking = true
            picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
        }
    }
    DisposableEffect(controller) {
        GpuDriverMenu.service = controller
        controller.refresh()
        onDispose {
            if (GpuDriverMenu.service === controller) {
                GpuDriverMenu.close()
                GpuDriverMenu.service = null
            }
        }
    }
}

internal class WowGpuDriverController(
    private val context: Context,
    private val scope: CoroutineScope,
    picking: Boolean = false,
) : GpuDriverMenu.Service {
    override var state by mutableStateOf(GpuDriverMenu.State(
        selected = WowGpuDrivers.selected(context),
        adreno = runCatching { GPUInformation.isAdrenoGPU(context) }.getOrDefault(false),
        busy = picking,
    ))
        private set
    override val enabled get() = !GpuDriverMenu.launching && !LauncherHooks.gameUp()
    var pick: () -> Unit = {}

    override fun refresh() {
        scope.launch {
            try {
                val drivers = withContext(Dispatchers.IO) { WowGpuDrivers.installed(context) }
                state = state.copy(installed = drivers.map { GpuDriverMenu.Driver(it.id, it.label) }, selected = WowGpuDrivers.selected(context))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(message = "Cannot read drivers: ${e.message}") }
        }
    }

    override fun select(id: String?) {
        if (!enabled || state.busy || (id != null && id != WowGpuDrivers.SYSTEM && !state.adreno)) return
        state = state.copy(busy = true, message = "Saving driver choice…")
        scope.launch {
            try {
                withContext(Dispatchers.IO) { WowGpuDrivers.select(context, id) }
                state = state.copy(selected = id, message = "Saved. Applies on the next launch.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(message = "Save failed: ${e.message}. Previous choice is unchanged.") }
            finally { state = state.copy(busy = false) }
        }
    }

    override fun importZip() {
        if (!enabled || state.busy || !state.adreno) return
        state = state.copy(busy = true, message = "Choose a trusted ARM64 Adrenotools ZIP.")
        try { pick() }
        catch (e: Exception) { state = state.copy(busy = false, message = "Cannot open ZIP picker: ${e.message}") }
    }

    fun picked(uri: Uri?) {
        if (uri == null) { state = state.copy(busy = false, message = "Import cancelled. Previous choice is unchanged."); return }
        state = state.copy(busy = true, message = "Importing driver…")
        scope.launch {
            try {
                val drivers = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected ZIP.")
                    val imported = input.use { WowGpuDrivers.importZip(context, it) }
                    val installed = WowGpuDrivers.installed(context)
                    WowGpuDrivers.select(context, imported.id)
                    installed
                }
                state = state.copy(installed = drivers.map { GpuDriverMenu.Driver(it.id, it.label) },
                    selected = WowGpuDrivers.selected(context), message = "Imported and saved. Applies on the next launch.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(message = "Import failed: ${e.message ?: "invalid driver ZIP"}. Previous choice is unchanged.") }
            finally { state = state.copy(busy = false) }
        }
    }
}

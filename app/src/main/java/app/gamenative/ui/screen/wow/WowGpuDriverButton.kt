package app.gamenative.ui.screen.wow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winlator.core.GPUInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WowGpuDriverButton(enabled: Boolean, onOpenChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var drivers by remember { mutableStateOf(emptyList<WowGpuDrivers.Driver>()) }
    var selected by remember { mutableStateOf(WowGpuDrivers.selected(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val adreno = remember { runCatching { GPUInformation.isAdrenoGPU(context) }.getOrDefault(false) }

    fun save(id: String?) {
        runCatching { WowGpuDrivers.select(context, id) }
            .onSuccess { selected = id; message = "Saved. Applies on the next launch." }
            .onFailure { message = it.message ?: "Could not save driver choice." }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            message = "Importing driver…"
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected ZIP.")
                        input.use { WowGpuDrivers.importZip(context, it) }
                    }
                    drivers = withContext(Dispatchers.IO) { WowGpuDrivers.installed(context) }
                    save(imported.id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = "Import failed: ${e.message ?: "invalid driver ZIP"}. Previous choice is unchanged."
                } finally {
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(open) {
        if (open) drivers = withContext(Dispatchers.IO) { WowGpuDrivers.installed(context) }
    }
    FilledTonalButton(enabled = enabled, onClick = { open = true; onOpenChanged(true) }) {
        Text("GPU driver")
    }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy) { open = false; onOpenChanged(false) } },
        title = { Text("WoW GPU driver") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved for every launch. Bundled default uses the recommended driver for your GPU.")
                OutlinedButton(enabled = !busy, onClick = { save(null) }) {
                    Text(if (selected == null) "Selected: Bundled default" else "Reset to Bundled default")
                }
                drivers.forEach { driver ->
                    OutlinedButton(enabled = !busy && adreno, onClick = { save(driver.id) }) {
                        Text(if (selected == driver.id) "Selected: ${driver.label}" else driver.label)
                    }
                }
                if (selected != null && drivers.none { it.id == selected }) Text("Saved driver is unavailable. Reset or import it again.")
                Text("Custom Turnip drivers require an Adreno GPU and an ARM64 Adrenotools ZIP. Only import from a source you trust. A valid ZIP may still be incompatible with your GPU; reset if the game fails to start.")
                if (!adreno) Text("Custom Turnip import is unavailable: no Adreno GPU detected.")
                OutlinedButton(enabled = !busy && adreno, onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) {
                    Text("Import Turnip ZIP")
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { open = false; onOpenChanged(false) }) { Text("Done") } },
    )
}

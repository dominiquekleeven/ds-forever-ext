package app.gamenative.ui.screen.wow

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class WowGpuDriverControllerTest {
    @Test fun cancelledOsPickerReleasesBusyWithoutRewritingTheOldSavedId() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("wow_gpu_drivers", Context.MODE_PRIVATE)
        val oldId = "Turnip-custom-legacy-not-installed"
        prefs.edit().putString("selected", oldId).commit()
        val job = SupervisorJob()
        try {
            val controller = WowGpuDriverController(context, CoroutineScope(job), picking = true)
            assertTrue(controller.state.busy)
            assertEquals(oldId, controller.state.selected)
            controller.picked(null)
            assertFalse(controller.state.busy)
            assertEquals(oldId, WowGpuDrivers.selected(context))
            assertEquals(oldId, controller.state.selected)
            assertTrue(controller.state.message.orEmpty().contains("cancelled"))
        } finally { job.cancel(); prefs.edit().clear().commit() }
    }
}

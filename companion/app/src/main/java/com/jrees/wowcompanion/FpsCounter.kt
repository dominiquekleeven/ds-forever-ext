package com.jrees.wowcompanion

/** Explicit local Ctrl+R. It does not depend on an add-on version, document or Lua global. */
object FpsCounter {
    private const val R = 27
    private const val CTRL_L = 37
    private const val CTRL_R = 105
    private val otherModifiers = setOf(50, 62, 64, 108, 133, 134) // Shift, Alt, Super (both sides)

    fun unavailable(): String? {
        if (PlayModes.current == PlayMode.STREAM) return "FPS toggle unavailable while streaming"
        if (!LauncherHooks.gameUp() || LauncherHooks.gameStartSec == 0L ||
            GameLife.endedAt >= maxOf(GameLife.startedAtMs, LauncherHooks.gameStartSec * 1000L) || SetupBridge.restarting)
            return "Start the local game to toggle FPS"
        val keys = InputSink.sink?.pressedKeys() ?: return "Local keyboard input unavailable"
        if (R in keys || keys.any { it in otherModifiers }) return "Release R, Shift, Alt and Super to toggle FPS"
        return null
    }

    /** One press/release, never an add-on tap as well. Leave an already held Ctrl untouched. */
    @Synchronized fun toggle(): Boolean {
        unavailable()?.let { GameInput.onStatus(it); return false }
        val sink = InputSink.sink ?: return false
        val keys = sink.pressedKeys() ?: return false
        val ownCtrl = CTRL_L !in keys && CTRL_R !in keys
        try {
            try {
                if (ownCtrl) sink.key(CTRL_L, true)
                try {
                    sink.key(R, true)
                    InputSink.pause(InputSink.KEY_HOLD_MS)
                } finally { sink.key(R, false) }
            } finally {
                if (ownCtrl) sink.key(CTRL_L, false)
            }
        } catch (_: Exception) {
            GameInput.onStatus("FPS toggle failed: local keyboard input unavailable")
            return false
        }
        return true
    }
}

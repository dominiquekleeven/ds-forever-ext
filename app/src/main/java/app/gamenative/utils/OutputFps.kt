package app.gamenative.utils

/** Successful renderer presents, not verified panel scanout. */
internal class OutputFps {
    private var frames = -1L
    private var at = 0L

    fun reset() { frames = -1L; at = 0L }

    fun sample(count: Long, now: Long): Float? {
        val elapsed = now - at
        val delta = count - frames
        val valid = frames >= 0 && delta > 0 && elapsed in 500..2_500
        frames = count
        at = now
        return if (valid) (delta * 1000.0 / elapsed).toFloat() else null
    }

    companion object {
        /** The layer writes fps= (output) and base= (input); never substitute base. */
        fun parse(text: String, modified: Long, now: Long): Float? {
            if (now - modified !in 0..2_000) return null
            return text.lineSequence().firstOrNull { it.startsWith("fps=") }
                ?.substringAfter('=')?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
        }
    }
}

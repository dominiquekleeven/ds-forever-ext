package com.jrees.wowcompanion

import java.util.Locale

/** WoW's GetFramerate(), never renderer arrivals or a configured frame multiplier. */
object GameFps {
    const val FRESH_MS = 3_000L
    private data class Sample(val fps: Float, val at: Long)
    @Volatile private var sample: Sample? = null

    fun note(value: Double, current: Boolean, now: Long) {
        sample = if (current && value.isFinite() && value > 0 && value <= 10_000)
            Sample(value.toFloat(), now) else null
    }

    fun read(now: Long): Float? = sample?.takeIf { now - it.at in 0..FRESH_MS }?.fps
    fun reset() { sample = null }

    fun label(name: String, value: Float?): String = "$name FPS " +
        (value?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.US, "%.1f", it) } ?: "unavailable")
}

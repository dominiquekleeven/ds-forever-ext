package com.jrees.wowcompanion

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils

/** The existing module/cloth-button vocabulary, sized to either launcher's available menu area. */
object GpuDriverPanel {
    fun draw(c: Canvas, area: RectF, pressed: Int, hits: ArrayList<Pair<RectF, Int>>) {
        val panel = RectF(area.left, area.centerY() - minOf(820f, area.height()) / 2f,
            area.right, area.centerY() + minOf(820f, area.height()) / 2f)
        UiKit.module(c, panel)
        val k = minOf(1f, panel.height() / 820f)
        val pad = 28f * k; val gap = 14f * k
        val left = panel.left + pad; val right = panel.right - pad
        var y = panel.top + pad
        fun text(words: String, height: Float, size: Float, color: Int = Theme.text) {
            val p = UiKit.bodyPaint(size * k).apply { textAlign = Paint.Align.CENTER }
            val label = TextUtils.ellipsize(words, android.text.TextPaint(p), right - left, TextUtils.TruncateAt.END).toString()
            UiKit.outlined(c, label, panel.centerX(), y + height / 2f, p.textSize, color)
            y += height
        }
        val title = UiKit.titlePaint(48f * k).apply { textAlign = Paint.Align.CENTER }
        c.drawText(Tr.t("GPU driver"), panel.centerX(), y + 42f * k, title)
        y += 64f * k
        val state = GpuDriverMenu.state
        text("Selected: ${state.selectedLabel}", 44f * k, 30f)
        text("Saved for the next launch. No change to the running game.", 40f * k, 27f, Theme.dim)
        y += 8f * k
        fun button(r: RectF, word: String, id: Int, selected: Boolean = false) {
            val enabled = GpuDriverMenu.enabled(id)
            val p = UiKit.bodyPaint(36f * k)
            val label = TextUtils.ellipsize(word, android.text.TextPaint(p), r.width() - (if (selected) 148f else 60f) * k, TextUtils.TruncateAt.END).toString()
            UiKit.button(c, r, label, when {
                !enabled -> UiKit.Button.DISABLED
                pressed == id -> UiKit.Button.DOWN
                else -> UiKit.Button.UP
            }, textSize = 36f * k)
            if (selected) UiKit.check(c, r.left + 40f * k, r.centerY(), 48f * k, true)
            if (enabled) hits += r to id
            if (GpuDriverMenu.focus == id) UiKit.focus(c, RectF(r.left - 4f * k, r.top - 4f * k, r.right + 4f * k, r.bottom + 4f * k))
        }
        val start = GpuDriverMenu.page * GpuDriverMenu.PAGE_SIZE
        for (i in 0 until GpuDriverMenu.PAGE_SIZE) {
            val index = start + i
            state.choices.getOrNull(index)?.let { driver ->
                button(RectF(left, y, right, y + 88f * k),
                    driver.label, GpuDriverMenu.ROW0 + index, selected = driver.id == state.selected)
            }
            y += 102f * k
        }
        val mid = panel.centerX()
        button(RectF(left, y, mid - gap / 2, y + 64f * k), "Previous", GpuDriverMenu.PREVIOUS)
        button(RectF(mid + gap / 2, y, right, y + 64f * k), "Next", GpuDriverMenu.NEXT)
        y += 68f * k
        text("${GpuDriverMenu.page + 1} / ${GpuDriverMenu.pageCount}", 34f * k, 26f, Theme.dim)
        y += 4f * k
        button(RectF(left, y, mid - gap / 2, y + 88f * k), if (state.busy) "Please wait…" else "Import ZIP", GpuDriverMenu.IMPORT)
        button(RectF(mid + gap / 2, y, right, y + 88f * k), "Back", GpuDriverMenu.BACK)
        y += 102f * k
        // Reserve three lines for errors and import guidance. Never let a ZIP's long metadata escape the module.
        val status = state.message ?: when {
            !state.adreno -> "Custom drivers require an Adreno GPU. Bundled default and System remain available."
            else -> "Import trusted ARM64 Adrenotools ZIPs (Turnip or Qualcomm). If a driver fails, reset to Bundled default."
        }
        val p = UiKit.bodyPaint(27f * k)
        val lines = SetupPanel.wrap(status, p, right - left)
        for ((i, line) in lines.take(3).withIndex()) {
            text(if (i == 2 && lines.size > 3) "$line…" else line, 30f * k, 27f, Theme.dim)
        }
    }
}

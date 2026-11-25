package com.jrees.wowcompanion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** A single destination diamond. No trail, connector, invented path or second navigation arrow. */
class SpfRoutePaint {
    private val cue = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    @Suppress("UNUSED_PARAMETER")
    fun draw(c: Canvas, goal: SpfRoute.Goal, field: RectF, now: Long, project: (Double, Double) -> FloatArray) {
        if (field.width() <= 28f || field.height() <= 28f) return
        val p = project(goal.x, goal.y)
        if (p.any { !it.isFinite() }) return
        val x = p[0].coerceIn(field.left + 14f, field.right - 14f)
        val y = p[1].coerceIn(field.top + 14f, field.bottom - 14f)
        val save = c.save(); c.clipRect(field)
        cue.reset(); cue.moveTo(x, y - 8); cue.lineTo(x + 6, y); cue.lineTo(x, y + 8); cue.lineTo(x - 6, y); cue.close()
        paint.color = Color.BLACK; paint.strokeWidth = 7f; c.drawPath(cue, paint)
        paint.color = MarkerArt.PIN_GOLD; paint.strokeWidth = 3f; c.drawPath(cue, paint)
        c.restoreToCount(save)
    }
}

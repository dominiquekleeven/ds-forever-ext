package com.jrees.wowcompanion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.hypot

/** Both bottom maps use the same gold breadcrumbs and blue direction cue. Coordinates are world X,Y. */
class SpfRoutePaint {
    private val path = Path()
    private val cue = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun draw(c: Canvas, w: SpfRoute.Walk, field: RectF, now: Long, project: (Double, Double) -> FloatArray) {
        if (field.width() <= 28f || field.height() <= 28f) return
        val save = c.save()
        c.clipRect(field)
        path.reset()
        var previous = project(w.points[0], w.points[1])
        for (i in 2 until w.points.size step 2) {
            val p = project(w.points[i], w.points[i + 1])
            // Clip BEFORE dashing. A world-size offscreen segment must not expand into millions of dots when zoomed in.
            clipped(previous, p, field)?.let { line ->
                path.moveTo(line[0], line[1]); path.lineTo(line[2], line[3])
            }
            previous = p
        }
        // Round-capped dots, the existing route's spacing, speed and gold. No per-pixel walk along a huge path.
        paint.pathEffect = DashPathEffect(floatArrayOf(RouteArrows.DOT, RouteArrows.SPACING - RouteArrows.DOT), RouteArrows.phase(now))
        paint.color = Color.argb(180, 0, 0, 0); paint.strokeWidth = 9f; c.drawPath(path, paint)
        paint.color = MarkerArt.PIN_GOLD; paint.strokeWidth = 6f; c.drawPath(path, paint)
        paint.pathEffect = null
        val start = project(w.points[0], w.points[1]); val next = project(w.next[0], w.next[1])
        val dx = next[0] - start[0]; val dy = next[1] - start[1]; val len = hypot(dx, dy)
        if (len > 1f) {
            val fx = dx / len; val fy = dy / len
            // Blue chevron on the current segment: a direction cue even when the next bend is off the field.
            val cx = start[0] + fx * minOf(30f, len / 2); val cy = start[1] + fy * minOf(30f, len / 2)
            cue.reset(); cue.moveTo(cx - fx * 4 - fy * 7, cy - fy * 4 + fx * 7)
            cue.lineTo(cx + fx * 5, cy + fy * 5); cue.lineTo(cx - fx * 4 + fy * 7, cy - fy * 4 - fx * 7)
            paint.color = Color.BLACK; paint.strokeWidth = 7f; c.drawPath(cue, paint)
            paint.color = Color.rgb(143, 184, 232); paint.strokeWidth = 4f; c.drawPath(cue, paint)
        }
        // The next waypoint uses the map's current-checkpoint diamond, pinned to the rim when outside.
        val inset = 14f
        val rim = RectF(field.left + inset, field.top + inset, field.right - inset, field.bottom - inset)
        val ray = clipped(start, next, rim)
        val x = ray?.get(2) ?: next[0].coerceIn(rim.left, rim.right)
        val y = ray?.get(3) ?: next[1].coerceIn(rim.top, rim.bottom)
        cue.reset(); cue.moveTo(x, y - 8); cue.lineTo(x + 6, y); cue.lineTo(x, y + 8); cue.lineTo(x - 6, y); cue.close()
        paint.color = Color.BLACK; paint.strokeWidth = 7f; c.drawPath(cue, paint)
        paint.color = MarkerArt.PIN_GOLD; paint.strokeWidth = 3f; c.drawPath(cue, paint)
        c.restoreToCount(save)
    }

    private fun clipped(a: FloatArray, b: FloatArray, r: RectF): FloatArray? {
        if (a.any { !it.isFinite() } || b.any { !it.isFinite() }) return null
        val dx = b[0].toDouble() - a[0]; val dy = b[1].toDouble() - a[1]
        var lo = 0.0; var hi = 1.0
        fun edge(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val t = q / p
            if (p < 0) lo = maxOf(lo, t) else hi = minOf(hi, t)
            return lo <= hi
        }
        if (!edge(-dx, a[0].toDouble() - r.left) || !edge(dx, r.right.toDouble() - a[0]) ||
            !edge(-dy, a[1].toDouble() - r.top) || !edge(dy, r.bottom.toDouble() - a[1])) return null
        return floatArrayOf((a[0] + lo * dx).toFloat(), (a[1] + lo * dy).toFloat(),
            (a[0] + hi * dx).toFloat(), (a[1] + hi * dy).toFloat())
    }
}

package com.jarvis.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Holographic HUD core. mode: 0 idle, 1 listening, 2 speaking, 3 thinking. */
class HudView(context: Context) : View(context) {
    var mode = 0
    private var a1 = 0f
    private var a2 = 0f
    private var last = 0L
    private val rnd = Random()
    private val bars = FloatArray(22) { 4f }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cBlue = Color.parseColor("#D99A24")
    private val cDark = Color.parseColor("#765015")
    private val cMid = Color.parseColor("#E6B84D")
    private val cLight = Color.parseColor("#F5D58A")
    private val cTeal = Color.parseColor("#B97816")
    private val cCore = Color.parseColor("#4A300B")
    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, w: Float, dash: FloatArray?, rot: Float, alpha: Int = 255) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.strokeWidth = w
        stroke.pathEffect = if (dash != null) DashPathEffect(dash, 0f) else null
        c.save()
        c.rotate(rot, cx, cy)
        c.drawCircle(cx, cy, r, stroke)
        c.restore()
    }

    private fun arcs(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, w: Float, rot: Float) {
        stroke.color = color
        stroke.alpha = 255
        stroke.strokeWidth = w
        stroke.pathEffect = null
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.save()
        c.rotate(rot, cx, cy)
        c.drawArc(rect, 270f, 90f, false, stroke)
        c.drawArc(rect, 90f, 90f, false, stroke)
        c.restore()
    }

    private fun triangle(c: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, color: Int, w: Float) {
        val path = Path()
        for (k in 0 until 3) {
            val a = (startDeg + k * 120f) * PI.toFloat() / 180f
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        stroke.color = color
        stroke.alpha = 255
        stroke.strokeWidth = w
        stroke.pathEffect = null
        c.drawPath(path, stroke)
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else (now - last) / 1e9f
        last = now
        val speed = when (mode) { 1, 2 -> 45f; 3 -> 90f; else -> 8f }
        a1 = (a1 + speed * dt) % 360f
        a2 = (a2 - speed * 0.6f * dt + 360f) % 360f

        val barsH = dp(44f)
        val cx = width / 2f
        val cy = (height - barsH) / 2f
        val r = min(width / 2f, (height - barsH) / 2f) * 0.94f

        ring(c, cx, cy, r, cBlue, dp(7f), floatArrayOf(dp(1.5f), dp(7f)), a1, 140)
        ring(c, cx, cy, r * 0.90f, cDark, dp(1f), null, 0f)
        arcs(c, cx, cy, r * 0.90f, cLight, dp(3.5f), a2)
        ring(c, cx, cy, r * 0.74f, cBlue, dp(1.5f), floatArrayOf(dp(12f), dp(5f)), a1)
        ring(c, cx, cy, r * 0.61f, cDark, dp(1f), null, 0f)
        arcs(c, cx, cy, r * 0.61f, cMid, dp(3f), a2)
        ring(c, cx, cy, r * 0.44f, cTeal, dp(1f), floatArrayOf(dp(2f), dp(5f)), a1)
        triangle(c, cx, cy, r * 0.30f, -90f, cLight, dp(1.5f))
        triangle(c, cx, cy, r * 0.30f, 90f, cBlue, dp(1f))
        fill.color = cCore
        c.drawCircle(cx, cy, r * 0.13f, fill)
        ring(c, cx, cy, r * 0.13f, cLight, dp(1.5f), null, 0f)
        fill.color = Color.parseColor("#FFF1C7")
        c.drawCircle(cx, cy, r * 0.05f, fill)

        val active = mode == 1 || mode == 2
        val w = width * 0.8f
        val x0 = (width - w) / 2f
        val step = w / bars.size
        val base = height - dp(4f)
        fill.color = cBlue
        for (i in bars.indices) {
            val target = if (active) dp(4f) + rnd.nextFloat() * dp(36f) else dp(4f)
            bars[i] += (target - bars[i]) * 0.35f
            c.drawRect(x0 + i * step, base - bars[i], x0 + i * step + dp(4f), base, fill)
        }
        postInvalidateOnAnimation()
    }
}

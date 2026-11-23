package com.jarvis.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Cinematic Quantum Core reactor. mode: 0 idle, 1 listening, 2 speaking, 3 thinking. */
class HudView(context: Context) : View(context) {
    var mode = 0
        set(value) { field = value.coerceIn(0, 3); invalidate() }

    private var rotation = 0f
    private var lastFrame = 0L
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Color.rgb(218, 166, 72)
    private val paleGold = Color.rgb(255, 225, 164)
    private val dimGold = Color.rgb(119, 82, 32)

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val delta = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(.05f)
        lastFrame = now
        val speed = when (mode) { 1 -> 38f; 2 -> 54f; 3 -> 82f; else -> 8f }
        rotation = (rotation + delta * speed) % 360f

        val cx = width / 2f
        val cy = height * .47f
        val radius = min(width * .36f, height * .34f).coerceAtLeast(dp(62f))
        val pulse = if (mode == 0) 1f else 1f + .025f * kotlin.math.sin(rotation * PI.toFloat() / 45f)
        val r = radius * pulse

        // Atmospheric glow and understated orbital field.
        fill.shader = RadialGradient(cx, cy, r * 1.9f,
            intArrayOf(0x665C3A0D, 0x223B2A12, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.9f, fill)
        fill.shader = null

        stroke.color = dimGold
        stroke.alpha = 145
        stroke.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, r * 1.58f, stroke)
        canvas.drawCircle(cx, cy, r * 1.43f, stroke)

        // Segmented outer telemetry ring.
        canvas.save()
        canvas.rotate(-rotation * .42f, cx, cy)
        val outer = RectF(cx-r*1.48f, cy-r*1.48f, cx+r*1.48f, cy+r*1.48f)
        for (i in 0 until 72) {
            val major = i % 6 == 0
            stroke.color = if (major) paleGold else gold
            stroke.alpha = if (major) 190 else 65
            stroke.strokeWidth = if (major) dp(2f) else dp(.8f)
            val start = i * 5f
            canvas.drawArc(outer, start, if (major) 2.8f else 1.1f, false, stroke)
        }
        canvas.restore()

        // Floating orbital arcs.
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        stroke.color = gold
        stroke.alpha = 210
        stroke.strokeWidth = dp(2f)
        val orbit = RectF(cx-r*1.18f, cy-r*.72f, cx+r*1.18f, cy+r*.72f)
        canvas.drawArc(orbit, 205f, 112f, false, stroke)
        stroke.alpha = 125
        canvas.drawArc(orbit, 25f, 82f, false, stroke)
        canvas.restore()

        // Dark metallic reactor shell.
        fill.shader = RadialGradient(cx-r*.22f, cy-r*.28f, r,
            intArrayOf(0xFF6B4A19.toInt(), 0xFF30210D.toInt(), 0xFF100D08.toInt(), 0xFF050709.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r*.91f, fill)
        fill.shader = null
        stroke.color = paleGold
        stroke.alpha = 165
        stroke.strokeWidth = dp(1.2f)
        canvas.drawCircle(cx, cy, r*.91f, stroke)

        // Holographic longitude and latitude.
        for (i in -2..2) {
            val offset = i * .25f
            val y = cy + offset*r*.86f
            val half = r*.86f*kotlin.math.sqrt((1f-offset*offset).coerceAtLeast(.08f))
            stroke.color = gold
            stroke.alpha = if (i == 0) 155 else 75
            stroke.strokeWidth = dp(.8f)
            canvas.drawOval(RectF(cx-half, y-half*.19f, cx+half, y+half*.19f), stroke)
        }
        for (i in 0..3) {
            canvas.save()
            canvas.rotate(rotation * if (i % 2 == 0) 1f else -1f + i*31f, cx, cy)
            stroke.color = if (i == 0) paleGold else gold
            stroke.alpha = if (i == 0) 190 else 90
            stroke.strokeWidth = dp(1f)
            val halfWidth = r*(.25f+i*.11f)
            canvas.drawOval(RectF(cx-halfWidth, cy-r*.89f, cx+halfWidth, cy+r*.89f), stroke)
            canvas.restore()
        }

        // Luminous inner energy chamber.
        fill.shader = RadialGradient(cx-r*.12f, cy-r*.16f, r*.56f,
            intArrayOf(Color.WHITE, 0xFFFFE7B5.toInt(), 0xFFFFC45A.toInt(), 0x55D98D24, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r*.53f, fill)
        fill.shader = null

        stroke.color = paleGold
        stroke.alpha = 220
        stroke.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, r*.52f, stroke)
        stroke.color = gold
        stroke.alpha = 190
        stroke.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, r*.62f, stroke)

        canvas.save()
        canvas.rotate(-rotation*1.4f, cx, cy)
        for (i in 0 until 36) {
            val angle = i * 10f * PI.toFloat()/180f
            val inner = r*.64f
            val outerTick = r*(if (i % 3 == 0) .76f else .70f)
            stroke.color = if (i % 3 == 0) paleGold else gold
            stroke.alpha = if (i % 3 == 0) 220 else 95
            stroke.strokeWidth = if (i % 3 == 0) dp(1.5f) else dp(.7f)
            canvas.drawLine(cx+cos(angle)*inner, cy+sin(angle)*inner,
                cx+cos(angle)*outerTick, cy+sin(angle)*outerTick, stroke)
        }
        canvas.restore()

        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, r*.065f, fill)
        fill.color = paleGold
        for (i in 0 until 4) {
            val angle = (rotation*(if (i%2==0) 1f else -1f) + i*90f) * PI.toFloat()/180f
            val x = cx + cos(angle)*r*1.08f
            val y = cy + sin(angle)*r*.47f
            canvas.drawCircle(x, y, dp(2.7f), fill)
            stroke.color = gold
            stroke.alpha = 125
            stroke.strokeWidth = dp(1f)
            canvas.drawCircle(x, y, dp(7f), stroke)
        }

        // State-reactive signal trace.
        val baseline = height - dp(18f)
        val signal = Path()
        val amplitude = if (mode == 0) 1.8f else if (mode == 3) 9f else 6f
        for (i in 0..96) {
            val x = width*.12f + width*.76f*i/96f
            val y = baseline + sin((i*.28f + rotation*.09f).toDouble()).toFloat()*dp(amplitude)
            if (i == 0) signal.moveTo(x, y) else signal.lineTo(x, y)
        }
        stroke.color = gold
        stroke.alpha = if (mode == 0) 100 else 220
        stroke.strokeWidth = dp(1.4f)
        canvas.drawPath(signal, stroke)

        postInvalidateOnAnimation()
    }
}

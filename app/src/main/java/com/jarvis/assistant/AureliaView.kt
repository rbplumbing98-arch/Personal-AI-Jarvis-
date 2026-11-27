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
import kotlin.math.sin

/**
 * Cinematic Quantum Core visualiser.
 * mode: 0 standby, 1 listening, 2 speaking, 3 thinking.
 *
 * Kept as a lightweight custom Canvas view so the Android HUD stays offline-capable,
 * dependency-free and smooth on mid-range devices.
 */
class AureliaView(context: Context) : View(context) {
    var mode = 0
        set(value) { field = value.coerceIn(0, 3); invalidate() }

    private var phase = 0f
    private var lastFrame = 0L
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Color.rgb(218, 166, 65)
    private val paleGold = Color.rgb(255, 226, 166)
    private val deepGold = Color.rgb(113, 73, 22)
    private val cyan = Color.rgb(105, 184, 197)

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun ring(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int,
                     width: Float, alpha: Int = 255, rotation: Float = 0f) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.strokeWidth = width
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawCircle(cx, cy, radius, stroke)
        canvas.restore()
    }

    private fun segmentedRing(canvas: Canvas, cx: Float, cy: Float, radius: Float,
                              rotation: Float, count: Int, sweep: Float, color: Int,
                              width: Float, alpha: Int) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.strokeWidth = width
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        for (i in 0 until count) {
            val start = i * 360f / count
            canvas.drawArc(bounds, start, sweep, false, stroke)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val delta = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(.05f)
        lastFrame = now
        val speed = when (mode) { 1 -> 1.7f; 2 -> 2.5f; 3 -> 3.2f; else -> .28f }
        phase = (phase + delta * speed) % (2f * PI.toFloat())

        val cx = width / 2f
        val cy = height * .46f
        val radius = minOf(width * .205f, height * .34f).coerceAtLeast(dp(48f))
        val energy = when (mode) { 1 -> 1.22f; 2 -> 1.38f; 3 -> 1.12f; else -> .82f }
        val pulse = 1f + sin(phase * 1.8f) * if (mode == 0) .012f else .035f

        // Ambient reactor bloom.
        fill.shader = RadialGradient(cx, cy, radius * 3.2f,
            intArrayOf(0x554A3214, 0x2227190B, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 3.2f, fill)
        fill.shader = null

        // Outer calibration rings.
        ring(canvas, cx, cy, radius * 1.92f, deepGold, dp(1f), 75, -phase * 8f)
        segmentedRing(canvas, cx, cy, radius * 1.78f, phase * 18f, 48, 2.2f,
            paleGold, dp(1.2f), if (mode == 0) 85 else 150)
        segmentedRing(canvas, cx, cy, radius * 1.56f, -phase * 26f, 12, 13f,
            gold, dp(2f), if (mode == 0) 105 else 205)

        // Main rotating containment rings.
        ring(canvas, cx, cy, radius * pulse * 1.36f, gold, dp(2f), 205, phase * 22f)
        segmentedRing(canvas, cx, cy, radius * 1.22f, -phase * 35f, 8, 25f,
            paleGold, dp(2.2f), 210)
        ring(canvas, cx, cy, radius * 1.08f, deepGold, dp(1.2f), 190, 0f)
        segmentedRing(canvas, cx, cy, radius * .94f, phase * 52f, 20, 8f,
            gold, dp(2f), 225)

        // Orbital markers.
        for (i in 0 until 4) {
            val angle = phase * (if (i % 2 == 0) 1f else -1f) + i * PI.toFloat() / 2f
            val x = cx + cos(angle) * radius * 1.58f
            val y = cy + sin(angle) * radius * 1.58f
            fill.color = if (mode == 1 && i == 0) cyan else paleGold
            fill.alpha = 225
            canvas.drawCircle(x, y, dp(3.2f), fill)
            stroke.color = gold
            stroke.alpha = 160
            stroke.strokeWidth = dp(1f)
            canvas.drawCircle(x, y, dp(7f), stroke)
        }

        // Core lens with a warm radial gradient.
        fill.shader = RadialGradient(cx - radius * .12f, cy - radius * .16f, radius * .88f,
            intArrayOf(0xFFFFE8B8.toInt(), 0xFFB98232.toInt(), 0xFF35230D.toInt(), 0xFF090806.toInt()),
            floatArrayOf(0f, .22f, .66f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * .78f, fill)
        fill.shader = null
        ring(canvas, cx, cy, radius * .79f, paleGold, dp(2f), 235)
        ring(canvas, cx, cy, radius * .68f, gold, dp(1f), 155, -phase * 30f)

        // Central energy aperture.
        fill.shader = RadialGradient(cx, cy, radius * .35f,
            intArrayOf(0xFFFFF4D7.toInt(), 0xFFE7B95F.toInt(), 0x664D3514, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * .36f, fill)
        fill.shader = null
        fill.color = Color.WHITE
        fill.alpha = 230
        canvas.drawCircle(cx, cy, radius * .075f * energy, fill)

        // Signal trace beneath the reactor, reactive to the assistant state.
        val baseline = height * .91f
        val signal = Path()
        val amplitude = when (mode) { 1 -> 10f; 2 -> 15f; 3 -> 7f; else -> 2.2f }
        for (i in 0..120) {
            val x = width * .08f + width * .84f * i / 120f
            val envelope = .18f + .82f * sin(i / 120f * PI).toFloat().let { it * it }
            val wave = sin(i * .19f + phase * (if (mode == 2) 5f else 2.2f))
            val y = baseline + wave * dp(amplitude) * envelope
            if (i == 0) signal.moveTo(x, y) else signal.lineTo(x, y)
        }
        stroke.color = if (mode == 1) cyan else paleGold
        stroke.alpha = if (mode == 0) 85 else 220
        stroke.strokeWidth = dp(1.4f)
        canvas.drawPath(signal, stroke)

        // Tiny state pulse markers; the host screen supplies the readable status text.
        fill.color = if (mode == 1) cyan else gold
        fill.alpha = if (mode == 0) 90 else 220
        canvas.drawCircle(width * .5f, height * .985f, dp(if (mode == 0) 2f else 3f), fill)

        postInvalidateOnAnimation()
    }
}

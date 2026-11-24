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
import kotlin.math.sin

/** Aurelia voice-first visualizer. mode: 0 idle, 1 listening, 2 speaking, 3 thinking. */
class AureliaView(context: Context) : View(context) {
    var mode = 0
        set(value) { field = value.coerceIn(0, 3); invalidate() }
    private var phase = 0f
    private var lastFrame = 0L
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Color.rgb(229, 177, 76)
    private val pale = Color.rgb(255, 225, 164)
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val delta = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(.05f)
        lastFrame = now
        phase = (phase + delta * when (mode) { 1 -> 3.1f; 2 -> 4.2f; 3 -> 5.2f; else -> .65f }) % (2f * PI.toFloat())
        val cx = width / 2f
        val cy = height * .47f
        val radius = minOf(width * .13f, height * .20f).coerceAtLeast(dp(35f))

        fill.shader = RadialGradient(cx, cy, radius * 3.5f,
            intArrayOf(0x332B1B08, 0x1827190B, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 3.5f, fill)
        fill.shader = null

        // Fine concentric rings keep the centre calm and uncluttered.
        line.color = gold; line.alpha = 75; line.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, radius * 1.65f, line)
        line.alpha = 120
        canvas.drawCircle(cx, cy, radius * 1.34f, line)

        // Flowing audio waveform.
        for (layer in 0..2) {
            val path = Path()
            val amp = (if (mode == 0) 8f else if (mode == 3) 23f else 17f) * (1f - layer * .22f)
            for (i in 0..180) {
                val x = width * .06f + width * .88f * i / 180f
                val nx = i / 180f
                val envelope = (.18f + .82f * sin(nx * PI).toFloat().let { it * it })
                val wave = sin(i * .115f + phase * (1f + layer * .13f) + layer * 1.1f)
                val y = cy + wave * amp * envelope
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            line.color = if (layer == 0) pale else gold
            line.alpha = if (layer == 0) 220 else 95 - layer * 20
            line.strokeWidth = dp(if (layer == 0) 1.8f else 1f)
            canvas.drawPath(path, line)
        }

        // Soft central voice control motif.
        fill.shader = RadialGradient(cx, cy, radius,
            intArrayOf(0xFF4B3518.toInt(), 0xFF1B140A.toInt(), 0xFF080706.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * .78f, fill)
        fill.shader = null
        line.color = pale; line.alpha = 230; line.strokeWidth = dp(1.7f)
        canvas.drawCircle(cx, cy, radius * .78f, line)
        line.color = gold; line.alpha = 190; line.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, radius * .94f, line)

        // Minimal microphone glyph.
        val mic = RectF(cx - radius * .17f, cy - radius * .38f, cx + radius * .17f, cy + radius * .20f)
        fill.color = pale
        canvas.drawRoundRect(mic, radius * .17f, radius * .17f, fill)
        line.color = pale; line.alpha = 235; line.strokeWidth = dp(2.4f)
        val arc = RectF(cx - radius * .31f, cy - radius * .19f, cx + radius * .31f, cy + radius * .39f)
        canvas.drawArc(arc, 0f, 180f, false, line)
        canvas.drawLine(cx, cy + radius * .39f, cx, cy + radius * .54f, line)
        canvas.drawLine(cx - radius * .16f, cy + radius * .54f, cx + radius * .16f, cy + radius * .54f, line)

        postInvalidateOnAnimation()
    }
}

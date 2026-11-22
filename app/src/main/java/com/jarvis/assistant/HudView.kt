package com.jarvis.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import java.util.Random
import kotlin.math.min

/** Two selectable animated J.A.R.V.I.S. cores. mode: 0 idle, 1 listening, 2 speaking, 3 thinking. */
class HudView(context: Context) : View(context) {
    var mode = 0
    var hubStyle = 3
        set(value) { field = if (value == 4) 4 else 3; invalidate() }

    private var angle = 0f
    private var last = 0L
    private val random = Random()
    private val bars = FloatArray(24) { 3f }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val gold get() = Color.parseColor("#E6B84D")
    private val paleGold get() = Color.parseColor("#FFF0C2")
    private val darkGold get() = Color.parseColor("#69400B")
    private val cyan get() = Color.parseColor("#51D9F5")
    private val paleBlue get() = Color.parseColor("#D7FAFF")
    private val deepBlue get() = Color.parseColor("#102D55")
    private val accent get() = if (hubStyle == 3) gold else cyan
    private val highlight get() = if (hubStyle == 3) paleGold else paleBlue

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, color: Int, width: Float, dash: FloatArray? = null, rotation: Float = 0f, alpha: Int = 255) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.strokeWidth = width
        stroke.pathEffect = dash?.let { DashPathEffect(it, 0f) }
        c.save()
        c.rotate(rotation, x, y)
        c.drawCircle(x, y, r, stroke)
        c.restore()
        stroke.pathEffect = null
    }

    private fun ellipse(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, rotation: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        stroke.pathEffect = null
        c.save()
        c.rotate(rotation, x, y)
        c.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), stroke)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
        last = now
        val speed = when (mode) { 1, 2 -> 55f; 3 -> 105f; else -> 12f }
        angle = (angle + speed * dt) % 360f

        val barsHeight = dp(46f)
        val cx = width / 2f
        val cy = (height - barsHeight) / 2f
        val radius = min(width / 2f, (height - barsHeight) / 2f) * 0.88f
        if (hubStyle == 3) drawQuantumCore(c, cx, cy, radius)
        else drawHolographicOrb(c, cx, cy, radius)

        val active = mode == 1 || mode == 2
        val barWidth = width * 0.82f
        val start = (width - barWidth) / 2f
        val step = barWidth / bars.size
        val base = height - dp(5f)
        fill.color = accent
        for (i in bars.indices) {
            val target = if (active) dp(4f) + random.nextFloat() * dp(32f) else dp(3f)
            bars[i] += (target - bars[i]) * 0.28f
            c.drawRoundRect(start + i * step, base - bars[i], start + i * step + dp(3.5f), base, dp(2f), dp(2f), fill)
        }
        postInvalidateOnAnimation()
    }

    /** Hub 3: layered gold reactor with a precise geometric quantum nucleus. */
    private fun drawQuantumCore(c: Canvas, x: Float, y: Float, r: Float) {
        ring(c, x, y, r, gold, dp(5f), floatArrayOf(dp(2f), dp(8f)), angle, 150)
        ring(c, x, y, r * .91f, darkGold, dp(1.2f))
        ring(c, x, y, r * .82f, paleGold, dp(2.5f), floatArrayOf(dp(34f), dp(8f)), -angle * .7f)
        ring(c, x, y, r * .68f, gold, dp(2f), floatArrayOf(dp(4f), dp(5f)), angle * 1.25f)
        ring(c, x, y, r * .53f, darkGold, dp(1f))
        ring(c, x, y, r * .40f, paleGold, dp(2f), floatArrayOf(dp(12f), dp(5f)), -angle)
        stroke.color = gold
        stroke.strokeWidth = dp(1.5f)
        val core = r * .27f
        c.save()
        c.rotate(angle * .35f, x, y)
        val path = android.graphics.Path().apply {
            moveTo(x, y - core); lineTo(x + core * .86f, y + core * .5f)
            lineTo(x - core * .86f, y + core * .5f); close()
        }
        c.drawPath(path, stroke)
        c.restore()
        fill.shader = RadialGradient(x, y, r * .22f, intArrayOf(paleGold, gold, darkGold), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * .17f, fill)
        fill.shader = null
        ring(c, x, y, r * .17f, paleGold, dp(1.5f))
        fill.color = Color.WHITE
        c.drawCircle(x, y, r * .045f, fill)
    }

    /** Hub 4: luminous blue holographic sphere with latitude/longitude scan lines. */
    private fun drawHolographicOrb(c: Canvas, x: Float, y: Float, r: Float) {
        ring(c, x, y, r, cyan, dp(2.5f), floatArrayOf(dp(2f), dp(7f)), angle, 180)
        ring(c, x, y, r * .91f, deepBlue, dp(1f), floatArrayOf(dp(3f), dp(5f)), -angle)
        fill.shader = RadialGradient(x - r * .22f, y - r * .25f, r * .95f,
            intArrayOf(Color.parseColor("#B8F8FF"), Color.parseColor("#176A9B"), Color.parseColor("#061426")),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * .70f, fill)
        fill.shader = null
        ellipse(c, x, y, r * .70f, r * .30f, angle, paleBlue, dp(1.6f))
        ellipse(c, x, y, r * .70f, r * .30f, -angle * .65f + 70f, cyan, dp(1.2f))
        ellipse(c, x, y, r * .38f, r * .69f, angle * .5f, paleBlue, dp(1.3f))
        ring(c, x, y, r * .70f, cyan, dp(1.4f), floatArrayOf(dp(5f), dp(5f)), angle)
        ring(c, x, y, r * .48f, paleBlue, dp(1f), floatArrayOf(dp(2f), dp(7f)), -angle * 1.4f)
        fill.color = Color.WHITE
        c.drawCircle(x - r * .12f, y - r * .14f, r * .035f, fill)
        ring(c, x, y, r * .13f, paleBlue, dp(1.8f))
    }
}

package com.jarvis.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Quantum Core holographic reactor. mode: 0 idle, 1 listening, 2 speaking, 3 thinking. */
class HudView(context: Context) : View(context) {
    var mode = 0
        set(value) { field = value; invalidate() }
    private var rotation = 0f
    private var last = 0L
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(239, 180, 67)
    private val ice = Color.rgb(255, 232, 183)
    private val gold = Color.rgb(224, 169, 79)
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else ((now-last)/1e9f).coerceAtMost(.05f)
        last = now
        rotation = (rotation + dt * when(mode) { 1,2 -> 48f; 3 -> 95f; else -> 12f }) % 360f
        val cx = width/2f
        val cy = height*.48f
        val r = min(width*.43f, height*.39f).coerceAtLeast(dp(65f))

        fill.shader = RadialGradient(cx,cy,r*1.5f,intArrayOf(0x55F2B84B,0x22C58A25,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,r*1.5f,fill); fill.shader = null

        line.color=gold; line.alpha=125; line.strokeWidth=dp(1f)
        c.drawCircle(cx,cy,r*1.18f,line)
        c.save(); c.rotate(rotation,cx,cy)
        line.color=gold; line.alpha=190; line.strokeWidth=dp(2.5f)
        c.drawArc(RectF(cx-r*1.08f,cy-r*1.08f,cx+r*1.08f,cy+r*1.08f),-35f,105f,false,line)
        line.color=gold; line.alpha=220; line.strokeWidth=dp(2f)
        c.drawArc(RectF(cx-r*1.08f,cy-r*1.08f,cx+r*1.08f,cy+r*1.08f),150f,75f,false,line)
        c.restore()

        fill.shader = RadialGradient(cx-r*.25f,cy-r*.28f,r*.95f,
            intArrayOf(0xFF1B5367.toInt(),0xFF0B2538.toInt(),0xFF030B14.toInt()),null,Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,r*.78f,fill); fill.shader=null
        line.color=gold; line.alpha=225; line.strokeWidth=dp(1.4f)
        c.drawCircle(cx,cy,r*.78f,line)

        // Curved latitude lines and rotating longitude ellipses create a holographic globe.
        for (i in -2..2) {
            val offset=i*.23f
            val y=cy+offset*r
            val half=r*.78f*kotlin.math.sqrt((1f-offset*offset).coerceAtLeast(.08f))
            line.color=gold; line.alpha=if(i==0) 200 else 115; line.strokeWidth=dp(1f)
            c.drawOval(RectF(cx-half,y-half*.23f,cx+half,y+half*.23f),line)
        }
        for (i in 0..3) {
            c.save(); c.rotate(rotation*(if(i%2==0) 1f else -1f)+i*45f,cx,cy)
            line.color=if(i==0) gold else ice; line.alpha=if(i==0) 220 else 125
            line.strokeWidth=dp(1f)
            val w=r*(.24f+i*.12f)
            c.drawOval(RectF(cx-w,cy-r*.78f,cx+w,cy+r*.78f),line)
            c.restore()
        }

        fill.shader=RadialGradient(cx,cy,r*.42f,intArrayOf(Color.WHITE,ice,0xFFFFB52E.toInt(),0x00FFB52E),null,Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,r*.40f,fill); fill.shader=null
        fill.color=Color.WHITE; c.drawCircle(cx,cy,r*.08f,fill)

        for(i in 0 until 6) {
            val a=(rotation*(if(i%2==0) 1f else -1f)+i*60f)*PI.toFloat()/180f
            val x=cx+cos(a)*r*.98f; val y=cy+sin(a)*r*.42f
            fill.color=if(i%2==0) gold else ice
            c.drawCircle(x,y,dp(3f),fill)
            line.color=fill.color; line.alpha=110; line.strokeWidth=dp(1f)
            c.drawCircle(x,y,dp(7f),line)
        }

        val base=height-dp(14f); val left=width*.14f; val span=width*.72f
        val wave=Path()
        for(i in 0..72) {
            val x=left+span*i/72f
            val amp=if(mode==0) 2f else 7f
            val y=base+sin((i*.3f+rotation*.08f).toDouble()).toFloat()*dp(amp)
            if(i==0) wave.moveTo(x,y) else wave.lineTo(x,y)
        }
        line.color=gold; line.alpha=210; line.strokeWidth=dp(1.5f); c.drawPath(wave,line)
        postInvalidateOnAnimation()
    }
}

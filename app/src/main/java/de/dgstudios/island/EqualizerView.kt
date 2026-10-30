package de.dgstudios.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/** Kleine animierte Equalizer-Balken für die eingeklappte Insel. */
class EqualizerView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    var playing = false
        set(v) { field = v; if (v) anim.start() else { anim.cancel(); invalidate() } }
    private val anim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 1100; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        paint.shader = LinearGradient(0f, h.toFloat(), 0f, 0f, 0xFF2F7BFF.toInt(), 0xFFC15CFF.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val bars = 4
        val gap = width / (bars * 2f)
        val bw = gap
        for (i in 0 until bars) {
            val f = if (playing) 0.25f + 0.75f * abs(sin(phase + i * 1.3f)) else 0.25f
            val bh = height * f
            val x = i * (bw + gap) + gap / 2
            c.drawRoundRect(x, (height - bh) / 2, x + bw, (height + bh) / 2, bw / 2, bw / 2, paint)
        }
    }

    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}

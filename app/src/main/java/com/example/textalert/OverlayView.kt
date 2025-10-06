package com.example.textalert

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class OverlayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val boxes = mutableListOf<RectF>()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        color = Color.RED
    }
    private var transform: Matrix? = null

    fun setTransform(m: Matrix?) { transform = m }
    fun hasNoTransform(): Boolean = transform == null

    fun show(imgBoxes: List<RectF>, bufW: Int, bufH: Int) {
        boxes.clear()
        val m = transform
        if (m != null) {
            for (r in imgBoxes) {
                val c = RectF(r)
                m.mapRect(c)
                boxes.add(c)
            }
        } else {
            if (bufW > 0 && bufH > 0 && width > 0 && height > 0) {
                val s = max(width.toFloat() / bufW, height.toFloat() / bufH)
                val dx = (width - bufW * s) / 2f
                val dy = (height - bufH * s) / 2f
                val tmp = Matrix().apply { setScale(s, s); postTranslate(dx, dy) }
                for (r in imgBoxes) {
                    val c = RectF(r)
                    tmp.mapRect(c)
                    boxes.add(c)
                }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in boxes) canvas.drawRect(r, paint)
    }
}

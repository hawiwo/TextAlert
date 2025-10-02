package com.example.textalert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val boxes = mutableListOf<RectF>()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private var transform: Matrix? = null

    fun setTransform(matrix: Matrix?) {
        transform = matrix
    }

    fun show(boxesImageSpace: List<RectF>) {
        boxes.clear()
        val m = transform
        if (m != null) {
            for (r in boxesImageSpace) {
                val c = RectF(r)
                m.mapRect(c)
                boxes.add(c)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in boxes) canvas.drawRect(r, paint)
    }
}


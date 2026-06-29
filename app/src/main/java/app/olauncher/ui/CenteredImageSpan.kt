package app.olauncher.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        if (fm != null) {
            val paintFm = paint.fontMetricsInt
            fm.ascent = paintFm.ascent
            fm.descent = paintFm.descent
            fm.top = paintFm.top
            fm.bottom = paintFm.bottom
        }
        return rect.right
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val b = drawable
        canvas.save()
        val fm = paint.fontMetricsInt
        val transY = y + (fm.descent + fm.ascent - b.bounds.bottom) / 2
        canvas.translate(x, transY.toFloat())
        b.draw(canvas)
        canvas.restore()
    }
}

package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class LineNumberEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val rect = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // Disable text wrapping and enable horizontal scrolling natively
        setHorizontallyScrolling(true)
        
        // Add left padding to make space for the line numbers gutter
        val density = context.resources.displayMetrics.density
        val paddingLeft = (45 * density).toInt()
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        val count = lineCount
        val r = rect
        val p = paint
        
        val density = context.resources.displayMetrics.density
        val separatorX = 35 * density
        
        // Draw vertical separator line between gutter and text editor area
        p.color = currentTextColor
        p.alpha = 40 // Subtle opacity
        p.strokeWidth = 1f * density
        canvas.drawLine(separatorX, scrollY.toFloat(), separatorX, (scrollY + height).toFloat(), p)
        
        // Set styling for drawing line numbers
        p.textSize = textSize * 0.85f
        p.color = currentTextColor
        p.alpha = 110 // Subtle line number coloring
        
        for (i in 0 until count) {
            val baseline = getLineBounds(i, r)
            val lineNum = (i + 1).toString()
            val textWidth = p.measureText(lineNum)
            // Align numbers to the right, just left of the separator
            val drawX = separatorX - 8 * density - textWidth
            canvas.drawText(lineNum, drawX, baseline.toFloat(), p)
        }
        
        super.onDraw(canvas)
    }
}

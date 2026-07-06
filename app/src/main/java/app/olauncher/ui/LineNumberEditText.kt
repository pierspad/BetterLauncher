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
        // Enable text wrapping and disable horizontal scrolling natively
        setHorizontallyScrolling(false)
        
        // Add left padding to make space for the line numbers gutter
        val density = context.resources.displayMetrics.density
        val paddingLeft = (36 * density).toInt()
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return
        val text = text?.toString() ?: ""
        val r = rect
        val p = paint
        
        val density = context.resources.displayMetrics.density
        val separatorX = 28 * density
        
        // Draw vertical separator line between gutter and text editor area
        p.color = currentTextColor
        p.alpha = 40 // Subtle opacity
        p.strokeWidth = 1f * density
        canvas.drawLine(separatorX, scrollY.toFloat(), separatorX, (scrollY + height).toFloat(), p)
        
        // Set styling for drawing line numbers
        p.textSize = textSize * 0.85f
        p.color = currentTextColor
        p.alpha = 110 // Subtle line number coloring
        
        // Find start offsets of each physical line
        val physicalLineStarts = ArrayList<Int>()
        physicalLineStarts.add(0)
        var index = 0
        while (true) {
            index = text.indexOf('\n', index)
            if (index == -1) break
            physicalLineStarts.add(index + 1)
            index++
        }
        
        // Draw line numbers at the layout line index where each physical line starts
        for (i in 0 until physicalLineStarts.size) {
            val startOffset = physicalLineStarts[i]
            val layoutLine = layout.getLineForOffset(startOffset)
            
            // Safety check
            if (layoutLine < 0 || layoutLine >= layout.lineCount) continue
            
            val baseline = getLineBounds(layoutLine, r)
            val lineNum = (i + 1).toString()
            val textWidth = p.measureText(lineNum)
            // Align numbers to the right, just left of the separator
            val drawX = separatorX - 6 * density - textWidth
            canvas.drawText(lineNum, drawX, baseline.toFloat(), p)
        }
        
        super.onDraw(canvas)
    }
}

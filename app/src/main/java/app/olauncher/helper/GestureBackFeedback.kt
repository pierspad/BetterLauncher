package app.olauncher.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Non-interactive feedback; never expands the gesture's touch interception area. */
internal class GestureBackFeedback(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()
    var fromRight = false
    var bottom = false
    var progress = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val radius = 20f * density
        val inset = (8f + 20f * progress) * density
        val cx = if (bottom) width / 2f else if (fromRight) width - inset else inset
        val cy = height / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(41, 121, 255)
        paint.alpha = (180 + 75 * progress).toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = Color.WHITE
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        arrow.reset()
        if (bottom) {
            arrow.moveTo(cx - 6f * density, cy + 3f * density)
            arrow.lineTo(cx, cy - 3f * density)
            arrow.lineTo(cx + 6f * density, cy + 3f * density)
        } else {
        arrow.moveTo(cx + 3f * density, cy - 6f * density)
        arrow.lineTo(cx - 3f * density, cy)
        arrow.lineTo(cx + 3f * density, cy + 6f * density)
        }
        canvas.drawPath(arrow, paint)
    }
}

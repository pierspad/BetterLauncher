package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import app.olauncher.R

/**
 * Full-screen overlay that punches a real "hole" through the (opaque) settings panel onto
 * the live system wallpaper. The launcher window is already transparent and shows the
 * wallpaper (AppTheme sets windowBackground=transparent + windowShowWallpaper=true), so
 * clearing the window pixels inside the hole reveals exactly what sits behind the launcher
 * — as if the panel weren't there. A scrim is then painted over the hole to preview the
 * chosen opacity, with an optional centered label.
 *
 * IMPORTANT: this view must NOT use a hardware/software layer. The PorterDuff.CLEAR has to
 * land on the window framebuffer (where the panel and cards were already drawn) rather than
 * on an isolated offscreen layer — otherwise it would clear nothing visible.
 */
class WallpaperHoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val appTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    private val iconPhone by lazy {
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_sc_phone)?.mutate()
    }
    private val iconCamera by lazy {
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_sc_camera)?.mutate()
    }

    private var target: View? = null
    private var cornerRadius = 0f
    private val holeRect = RectF()
    private val locTarget = IntArray(2)
    private val locSelf = IntArray(2)
    private var label = ""

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (syncHole()) invalidate()
        true
    }

    init {
        // Never intercept touches meant for the sliders sitting beneath the overlay.
        isClickable = false
        isFocusable = false
        labelPaint.textSize = 14f * resources.displayMetrics.scaledDensity
        appTextPaint.textSize = 18f * resources.displayMetrics.scaledDensity
        appTextPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    /** Binds the hole to [target]'s on-screen bounds, with [cornerRadiusPx] rounded corners. */
    fun attachTo(target: View, cornerRadiusPx: Float) {
        this.target = target
        this.cornerRadius = cornerRadiusPx
        if (syncHole()) invalidate()
    }

    fun setScrimColor(color: Int) {
        if (scrimPaint.color == color) return
        scrimPaint.color = color
        invalidate()
    }

    fun setLabel(text: String) {
        if (label == text) return
        label = text
        invalidate()
    }

    // Recomputes the hole from the target's current window position. Returns true if it moved.
    private fun syncHole(): Boolean {
        val t = target ?: return false
        if (t.width == 0 || t.height == 0) return false
        t.getLocationInWindow(locTarget)
        getLocationInWindow(locSelf)
        val left = (locTarget[0] - locSelf[0]).toFloat()
        val top = (locTarget[1] - locSelf[1]).toFloat()
        val right = left + t.width
        val bottom = top + t.height
        if (left == holeRect.left && top == holeRect.top &&
            right == holeRect.right && bottom == holeRect.bottom
        ) return false
        holeRect.set(left, top, right, bottom)
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (holeRect.isEmpty) return
        canvas.drawRoundRect(holeRect, cornerRadius, cornerRadius, clearPaint)
        if (scrimPaint.color != Color.TRANSPARENT)
            canvas.drawRoundRect(holeRect, cornerRadius, cornerRadius, scrimPaint)

        // Draw preview app names and icons on top of the hole for legibility reference
        val density = resources.displayMetrics.density
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeColor = if (night) Color.WHITE else Color.BLACK

        appTextPaint.color = themeColor

        val paddingLeft = 24f * density
        val paddingRight = 24f * density
        val centerY = holeRect.centerY()
        val spaceBetween = 24f * density

        val appPhone = context.getString(R.string.preview_app_phone)
        val appCamera = context.getString(R.string.preview_app_camera)

        val app1Y = centerY - spaceBetween / 2f - (appTextPaint.descent() + appTextPaint.ascent()) / 2f
        val app2Y = centerY + spaceBetween / 2f - (appTextPaint.descent() + appTextPaint.ascent()) / 2f

        canvas.drawText(appPhone, holeRect.left + paddingLeft, app1Y, appTextPaint)
        canvas.drawText(appCamera, holeRect.left + paddingLeft, app2Y, appTextPaint)

        val iconSize = 24f * density
        val iconLeft = holeRect.right - paddingRight - iconSize
        val iconRight = holeRect.right - paddingRight

        iconPhone?.let {
            it.setTint(themeColor)
            val icon1Top = centerY - spaceBetween / 2f - iconSize / 2f
            it.setBounds(iconLeft.toInt(), icon1Top.toInt(), iconRight.toInt(), (icon1Top + iconSize).toInt())
            it.draw(canvas)
        }

        iconCamera?.let {
            it.setTint(themeColor)
            val icon2Top = centerY + spaceBetween / 2f - iconSize / 2f
            it.setBounds(iconLeft.toInt(), icon2Top.toInt(), iconRight.toInt(), (icon2Top + iconSize).toInt())
            it.draw(canvas)
        }

        if (label.isNotEmpty()) {
            val baseline = holeRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(label, holeRect.centerX(), baseline, labelPaint)
        }
    }
}

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
import androidx.core.graphics.withSave

/**
 * Full-screen overlay that punches a real "hole" through the (opaque) settings panel onto
 * the live system wallpaper. The launcher window is already transparent and shows the
 * wallpaper (AppTheme sets windowBackground=transparent + windowShowWallpaper=true), so
 * clearing the window pixels inside the hole reveals exactly what sits behind the launcher
 * — as if the panel weren't there. A scrim is then painted over the hole to preview the
 * chosen opacity, with sample app names and icons.
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
    private val appTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var foreground = Color.WHITE
    private var protection = Color.TRANSPARENT

    private val iconPhone by lazy {
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_sc_phone)?.mutate()
    }
    private val iconCamera by lazy {
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_sc_camera)?.mutate()
    }

    private var target: View? = null
    private var viewport: View? = null
    private val locViewport = IntArray(2)
    private var cornerRadius = 0f
    private val holeRect = RectF()
    private val locTarget = IntArray(2)
    private val locSelf = IntArray(2)

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (syncHole()) invalidate()
        true
    }

    init {
        // Never intercept touches meant for the sliders sitting beneath the overlay.
        isClickable = false
        isFocusable = false
        appTextPaint.textSize = 18f * resources.displayMetrics.scaledDensity
        appTextPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** Binds the hole to [target]'s on-screen bounds, with [cornerRadiusPx] rounded corners. */
    fun attachTo(target: View, cornerRadiusPx: Float, viewport: View? = null) {
        this.target = target
        this.viewport = viewport
        this.cornerRadius = cornerRadiusPx
        if (syncHole()) invalidate()
    }

    fun setPreviewColors(scrim: Int, foreground: Int, protectionEnabled: Boolean) {
        val protection = HomeWallpaperContrast.protectionColor(scrim, foreground, protectionEnabled)
        if (scrimPaint.color == scrim && this.foreground == foreground && this.protection == protection) return
        scrimPaint.color = scrim
        this.foreground = foreground
        this.protection = protection
        invalidate()
    }

    // Recomputes the hole from the target's current window position. Returns true if it moved.
    private fun syncHole(): Boolean {
        val t = target ?: return false
        if (!t.isShown || t.width == 0 || t.height == 0) {
            val changed = !holeRect.isEmpty
            holeRect.setEmpty()
            return changed
        }
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
        canvas.withSave {
            viewport?.let { area ->
                area.getLocationInWindow(locViewport)
                getLocationInWindow(locSelf)
                val left = locViewport[0] - locSelf[0]
                val top = locViewport[1] - locSelf[1]
                // CLEAR and preview labels must never paint over the fixed header/search.
                canvas.clipRect(left, top, left + area.width, top + area.height)
            }
            canvas.drawRoundRect(holeRect, cornerRadius, cornerRadius, clearPaint)
            if (scrimPaint.color != Color.TRANSPARENT)
                canvas.drawRoundRect(holeRect, cornerRadius, cornerRadius, scrimPaint)

            // Draw preview app names and icons on top of the hole for legibility reference
            val density = resources.displayMetrics.density
            appTextPaint.color = foreground
            appTextPaint.setShadowLayer(HomeWallpaperContrast.TEXT_SHADOW_RADIUS_DP * density, 0f, 0f, protection)
            val haloDiameter = (48f * density).toInt()
            val halo = if (Color.alpha(protection) > 0) {
                HomeWallpaperContrast.shortcutHalo(protection, haloDiameter)
            } else null

            val paddingLeft = 24f * density
            val paddingRight = 24f * density
            val centerY = holeRect.centerY()
            val spaceBetweenText = 32f * density

            val appPhone = context.getString(R.string.preview_app_phone)
            val appCamera = context.getString(R.string.preview_app_camera)

            val app1Y = centerY - spaceBetweenText / 2f - (appTextPaint.descent() + appTextPaint.ascent()) / 2f
            val app2Y = centerY + spaceBetweenText / 2f - (appTextPaint.descent() + appTextPaint.ascent()) / 2f

            canvas.drawText(appPhone, holeRect.left + paddingLeft, app1Y, appTextPaint)
            canvas.drawText(appCamera, holeRect.left + paddingLeft, app2Y, appTextPaint)

            val iconSize = 24f * density
            val iconLeft = holeRect.right - paddingRight - iconSize
            val iconRight = holeRect.right - paddingRight

            listOf(iconPhone, iconCamera).forEachIndexed { index, icon ->
                if (icon == null) return@forEachIndexed
                val iconCenterY = centerY + (index - 0.5f) * spaceBetweenText
                halo?.let {
                    val left = (iconLeft + (iconSize - haloDiameter) / 2f).toInt()
                    val top = (iconCenterY - haloDiameter / 2f).toInt()
                    it.setBounds(left, top, left + haloDiameter, top + haloDiameter)
                    it.draw(canvas)
                }
                icon.setTint(foreground)
                val iconTop = iconCenterY - iconSize / 2
                icon.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), (iconTop + iconSize).toInt())
                icon.draw(canvas)
            }
        }
    }
}

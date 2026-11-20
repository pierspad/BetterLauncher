package app.olauncher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.olauncher.R

/** Keeps themed Home labels and icons legible over wallpaper as the scrim fades. */
internal object HomeWallpaperContrast {
    const val TEXT_SHADOW_RADIUS_DP = 2f

    fun protectionColor(scrim: Int, foreground: Int, enabled: Boolean): Int {
        if (!enabled) return Color.TRANSPARENT
        val lightForeground = foreground == Color.WHITE
        val worstWallpaper = if (lightForeground) Color.WHITE else Color.BLACK
        val backing = if (lightForeground) Color.BLACK else Color.WHITE
        val background = ColorUtils.compositeColors(scrim, worstWallpaper)
        val contrast = ColorUtils.calculateContrast(foreground, background)
        // Smoothstep keeps the fade gentle at both ends of the contrast range.
        val fade = ((5.5 - contrast) / 4.0).coerceIn(0.0, 1.0)
        val alpha = (220 * fade * fade * (3 - 2 * fade)).toInt()
        return ColorUtils.setAlphaComponent(backing, alpha)
    }

    fun shortcutHalo(color: Int, diameter: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            color,
            color,
            ColorUtils.setAlphaComponent(color, Color.alpha(color) / 2),
            ColorUtils.setAlphaComponent(color, 0),
        )).apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = diameter / 2f
            setSize(diameter, diameter)
        }

    fun apply(root: View, color: Int, scrim: Int, protectionEnabled: Boolean) {
        val density = root.resources.displayMetrics.density
        val protection = protectionColor(scrim, color, protectionEnabled)
        val faded = ColorUtils.setAlphaComponent(color, 204)
        val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(faded, color))
        // Do not recolor widgets or the reorder button's opaque themed surface.
        val textIds = listOf(R.id.clock, R.id.date, R.id.tvScreenTime, R.id.firstRunTips, R.id.setDefaultLauncher,
            R.id.homeApp1, R.id.homeApp2, R.id.homeApp3, R.id.homeApp4,
            R.id.homeApp5, R.id.homeApp6, R.id.homeApp7, R.id.homeApp8)
        textIds.forEach { id ->
            root.findViewById<TextView>(id)?.apply {
                setTextColor(colors)
                setHintTextColor(faded)
                setShadowLayer(TEXT_SHADOW_RADIUS_DP * density, 0f, 0f, protection)
            }
        }
        listOf(R.id.ivShortcut1, R.id.ivShortcut2, R.id.ivShortcut3, R.id.ivShortcut4,
            R.id.ivShortcut5, R.id.ivShortcut6, R.id.ivShortcut7, R.id.ivShortcut8).forEach { id ->
            val icon = root.findViewById<ImageView>(id) ?: return@forEach
            icon.imageTintList = colors
            applyShortcutBacking(icon, protection, density)
        }
    }

    private fun applyShortcutBacking(icon: ImageView, color: Int, density: Float) {
        val existing = icon.background as? LayerDrawable
        val plate = existing?.takeIf { it.numberOfLayers == 1 }?.getDrawable(0) as? GradientDrawable
        val hasHalo = Color.alpha(color) > 0
        if (!hasHalo && icon.background == null) return

        val width = icon.layoutParams.width
        val iconWidth = width - icon.paddingLeft - icon.paddingRight
        val diameter = minOf(iconWidth + (24 * density).toInt(), width, icon.layoutParams.height)
        if (plate?.colors?.firstOrNull() == color && plate.intrinsicWidth == diameter) return

        val left = icon.paddingLeft
        val top = icon.paddingTop
        val right = icon.paddingRight
        val bottom = icon.paddingBottom
        if (icon.foreground == null) {
            icon.foreground = icon.background
            icon.foregroundGravity = Gravity.FILL
        }
        icon.background = if (hasHalo) {
            LayerDrawable(arrayOf(shortcutHalo(color, diameter))).apply {
                setLayerSize(0, diameter, diameter)
                setLayerGravity(0, Gravity.CENTER)
            }
        } else null
        icon.setPadding(left, top, right, bottom)
    }
}

package app.olauncher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.olauncher.R

/** Home sits on wallpaper, so its foreground must follow the wallpaper plus the chosen scrim. */
internal object HomeWallpaperContrast {
    fun foreground(samples: List<Int>, scrim: Int, fallback: Int): Int {
        if (samples.isEmpty()) return fallback
        val backgrounds = samples.map { ColorUtils.compositeColors(scrim, ColorUtils.setAlphaComponent(it, 255)) }
        fun worstContrast(color: Int) = backgrounds.minOf { ColorUtils.calculateContrast(color, it) }
        return if (worstContrast(Color.BLACK) >= worstContrast(Color.WHITE)) Color.BLACK else Color.WHITE
    }

    fun apply(root: View, color: Int) {
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
                setShadowLayer(shadowRadius, shadowDx, shadowDy,
                    ColorUtils.setAlphaComponent(if (color == Color.WHITE) Color.BLACK else Color.WHITE, 128))
            }
        }
        listOf(R.id.ivShortcut1, R.id.ivShortcut2, R.id.ivShortcut3, R.id.ivShortcut4,
            R.id.ivShortcut5, R.id.ivShortcut6, R.id.ivShortcut7, R.id.ivShortcut8).forEach { id ->
            root.findViewById<ImageView>(id)?.imageTintList = colors
        }
    }
}

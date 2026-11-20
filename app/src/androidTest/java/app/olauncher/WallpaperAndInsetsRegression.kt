package app.olauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import app.olauncher.ui.HomeWallpaperContrast
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.scrimColor
import app.olauncher.helper.themedSurfaceOpacity
import app.olauncher.ui.SettingsInsets
import app.olauncher.ui.SettingsSearch
import app.olauncher.ui.ScreenInsets

fun runWallpaperAndInsetsRegression(context: Context) {
    for (night in listOf(false, true)) {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.AppTheme)
        val themedForeground = if (night) Color.WHITE else Color.BLACK
        val minimumOpacity = themed.themedSurfaceOpacity(0f)
        val minimumScrim = themed.scrimColor((minimumOpacity * 255).toInt())
        val worstWallpaper = if (night) Color.WHITE else Color.BLACK
        val minimumBackground = ColorUtils.compositeColors(minimumScrim, worstWallpaper)
        check(ColorUtils.calculateContrast(themedForeground, minimumBackground) >= 4.5)
        val noScrim = themed.scrimColor(0)
        val maximumScrim = themed.scrimColor(255)
        fun protectionAlpha(scrim: Int) =
            Color.alpha(HomeWallpaperContrast.protectionColor(scrim, themedForeground, true))
        check(protectionAlpha(noScrim) > protectionAlpha(minimumScrim))
        check(protectionAlpha(minimumScrim) > protectionAlpha(maximumScrim))
        val alphas = (0..255).map { protectionAlpha(themed.scrimColor(it)) }
        check(alphas.zipWithNext().all { (a, b) -> a - b in 0..5 }) { "Protection must fade smoothly" }
        check(Color.alpha(HomeWallpaperContrast.protectionColor(noScrim, themedForeground, false)) == 0)
        check(themed.themedSurfaceOpacity(1f) == 1f)
        val home = LayoutInflater.from(themed).inflate(R.layout.fragment_home, null)
        val reorder = home.findViewById<TextView>(R.id.reorderDone)
        val reorderColor = reorder.currentTextColor
        val color = themed.getColorFromAttr(R.attr.primaryColor)
        check(color == themedForeground)
        // Leave Home fully transparent again after cycling the opacity slider.
        for (opacity in listOf(0, 128, 255, 0)) {
            val scrim = themed.scrimColor(opacity)
            HomeWallpaperContrast.apply(home, color, scrim, true)
            check(home.findViewById<TextView>(R.id.clock).currentTextColor == color)
            check(home.findViewById<TextView>(R.id.homeApp1).currentTextColor == color)
            check(home.findViewById<TextView>(R.id.homeApp1).hintTextColors.defaultColor == ColorUtils.setAlphaComponent(color, 204))
            check(home.findViewById<ImageView>(R.id.ivShortcut1).imageTintList!!.defaultColor == color)
            check(reorder.currentTextColor == reorderColor) { "Do not recolor an opaque themed button" }
        }
        val clock = home.findViewById<TextView>(R.id.clock)
        val label = home.findViewById<TextView>(R.id.homeApp1)
        val shortcut = home.findViewById<ImageView>(R.id.ivShortcut1)
        HomeWallpaperContrast.apply(home, themedForeground, noScrim, true)
        check(Color.alpha(clock.shadowColor) > 0 && clock.shadowColor == label.shadowColor)
        check(shortcut.background != null)
        HomeWallpaperContrast.apply(home, themedForeground, noScrim, false)
        check(Color.alpha(clock.shadowColor) == 0 && Color.alpha(label.shadowColor) == 0)
        check(shortcut.background == null && shortcut.foreground != null)
        check(clock.currentTextColor == themedForeground)
    }
    for (landscape in listOf(false, true)) {
        val configuration = Configuration(context.resources.configuration).apply {
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.AppTheme)
        val density = themed.resources.displayMetrics.density
        val width = ((if (landscape) 854 else 480) * density).toInt()
        val height = ((if (landscape) 480 else 854) * density).toInt()
        val root = LayoutInflater.from(themed).inflate(R.layout.fragment_settings, null) as ViewGroup
        val home = LayoutInflater.from(themed).inflate(R.layout.fragment_home, null) as ViewGroup
        SettingsSearch(themed, root.findViewById(R.id.scrollLayout))
        // Include right and left side navigation, gesture navigation and a cutout.
        for (safe in listOf(Insets.of(0, 24, 72, 0), Insets.of(72, 24, 0, 0),
            Insets.of(0, 24, 0, 72), Insets.of(36, 24, 0, 24))) {
            // Before API 30 setInsetsIgnoringVisibility is a no-op in the compat
            // builder. Populate the legacy platform fields as a real window does.
            @Suppress("DEPRECATION")
            val insets = WindowInsetsCompat.Builder()
                .setSystemWindowInsets(safe)
                .setStableInsets(safe)
                .setInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(), safe)
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(), safe)
                .build()
            SettingsInsets.apply(root, insets)
            check(root.paddingLeft == safe.left && root.paddingRight == safe.right && root.paddingBottom == safe.bottom) {
                "Synthetic insets were not applied: $safe"
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val controls = arrayListOf<View>()
            root.addFocusables(controls, View.FOCUS_FORWARD)
            controls += root.findViewById<View>(R.id.settingsClose)
            controls += root.findViewById<View>(R.id.scrollView)
            for (control in controls) {
                val bounds = Rect()
                control.getDrawingRect(bounds)
                root.offsetDescendantRectToMyCoords(control, bounds)
                check(bounds.left >= safe.left && bounds.right <= width - safe.right) {
                    "Settings control overlaps a side system bar: ${control.id} $bounds safe=$safe"
                }
            }
            val scroll = root.findViewById<View>(R.id.scrollView)
            val bounds = Rect()
            scroll.getDrawingRect(bounds)
            root.offsetDescendantRectToMyCoords(scroll, bounds)
            check(bounds.bottom <= height - safe.bottom)
            ScreenInsets.apply(home, insets)
            home.findViewById<View>(R.id.shortcutIconsLayout).visibility = View.VISIBLE
            home.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            home.layout(0, 0, width, height)
            val icon = home.findViewById<View>(R.id.ivShortcut1)
            icon.getDrawingRect(bounds)
            home.offsetDescendantRectToMyCoords(icon, bounds)
            check(bounds.left >= safe.left && bounds.right <= width - safe.right) {
                "Home shortcut overlaps a side system bar: $bounds safe=$safe"
            }
        }
    }
}

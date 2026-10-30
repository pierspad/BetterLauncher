package app.olauncher.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/** Wallpaper may extend behind system bars; interactive content must stay outside them. */
internal object ScreenInsets {
    fun apply(root: View, insets: WindowInsetsCompat): Insets {
        val safe = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        root.setPadding(safe.left, 0, safe.right, safe.bottom)
        return safe
    }
}

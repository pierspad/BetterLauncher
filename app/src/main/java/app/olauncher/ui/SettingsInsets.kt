package app.olauncher.ui

import android.view.View
import androidx.core.view.WindowInsetsCompat
import app.olauncher.R
import app.olauncher.helper.dpToPx

/** Keep the entire settings screen outside side navigation bars and display cutouts. */
internal object SettingsInsets {
    fun apply(root: View, insets: WindowInsetsCompat) {
        val safe = ScreenInsets.apply(root, insets)
        root.findViewById<View>(R.id.settingsTopBar).setPadding(
            0, maxOf(safe.top, 24.dpToPx()) + 16.dpToPx(), 0, 0)
    }
}

package app.olauncher.helper

import android.content.Context
import android.provider.Settings

/** HyperOS can keep reporting three-button insets after switching to gestures. */
internal object GestureNavigationMode {
    fun isGestural(context: Context): Boolean = isGestural(
        Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1),
        Settings.Global.getInt(context.contentResolver, "force_fsg_nav_bar", 0)
    )

    internal fun isGestural(androidMode: Int, vendorMode: Int): Boolean = when (androidMode) {
        2 -> true
        0, 1 -> false
        else -> vendorMode == 1
    }

    internal fun bottomOffset(gestural: Boolean, inset: Int): Int = if (gestural) 0 else inset
}

package app.olauncher.helper

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.ViewGroup
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.olauncher.R

/** Separate, versioned disclosure: existing users must also consent to app limits. */
object AccessibilityConsent {
    private const val VERSION = 2
    private fun preferences(context: Context) =
        context.getSharedPreferences("accessibility_consent", Context.MODE_PRIVATE)

    fun hasConsent(context: Context) = preferences(context).getInt("accepted_version", 0) == VERSION

    fun showOnLaunch(context: Context) {
        val prefs = app.olauncher.data.Prefs(context)
        if ((prefs.lockModeOn || prefs.appLimitEnabled || prefs.xiaomiGestures) &&
            !hasConsent(context) && preferences(context).getInt("shown_version", 0) != VERSION) {
            show(context)
        }
    }

    fun needsSetup(context: Context): Boolean = !hasConsent(context) || !isAccessServiceEnabled(context)

    fun openSettings(context: Context) {
        if (hasConsent(context)) {
            openSystemSettings(context)
        } else {
            show(context)
        }
    }

    private fun openSystemSettings(context: Context) {
        val component = android.content.ComponentName(context, MyAccessibilityService::class.java)
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra("android.intent.extra.COMPONENT_NAME", component.flattenToString())
        runCatching { context.startActivity(details) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun show(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_accessibility_consent, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.consentAccept).setOnClickListener {
            preferences(context).edit().putInt("accepted_version", VERSION)
                .putInt("shown_version", VERSION).commit()
            dialog.dismiss()
            openSystemSettings(context)
        }
        view.findViewById<TextView>(R.id.consentDecline).setOnClickListener {
            preferences(context).edit().putInt("shown_version", VERSION).apply()
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.let { window ->
            // Bound the whole dialog to the visible area; only the weighted message
            // shrinks and scrolls, keeping consent and refusal reachable in landscape.
            val visibleFrame = Rect()
            window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
            val availableHeight = visibleFrame.height().takeIf { it > 0 }
                ?: context.resources.displayMetrics.heightPixels
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, (availableHeight * 0.9f).toInt())
        }
    }
}

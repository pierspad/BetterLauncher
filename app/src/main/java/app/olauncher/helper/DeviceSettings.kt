package app.olauncher.helper

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings

internal object DeviceSettings {
    fun isXiaomi(): Boolean = listOf(Build.MANUFACTURER, Build.BRAND)
        .any { it.lowercase(java.util.Locale.ROOT) in setOf("xiaomi", "redmi", "poco") }

    fun appInfo(context: Context) = open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri()))

    fun battery(context: Context) {
        val vendor = Intent().setComponent(android.content.ComponentName("com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.getString(app.olauncher.R.string.app_name))
        if (!isXiaomi() || !open(context, vendor)) appInfo(context)
    }

    fun autostart(context: Context) {
        if (!open(context, Intent().setComponent(android.content.ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")))) appInfo(context)
    }

    fun navigation(context: Context) {
        if (!open(context, Intent("com.android.settings.NAVIGATION_MODE_SETTINGS"))) {
            if (!open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))) appInfo(context)
        }
    }

    private fun open(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

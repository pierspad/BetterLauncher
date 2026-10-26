package app.olauncher.helper

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings

internal object DeviceSettings {
    fun isXiaomi(): Boolean = listOf(Build.MANUFACTURER, Build.BRAND)
        .any { it.lowercase(java.util.Locale.ROOT) in setOf("xiaomi", "redmi", "poco") }

    /** null means the vendor restriction cannot be verified with Android's public API. */
    fun batteryUnrestricted(context: Context): Boolean? {
        // OEM background policies are separate from Android's Doze allowlist.
        // These vendors need explicit user confirmation; Doze cannot certify
        // their additional restrictions. This list is conservative, not exhaustive.
        if (listOf(Build.MANUFACTURER, Build.BRAND).any {
                it.lowercase(java.util.Locale.ROOT) in setOf(
                    "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme",
                    "oneplus", "vivo", "iqoo", "samsung", "asus", "meizu", "tecno", "infinix"
                )
            }) return null
        return runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull()
    }

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

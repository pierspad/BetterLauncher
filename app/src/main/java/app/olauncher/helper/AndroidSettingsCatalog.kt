package app.olauncher.helper

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.olauncher.R
import app.olauncher.data.AppModel

/**
 * A small, curated catalog of Android system Settings screens that can be searched
 * from the app drawer (e.g. "wifi", "bluetooth", "battery"). Each entry is a stable
 * Settings action string; unsupported actions fall back to the main Settings screen
 * when launched (see [launchSettingTile]).
 */
object AndroidSettingsCatalog {

    private data class Entry(val labelRes: Int, val action: String)

    private val entries = listOf(
        Entry(R.string.setting_wifi, Settings.ACTION_WIFI_SETTINGS),
        Entry(R.string.setting_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
        Entry(R.string.setting_airplane, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        Entry(R.string.setting_data, Settings.ACTION_WIRELESS_SETTINGS),
        Entry(R.string.setting_nfc, Settings.ACTION_NFC_SETTINGS),
        Entry(R.string.setting_display, Settings.ACTION_DISPLAY_SETTINGS),
        Entry(R.string.setting_sound, Settings.ACTION_SOUND_SETTINGS),
        Entry(R.string.setting_battery, Intent.ACTION_POWER_USAGE_SUMMARY),
        Entry(R.string.setting_apps, Settings.ACTION_APPLICATION_SETTINGS),
        Entry(R.string.setting_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Entry(R.string.setting_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        Entry(R.string.setting_security, Settings.ACTION_SECURITY_SETTINGS),
        Entry(R.string.setting_privacy, Settings.ACTION_PRIVACY_SETTINGS),
        Entry(R.string.setting_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Entry(R.string.setting_language, Settings.ACTION_LOCALE_SETTINGS),
        Entry(R.string.setting_datetime, Settings.ACTION_DATE_SETTINGS),
        Entry(R.string.setting_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Entry(R.string.setting_about, Settings.ACTION_DEVICE_INFO_SETTINGS),
    )

    /** Builds the searchable tiles, each labelled "<name> · Settings" for clarity. */
    fun tiles(context: Context): List<AppModel.SettingTile> {
        val suffix = context.getString(R.string.settings)
        return entries.map {
            AppModel.SettingTile(
                appLabel = context.getString(it.labelRes) + " · " + suffix,
                intentAction = it.action,
            )
        }
    }

    /** Opens the given Settings tile, falling back to the main Settings screen. */
    fun launchSettingTile(context: Context, tile: AppModel.SettingTile) {
        val intent = Intent(tile.intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}

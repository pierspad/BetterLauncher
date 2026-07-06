package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class Prefs(private val context: Context) {
    private val PREFS_FILENAME = "com.pierspad.betterlauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val HOME_SHORTCUT_ICONS_NUM = "HOME_SHORTCUT_ICONS_NUM"
    private val FOLDERS = "FOLDERS"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val CLOCK_ALIGNMENT = "CLOCK_ALIGNMENT"
    private val SHORTCUT_ICONS_ALIGNMENT = "SHORTCUT_ICONS_ALIGNMENT"
    private val SCREEN_TIME_ALIGNMENT = "SCREEN_TIME_ALIGNMENT"
    private val HOME_VERTICAL_ALIGNMENT = "HOME_VERTICAL_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val LOCKED_APPS = "LOCKED_APPS"
    private val LIMITED_APPS = "LIMITED_APPS"
    private val APP_LIMIT_ENABLED = "APP_LIMIT_ENABLED"
    private val APP_LIMIT_LADDER = "APP_LIMIT_LADDER"
    private val APP_LIMIT_WINDOW = "APP_LIMIT_WINDOW"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val FONT_FAMILY = "FONT_FAMILY"
    private val SHOW_HOME_ICONS = "SHOW_HOME_ICONS"
    private val SHOW_DRAWER_ICONS = "SHOW_DRAWER_ICONS"
    private val SHOW_HOME_ONLY_ICONS = "SHOW_HOME_ONLY_ICONS"
    private val USE_MINIMAL_ICONS = "USE_MINIMAL_ICONS"
    private val CUSTOM_FONT_PATH = "CUSTOM_FONT_PATH"
    private val OPACITY_HOME = "OPACITY_HOME"
    private val OPACITY_DRAWER = "OPACITY_DRAWER"
    private val LAST_DECAY_DAY = "LAST_DECAY_DAY"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val IS_SHORTCUT_1 = "IS_SHORTCUT_1"
    private val SHORTCUT_ID_1 = "SHORTCUT_ID_1"
    private val IS_SHORTCUT_2 = "IS_SHORTCUT_2"
    private val SHORTCUT_ID_2 = "SHORTCUT_ID_2"
    private val IS_SHORTCUT_3 = "IS_SHORTCUT_3"
    private val SHORTCUT_ID_3 = "SHORTCUT_ID_3"
    private val IS_SHORTCUT_4 = "IS_SHORTCUT_4"
    private val SHORTCUT_ID_4 = "SHORTCUT_ID_4"
    private val IS_SHORTCUT_5 = "IS_SHORTCUT_5"
    private val SHORTCUT_ID_5 = "SHORTCUT_ID_5"
    private val IS_SHORTCUT_6 = "IS_SHORTCUT_6"
    private val SHORTCUT_ID_6 = "SHORTCUT_ID_6"
    private val IS_SHORTCUT_7 = "IS_SHORTCUT_7"
    private val SHORTCUT_ID_7 = "SHORTCUT_ID_7"
    private val IS_SHORTCUT_8 = "IS_SHORTCUT_8"
    private val SHORTCUT_ID_8 = "SHORTCUT_ID_8"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    // ---- Backup / Restore ----
    // Serializes every stored preference (with its type) to a JSON string.
    fun exportToJson(): String {
        val root = JSONObject()
        for ((key, value) in prefs.all) {
            try {
                val entry = JSONObject()
                when (value) {
                    is Boolean -> entry.put("t", "b").put("v", value)
                    is Int -> entry.put("t", "i").put("v", value)
                    is Long -> entry.put("t", "l").put("v", value)
                    is Float -> {
                        if (value.isNaN() || value.isInfinite()) continue
                        entry.put("t", "f").put("v", value.toDouble())
                    }
                    is Double -> {
                        if (value.isNaN() || value.isInfinite()) continue
                        entry.put("t", "f").put("v", value)
                    }
                    is String -> entry.put("t", "s").put("v", value)
                    is Set<*> -> {
                        val arr = JSONArray()
                        value.forEach { it?.let { arr.put(it.toString()) } }
                        entry.put("t", "set").put("v", arr)
                    }
                    else -> {
                        val className = value?.javaClass?.name ?: ""
                        when {
                            className.contains("Boolean") -> entry.put("t", "b").put("v", value as Boolean)
                            className.contains("Integer") || className.contains("Int") -> entry.put("t", "i").put("v", (value as Number).toInt())
                            className.contains("Long") -> entry.put("t", "l").put("v", (value as Number).toLong())
                            className.contains("Float") || className.contains("Double") -> {
                                val d = (value as Number).toDouble()
                                if (d.isNaN() || d.isInfinite()) continue
                                entry.put("t", "f").put("v", d)
                            }
                            className.contains("String") -> entry.put("t", "s").put("v", value.toString())
                            value is java.util.Set<*> -> {
                                val arr = JSONArray()
                                value.forEach { it?.let { arr.put(it.toString()) } }
                                entry.put("t", "set").put("v", arr)
                            }
                            else -> continue
                        }
                    }
                }
                root.put(key, entry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Export custom font file as Base64 if it exists in private storage
        try {
            val fontFile = File(context.filesDir, "custom_font")
            if (fontFile.exists()) {
                val bytes = fontFile.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val entry = JSONObject().put("t", "s").put("v", base64)
                root.put("CUSTOM_FONT_BASE64", entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return root.toString(2)
    }

    // Replaces all current preferences with the ones from a previously exported JSON.
    // Returns true on success.
    fun importFromJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val editor = prefs.edit()
            editor.clear()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "CUSTOM_FONT_BASE64") {
                    try {
                        val entry = root.getJSONObject(key)
                        val base64 = entry.getString("v")
                        if (base64.isNotEmpty()) {
                            val bytes = Base64.decode(base64, Base64.NO_WRAP)
                            val fontFile = File(context.filesDir, "custom_font")
                            fontFile.writeBytes(bytes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    continue
                }
                if (key == LOCK_MODE) {
                    editor.putBoolean(key, false)
                    continue
                }
                if (key == SCREEN_TIME_APP_PACKAGE || key == SCREEN_TIME_APP_USER || key == SCREEN_TIME_APP_CLASS_NAME) {
                    editor.putString(key, "")
                    continue
                }
                try {
                    val entry = root.getJSONObject(key)
                    when (entry.getString("t")) {
                        "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                        "i" -> editor.putInt(key, entry.getInt("v"))
                        "l" -> editor.putLong(key, entry.getLong("v"))
                        "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                        "s" -> editor.putString(key, entry.getString("v"))
                        "set" -> {
                            val arr = entry.getJSONArray("v")
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) set.add(arr.getString(i))
                            editor.putStringSet(key, set)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            editor.apply()

            // If the restored settings do NOT contain a custom font, remove any leftover font file
            if (!root.has("CUSTOM_FONT_BASE64")) {
                try {
                    File(context.filesDir, "custom_font").delete()
                } catch (_: Exception) {}
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }



    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 6)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    // Number of home-screen shortcut icons (right column) shown, 0..SHORTCUT_COUNT.
    // 0 means the icons column is hidden entirely.
    var homeShortcutIconsNum: Int
        get() = prefs.getInt(HOME_SHORTCUT_ICONS_NUM, 6)
        set(value) = prefs.edit { putInt(HOME_SHORTCUT_ICONS_NUM, value).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    // Independent horizontal alignment for the clock and the shortcut icons column
    var clockAlignment: Int
        get() = prefs.getInt(CLOCK_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(CLOCK_ALIGNMENT, value).apply() }

    var shortcutIconsAlignment: Int
        get() = prefs.getInt(SHORTCUT_ICONS_ALIGNMENT, Gravity.END)
        set(value) = prefs.edit { putInt(SHORTCUT_ICONS_ALIGNMENT, value).apply() }

    var screenTimeAlignment: Int
        get() = prefs.getInt(SCREEN_TIME_ALIGNMENT, Gravity.END)
        set(value) = prefs.edit { putInt(SCREEN_TIME_ALIGNMENT, value).apply() }

    // Vertical alignment (Gravity.TOP / CENTER_VERTICAL / BOTTOM) for apps + icons block
    var homeVerticalAlignment: Int
        get() = prefs.getInt(HOME_VERTICAL_ALIGNMENT, Gravity.BOTTOM)
        set(value) = prefs.edit { putInt(HOME_VERTICAL_ALIGNMENT, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    // Selected font. Empty string = system default. A value starting with "custom:"
    // means a user-imported font file whose absolute path is in [customFontPath];
    // any other value is a built-in Android font family name (e.g. "serif").
    var fontFamily: String
        get() = prefs.getString(FONT_FAMILY, "").toString()
        set(value) = prefs.edit { putString(FONT_FAMILY, value).apply() }

    var customFontPath: String
        get() = prefs.getString(CUSTOM_FONT_PATH, "").toString()
        set(value) = prefs.edit { putString(CUSTOM_FONT_PATH, value).apply() }

    var showHomeIcons: Boolean
        get() = prefs.getBoolean(SHOW_HOME_ICONS, false)
        set(value) = prefs.edit { putBoolean(SHOW_HOME_ICONS, value).apply() }

    var showDrawerIcons: Boolean
        get() = prefs.getBoolean(SHOW_DRAWER_ICONS, false)
        set(value) = prefs.edit { putBoolean(SHOW_DRAWER_ICONS, value).apply() }

    var showHomeOnlyIcons: Boolean
        get() = prefs.getBoolean(SHOW_HOME_ONLY_ICONS, false)
        set(value) = prefs.edit { putBoolean(SHOW_HOME_ONLY_ICONS, value).apply() }

    var useMinimalIcons: Boolean
        get() = prefs.getBoolean(USE_MINIMAL_ICONS, true)
        set(value) = prefs.edit { putBoolean(USE_MINIMAL_ICONS, value).apply() }

    // Scrim intensity (0f..1f) for the home screen and the app drawer respectively.
    // A value of 0 means no scrim. (Replaces the former master on/off toggle.)
    var opacityHome: Float
        get() = prefs.getFloat(OPACITY_HOME, 0.05f)
        set(value) = prefs.edit { putFloat(OPACITY_HOME, value).apply() }

    var opacityDrawer: Float
        get() = prefs.getFloat(OPACITY_DRAWER, 0.4f)
        set(value) = prefs.edit { putFloat(OPACITY_DRAWER, value).apply() }



    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    // Set right before a settings-initiated recreate (theme/font/text size) so the
    // launcher reopens the Settings screen instead of dropping back to the home.
    private val REOPEN_SETTINGS = "REOPEN_SETTINGS"
    var reopenSettingsAfterRestart: Boolean
        get() = prefs.getBoolean(REOPEN_SETTINGS, false)
        set(value) = prefs.edit { putBoolean(REOPEN_SETTINGS, value).apply() }

    private val SETTINGS_SCROLL_Y = "SETTINGS_SCROLL_Y"
    var settingsScrollY: Int
        get() = prefs.getInt(SETTINGS_SCROLL_Y, 0)
        set(value) = prefs.edit { putInt(SETTINGS_SCROLL_Y, value).apply() }

    var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    // Apps protected behind biometric / device-credential. Keys are "package|user".
    var lockedApps: MutableSet<String>
        get() = prefs.getStringSet(LOCKED_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(LOCKED_APPS, value).apply() }

    fun isAppLocked(key: String): Boolean = lockedApps.contains(key)

    // ---- Soft app limit ("use it less"): progressive cooldown, no password ----
    // Apps under the soft limit. Keys are "package|user", same scheme as lockedApps.
    var limitedApps: MutableSet<String>
        get() = prefs.getStringSet(LIMITED_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(LIMITED_APPS, value).apply() }

    fun isAppLimited(key: String): Boolean = limitedApps.contains(key)

    // Master switch. When off, limited apps open freely (selection is preserved).
    var appLimitEnabled: Boolean
        get() = prefs.getBoolean(APP_LIMIT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(APP_LIMIT_ENABLED, value).apply() }

    // Escalation ladder in minutes, comma-separated. The Nth re-open during an active
    // cooldown jumps to the Nth entry; the last entry is the cap. Fully parametric.
    var appLimitLadderMinutes: String
        get() = prefs.getString(APP_LIMIT_LADDER, "1,5,10,15,30,60") ?: "1,5,10,15,30,60"
        set(value) = prefs.edit { putString(APP_LIMIT_LADDER, value).apply() }

    // "Recently opened" window (minutes): re-opening a limited app within this window
    // after a clean launch triggers the first cooldown. 0 disables the grace window.
    var appLimitRecentWindowMin: Int
        get() = prefs.getInt(APP_LIMIT_WINDOW, 10)
        set(value) = prefs.edit { putInt(APP_LIMIT_WINDOW, value).apply() }

    // Per-app cooldown state. Stored under per-key suffixes to avoid a serialized blob.
    fun limitLastOpen(key: String): Long = prefs.getLong("LIMIT_LAST_OPEN_$key", 0L)
    fun setLimitLastOpen(key: String, value: Long) =
        prefs.edit { putLong("LIMIT_LAST_OPEN_$key", value).apply() }

    fun limitLevel(key: String): Int = prefs.getInt("LIMIT_LEVEL_$key", 0)
    fun setLimitLevel(key: String, value: Int) =
        prefs.edit { putInt("LIMIT_LEVEL_$key", value).apply() }

    fun limitUntil(key: String): Long = prefs.getLong("LIMIT_UNTIL_$key", 0L)
    fun setLimitUntil(key: String, value: Long) =
        prefs.edit { putLong("LIMIT_UNTIL_$key", value).apply() }

    var lastOpenedLimitedApp: String
        get() = prefs.getString("LAST_OPENED_LIMITED_APP", "").toString()
        set(value) = prefs.edit { putString("LAST_OPENED_LIMITED_APP", value).apply() }

    fun limitRetryCount(key: String): Int = prefs.getInt("LIMIT_RETRY_COUNT_$key", 0)
    fun setLimitRetryCount(key: String, value: Int) =
        prefs.edit { putInt("LIMIT_RETRY_COUNT_$key", value).apply() }

    fun limitLastOpenDay(key: String): Long = prefs.getLong("LIMIT_LAST_OPEN_DAY_$key", 0L)
    fun setLimitLastOpenDay(key: String, value: Long) =
        prefs.edit { putLong("LIMIT_LAST_OPEN_DAY_$key", value).apply() }

    fun clearLimitState(key: String) = prefs.edit {
        remove("LIMIT_LAST_OPEN_$key")
        remove("LIMIT_LEVEL_$key")
        remove("LIMIT_UNTIL_$key")
        remove("LIMIT_RETRY_COUNT_$key")
        remove("LIMIT_LAST_OPEN_DAY_$key")
        apply()
    }

    var lastDecayDay: Long
        get() = prefs.getLong(LAST_DECAY_DAY, 0L)
        set(value) = prefs.edit { putLong(LAST_DECAY_DAY, value).apply() }

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }



    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value).apply() }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value).apply() }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value).apply() }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value).apply() }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value).apply() }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value).apply() }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value).apply() }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value).apply() }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value).apply() }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value).apply() }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value).apply() }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value).apply() }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value).apply() }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value).apply() }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value).apply() }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value).apply() }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value).apply() }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value).apply() }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value).apply() }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value).apply() }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value).apply() }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value).apply() }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value).apply() }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value).apply() }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value).apply() }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value).apply() }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value).apply() }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value).apply() }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value).apply() }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value).apply() }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value).apply() }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value).apply() }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var isShortcut1: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_1, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_1, value) }

    var shortcutId1: String
        get() = prefs.getString(SHORTCUT_ID_1, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_1, value) }

    var isShortcut2: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_2, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_2, value) }

    var shortcutId2: String
        get() = prefs.getString(SHORTCUT_ID_2, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_2, value) }

    var isShortcut3: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_3, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_3, value) }

    var shortcutId3: String
        get() = prefs.getString(SHORTCUT_ID_3, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_3, value) }

    var isShortcut4: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_4, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_4, value) }

    var shortcutId4: String
        get() = prefs.getString(SHORTCUT_ID_4, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_4, value) }

    var isShortcut5: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_5, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_5, value) }

    var shortcutId5: String
        get() = prefs.getString(SHORTCUT_ID_5, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_5, value) }

    var isShortcut6: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_6, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_6, value) }

    var shortcutId6: String
        get() = prefs.getString(SHORTCUT_ID_6, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_6, value) }

    var isShortcut7: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_7, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_7, value) }

    var shortcutId7: String
        get() = prefs.getString(SHORTCUT_ID_7, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_7, value) }

    var isShortcut8: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_8, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_8, value) }

    var shortcutId8: String
        get() = prefs.getString(SHORTCUT_ID_8, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_8, value) }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    // Home-screen shortcut icons (right side column).
    // The feature no longer has a dedicated on/off switch: it is simply "on" whenever
    // the user keeps at least one icon slot (see [homeShortcutIconsNum], slider 0..N).
    val shortcutIconsEnabled: Boolean
        get() = homeShortcutIconsNum > 0

    fun getShortcutIconIndex(slot: Int): Int =
        prefs.getInt("SHORTCUT_ICON_INDEX_$slot", Constants.SHORTCUT_DEFAULT_ICONS[slot])

    fun setShortcutIconIndex(slot: Int, index: Int) =
        prefs.edit { putInt("SHORTCUT_ICON_INDEX_$slot", index) }

    fun getShortcutTargetPackage(slot: Int): String = prefs.getString("SHORTCUT_PKG_$slot", "").toString()
    fun getShortcutTargetClassName(slot: Int): String = prefs.getString("SHORTCUT_CLS_$slot", "").toString()
    fun getShortcutTargetUser(slot: Int): String = prefs.getString("SHORTCUT_USR_$slot", "").toString()
    fun getShortcutTargetLabel(slot: Int): String = prefs.getString("SHORTCUT_LBL_$slot", "").toString()

    fun setShortcutTarget(slot: Int, packageName: String, className: String?, user: String, label: String) {
        prefs.edit {
            putString("SHORTCUT_PKG_$slot", packageName)
            putString("SHORTCUT_CLS_$slot", className ?: "")
            putString("SHORTCUT_USR_$slot", user)
            putString("SHORTCUT_LBL_$slot", label)
        }
    }

    fun clearShortcutTarget(slot: Int) {
        prefs.edit {
            remove("SHORTCUT_PKG_$slot")
            remove("SHORTCUT_CLS_$slot")
            remove("SHORTCUT_USR_$slot")
            remove("SHORTCUT_LBL_$slot")
        }
    }

    // Home-screen widget (single)
    private val WIDGET_ID = "WIDGET_ID"
    private val WIDGET_ENABLED = "WIDGET_ENABLED"
    private val PENDING_WIDGET_ID = "PENDING_WIDGET_ID"

    var widgetId: Int
        get() = prefs.getInt(WIDGET_ID, -1)
        set(value) = prefs.edit { putInt(WIDGET_ID, value) }

    var pendingWidgetId: Int
        get() = prefs.getInt(PENDING_WIDGET_ID, -1)
        set(value) = prefs.edit { putInt(PENDING_WIDGET_ID, value) }

    var widgetEnabled: Boolean
        get() = prefs.getBoolean(WIDGET_ENABLED, false)
        set(value) = prefs.edit { putBoolean(WIDGET_ENABLED, value) }

    private val WIDGET_PROVIDER_PACKAGE = "WIDGET_PROVIDER_PACKAGE"
    private val WIDGET_PROVIDER_CLASS = "WIDGET_PROVIDER_CLASS"

    var widgetProviderPackage: String
        get() = prefs.getString(WIDGET_PROVIDER_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(WIDGET_PROVIDER_PACKAGE, value) }

    var widgetProviderClass: String
        get() = prefs.getString(WIDGET_PROVIDER_CLASS, "").toString()
        set(value) = prefs.edit { putString(WIDGET_PROVIDER_CLASS, value) }

    // ---- App drawer cache ----
    // A JSON snapshot of the last computed regular-app list, used to show the drawer
    // instantly on cold start while a fresh list is loaded from PackageManager.
    private val APP_LIST_CACHE = "APP_LIST_CACHE"

    var appListCache: String
        get() = prefs.getString(APP_LIST_CACHE, "").toString()
        set(value) = prefs.edit { putString(APP_LIST_CACHE, value).apply() }

    // ---- Drawer search ----
    private val SEARCH_SETTINGS_ENABLED = "SEARCH_SETTINGS_ENABLED"
    private val SEARCH_CONTACTS_ENABLED = "SEARCH_CONTACTS_ENABLED"

    var searchSettingsEnabled: Boolean
        get() = prefs.getBoolean(SEARCH_SETTINGS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SEARCH_SETTINGS_ENABLED, value).apply() }

    var searchContactsEnabled: Boolean
        get() = prefs.getBoolean(SEARCH_CONTACTS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(SEARCH_CONTACTS_ENABLED, value).apply() }

    // Per-app launch counter used to rank search results by frequency of use.
    fun getUsageCount(key: String): Int = prefs.getInt("USAGE_$key", 0)

    fun incrementUsage(key: String) =
        prefs.edit { putInt("USAGE_$key", getUsageCount(key) + 1).apply() }

    fun getAppName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_NAME_1, "").toString()
            2 -> prefs.getString(APP_NAME_2, "").toString()
            3 -> prefs.getString(APP_NAME_3, "").toString()
            4 -> prefs.getString(APP_NAME_4, "").toString()
            5 -> prefs.getString(APP_NAME_5, "").toString()
            6 -> prefs.getString(APP_NAME_6, "").toString()
            7 -> prefs.getString(APP_NAME_7, "").toString()
            8 -> prefs.getString(APP_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppPackage(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_PACKAGE_1, "").toString()
            2 -> prefs.getString(APP_PACKAGE_2, "").toString()
            3 -> prefs.getString(APP_PACKAGE_3, "").toString()
            4 -> prefs.getString(APP_PACKAGE_4, "").toString()
            5 -> prefs.getString(APP_PACKAGE_5, "").toString()
            6 -> prefs.getString(APP_PACKAGE_6, "").toString()
            7 -> prefs.getString(APP_PACKAGE_7, "").toString()
            8 -> prefs.getString(APP_PACKAGE_8, "").toString()
            else -> ""
        }
    }

    fun getAppActivityClassName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
            2 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
            3 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
            4 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
            5 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
            6 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
            7 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
            8 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppUser(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_USER_1, "").toString()
            2 -> prefs.getString(APP_USER_2, "").toString()
            3 -> prefs.getString(APP_USER_3, "").toString()
            4 -> prefs.getString(APP_USER_4, "").toString()
            5 -> prefs.getString(APP_USER_5, "").toString()
            6 -> prefs.getString(APP_USER_6, "").toString()
            7 -> prefs.getString(APP_USER_7, "").toString()
            8 -> prefs.getString(APP_USER_8, "").toString()
            else -> ""
        }
    }

    fun getShortcutId(location: Int): String {
        return when (location) {
            1 -> shortcutId1
            2 -> shortcutId2
            3 -> shortcutId3
            4 -> shortcutId4
            5 -> shortcutId5
            6 -> shortcutId6
            7 -> shortcutId7
            8 -> shortcutId8
            else -> ""
        }
    }

    fun getIsShortcut(location: Int): Boolean {
        return when (location) {
            1 -> isShortcut1
            2 -> isShortcut2
            3 -> isShortcut3
            4 -> isShortcut4
            5 -> isShortcut5
            6 -> isShortcut6
            7 -> isShortcut7
            8 -> isShortcut8
            else -> false
        }
    }

    fun setAppActivityClassName(location: Int, activityClassName: String) {
        when (location) {
            1 -> appActivityClassName1 = activityClassName
            2 -> appActivityClassName2 = activityClassName
            3 -> appActivityClassName3 = activityClassName
            4 -> appActivityClassName4 = activityClassName
            5 -> appActivityClassName5 = activityClassName
            6 -> appActivityClassName6 = activityClassName
            7 -> appActivityClassName7 = activityClassName
            8 -> appActivityClassName8 = activityClassName
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..8) {
            if (getAppPackage(i) == packageName) setAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    // ---- Folders (app groups) ----

    var folders: MutableList<Folder>
        get() = Folder.listFromJson(prefs.getString(FOLDERS, ""))
        set(value) = prefs.edit { putString(FOLDERS, Folder.listToJson(value)).apply() }

    fun getFolder(id: String): Folder? = folders.firstOrNull { it.id == id }

    // Inserts the folder or replaces the existing one with the same id.
    fun upsertFolder(folder: Folder) {
        val list = folders
        val idx = list.indexOfFirst { it.id == folder.id }
        if (idx >= 0) list[idx] = folder else list.add(folder)
        folders = list
    }

    fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }.toMutableList()
        // Free any home slot that pointed at the deleted folder.
        for (i in 1..8) if (getIsFolder(i) && getFolderIdAt(i) == id) clearHomeSlot(i)
    }

    // Toggles an app's membership in a folder and persists the change.
    fun toggleAppInFolder(folderId: String, appKey: String) {
        val list = folders
        val folder = list.firstOrNull { it.id == folderId } ?: return
        if (!folder.apps.remove(appKey)) folder.apps.add(appKey)
        folders = list
    }

    // ---- Home-slot folders ----
    // A home slot can hold a folder instead of an app/shortcut. We reuse the slot's
    // name field for the display label and flag it with IS_FOLDER_/FOLDER_ID_.

    fun getIsFolder(location: Int): Boolean = prefs.getBoolean("IS_FOLDER_$location", false)

    fun getFolderIdAt(location: Int): String = prefs.getString("FOLDER_ID_$location", "").toString()

    fun setFolderAt(location: Int, isFolder: Boolean, folderId: String) = prefs.edit {
        putBoolean("IS_FOLDER_$location", isFolder)
        putString("FOLDER_ID_$location", folderId)
    }

    fun assignFolderToHome(location: Int, folder: Folder) {
        setAppName(location, folder.name)
        setAppPackage(location, "")
        setAppUser(location, android.os.Process.myUserHandle().toString())
        setAppActivityClassName(location, "")
        setIsShortcut(location, false)
        setShortcutId(location, "")
        setFolderAt(location, true, folder.id)
    }

    fun clearHomeSlot(location: Int) {
        setAppName(location, "")
        setAppPackage(location, "")
        setFolderAt(location, false, "")
    }

    fun reduceHomeApps(oldNum: Int, newNum: Int) {
        if (newNum >= oldNum) return
        var currentNum = oldNum
        val diff = oldNum - newNum
        for (step in 1..diff) {
            var emptyIndex = -1
            for (i in 1..currentNum) {
                if (getAppName(i).isBlank() && !getIsFolder(i)) {
                    emptyIndex = i
                    break
                }
            }
            if (emptyIndex != -1) {
                for (i in emptyIndex until currentNum) {
                    val data = readHomeSlot(i + 1)
                    writeHomeSlot(i, data)
                }
                writeHomeSlot(currentNum, HomeSlotData("", "", "", "", false, "", false, ""))
            }
            currentNum--
        }
    }

    // ---- Generic per-slot setters (the getters already exist as getAppName(i) etc.) ----

    fun setAppName(location: Int, value: String) {
        when (location) {
            1 -> appName1 = value
            2 -> appName2 = value
            3 -> appName3 = value
            4 -> appName4 = value
            5 -> appName5 = value
            6 -> appName6 = value
            7 -> appName7 = value
            8 -> appName8 = value
        }
    }

    fun setAppPackage(location: Int, value: String) {
        when (location) {
            1 -> appPackage1 = value
            2 -> appPackage2 = value
            3 -> appPackage3 = value
            4 -> appPackage4 = value
            5 -> appPackage5 = value
            6 -> appPackage6 = value
            7 -> appPackage7 = value
            8 -> appPackage8 = value
        }
    }

    fun setAppUser(location: Int, value: String) {
        when (location) {
            1 -> appUser1 = value
            2 -> appUser2 = value
            3 -> appUser3 = value
            4 -> appUser4 = value
            5 -> appUser5 = value
            6 -> appUser6 = value
            7 -> appUser7 = value
            8 -> appUser8 = value
        }
    }

    fun setIsShortcut(location: Int, value: Boolean) {
        when (location) {
            1 -> isShortcut1 = value
            2 -> isShortcut2 = value
            3 -> isShortcut3 = value
            4 -> isShortcut4 = value
            5 -> isShortcut5 = value
            6 -> isShortcut6 = value
            7 -> isShortcut7 = value
            8 -> isShortcut8 = value
        }
    }

    fun setShortcutId(location: Int, value: String) {
        when (location) {
            1 -> shortcutId1 = value
            2 -> shortcutId2 = value
            3 -> shortcutId3 = value
            4 -> shortcutId4 = value
            5 -> shortcutId5 = value
            6 -> shortcutId6 = value
            7 -> shortcutId7 = value
            8 -> shortcutId8 = value
        }
    }

    // ---- Reorder support ----
    // Reads/writes a whole home slot (app, shortcut or folder) atomically. Used to
    // permute slots when the user reorders the home screen, without losing any field.

    fun readHomeSlot(location: Int): HomeSlotData = HomeSlotData(
        name = getAppName(location),
        pkg = getAppPackage(location),
        cls = getAppActivityClassName(location),
        user = getAppUser(location),
        isShortcut = getIsShortcut(location),
        shortcutId = getShortcutId(location),
        isFolder = getIsFolder(location),
        folderId = getFolderIdAt(location),
    )

    fun writeHomeSlot(location: Int, data: HomeSlotData) {
        setAppName(location, data.name)
        setAppPackage(location, data.pkg)
        setAppActivityClassName(location, data.cls)
        setAppUser(location, data.user)
        setIsShortcut(location, data.isShortcut)
        setShortcutId(location, data.shortcutId)
        setFolderAt(location, data.isFolder, data.folderId)
    }

    fun readIconSlot(slot: Int): IconSlotData = IconSlotData(
        iconIndex = getShortcutIconIndex(slot),
        pkg = getShortcutTargetPackage(slot),
        cls = getShortcutTargetClassName(slot),
        user = getShortcutTargetUser(slot),
        label = getShortcutTargetLabel(slot),
    )

    fun writeIconSlot(slot: Int, data: IconSlotData) {
        setShortcutIconIndex(slot, data.iconIndex)
        if (data.pkg.isEmpty()) clearShortcutTarget(slot)
        else setShortcutTarget(slot, data.pkg, data.cls, data.user, data.label)
    }
}

/** Snapshot of everything stored for a single home slot (1..8). */
data class HomeSlotData(
    val name: String,
    val pkg: String,
    val cls: String,
    val user: String,
    val isShortcut: Boolean,
    val shortcutId: String,
    val isFolder: Boolean,
    val folderId: String,
)

/** Snapshot of everything stored for a single shortcut-icon slot (0..7). */
data class IconSlotData(
    val iconIndex: Int,
    val pkg: String,
    val cls: String,
    val user: String,
    val label: String,
)
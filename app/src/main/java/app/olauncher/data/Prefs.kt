package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed facade over the app's single [SharedPreferences] file.
 *
 * Every scalar setting is a one-line delegated property (see the factories at the
 * bottom of the class); per-slot data (the 8 home apps, the shortcut-icon column,
 * folders, the soft-limit state) is exposed through small indexed accessors whose
 * keys are built as "PREFIX_$index" — identical to the historical keys, so existing
 * installs and backups keep working unchanged.
 */
class Prefs(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_FILENAME = "com.pierspad.betterlauncher"
        const val HOME_SLOTS = 8

        // Keys that need special handling during import (see importFromJson).
        const val LOCK_MODE = "LOCK_MODE"
        const val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
        const val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
        const val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"
        const val CUSTOM_FONT_EXPORT_KEY = "CUSTOM_FONT_BASE64"
        const val CUSTOM_FONT_FILENAME = "custom_font"
    }

    // ---- First-run flags ----
    var firstOpen by bool("FIRST_OPEN", true)
    var firstOpenTime by long("FIRST_OPEN_TIME")
    var firstSettingsOpen by bool("FIRST_SETTINGS_OPEN", true)
    var firstHide by bool("FIRST_HIDE", true)

    // ---- General behaviour ----
    var lockModeOn by bool(LOCK_MODE)
    var autoShowKeyboard by bool("AUTO_SHOW_KEYBOARD", true)
    var keyboardMessageShown by bool("KEYBOARD_MESSAGE")
    var showStatusBar by bool("STATUS_BAR")
    var swipeLeftEnabled by bool("SWIPE_LEFT_ENABLED", true)
    var swipeRightEnabled by bool("SWIPE_RIGHT_ENABLED", true)
    var swipeDownAction by int("SWIPE_DOWN_ACTION", Constants.SwipeDownAction.NOTIFICATIONS)
    var hideSetDefaultLauncher by bool("HIDE_SET_DEFAULT_LAUNCHER")
    var toShowHintCounter by int("SHOW_HINT_COUNTER", 1)
    var aboutClicked by bool("ABOUT_CLICKED")
    var rateClicked by bool("RATE_CLICKED")
    var screenTimeLastUpdated by long("SCREEN_TIME_LAST_UPDATED")
    var launcherRestartTimestamp by long("LAUNCHER_RECREATE_TIMESTAMP")

    // Set right before a settings-initiated recreate (theme/font/text size) so the
    // launcher reopens the Settings screen instead of dropping back to the home.
    var reopenSettingsAfterRestart by bool("REOPEN_SETTINGS")
    var settingsScrollY by int("SETTINGS_SCROLL_Y")

    // ---- Look & feel ----
    var appTheme by int("APP_THEME", AppCompatDelegate.MODE_NIGHT_YES)
    var textSizeScale by float("TEXT_SIZE_SCALE", 1.0f)
    var dateTimeVisibility by int("DATE_TIME_VISIBILITY", Constants.DateTime.ON)

    // Selected font. Empty string = system default. A value starting with "custom:"
    // means a user-imported font file whose absolute path is in [customFontPath];
    // any other value is a built-in Android font family name (e.g. "serif").
    var fontFamily by string("FONT_FAMILY")
    var customFontPath by string("CUSTOM_FONT_PATH")

    var showHomeIcons by bool("SHOW_HOME_ICONS")
    var showDrawerIcons by bool("SHOW_DRAWER_ICONS")
    var showHomeOnlyIcons by bool("SHOW_HOME_ONLY_ICONS")
    var useColorfulIcons by bool("USE_COLORFUL_ICONS")

    // Scrim intensity (0f..1f) for the home screen and the app drawer respectively.
    // A value of 0 means no scrim.
    var opacityHome by float("OPACITY_HOME", 0.05f)
    var opacityDrawer by float("OPACITY_DRAWER", 0.4f)

    // ---- Alignment ----
    var homeAlignment by int("HOME_ALIGNMENT", Gravity.START)
    var homeBottomAlignment by bool("HOME_BOTTOM_ALIGNMENT")
    var clockAlignment by int("CLOCK_ALIGNMENT", Gravity.START)
    var shortcutIconsAlignment by int("SHORTCUT_ICONS_ALIGNMENT", Gravity.END)
    var screenTimeAlignment by int("SCREEN_TIME_ALIGNMENT", Gravity.END)
    var homeVerticalAlignment by int("HOME_VERTICAL_ALIGNMENT", Gravity.BOTTOM)
    var appLabelAlignment by int("APP_LABEL_ALIGNMENT", Gravity.START)

    // ---- Home screen ----
    var homeAppsNum by int("HOME_APPS_NUM", 6)

    // Number of home-screen shortcut icons (right column) shown, 0..SHORTCUT_COUNT.
    // 0 means the icons column is hidden entirely.
    var homeShortcutIconsNum by int("HOME_SHORTCUT_ICONS_NUM", 6)

    // The feature has no dedicated on/off switch: it is simply "on" whenever the user
    // keeps at least one icon slot (slider 0..N).
    val shortcutIconsEnabled: Boolean get() = homeShortcutIconsNum > 0

    // ---- Hidden / locked / limited apps (keys are "package|user") ----
    var hiddenApps by stringSet("HIDDEN_APPS")
    var hiddenAppsUpdated by bool("HIDDEN_APPS_UPDATED")

    // Apps protected behind biometric / device-credential.
    var lockedApps by stringSet("LOCKED_APPS")

    fun isAppLocked(key: String): Boolean = key in lockedApps

    // ---- Soft app limit ("use it less"): progressive cooldown, no password ----
    var limitedApps by stringSet("LIMITED_APPS")

    fun isAppLimited(key: String): Boolean = key in limitedApps

    // Master switch. When off, limited apps open freely (selection is preserved).
    var appLimitEnabled by bool("APP_LIMIT_ENABLED", true)

    var lastOpenedLimitedApp by string("LAST_OPENED_LIMITED_APP")
    var lastDecayDay by long("LAST_DECAY_DAY")

    // Per-app cooldown state, stored under per-key suffixes to avoid a serialized blob.
    fun limitLevel(key: String): Int = prefs.getInt("LIMIT_LEVEL_$key", 0)
    fun setLimitLevel(key: String, value: Int) = prefs.edit { putInt("LIMIT_LEVEL_$key", value) }

    fun limitUntil(key: String): Long = prefs.getLong("LIMIT_UNTIL_$key", 0L)
    fun setLimitUntil(key: String, value: Long) = prefs.edit { putLong("LIMIT_UNTIL_$key", value) }

    fun limitRetryCount(key: String): Int = prefs.getInt("LIMIT_RETRY_COUNT_$key", 0)
    fun setLimitRetryCount(key: String, value: Int) = prefs.edit { putInt("LIMIT_RETRY_COUNT_$key", value) }

    fun limitLastOpenDay(key: String): Long = prefs.getLong("LIMIT_LAST_OPEN_DAY_$key", 0L)
    fun setLimitLastOpenDay(key: String, value: Long) = prefs.edit { putLong("LIMIT_LAST_OPEN_DAY_$key", value) }

    fun clearLimitState(key: String) = prefs.edit {
        remove("LIMIT_LAST_OPEN_$key")
        remove("LIMIT_LEVEL_$key")
        remove("LIMIT_UNTIL_$key")
        remove("LIMIT_RETRY_COUNT_$key")
        remove("LIMIT_LAST_OPEN_DAY_$key")
    }

    // ---- Drawer search ----
    var searchSettingsEnabled by bool("SEARCH_SETTINGS_ENABLED", true)
    var searchContactsEnabled by bool("SEARCH_CONTACTS_ENABLED")

    // Per-app launch counter used to rank search results by frequency of use.
    fun getUsageCount(key: String): Int = prefs.getInt("USAGE_$key", 0)
    fun incrementUsage(key: String) = prefs.edit { putInt("USAGE_$key", getUsageCount(key) + 1) }

    // ---- App drawer cache ----
    // A JSON snapshot of the last computed regular-app list, used to show the drawer
    // instantly on cold start while a fresh list is loaded from PackageManager.
    var appListCache by string("APP_LIST_CACHE")

    // ---- Swipe apps ----
    var appNameSwipeLeft by string("APP_NAME_SWIPE_LEFT", "Camera")
    var appNameSwipeRight by string("APP_NAME_SWIPE_RIGHT", "Phone")
    var appPackageSwipeLeft by string("APP_PACKAGE_SWIPE_LEFT")
    var appPackageSwipeRight by string("APP_PACKAGE_SWIPE_RIGHT")
    var appActivityClassNameSwipeLeft by nullableString("APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT")
    var appActivityClassNameRight by nullableString("APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT")
    var appUserSwipeLeft by string("APP_USER_SWIPE_LEFT")
    var appUserSwipeRight by string("APP_USER_SWIPE_RIGHT")
    var shortcutIdSwipeLeft by string("SHORTCUT_ID_SWIPE_LEFT")
    var isShortcutSwipeLeft by bool("IS_SHORTCUT_SWIPE_LEFT")
    var shortcutIdSwipeRight by string("SHORTCUT_ID_SWIPE_RIGHT")
    var isShortcutSwipeRight by bool("IS_SHORTCUT_SWIPE_RIGHT")

    // ---- Clock / calendar / screen-time target apps ----
    var clockAppPackage by string("CLOCK_APP_PACKAGE")
    var clockAppUser by string("CLOCK_APP_USER")
    var clockAppClassName by nullableString("CLOCK_APP_CLASS_NAME")
    var calendarAppPackage by string("CALENDAR_APP_PACKAGE")
    var calendarAppUser by string("CALENDAR_APP_USER")
    var calendarAppClassName by nullableString("CALENDAR_APP_CLASS_NAME")
    var screenTimeAppPackage by string(SCREEN_TIME_APP_PACKAGE)
    var screenTimeAppUser by string(SCREEN_TIME_APP_USER)
    var screenTimeAppClassName by nullableString(SCREEN_TIME_APP_CLASS_NAME)

    // ---- Home-screen widget (single) ----
    var widgetId by int("WIDGET_ID", -1)
    var pendingWidgetId by int("PENDING_WIDGET_ID", -1)
    var widgetEnabled by bool("WIDGET_ENABLED")
    var widgetProviderPackage by string("WIDGET_PROVIDER_PACKAGE")
    var widgetProviderClass by string("WIDGET_PROVIDER_CLASS")

    // ---- Home slots (1..8): app, pinned shortcut or folder ----
    // Historical key scheme: APP_NAME_1..8, APP_PACKAGE_1..8, ...

    fun getAppName(location: Int): String = slotString("APP_NAME", location)
    fun getAppPackage(location: Int): String = slotString("APP_PACKAGE", location)
    fun getAppActivityClassName(location: Int): String = slotString("APP_ACTIVITY_CLASS_NAME", location)
    fun getAppUser(location: Int): String = slotString("APP_USER", location)
    fun getShortcutId(location: Int): String = slotString("SHORTCUT_ID", location)
    fun getIsShortcut(location: Int): Boolean =
        location in 1..HOME_SLOTS && prefs.getBoolean("IS_SHORTCUT_$location", false)

    fun setAppName(location: Int, value: String) = setSlotString("APP_NAME", location, value)
    fun setAppPackage(location: Int, value: String) = setSlotString("APP_PACKAGE", location, value)
    fun setAppActivityClassName(location: Int, value: String?) =
        setSlotString("APP_ACTIVITY_CLASS_NAME", location, value.orEmpty())

    fun setAppUser(location: Int, value: String) = setSlotString("APP_USER", location, value)
    fun setShortcutId(location: Int, value: String) = setSlotString("SHORTCUT_ID", location, value)
    fun setIsShortcut(location: Int, value: Boolean) {
        if (location in 1..HOME_SLOTS) prefs.edit { putBoolean("IS_SHORTCUT_$location", value) }
    }

    private fun slotString(prefix: String, location: Int): String =
        if (location in 1..HOME_SLOTS) prefs.getString("${prefix}_$location", "").orEmpty() else ""

    private fun setSlotString(prefix: String, location: Int, value: String) {
        if (location in 1..HOME_SLOTS) prefs.edit { putString("${prefix}_$location", value) }
    }

    /** Writes every app/shortcut field of a home slot in a single atomic edit. */
    fun setHomeApp(
        location: Int,
        name: String,
        pkg: String,
        user: String,
        activityClassName: String?,
        isShortcut: Boolean,
        shortcutId: String,
    ) {
        if (location !in 1..HOME_SLOTS) return
        prefs.edit {
            putString("APP_NAME_$location", name)
            putString("APP_PACKAGE_$location", pkg)
            putString("APP_USER_$location", user)
            putString("APP_ACTIVITY_CLASS_NAME_$location", activityClassName.orEmpty())
            putBoolean("IS_SHORTCUT_$location", isShortcut)
            putString("SHORTCUT_ID_$location", shortcutId)
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..HOME_SLOTS) {
            if (getAppPackage(i) == packageName) setAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").orEmpty()
    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    // ---- Shortcut-icon slots (0..7, right column) ----

    fun getShortcutIconIndex(slot: Int): Int =
        prefs.getInt("SHORTCUT_ICON_INDEX_$slot", Constants.SHORTCUT_DEFAULT_ICONS[slot])

    fun setShortcutIconIndex(slot: Int, index: Int) = prefs.edit { putInt("SHORTCUT_ICON_INDEX_$slot", index) }

    fun getShortcutTargetPackage(slot: Int): String = prefs.getString("SHORTCUT_PKG_$slot", "").orEmpty()
    fun getShortcutTargetClassName(slot: Int): String = prefs.getString("SHORTCUT_CLS_$slot", "").orEmpty()
    fun getShortcutTargetUser(slot: Int): String = prefs.getString("SHORTCUT_USR_$slot", "").orEmpty()
    fun getShortcutTargetLabel(slot: Int): String = prefs.getString("SHORTCUT_LBL_$slot", "").orEmpty()

    fun setShortcutTarget(slot: Int, packageName: String, className: String?, user: String, label: String) =
        prefs.edit {
            putString("SHORTCUT_PKG_$slot", packageName)
            putString("SHORTCUT_CLS_$slot", className.orEmpty())
            putString("SHORTCUT_USR_$slot", user)
            putString("SHORTCUT_LBL_$slot", label)
        }

    fun clearShortcutTarget(slot: Int) = prefs.edit {
        remove("SHORTCUT_PKG_$slot")
        remove("SHORTCUT_CLS_$slot")
        remove("SHORTCUT_USR_$slot")
        remove("SHORTCUT_LBL_$slot")
    }

    // ---- Folders (app groups) ----

    var folders: MutableList<Folder>
        get() = Folder.listFromJson(prefs.getString("FOLDERS", ""))
        set(value) = prefs.edit { putString("FOLDERS", Folder.listToJson(value)) }

    fun getFolder(id: String): Folder? = folders.firstOrNull { it.id == id }

    // Inserts the folder or replaces the existing one with the same id.
    fun upsertFolder(folder: Folder) {
        folders = folders.also { list ->
            val idx = list.indexOfFirst { it.id == folder.id }
            if (idx >= 0) list[idx] = folder else list.add(folder)
        }
    }

    fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }.toMutableList()
        // Free any home slot that pointed at the deleted folder.
        for (i in 1..HOME_SLOTS) if (getIsFolder(i) && getFolderIdAt(i) == id) clearHomeSlot(i)
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
    fun getFolderIdAt(location: Int): String = prefs.getString("FOLDER_ID_$location", "").orEmpty()

    fun setFolderAt(location: Int, isFolder: Boolean, folderId: String) = prefs.edit {
        putBoolean("IS_FOLDER_$location", isFolder)
        putString("FOLDER_ID_$location", folderId)
    }

    fun assignFolderToHome(location: Int, folder: Folder) {
        setHomeApp(
            location,
            name = folder.name,
            pkg = "",
            user = android.os.Process.myUserHandle().toString(),
            activityClassName = "",
            isShortcut = false,
            shortcutId = "",
        )
        setFolderAt(location, true, folder.id)
    }

    fun clearHomeSlot(location: Int) {
        setAppName(location, "")
        setAppPackage(location, "")
        setFolderAt(location, false, "")
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
        setHomeApp(location, data.name, data.pkg, data.user, data.cls, data.isShortcut, data.shortcutId)
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

    fun reduceHomeApps(oldNum: Int, newNum: Int) {
        if (newNum >= oldNum) return
        var currentNum = oldNum
        repeat(oldNum - newNum) {
            val emptyIndex = (1..currentNum).firstOrNull { getAppName(it).isBlank() && !getIsFolder(it) }
            if (emptyIndex != null) {
                for (i in emptyIndex until currentNum) writeHomeSlot(i, readHomeSlot(i + 1))
                writeHomeSlot(currentNum, HomeSlotData("", "", "", "", false, "", false, ""))
            }
            currentNum--
        }
    }

    // ---- Backup / Restore ----

    /** Serializes every stored preference (with its type) to a JSON string. */
    fun exportToJson(): String {
        val root = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> {
                    if (value.isNaN() || value.isInfinite()) continue
                    entry.put("t", "f").put("v", value.toDouble())
                }

                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> {
                    val arr = JSONArray()
                    value.filterNotNull().forEach { arr.put(it.toString()) }
                    entry.put("t", "set").put("v", arr)
                }

                else -> continue // unknown type: skip rather than corrupt the backup
            }
            root.put(key, entry)
        }

        // Export the custom font file as Base64 if it exists in private storage.
        runCatching {
            val fontFile = File(context.filesDir, CUSTOM_FONT_FILENAME)
            if (fontFile.exists()) {
                val base64 = Base64.encodeToString(fontFile.readBytes(), Base64.NO_WRAP)
                root.put(CUSTOM_FONT_EXPORT_KEY, JSONObject().put("t", "s").put("v", base64))
            }
        }

        return root.toString(2)
    }

    /**
     * Replaces all current preferences with the ones from a previously exported JSON.
     * Lock mode and the screen-time app binding are intentionally reset: both depend
     * on device-specific state (accessibility service, usage-access permission).
     * Returns true on success.
     */
    fun importFromJson(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        prefs.edit {
            clear()
            for (key in root.keys()) {
                when (key) {
                    CUSTOM_FONT_EXPORT_KEY -> continue // handled below, not a preference
                    LOCK_MODE -> putBoolean(key, false)
                    SCREEN_TIME_APP_PACKAGE, SCREEN_TIME_APP_USER, SCREEN_TIME_APP_CLASS_NAME ->
                        putString(key, "")

                    else -> runCatching {
                        val entry = root.getJSONObject(key)
                        when (entry.getString("t")) {
                            "b" -> putBoolean(key, entry.getBoolean("v"))
                            "i" -> putInt(key, entry.getInt("v"))
                            "l" -> putLong(key, entry.getLong("v"))
                            "f" -> putFloat(key, entry.getDouble("v").toFloat())
                            "s" -> putString(key, entry.getString("v"))
                            "set" -> {
                                val arr = entry.getJSONArray("v")
                                putStringSet(key, (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) })
                            }
                        }
                    }
                }
            }
        }

        // Restore (or remove) the custom font file shipped inside the backup.
        val fontFile = File(context.filesDir, CUSTOM_FONT_FILENAME)
        val fontBase64 = root.optJSONObject(CUSTOM_FONT_EXPORT_KEY)?.optString("v").orEmpty()
        runCatching {
            if (fontBase64.isNotEmpty()) fontFile.writeBytes(Base64.decode(fontBase64, Base64.NO_WRAP))
            else fontFile.delete()
        }

        true
    }.getOrDefault(false)

    fun resetToDefaults() = prefs.edit { clear() }

    // ---- Property-delegate factories ----
    // Each settings property above is declared as `var name by type(KEY, default)`.
    // Getters read straight from SharedPreferences (which caches in memory);
    // setters persist asynchronously via apply().

    private fun bool(key: String, default: Boolean = false) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit { putBoolean(key, value) }
    }

    private fun int(key: String, default: Int = 0) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            prefs.edit { putInt(key, value) }
    }

    private fun long(key: String, default: Long = 0L) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getLong(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) =
            prefs.edit { putLong(key, value) }
    }

    private fun float(key: String, default: Float = 0f) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) =
            prefs.edit { putFloat(key, value) }
    }

    private fun string(key: String, default: String = "") = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            prefs.edit { putString(key, value) }
    }

    // Historically these getters never returned null (missing = ""), only the setters
    // accepted null; we keep those exact semantics for backward compatibility.
    private fun nullableString(key: String) = object : ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String? = prefs.getString(key, "").orEmpty()
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) =
            prefs.edit { putString(key, value.orEmpty()) }
    }

    // Returns a defensive copy: mutating the set returned by SharedPreferences
    // directly is undefined behaviour per the Android API contract.
    private fun stringSet(key: String) = object : ReadWriteProperty<Any?, MutableSet<String>> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): MutableSet<String> =
            prefs.getStringSet(key, null)?.toMutableSet() ?: mutableSetOf()

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: MutableSet<String>) =
            prefs.edit { putStringSet(key, value) }
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

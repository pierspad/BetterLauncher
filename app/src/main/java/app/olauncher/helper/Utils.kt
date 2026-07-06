package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import android.app.Activity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import android.view.Gravity
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.pow
import kotlin.math.sqrt

fun Context.showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return

    val activity = findActivity()
    if (activity != null) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        if (rootView != null) {
            val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
            snackbar.duration = 1500
            val snackbarView = snackbar.view

            val density = resources.displayMetrics.density
            val background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 16f * density
            }
            snackbarView.background = background

            val params = snackbarView.layoutParams as? android.widget.FrameLayout.LayoutParams
            if (params != null) {
                params.setMargins((24 * density).toInt(), (64 * density).toInt(), (24 * density).toInt(), 0)
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                snackbarView.layoutParams = params
            }

            val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            if (textView != null) {
                textView.setTextColor(android.graphics.Color.BLACK)
                textView.textSize = 14f
                val logoDrawable = ContextCompat.getDrawable(this, R.drawable.logo)
                if (logoDrawable != null) {
                    val size = (20 * density).toInt()
                    logoDrawable.setBounds(0, 0, size, size)
                    textView.setCompoundDrawablesRelative(logoDrawable, null, null, null)
                    textView.compoundDrawablePadding = (12 * density).toInt()
                    textView.gravity = Gravity.CENTER_VERTICAL
                }
            }

            val actionView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
            actionView?.visibility = View.GONE

            snackbar.show()
            return
        }
    }

    // Fallback to standard Toast
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(stringResource: Int, duration: Int = Toast.LENGTH_SHORT) {
    showToast(getString(stringResource), duration)
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

suspend fun getAppsList(
    context: Context,
    prefs: Prefs,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!prefs.hiddenAppsUpdated) upgradeHiddenApps(prefs)
            val hiddenApps = prefs.hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                if (isPrivateSpaceProfile(context, profile)) continue
                for (app in launcherApps.getActivityList(null, profile)) {
                    val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                        .ifBlank { app.label.toString() }
                    val appModel = AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = (System.currentTimeMillis() - app.firstInstallTime) < Constants.ONE_HOUR_IN_MILLIS,
                        user = profile
                    )

                    // if the current app is not OLauncher
                    if (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID) {
                        // is this a hidden app?
                        if (hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())) {
                            if (includeHiddenApps) {
                                appList.add(appModel)
                            }
                        } else {
                            // this is a regular app
                            if (includeRegularApps) {
                                appList.add(appModel)
                            }
                        }
                    }
                }
            }

            // Add shortcuts if we're getting regular apps
            if (includeRegularApps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pinned = try {
                    getPinnedShortcuts(context, prefs, collator)
                } catch (e: Exception) {
                    emptyList()
                }
                appList.addAll(pinned)
            }

            appList.sortWith(compareBy(collator) { it.appLabel })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
}

// ---- App drawer cache ----
// Serializes the regular apps (AppModel.App only) of a drawer list to a compact JSON
// string. Pinned shortcuts are intentionally skipped: they are cheap and dynamic, so
// they are always recomputed.
fun appListToCacheJson(list: List<AppModel>): String {
    val arr = org.json.JSONArray()
    for (app in list) {
        if (app is AppModel.App) {
            arr.put(
                org.json.JSONObject()
                    .put("l", app.appLabel)
                    .put("p", app.appPackage)
                    .put("c", app.activityClassName ?: "")
                    .put("u", app.user.toString())
            )
        }
    }
    return arr.toString()
}

// Rebuilds a drawer list from a cached JSON snapshot. UserHandles are resolved once
// through a map to avoid a per-app lookup. Returns an empty list on any problem.
fun appListFromCacheJson(context: Context, json: String): MutableList<AppModel> {
    val result = mutableListOf<AppModel>()
    if (json.isBlank()) return result
    try {
        val collator = Collator.getInstance()
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val handleMap = userManager.userProfiles.associateBy { it.toString() }
        val myHandle = android.os.Process.myUserHandle()
        val arr = org.json.JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val label = o.optString("l")
            result.add(
                AppModel.App(
                    appLabel = label,
                    key = collator.getCollationKey(label),
                    appPackage = o.optString("p"),
                    activityClassName = o.optString("c").ifBlank { null },
                    isNew = false,
                    user = handleMap[o.optString("u")] ?: myHandle,
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

@RequiresApi(Build.VERSION_CODES.O)
private suspend fun getPinnedShortcuts(
    context: Context,
    prefs: Prefs,
    collator: Collator,
): List<AppModel.PinnedShortcut> =
    withContext(Dispatchers.IO) {
        val pinnedShortcuts = mutableListOf<AppModel.PinnedShortcut>()
        val shortcuts = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (shortcuts?.hasShortcutHostPermission() == true) {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            shortcuts.profiles.forEach { profile ->
                if (isPrivateSpaceProfile(context, profile)) return@forEach
                try {
                    shortcuts.getShortcuts(query, profile)?.forEach { shortcut ->
                        if (shortcut.isPinned && pinnedShortcuts.none { it.shortcutId == shortcut.id }) {
                            val label = prefs.getAppRenameLabel(shortcut.id)
                                .takeIf { it.isNotBlank() }
                                ?: shortcut.shortLabel?.toString()
                                ?: shortcut.longLabel?.toString().orEmpty()
                            pinnedShortcuts.add(
                                AppModel.PinnedShortcut(
                                    appLabel = label,
                                    key = collator.getCollationKey(label),
                                    appPackage = shortcut.`package`,
                                    shortcutId = shortcut.id,
                                    isNew = false,
                                    user = profile
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        pinnedShortcuts
    }

// This is to ensure backward compatibility with older app versions
// which did not support multiple user profiles
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    if (activityInfo.isNotEmpty()) return true
    return false
}

fun isPrivateSpaceProfile(context: Context, userHandle: UserHandle): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
    return try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getLauncherUserInfo(userHandle)?.userType == "android.os.usertype.profile.PRIVATE"
    } catch (e: Exception) {
        false
    }
}

fun isPrivateSpaceLocked(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.isQuietModeEnabled(userHandle)
    } catch (e: Exception) {
        true
    }
}

fun getPrivateSpaceUserHandle(context: Context): UserHandle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (profile in userManager.userProfiles) {
        if (isPrivateSpaceProfile(context, profile)) return profile
    }
    return null
}

suspend fun getPrivateSpaceApps(
    context: Context,
    prefs: Prefs,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        try {
            val privateSpaceHandle = getPrivateSpaceUserHandle(context) ?: return@withContext appList
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (app in launcherApps.getActivityList(null, privateSpaceHandle)) {
                if (app.applicationInfo.packageName == BuildConfig.APPLICATION_ID) continue
                val appLabelShown = prefs.getAppRenameLabel(app.applicationInfo.packageName)
                    .ifBlank { app.label.toString() }
                appList.add(
                    AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = false,
                        user = privateSpaceHandle
                    )
                )
            }
            appList.sortWith(compareBy(collator) { it.appLabel })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appList
    }
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun isOlauncherDefault(context: Context): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(context)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else "android"
}

fun setPlainWallpaperByTheme(context: Context, appTheme: Int) {
    when (appTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(context, android.R.color.black)
        AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(context, android.R.color.white)
        else -> {
            if (context.isDarkThemeOn())
                setPlainWallpaper(context, android.R.color.black)
            else setPlainWallpaper(context, android.R.color.white)
        }
    }
}

fun setPlainWallpaper(context: Context, color: Int) {
    try {
        val bitmap = createBitmap(1000, 2000)
        bitmap.eraseColor(context.getColor(color))
        val manager = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
            manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
        } else
            manager.setBitmap(bitmap)
        bitmap.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getChangedAppTheme(context: Context, currentAppTheme: Int): Int {
    return when (currentAppTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            if (context.isDarkThemeOn())
                AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null)
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    else
        context.showToast(context.getString(R.string.unable_to_open_app_info))
}

fun openSearch(context: Context) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, "")
    context.startActivity(intent)
}

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    // Source: https://stackoverflow.com/a/51132142
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openDialerApp(context: Context) {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openCameraApp(context: Context) {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openBrowserApp(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://".toUri()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun openGalleryApp(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setType("image/*"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun openMessagingApp(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "sms:".toUri()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.d("TAG", e.toString())
    }
}

fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun isAccessServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (e: Exception) {
        0
    }
    if (enabled == 1) {
        val enabledServicesString: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServicesString?.contains(context.packageName + "/" + MyAccessibilityService::class.java.name) ?: false
    }
    return false
}

fun isTablet(context: Context): Boolean {
    val metrics = context.resources.displayMetrics
    val (widthPixels, heightPixels) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = windowManager.maximumWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        metrics.widthPixels to metrics.heightPixels
    }
    val widthInches = widthPixels / metrics.xdpi
    val heightInches = heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    return diagonalInches >= 7.0
}

fun Context.isDarkThemeOn(): Boolean {
    return resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

fun Context.copyToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clipData)
    showToast("")
}

fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    startActivity(intent)
}

fun Context.isSystemApp(packageName: String, user: UserHandle? = null): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val targetUser = user ?: android.os.Process.myUserHandle()
        val activityList = launcherApps.getActivityList(packageName, targetUser)
        if (activityList.isNotEmpty()) {
            val applicationInfo = activityList.first().applicationInfo
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        } else {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.uninstall(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun View.animateAlpha(alpha: Float = 1.0f) {
    this.animate().apply {
        interpolator = LinearInterpolator()
        duration = 200
        alpha(alpha)
        start()
    }
}

fun Context.shareApp() {
    val message = getString(R.string.are_you_using_your_phone_or_is_your_phone_using_you) +
            "\n" + Constants.URL_OLAUNCHER_PLAY_STORE
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null)
    startActivity(shareIntent)
}

fun Context.rateApp() {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Constants.URL_OLAUNCHER_PLAY_STORE.toUri()
    )
    var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    flags = flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
    intent.addFlags(flags)
    startActivity(intent)
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
fun Context.deletePinnedShortcut(packageName: String, shortcutIdToDelete: String, user: UserHandle) {
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // 1. Query for existing pinned shortcuts for the package
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(packageName)
        // Query only for pinned shortcuts
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }

    try {
        val pinnedShortcuts = launcherApps.getShortcuts(query, user)

        if (pinnedShortcuts != null) {
            // 2. Filter out the shortcut to be deleted
            val updatedPinnedIds = pinnedShortcuts
                .filter { it.id != shortcutIdToDelete }
                .map { it.id }

            // 3. Re-pin the remaining shortcuts
            // This replaces the existing set of pinned shortcuts for this package
            launcherApps.pinShortcuts(packageName, updatedPinnedIds, user)
        }
    } catch (e: SecurityException) {
        // Handle cases where the app doesn't have permission
        // (e.g., not the default launcher or active voice interaction service)
        Log.e("ShortcutHelper", "Permission denied to modify pinned shortcuts for $packageName", e)
    } catch (e: IllegalStateException) {
        // Handle cases where the user profile is locked or not running
        Log.e("ShortcutHelper", "User profile unavailable for modifying pinned shortcuts for $packageName", e)
    } catch (e: Exception) {
        // Handle other potential exceptions (like RemoteException wrapped)
        Log.e("ShortcutHelper", "Failed to modify pinned shortcuts for $packageName", e)
    }
}

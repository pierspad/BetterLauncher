package app.olauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.AppLimiter
import app.olauncher.helper.ContactsHelper
import app.olauncher.helper.SingleLiveEvent
import app.olauncher.helper.appListFromCacheJson
import app.olauncher.helper.appListToCacheJson
import app.olauncher.helper.formattedTimeSpent
import app.olauncher.helper.getAppsList
import app.olauncher.helper.getPrivateSpaceApps
import app.olauncher.helper.getPrivateSpaceUserHandle
import app.olauncher.helper.hasBeenMinutes
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.isPrivateSpaceLocked
import app.olauncher.helper.showToast
import app.olauncher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar


class MainViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface ToggleLimitResult {
        data class Success(val nowLimited: Boolean) : ToggleLimitResult
        object PreventedBanned : ToggleLimitResult
        data class PreventedCompulsiveness(val level: Int) : ToggleLimitResult
    }

    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()

    // Device contacts for the drawer search (loaded only when the category is enabled).
    val drawerContacts = MutableLiveData<List<AppModel>>()

    val privateSpaceApps = MutableLiveData<List<AppModel>?>()
    val privateSpaceLocked = MutableLiveData<Boolean>()
    val privateSpaceAvailable = MutableLiveData<Boolean>()

    // Suppress backToHomeScreen during Private Space lock/unlock auth
    var isPrivateSpaceToggling = false

    val showDialog = SingleLiveEvent<String>()
    val checkForMessages = SingleLiveEvent<Unit?>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()
    // Fired from Settings to ask the Home screen to enter drag-to-reorder mode.
    val enterReorderMode = SingleLiveEvent<Unit?>()

    // A locked app was selected: the Activity must authenticate before launching it.
    val launchAppWithAuth = SingleLiveEvent<AppModel>()

    // A soft-limited app is in cooldown: the Activity shows a countdown instead of launching.
    val cooldownBlocked = SingleLiveEvent<CooldownBlock>()

    data class CooldownBlock(
        val packageName: String,
        val user: UserHandle,
        val untilMillis: Long,
        val isSevere: Boolean,
        val penaltyMinutes: Int,
        val compulsivenessLevel: Int,
        val totalBanMinutes: Int
    )

    init {
        // Seed the drawer from the last persisted snapshot so it renders instantly on
        // cold start; getAppList() then refreshes it from PackageManager in the
        // background. Parsing a few hundred JSON rows here is negligible.
        val cached = appListFromCacheJson(appContext, prefs.appListCache)
        if (cached.isNotEmpty()) appList.value = cached
    }
    // Home button for recents feature disabled
    // val showRecentApps = SingleLiveEvent<Unit?>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        if (appModel is AppModel.PrivateSpaceHeader) return
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                when (appModel) {
                    is AppModel.PinnedShortcut -> requestLaunch(appModel)
                    is AppModel.App ->
                        requestLaunch(appModel)

                    else -> {}
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel is AppModel.App) {
                    requestLaunch(appModel)
                }
            }

            Constants.FLAG_LOCKED_APPS -> {
                if (appModel is AppModel.App) toggleAppLock(appModel)
            }

            Constants.FLAG_LIMITED_APPS -> {
                if (appModel is AppModel.App) {
                    when (val result = toggleAppLimit(appModel)) {
                        is ToggleLimitResult.PreventedBanned -> {
                            appContext.showToast(appContext.getString(R.string.cannot_remove_limit_banned))
                        }
                        is ToggleLimitResult.PreventedCompulsiveness -> {
                            appContext.showToast(appContext.getString(R.string.cannot_remove_limit_compulsiveness, result.level))
                        }
                        else -> {}
                    }
                }
            }

            Constants.FLAG_SET_HOME_APP_1 -> saveHomeApp(appModel, 1)
            Constants.FLAG_SET_HOME_APP_2 -> saveHomeApp(appModel, 2)
            Constants.FLAG_SET_HOME_APP_3 -> saveHomeApp(appModel, 3)
            Constants.FLAG_SET_HOME_APP_4 -> saveHomeApp(appModel, 4)
            Constants.FLAG_SET_HOME_APP_5 -> saveHomeApp(appModel, 5)
            Constants.FLAG_SET_HOME_APP_6 -> saveHomeApp(appModel, 6)
            Constants.FLAG_SET_HOME_APP_7 -> saveHomeApp(appModel, 7)
            Constants.FLAG_SET_HOME_APP_8 -> saveHomeApp(appModel, 8)

            Constants.FLAG_SET_SWIPE_LEFT_APP -> saveSwipeApp(appModel, isLeft = true)
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> saveSwipeApp(appModel, isLeft = false)
            Constants.FLAG_SET_CLOCK_APP -> saveClockApp(appModel)
            Constants.FLAG_SET_CALENDAR_APP -> saveCalendarApp(appModel)
            Constants.FLAG_SET_SCREEN_TIME_APP -> saveScreenTimeApp(appModel)

            Constants.FLAG_SET_SHORTCUT_ICON_1 -> saveShortcutIconApp(appModel, 0)
            Constants.FLAG_SET_SHORTCUT_ICON_2 -> saveShortcutIconApp(appModel, 1)
            Constants.FLAG_SET_SHORTCUT_ICON_3 -> saveShortcutIconApp(appModel, 2)
            Constants.FLAG_SET_SHORTCUT_ICON_4 -> saveShortcutIconApp(appModel, 3)
            Constants.FLAG_SET_SHORTCUT_ICON_5 -> saveShortcutIconApp(appModel, 4)
            Constants.FLAG_SET_SHORTCUT_ICON_6 -> saveShortcutIconApp(appModel, 5)
            Constants.FLAG_SET_SHORTCUT_ICON_7 -> saveShortcutIconApp(appModel, 6)
            Constants.FLAG_SET_SHORTCUT_ICON_8 -> saveShortcutIconApp(appModel, 7)
        }
    }

    private fun saveShortcutIconApp(appModel: AppModel, slot: Int) {
        if (appModel is AppModel.App) {
            prefs.setShortcutTarget(
                slot = slot,
                packageName = appModel.appPackage,
                className = appModel.activityClassName,
                user = appModel.user.toString(),
                label = appModel.appLabel
            )
            refreshHome(false)
        }
    }

    private fun launchShortcut(appModel: AppModel.PinnedShortcut) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(appModel.appPackage)
                setShortcutIds(listOf(appModel.shortcutId))
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            val shortcut = launcher.getShortcuts(query, appModel.user)?.firstOrNull()
            if (shortcut == null) {
                appContext.showToast(appContext.getString(R.string.app_not_found))
                return
            }
            if (!canLaunch(appModel.appPackage, appModel.user)) return
            launcher.startShortcut(shortcut, null, null)
            prefs.incrementUsage(appKey(appModel))
        } catch (_: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    private fun saveHomeApp(appModel: AppModel, position: Int) {
        when (appModel) {
            is AppModel.App -> prefs.setHomeApp(
                position,
                name = appModel.appLabel,
                pkg = appModel.appPackage,
                user = appModel.user.toString(),
                activityClassName = appModel.activityClassName,
                isShortcut = false,
                shortcutId = "",
            )

            is AppModel.PinnedShortcut -> prefs.setHomeApp(
                position,
                name = appModel.appLabel,
                pkg = appModel.appPackage,
                user = appModel.user.toString(),
                activityClassName = null,
                isShortcut = true,
                shortcutId = appModel.shortcutId,
            )

            else -> return // headers, tiles and contacts cannot live on a home slot
        }
        // Assigning a real app/shortcut to a slot overrides any folder previously there.
        prefs.setFolderAt(position, false, "")
        refreshHome(false)
    }

    private fun saveSwipeApp(appModel: AppModel, isLeft: Boolean) {
        // (activityClassName, isShortcut, shortcutId) differ between the two kinds;
        // everything else is shared. Headers, tiles and contacts can't be swipe targets.
        val (cls, isShortcut, shortcutId) = when (appModel) {
            is AppModel.App -> Triple(appModel.activityClassName, false, "")
            is AppModel.PinnedShortcut -> Triple(null, true, appModel.shortcutId)
            else -> return
        }
        if (isLeft) {
            prefs.appNameSwipeLeft = if (appModel.appPackage.isEmpty()) "" else appModel.appLabel
            prefs.appPackageSwipeLeft = appModel.appPackage
            prefs.appUserSwipeLeft = if (appModel.appPackage.isEmpty()) "" else appModel.user.toString()
            prefs.appActivityClassNameSwipeLeft = cls
            prefs.isShortcutSwipeLeft = isShortcut
            prefs.shortcutIdSwipeLeft = shortcutId
        } else {
            prefs.appNameSwipeRight = if (appModel.appPackage.isEmpty()) "" else appModel.appLabel
            prefs.appPackageSwipeRight = appModel.appPackage
            prefs.appUserSwipeRight = if (appModel.appPackage.isEmpty()) "" else appModel.user.toString()
            prefs.appActivityClassNameRight = cls
            prefs.isShortcutSwipeRight = isShortcut
            prefs.shortcutIdSwipeRight = shortcutId
        }
        updateSwipeApps()
    }

    private fun saveClockApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            if (appModel.appPackage.isEmpty()) {
                prefs.clockAppPackage = ""
                prefs.clockAppUser = ""
                prefs.clockAppClassName = ""
            } else {
                prefs.clockAppPackage = appModel.appPackage
                prefs.clockAppUser = appModel.user.toString()
                prefs.clockAppClassName = appModel.activityClassName
            }
            refreshHome(false)
        }
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            if (appModel.appPackage.isEmpty()) {
                prefs.calendarAppPackage = ""
                prefs.calendarAppUser = ""
                prefs.calendarAppClassName = ""
            } else {
                prefs.calendarAppPackage = appModel.appPackage
                prefs.calendarAppUser = appModel.user.toString()
                prefs.calendarAppClassName = appModel.activityClassName
            }
            refreshHome(false)
        }
    }

    private fun saveScreenTimeApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            if (appModel.appPackage.isEmpty()) {
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppUser = ""
                prefs.screenTimeAppClassName = ""
            } else {
                prefs.screenTimeAppPackage = appModel.appPackage
                prefs.screenTimeAppUser = appModel.user.toString()
                prefs.screenTimeAppClassName = appModel.activityClassName
            }
            refreshHome(false)
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.value = appCountUpdated
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    // ---- App lock ----
    private fun appKey(appModel: AppModel): String =
        appModel.appPackage + "|" + appModel.user.toString()

    fun isAppLocked(appModel: AppModel): Boolean =
        (appModel is AppModel.App || appModel is AppModel.PinnedShortcut) && prefs.isAppLocked(appKey(appModel))

    // Toggles the locked state of an app and returns the new state (true = locked).
    fun toggleAppLock(appModel: AppModel): Boolean {
        val key = appKey(appModel)
        val locked = prefs.lockedApps // defensive copy, safe to mutate
        val nowLocked = !locked.remove(key)
        if (nowLocked) locked.add(key)
        prefs.lockedApps = locked
        return nowLocked
    }

    fun isAppLimited(appModel: AppModel): Boolean =
        appModel is AppModel.App && prefs.isAppLimited(appKey(appModel))

    fun appLimitLevel(appModel: AppModel): Int =
        if (appModel is AppModel.App) prefs.limitLevel(appKey(appModel)) else 0

    fun appCooldownRemainingMillis(appModel: AppModel): Long {
        if (appModel !is AppModel.App) return 0L
        val key = appKey(appModel)
        if (!prefs.isAppLimited(key)) return 0L
        val until = prefs.limitUntil(key)
        val remaining = until - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    fun toggleAppLimit(appModel: AppModel): ToggleLimitResult {
        val key = appKey(appModel)
        val limited = prefs.limitedApps // defensive copy, safe to mutate
        val nowLimited: Boolean
        if (key in limited) {
            // Removing the limit is itself limited: not while banned, nor once the
            // compulsiveness level shows a pattern the limit exists to break.
            if (prefs.limitUntil(key) > System.currentTimeMillis()) return ToggleLimitResult.PreventedBanned
            val level = prefs.limitLevel(key)
            if (level >= 3) return ToggleLimitResult.PreventedCompulsiveness(level)
            limited.remove(key)
            prefs.clearLimitState(key)
            nowLimited = false
        } else {
            limited.add(key)
            nowLimited = true
        }
        prefs.limitedApps = limited
        return ToggleLimitResult.Success(nowLimited)
    }

    fun triggerCooldownBlock(key: String) {
        val parts = key.split("|")
        if (parts.size < 2) return
        val packageName = parts[0]
        val userHandleStr = parts[1]
        val user = app.olauncher.helper.getUserHandleFromString(appContext, userHandleStr)

        val level = prefs.limitLevel(key)
        val until = prefs.limitUntil(key)
        if (until > System.currentTimeMillis()) {
            val retries = prefs.limitRetryCount(key)
            val totalBanMinutes = ((until - System.currentTimeMillis()) / 60_000L).toInt()

            cooldownBlocked.postValue(
                CooldownBlock(
                    packageName = packageName,
                    user = user,
                    untilMillis = until,
                    isSevere = retries > 1,
                    penaltyMinutes = if (retries > 1) AppLimiter.penaltyMinutesForRetry(retries) else 0,
                    compulsivenessLevel = level,
                    totalBanMinutes = totalBanMinutes
                )
            )
        }
    }

    private fun requestLaunch(appModel: AppModel) {
        if (isAppLocked(appModel)) launchAppWithAuth.value = appModel
        else launchAppDirectly(appModel)
    }

    // Called only after authorization, or for targets without an app lock.
    fun launchAppDirectly(appModel: AppModel) {
        when (appModel) {
            is AppModel.App -> launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
            is AppModel.PinnedShortcut -> launchShortcut(appModel)
            else -> Unit
        }
    }

    private fun canLaunch(packageName: String, userHandle: UserHandle): Boolean {
        val key = "$packageName|$userHandle"

        val lastOpened = prefs.lastOpenedLimitedApp
        if (lastOpened.isNotEmpty() && lastOpened != key) {
            AppLimiter.endSession(prefs, System.currentTimeMillis())
        }

        // Soft "use it less" limit: gate the launch behind a progressive cooldown.
        // Evaluated at the single launch chokepoint so it covers home, drawer, swipe
        // and post-auth launches alike.
        if (prefs.isAppLimited(key)) {
            when (val decision = AppLimiter.evaluate(prefs, key, System.currentTimeMillis())) {
                is AppLimiter.Decision.Block -> {
                    cooldownBlocked.postValue(
                        CooldownBlock(
                            packageName = packageName,
                            user = userHandle,
                            untilMillis = decision.untilMillis,
                            isSevere = decision.isSevere,
                            penaltyMinutes = decision.penaltyMinutes,
                            compulsivenessLevel = decision.compulsivenessLevel,
                            totalBanMinutes = decision.totalBanMinutes
                        )
                    )
                    return false
                }

                AppLimiter.Decision.Allow -> Unit // proceed
            }
        }

        return true
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val key = "$packageName|$userHandle"
        if (!canLaunch(packageName, userHandle)) return

        // Track launches to rank drawer search results by frequency of use.
        prefs.incrementUsage(key)
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val isActivityValid = activityClassName.isNullOrBlank().not()
                && activityInfo.any { it.componentName.className == activityClassName }

        val component = if (isActivityValid)
            ComponentName(packageName, activityClassName)
        else {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }

                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }.also { prefs.updateAppActivityClassName(packageName, it.className) }
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    private var appListJob: Job? = null
    private var appListGeneration = 0L

    fun getAppList(includeHiddenApps: Boolean = false) {
        val generation = ++appListGeneration
        appListJob?.cancel()
        appListJob = viewModelScope.launch {
            val apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            // JSON serialization stays off the UI thread. Publish the list and its
            // snapshot together only after confirming this is still the latest request.
            val snapshot = if (!includeHiddenApps)
                withContext(Dispatchers.IO) { appListToCacheJson(apps) }
            else null
            ensureActive()
            if (generation != appListGeneration) return@launch
            if (snapshot != null) prefs.appListCache = snapshot // SharedPreferences.apply is asynchronous.
            appList.value = apps
        }
        getPrivateSpaceAppList()
    }

    fun loadDrawerContacts() {
        viewModelScope.launch {
            drawerContacts.value = ContactsHelper.loadContacts(appContext)
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value =
                getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.value = isOlauncherDefault(appContext)
    }


    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    private var screenTimeJob: Job? = null

    fun getTodaysScreenTime() {
        if (screenTimeJob?.isActive == true ||
            (screenTimeValue.value != null && !prefs.screenTimeLastUpdated.hasBeenMinutes(1))) return
        screenTimeJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { calculateScreenTime() }.getOrNull()
            } ?: return@launch
            screenTimeValue.value = result.first
            prefs.screenTimeLastUpdated = result.second
        }
    }

    private fun calculateScreenTime(): Pair<String, Long> {

        val eventLogWrapper = EventLogWrapper(
            appContext
        )
        // Start of today in millis
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val timeSpent = eventLogWrapper.getScreenTime(startTime, endTime)
        val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
        return viewTimeSpent to endTime
    }

    fun getPrivateSpaceAppList() {
        viewModelScope.launch {
            val handle = getPrivateSpaceUserHandle(appContext)
            privateSpaceAvailable.value = handle != null
            if (handle != null) {
                privateSpaceLocked.value = isPrivateSpaceLocked(appContext, handle)
                privateSpaceApps.value = getPrivateSpaceApps(appContext, prefs)
            } else {
                privateSpaceLocked.value = true
                privateSpaceApps.value = emptyList()
            }
        }
    }

    fun openPrivateSpaceSettings() {
        try {
            val intent = Intent("android.settings.PRIVATE_SPACE_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun togglePrivateSpaceLock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val handle = getPrivateSpaceUserHandle(appContext) ?: return
        try {
            isPrivateSpaceToggling = true
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            val currentlyLocked = userManager.isQuietModeEnabled(handle)
            userManager.requestQuietModeEnabled(!currentlyLocked, handle)
        } catch (e: Exception) {
            isPrivateSpaceToggling = false
            e.printStackTrace()
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
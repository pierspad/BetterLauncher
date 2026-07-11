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
    val launchAppWithAuth = SingleLiveEvent<AppModel.App>()

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
                    is AppModel.PinnedShortcut -> launchShortcut(appModel)
                    is AppModel.App ->
                        if (isAppLocked(appModel)) launchAppWithAuth.postValue(appModel)
                        else launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)

                    else -> {}
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel is AppModel.App) {
                    if (isAppLocked(appModel)) launchAppWithAuth.postValue(appModel)
                    else launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
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
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        launcher.getShortcuts(query, appModel.user)?.find { it.id == appModel.shortcutId }
            ?.let { shortcut ->
                launcher.startShortcut(shortcut, null, null)
            }
    }

    private fun saveHomeApp(appModel: AppModel, position: Int) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.FolderHeader -> return
            is AppModel.SettingTile -> return
            is AppModel.Contact -> return
            is AppModel.App -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = appModel.activityClassName
                        prefs.isShortcut1 = false
                        prefs.shortcutId1 = ""
                    }

                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = appModel.activityClassName
                        prefs.isShortcut2 = false
                        prefs.shortcutId2 = ""
                    }

                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = appModel.activityClassName
                        prefs.isShortcut3 = false
                        prefs.shortcutId3 = ""
                    }

                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = appModel.activityClassName
                        prefs.isShortcut4 = false
                        prefs.shortcutId4 = ""
                    }

                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = appModel.activityClassName
                        prefs.isShortcut5 = false
                        prefs.shortcutId5 = ""
                    }

                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = appModel.activityClassName
                        prefs.isShortcut6 = false
                        prefs.shortcutId6 = ""
                    }

                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = appModel.activityClassName
                        prefs.isShortcut7 = false
                        prefs.shortcutId7 = ""
                    }

                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = appModel.activityClassName
                        prefs.isShortcut8 = false
                        prefs.shortcutId8 = ""
                    }
                }
            }

            is AppModel.PinnedShortcut -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = null
                        prefs.isShortcut1 = true
                        prefs.shortcutId1 = appModel.shortcutId
                    }

                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = null
                        prefs.isShortcut2 = true
                        prefs.shortcutId2 = appModel.shortcutId
                    }

                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = null
                        prefs.isShortcut3 = true
                        prefs.shortcutId3 = appModel.shortcutId
                    }

                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = null
                        prefs.isShortcut4 = true
                        prefs.shortcutId4 = appModel.shortcutId
                    }

                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = null
                        prefs.isShortcut5 = true
                        prefs.shortcutId5 = appModel.shortcutId
                    }

                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = null
                        prefs.isShortcut6 = true
                        prefs.shortcutId6 = appModel.shortcutId
                    }

                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = null
                        prefs.isShortcut7 = true
                        prefs.shortcutId7 = appModel.shortcutId
                    }

                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = null
                        prefs.isShortcut8 = true
                        prefs.shortcutId8 = appModel.shortcutId
                    }
                }
            }
        }
        // Assigning a real app/shortcut to a slot overrides any folder previously there.
        prefs.setFolderAt(position, false, "")
        refreshHome(false)
    }

    private fun saveSwipeApp(appModel: AppModel, isLeft: Boolean) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.FolderHeader -> return
            is AppModel.SettingTile -> return
            is AppModel.Contact -> return
            is AppModel.App -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                    prefs.isShortcutSwipeLeft = false
                    prefs.shortcutIdSwipeLeft = ""
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = appModel.activityClassName
                    prefs.isShortcutSwipeRight = false
                    prefs.shortcutIdSwipeRight = ""
                }
            }

            is AppModel.PinnedShortcut -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = null
                    prefs.isShortcutSwipeLeft = true
                    prefs.shortcutIdSwipeLeft = appModel.shortcutId
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = null
                    prefs.isShortcutSwipeRight = true
                    prefs.shortcutIdSwipeRight = appModel.shortcutId
                }
            }
        }
        updateSwipeApps()
    }

    private fun saveClockApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.clockAppPackage = appModel.appPackage
            prefs.clockAppUser = appModel.user.toString()
            prefs.clockAppClassName = appModel.activityClassName
        }
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.calendarAppPackage = appModel.appPackage
            prefs.calendarAppUser = appModel.user.toString()
            prefs.calendarAppClassName = appModel.activityClassName
        }
    }

    private fun saveScreenTimeApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.screenTimeAppPackage = appModel.appPackage
            prefs.screenTimeAppUser = appModel.user.toString()
            prefs.screenTimeAppClassName = appModel.activityClassName
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
        appModel is AppModel.App && prefs.isAppLocked(appKey(appModel))

    // Toggles the locked state of an app and returns the new state (true = locked).
    fun toggleAppLock(appModel: AppModel): Boolean {
        val key = appKey(appModel)
        val newSet = prefs.lockedApps.toMutableSet()
        val nowLocked: Boolean
        if (newSet.contains(key)) {
            newSet.remove(key)
            nowLocked = false
        } else {
            newSet.add(key)
            nowLocked = true
        }
        prefs.lockedApps = newSet
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
        val newSet = prefs.limitedApps.toMutableSet()
        val nowLimited: Boolean
        if (newSet.contains(key)) {
            val isBanned = prefs.limitUntil(key) > System.currentTimeMillis()
            val compulsivenessLevel = prefs.limitLevel(key)
            if (isBanned) {
                return ToggleLimitResult.PreventedBanned
            }
            if (compulsivenessLevel >= 3) {
                return ToggleLimitResult.PreventedCompulsiveness(compulsivenessLevel)
            }
            newSet.remove(key)
            prefs.clearLimitState(key)
            nowLimited = false
        } else {
            newSet.add(key)
            nowLimited = true
        }
        prefs.limitedApps = newSet
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

    // Called by the Activity after a successful unlock.
    fun launchAppDirectly(appModel: AppModel.App) {
        launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val key = "$packageName|$userHandle"

        val lastOpened = prefs.lastOpenedLimitedApp
        if (lastOpened.isNotEmpty() && lastOpened != key) {
            val now = System.currentTimeMillis()
            val currentLevel = prefs.limitLevel(lastOpened)
            if (currentLevel > 0) {
                val duration = AppLimiter.durationForStep(currentLevel)
                prefs.setLimitUntil(lastOpened, now + duration)
                prefs.setLimitRetryCount(lastOpened, 0)
            }
            prefs.lastOpenedLimitedApp = ""
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
                    return
                }

                AppLimiter.Decision.Allow -> Unit // proceed
            }
        }

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
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch {
            val apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            appList.value = apps
            // Persist the drawer snapshot for the next cold start. Skip the hidden-apps
            // variant (used by the app picker) so the cache stays the real drawer list.
            if (!includeHiddenApps)
                withContext(Dispatchers.IO) { prefs.appListCache = appListToCacheJson(apps) }
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

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

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

        val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
            eventLogWrapper.aggregateForegroundStats(
                eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
            )
        )
        val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
        screenTimeValue.postValue(viewTimeSpent)
        prefs.screenTimeLastUpdated = endTime
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
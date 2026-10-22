package app.olauncher

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import android.util.Base64
import app.olauncher.helper.readBoundedBytes
import android.view.Gravity
import androidx.lifecycle.Observer
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.AppLimiter
import app.olauncher.helper.FuzzySearch
import app.olauncher.helper.IconManager
import app.olauncher.helper.SearchMode
import app.olauncher.ui.AppDrawerAdapter
import java.io.File
import org.json.JSONObject

/** Runs against real Android APIs, with isolated preferences and font storage. */
class FeedbackRegressionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val output = StringBuilder()
        var status = Activity.RESULT_OK
        val isolated = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("regression-$name", mode)
            override fun getFilesDir(): File = getDir("regression-fonts", Context.MODE_PRIVATE)
        }
        val prefs = Prefs(isolated)
        prefs.resetToDefaults()
        isolated.filesDir.listFiles()?.forEach { it.delete() }
        fun test(name: String, block: () -> Unit) {
            sendStatus(0, Bundle().apply { putString("stream", "RUN: $name\n") })
            block()
            output.append("PASS: ").append(name).append('\n')
        }
        try {
            test("runtime optimization regressions") {
                val failures = mutableListOf<String>()
                fun checkCase(name: String, block: () -> Unit) {
                    runCatching(block).onFailure { failures += "$name: ${it.message}" }
                }
                checkCase("precise default migration") { runSearchDefaultMigrationRegression(isolated) }
                checkCase("device/widget") { onMain { runDeviceRuntimeRegression(isolated) } }
                runSearchRuntimeRegression(this, isolated) { name, block -> checkCase(name, block) }
                checkCase("package callbacks") { runLauncherRefreshRegression() }
                checkCase("loading") { runLoadingRuntimeRegression(this, isolated) }
                checkCase("lawnicon index") { runLawniconIndexRegression() }
                check(failures.isEmpty()) { failures.joinToString("\n") }
            }
            prefs.textSizeScale = 1.5f
            prefs.homeAppsNum = 6
            val valid = prefs.exportToJson()
            test("invalid types and ranges preserve the entire configuration") {
                val invalidEntries = listOf(
                    Triple("TEXT_SIZE_SCALE", "f", "invalid"),
                    Triple("TEXT_SIZE_SCALE", "s", "invalid"),
                    Triple("TEXT_SIZE_SCALE", "i", 1),
                    Triple("TEXT_SIZE_SCALE", "f", 2.0),
                    Triple("HOME_APPS_NUM", "i", 100),
                    Triple("HOME_APPS_NUM", "i", 1.5),
                    Triple("SHORTCUT_ICON_INDEX_0", "i", 10000),
                    Triple("SEARCH_MODE", "b", true),
                    Triple("FIRST_OPEN", "b", "false"),
                    Triple("LOCKED_APPS", "set", "app.fake"),
                    Triple("UNKNOWN", "unknown", 1),
                )
                for ((key, type, value) in invalidEntries) {
                    val invalid = JSONObject(valid).put(key, JSONObject().put("t", type).put("v", value))
                    check(!prefs.importFromJson(invalid.toString())) { "Accepted $key/$type/$value" }
                    check(JSONObject(prefs.exportToJson()).toString() == JSONObject(valid).toString())
                }
            }
            test("invalid fonts leave preferences and the original file intact") {
                val font = File(isolated.filesDir, "custom_font")
                font.writeBytes(byteArrayOf(1, 2, 3))
                val invalidFont = JSONObject(valid).put("CUSTOM_FONT_BASE64",
                    JSONObject().put("t", "s").put("v", "bm90IGEgZm9udA=="))
                check(!prefs.importFromJson(invalidFont.toString()))
                check(font.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
                check(prefs.textSizeScale == 1.5f)
                font.delete()
                val systemFont = File("/system/fonts").listFiles()!!.first {
                    it.extension == "ttf" && it.length() in 1..(1024 * 1024)
                }.readBytes()
                val withFont = JSONObject(valid).put("CUSTOM_FONT_BASE64",
                    JSONObject().put("t", "s").put("v", Base64.encodeToString(systemFont, Base64.NO_WRAP)))
                check(prefs.importFromJson(withFont.toString()))
                check(font.readBytes().contentEquals(systemFont))
                check(prefs.customFontPath == font.absolutePath)
                check(prefs.importFromJson(valid))
                check(!font.exists())
                check(runCatching { ByteArray(16).inputStream().readBoundedBytes(8) }.isFailure)
            }
            test("legacy backup, dynamic keys and valid round trip") {
                prefs.setAppRenameLabel("arbitrary-shortcut-id", "My shortcut")
                prefs.setHomeApp(1, "Clock", "com.android.deskclock", Process.myUserHandle().toString(), "", false, "")
                val legacy = JSONObject(prefs.exportToJson()).apply { remove("BACKUP_FORMAT_VERSION") }.toString()
                prefs.textSizeScale = 1f
                check(prefs.importFromJson(legacy))
                check(prefs.textSizeScale == 1.5f)
                check(prefs.getAppName(1) == "Clock")
                check(prefs.getAppRenameLabel("arbitrary-shortcut-id") == "My shortcut")
            }
            test("cooldown session ends once; first retry unchanged; second adds 15 minutes; expiry allows") {
                val key = "test.limited.app|0"
                prefs.clearLimitState(key)
                prefs.appLimitEnabled = true
                val now = System.currentTimeMillis()
                check(AppLimiter.evaluate(prefs, key, now) == AppLimiter.Decision.Allow)
                AppLimiter.endSession(prefs, now)
                val until = prefs.limitUntil(key)
                AppLimiter.endSession(prefs, now + 1000)
                check(prefs.limitUntil(key) == until)
                check(prefs.limitRetryCount(key) == 0)
                val first = AppLimiter.evaluate(prefs, key, now + 1) as AppLimiter.Decision.Block
                check(first.untilMillis == until && first.penaltyMinutes == 0)
                val second = AppLimiter.evaluate(prefs, key, now + 2) as AppLimiter.Decision.Block
                check(second.untilMillis == until + 900_000L && second.penaltyMinutes == 15)
                check(AppLimiter.evaluate(prefs, key, second.untilMillis + 1) == AppLimiter.Decision.Allow)
                prefs.limitedApps = mutableSetOf(key)
                prefs.lastDecayDay = AppLimiter.localDayNumber(now)
                prefs.setLimitLevel(key, 5)
                val tomorrow = now + 86_400_000L
                AppLimiter.decayDaily(prefs, tomorrow)
                check(prefs.limitLevel(key) == 4)
                AppLimiter.decayDaily(prefs, tomorrow + 1000)
                check(prefs.limitLevel(key) == 4)
            }
            test("shortcut and activity identities remain distinct") {
                val shortcut = AppModel.PinnedShortcut("Compose", null, "app.one", "compose", user = Process.myUserHandle())
                check(!AppDrawerAdapter.DIFF_CALLBACK.areItemsTheSame(shortcut, shortcut.copy(appPackage = "app.two")))
                val app = AppModel.App("One", null, "app.one", "First", user = Process.myUserHandle())
                check(!AppDrawerAdapter.DIFF_CALLBACK.areItemsTheSame(app, app.copy(activityClassName = "Second")))
            }
            test("locked shortcuts request authentication before launch") {
                val actual = Prefs(targetContext)
                val originalLocks = actual.lockedApps
                try {
                    onMain {
                        val vm = MainViewModel(targetContext.applicationContext as android.app.Application)
                        val shortcut = AppModel.PinnedShortcut("Test", null, "app.regression", "compose", user = Process.myUserHandle())
                        actual.lockedApps = mutableSetOf("${shortcut.appPackage}|${shortcut.user}")
                        var requested: AppModel? = null
                        val observer = Observer<AppModel> { requested = it }
                        vm.launchAppWithAuth.observeForever(observer)
                        try {
                            vm.selectedApp(shortcut, Constants.FLAG_LAUNCH_APP)
                            check(requested == shortcut)
                        } finally {
                            vm.launchAppWithAuth.removeObserver(observer)
                        }
                    }
                } finally {
                    actual.lockedApps = originalLocks
                }
            }
            test("settings fragment can be destroyed before creating its view") {
                val activity = startActivitySync(android.content.Intent(
                    targetContext, SettingsLifecycleHostActivity::class.java
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                try {
                    check(!activity.isFinishing)
                    check(SettingsLifecycleHostActivity.wasDestroyedWithoutView) {
                        "SettingsFragment was not destroyed before onCreateView"
                    }
                } finally {
                    activity.finish()
                    waitForIdleSync()
                }
            }
            test("drawer snapshots do not mutate caller lists; hide removes all occurrences") {
                onMain {
                    val one = AppModel.App("One", null, "app.one", "First", user = Process.myUserHandle())
                    val two = one.copy(appLabel = "Two", appPackage = "app.two")
                    val input = mutableListOf<AppModel>(one, two)
                    val adapter = AppDrawerAdapter(Constants.FLAG_LAUNCH_APP, Gravity.START,
                        appClickListener = {}, appInfoListener = {}, appDeleteListener = {},
                        appHideListener = { _, _ -> }, appRenameListener = { _, _ -> })
                    adapter.allowAutoLaunch = false
                    adapter.setAppList(input)
                    check(input.size == 2)
                    check(adapter.currentList.size == 3)
                    adapter.removeApp(one)
                    // The committed list is immutable and remains valid while the new diff runs.
                    check(adapter.currentList.size == 3)
                    check(adapter.currentList.contains(one))
                }
            }
            test("screen time excludes overlap, screen-off and locked intervals") {
                runScreenTimeRegression()
            }
            test("search modes preserve their intended matching") {
                runSearchMatchingRegression()
                check(FuzzySearch.score("Proton Mail", "ton ail", SearchMode.PRECISE) >= 0)
                check(FuzzySearch.score("Proton Mail", "Mail Proton", SearchMode.PRECISE) < 0)
                check(FuzzySearch.score("Reddit", "rdd", SearchMode.LOOSE_FUZZY) >= 0)
            }
            test("precise drawer launches only unique matches, including internal word fragments") {
                val proton = AppModel.App("Proton Mail", null, "app.proton", "Main", user = Process.myUserHandle())
                val pedometer = proton.copy(appLabel = "Pedometro - Contapassi", appPackage = "app.steps")
                val reddit = proton.copy(appLabel = "Reddit", appPackage = "app.reddit")
                val redditNews = reddit.copy(appLabel = "Reddit News", appPackage = "app.news")
                fun search(query: String, apps: List<AppModel>, expected: List<AppModel>, launches: List<AppModel>) {
                    val clicked = mutableListOf<AppModel>()
                    lateinit var adapter: AppDrawerAdapter
                    onMain {
                        adapter = AppDrawerAdapter(Constants.FLAG_LAUNCH_APP, Gravity.START,
                            appClickListener = { clicked.add(it) }, appInfoListener = {}, appDeleteListener = {},
                            appHideListener = { _, _ -> }, appRenameListener = { _, _ -> })
                        adapter.allowAutoLaunch = false
                        adapter.setAppList(apps.toMutableList())
                        adapter.allowAutoLaunch = true
                        adapter.applyFilter(query)
                    }
                    val deadline = android.os.SystemClock.uptimeMillis() + 5000
                    var settled = false
                    while (!settled && android.os.SystemClock.uptimeMillis() < deadline) {
                        waitForIdleSync()
                        onMain { settled = adapter.currentList == expected }
                        if (!settled) Thread.sleep(10)
                    }
                    onMain {
                        check(settled) { "Wrong results for $query: ${adapter.currentList}" }
                        check(clicked == launches) { "Wrong automatic launch for $query: $clicked" }
                    }
                }
                search("Prot", listOf(proton, pedometer), listOf(proton), listOf(proton))
                search("edd", listOf(reddit, pedometer), listOf(reddit), listOf(reddit))
                search("ton ail", listOf(proton, pedometer), listOf(proton), listOf(proton))
                search("pro ma", listOf(proton, pedometer), listOf(proton), listOf(proton))
                search("edd", listOf(reddit, redditNews), listOf(reddit, redditNews), emptyList())
                search("rdd", listOf(reddit, pedometer), emptyList(), emptyList())
                search("ail ton", listOf(proton), emptyList(), emptyList())
                search("pro ton", listOf(proton), emptyList(), emptyList())
            }
            test("consent keeps title and both actions visible while only the message scrolls") {
                val activity = startActivitySync(android.content.Intent(
                    targetContext, SettingsLifecycleHostActivity::class.java
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                try {
                    onMain {
                        for (heightDp in listOf(280, 320, 480)) {
                            val root = android.view.LayoutInflater.from(activity)
                                .inflate(R.layout.dialog_accessibility_consent, null) as android.view.ViewGroup
                            val density = activity.resources.displayMetrics.density
                            val width = (320 * density).toInt()
                            val height = (heightDp * density).toInt()
                            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, width, height)
                            fun findScroll(view: android.view.View): android.widget.ScrollView? {
                                if (view is android.widget.ScrollView) return view
                                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                                    findScroll(view.getChildAt(i))?.let { return it }
                                }
                                return null
                            }
                            val scroll = checkNotNull(findScroll(root))
                            check(scroll.height > 0) { "No room to read consent at $heightDp dp" }
                            for (id in listOf(R.id.consentTitle, R.id.consentAccept, R.id.consentDecline)) {
                                val control = root.findViewById<android.view.View>(id)
                                val bounds = android.graphics.Rect()
                                control.getDrawingRect(bounds)
                                root.offsetDescendantRectToMyCoords(control, bounds)
                                check(bounds.top >= 0 && bounds.bottom <= height) { "Control $id clipped at $heightDp dp: $bounds" }
                                var parent = control.parent
                                while (parent != null) {
                                    check(parent !== scroll) { "Fixed control $id scrolls with the message" }
                                    parent = parent.parent
                                }
                            }
                            val titleTop = root.findViewById<android.view.View>(R.id.consentTitle).top
                            val declineTop = root.findViewById<android.view.View>(R.id.consentDecline).top
                            scroll.scrollTo(0, scroll.getChildAt(0).height)
                            check(scroll.scrollY > 0) { "Long consent message does not scroll at $heightDp dp" }
                            check(root.findViewById<android.view.View>(R.id.consentTitle).top == titleTop)
                            check(root.findViewById<android.view.View>(R.id.consentDecline).top == declineTop)
                        }
                    }
                } finally {
                    activity.finish()
                    waitForIdleSync()
                }
            }
            test("icon cache returns independently mutable drawables") {
                val first = IconManager.getAppIcon(targetContext, targetContext.packageName, Process.myUserHandle())!!
                val second = IconManager.getAppIcon(targetContext, targetContext.packageName, Process.myUserHandle())!!
                first.setBounds(0, 0, 10, 10)
                second.setBounds(0, 0, 20, 20)
                check(first !== second && first.bounds.width() == 10 && second.bounds.width() == 20)
            }
        } catch (failure: Throwable) {
            status = Activity.RESULT_CANCELED
            output.append("FAIL: ").append(failure.stackTraceToString())
        } finally {
            prefs.resetToDefaults()
            isolated.filesDir.listFiles()?.forEach { it.delete() }
        }
        finish(status, Bundle().apply { putString("stream", output.toString()) })
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { block() } catch (caught: Throwable) { failure = caught } }
        failure?.let { throw it }
    }
}

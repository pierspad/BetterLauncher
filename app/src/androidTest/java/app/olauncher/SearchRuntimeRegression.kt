package app.olauncher

import android.app.Instrumentation
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.ui.AppDrawerAdapter

/** Each query/submit pair runs in one main-thread turn, before a diff can commit. */
fun runSearchRuntimeRegression(
    instrumentation: Instrumentation,
    context: Context,
    test: (String, () -> Unit) -> Unit,
) {
    fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    fun adapter(clicked: MutableList<AppModel>) = AppDrawerAdapter(
        Constants.FLAG_LAUNCH_APP, Gravity.START,
        appClickListener = { clicked.add(it) }, appInfoListener = {}, appDeleteListener = {},
        appHideListener = { _, _ -> }, appRenameListener = { _, _ -> },
    )
    fun awaitList(adapter: AppDrawerAdapter, expected: List<AppModel>) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var settled = false
        while (!settled && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            onMain { settled = adapter.currentList == expected }
            if (!settled) Thread.sleep(10)
        }
        check(settled) { "Unsettled search: ${adapter.currentList}, expected $expected" }
    }
    val camera = AppModel.App("Camera", null, "app.camera", "Main", user = Process.myUserHandle())
    val chrome = camera.copy(appLabel = "Chrome", appPackage = "app.chrome")
    test("Enter uses computed query results before async diff commits") {
        val clicked = mutableListOf<AppModel>()
        lateinit var drawer: AppDrawerAdapter
        onMain {
            drawer = adapter(clicked)
            drawer.allowAutoLaunch = false
            drawer.setAppList(mutableListOf(chrome, camera))
            drawer.applyFilter("camera")
            drawer.launchFirstInList()
            check(clicked == listOf(camera)) { "Enter launched stale results: $clicked" }
        }
        awaitList(drawer, listOf(camera))
    }
    test("Enter opens first contact or setting instead of skipping it") {
        val contact = AppModel.Contact("Alice", "content://contacts/lookup/alice")
        val setting = AppModel.SettingTile("Wi-Fi", "android.settings.WIFI_SETTINGS")
        for ((item, query) in listOf(contact to "alice", setting to "wi")) {
            val clicked = mutableListOf<AppModel>()
            lateinit var drawer: AppDrawerAdapter
            onMain {
                drawer = adapter(clicked)
                drawer.allowAutoLaunch = false
                drawer.setAppList(mutableListOf(camera))
                drawer.setSearchSources(listOf(item))
                drawer.applyFilter(query)
                drawer.launchFirstInList()
                check(clicked == listOf(item)) { "Enter skipped ${item.appLabel}: $clicked" }
            }
            awaitList(drawer, listOf(item))
        }
    }
    test("manual submit and later automatic commit launch a query only once") {
        val clicked = mutableListOf<AppModel>()
        lateinit var drawer: AppDrawerAdapter
        onMain {
            drawer = adapter(clicked)
            drawer.setAppList(mutableListOf(camera, chrome))
            drawer.applyFilter("camera")
            drawer.launchFirstInList()
        }
        awaitList(drawer, listOf(camera))
        onMain {
            drawer.launchFirstInList()
            drawer.setSearchSources(emptyList())
        }
        instrumentation.waitForIdleSync()
        onMain { check(clicked == listOf(camera)) { "Duplicate launch: $clicked" } }
    }
    test("query changes rebind highlights even when matching rows do not change") {
        val clicked = mutableListOf<AppModel>()
        lateinit var drawer: AppDrawerAdapter
        lateinit var holder: RecyclerView.ViewHolder
        onMain {
            drawer = adapter(clicked)
            drawer.allowAutoLaunch = false
            drawer.setAppList(mutableListOf(camera, chrome))
            drawer.applyFilter("c")
        }
        awaitList(drawer, listOf(camera, chrome))
        onMain {
            holder = drawer.onCreateViewHolder(FrameLayout(android.view.ContextThemeWrapper(context, R.style.AppTheme)), AppDrawerAdapter.VIEW_TYPE_APP)
            drawer.onBindViewHolder(holder, 0)
            drawer.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) {
                    if (start == 0 && count > 0) drawer.onBindViewHolder(holder, 0, mutableListOf(payload ?: Unit))
                }
            })
            // Both labels still match, with the same ranking and identities.
            drawer.applyFilter(" C")
        }
        awaitList(drawer, listOf(camera, chrome))
        onMain { drawer.applyFilter("") }
        awaitList(drawer, listOf(camera, chrome) + AppModel.App("", null, "", "", user = Process.myUserHandle()))
        onMain {
            val title = holder.itemView.findViewById<TextView>(R.id.appTitle).text as Spanned
            check(title.getSpans(0, title.length, UnderlineSpan::class.java).isEmpty()) {
                "Clearing a query retained stale highlights: $title"
            }
        }
    }
}

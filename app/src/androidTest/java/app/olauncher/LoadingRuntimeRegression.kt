package app.olauncher

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Process
import androidx.lifecycle.Observer
import app.olauncher.data.Prefs
import app.olauncher.helper.appListFromCacheJson
import app.olauncher.helper.getAppsList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Context must have isolated preferences, as provided by the regression instrumentation. */
fun runLoadingRuntimeRegression(instrumentation: Instrumentation, context: Context) {
    val prefs = Prefs(context)
    val originalHidden = prefs.hiddenApps
    val originalMigration = prefs.hiddenAppsUpdated
    val originalTimestamp = prefs.screenTimeLastUpdated
    try {
        val currentUser = Process.myUserHandle().toString()
        val cached = JSONArray().put(JSONObject().put("l", "Existing").put("p", "existing")
            .put("u", currentUser)).put(JSONObject().put("l", "Removed").put("p", "removed")
            .put("u", "UserHandle{2147483647}"))
        val restored = appListFromCacheJson(context, cached.toString())
        check(restored.size == 1 && restored.single().user == Process.myUserHandle()) {
            "Cached rows from removed profiles must be dropped, never remapped"
        }

        prefs.hiddenApps = mutableSetOf("legacy.package", "modern.package|$currentUser")
        prefs.hiddenAppsUpdated = false
        runBlocking { getAppsList(context, prefs) }
        check(prefs.hiddenApps == setOf("legacy.package|$currentUser", "modern.package|$currentUser"))

        prefs.screenTimeLastUpdated = System.currentTimeMillis()
        val emitted = CountDownLatch(1)
        lateinit var model: MainViewModel
        val observer = Observer<String> { if (it != null) emitted.countDown() }
        instrumentation.runOnMainSync {
            model = MainViewModel(object : Application() {
                override fun getApplicationContext(): Context = context
            })
            model.screenTimeValue.observeForever(observer)
            model.getTodaysScreenTime()
        }
        try {
            check(emitted.await(10, TimeUnit.SECONDS)) {
                "A fresh ViewModel must calculate screen time despite a recent persisted timestamp"
            }
            instrumentation.waitForIdleSync()
        val updatedAt = prefs.screenTimeLastUpdated
            instrumentation.runOnMainSync { model.getTodaysScreenTime() }
            instrumentation.waitForIdleSync()
            check(prefs.screenTimeLastUpdated == updatedAt) { "An existing value must remain throttled" }
        } finally {
            instrumentation.runOnMainSync { model.screenTimeValue.removeObserver(observer) }
        }
    } finally {
        prefs.hiddenApps = originalHidden
        prefs.hiddenAppsUpdated = originalMigration
        prefs.screenTimeLastUpdated = originalTimestamp
    }
}

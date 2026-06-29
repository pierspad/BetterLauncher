package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        Prefs(applicationContext).lockModeOn = true
        val info = serviceInfo
        info.packageNames = null // Monitor all packages for app limit enforcement
        serviceInfo = info
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val eventType = event.eventType
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkgName = event.packageName?.toString()
                if (pkgName != null) {
                    val prefs = Prefs(applicationContext)
                    val now = System.currentTimeMillis()

                    // 1. Check if the user just exited the last opened limited app
                    val lastOpened = prefs.lastOpenedLimitedApp
                    if (lastOpened.isNotEmpty()) {
                        val lastOpenedPkg = lastOpened.substringBefore("|")
                        if (pkgName != lastOpenedPkg && pkgName != "android" && pkgName != "com.android.systemui") {
                            val currentLevel = prefs.limitLevel(lastOpened)
                            if (currentLevel > 0) {
                                val duration = AppLimiter.durationForStep(currentLevel)
                                prefs.setLimitUntil(lastOpened, now + duration)
                                prefs.setLimitRetryCount(lastOpened, 0)
                            }
                            prefs.lastOpenedLimitedApp = ""
                        }
                    }

                    // 2. Check if the user is entering a limited app that is currently in cooldown
                    if (pkgName != packageName && pkgName != "android" && pkgName != "com.android.systemui") {
                        if (prefs.appLimitEnabled) {
                            val key = prefs.limitedApps.firstOrNull { it.startsWith("$pkgName|") }
                            if (key != null) {
                                val until = prefs.limitUntil(key)
                                if (until > now) {
                                    // App is banned! Escalate cooldown
                                    AppLimiter.evaluate(prefs, key, now)

                                    // Send home immediately
                                    performGlobalAction(GLOBAL_ACTION_HOME)

                                    // Trigger the cooldown dialog on MainActivity
                                    val intent = Intent(applicationContext, app.olauncher.MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        putExtra("SHOW_COOLDOWN_FOR_KEY", key)
                                    }
                                    startActivity(intent)
                                }
                            }
                        }
                    }
                }
            }

            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val source: AccessibilityNodeInfo = event.source ?: return
                if (source.className != "android.widget.FrameLayout") return

                when (source.contentDescription) {
                    getString(R.string.lock_layout_description) -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
            }
        } catch (e: Exception) {
            return
        }
    }

    override fun onInterrupt() {

    }
}
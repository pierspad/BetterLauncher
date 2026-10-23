package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    private var gestureController: XiaomiGestureController? = null
    private val prefs by lazy { Prefs(applicationContext) }

    // A single app open fires several TYPE_WINDOW_STATE_CHANGED events (splash →
    // main activity, dialogs…). Without this guard each event would escalate the
    // penalty again, stacking +15/+30/+60 within milliseconds for one violation.
    private var foregroundPackage: String? = null
    private var lastEscalatedKey: String? = null
    private var lastEscalatedAt: Long = 0L

    companion object {
        @Volatile var running = false
            private set
        const val ESCALATION_DEBOUNCE_MS = 5_000L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        if (!AccessibilityConsent.hasConsent(this)) {
            prefs.lockModeOn = false
            disableSelf()
            return
        }
        val info = serviceInfo
        info.packageNames = null // Monitor all packages for app limit enforcement
        serviceInfo = info
        gestureController?.stop()
        if (Build.VERSION.SDK_INT >= 29 && DeviceSettings.isXiaomi()) {
            gestureController = XiaomiGestureController(this).also { it.start() }
        }
        running = true
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!AccessibilityConsent.hasConsent(this)) {
            disableSelf()
            return
        }
        gestureController?.onWindowChanged()
        try {
            val eventType = event.eventType
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkgName = event.packageName?.toString()
                if (pkgName != null) {
                    // Ignore stale window events and additional windows within the
                    // same app; neither represents a new attempt to enter it.
                    val activePackage = rootInActiveWindow?.packageName?.toString() ?: return
                    if (activePackage != pkgName || foregroundPackage == pkgName) return
                    foregroundPackage = pkgName
                    val now = System.currentTimeMillis()

                    // 1. Check if the user just exited the last opened limited app
                    val lastOpened = prefs.lastOpenedLimitedApp
                    if (lastOpened.isNotEmpty()) {
                        val lastOpenedPkg = lastOpened.substringBefore("|")
                        if (pkgName != lastOpenedPkg && pkgName != "android" && pkgName != "com.android.systemui") {
                            AppLimiter.endSession(prefs, now)
                        }
                    }

                    // 2. Check if the user is entering a limited app that is currently in cooldown
                    if (pkgName != packageName && pkgName != "android" && pkgName != "com.android.systemui") {
                        if (prefs.appLimitEnabled) {
                            val key = prefs.limitedApps.firstOrNull { it.startsWith("$pkgName|") }
                            if (key != null) {
                                val until = prefs.limitUntil(key)
                                if (until > now) {
                                    // App is banned! Escalate cooldown — at most once per
                                    // burst of window events (see fields above).
                                    val isDuplicateBurst =
                                        key == lastEscalatedKey && now - lastEscalatedAt < ESCALATION_DEBOUNCE_MS
                                    if (!isDuplicateBurst) {
                                        lastEscalatedKey = key
                                        lastEscalatedAt = now
                                        AppLimiter.evaluate(prefs, key, now)
                                    }

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
                        if (!prefs.lockModeOn || source.packageName?.toString() != packageName) return
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
            }
        } catch (e: Exception) {
            return
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        gestureController?.onConfigurationChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        gestureController?.stop()
        gestureController = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        gestureController?.stop()
        gestureController = null
        super.onDestroy()
    }

    override fun onInterrupt() { }
}

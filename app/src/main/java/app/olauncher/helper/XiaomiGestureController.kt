package app.olauncher.helper

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.olauncher.data.Prefs

/** Experimental, opt-in navigation. No window contents, settings writes or native gesture claims. */
internal class XiaomiGestureController(private val service: AccessibilityService) : ContextWrapper(service), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = mutableListOf<View>()
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val store by lazy { getSharedPreferences("com.pierspad.betterlauncher", Context.MODE_PRIVATE) }
    private var receiverRegistered = false
    private var replaying = false
    private var replayGeneration = 0L
    private val gameNavigation = GameNavigationGuard()
    private var foregroundPackage: String? = null
    private data class Geometry(val width: Int, val height: Int, val nav: android.graphics.Rect, val gestural: Boolean)
    private var lastGeometry: Geometry? = null
    private val navigationObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private val backFeedback by lazy { GestureBackFeedback(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } }
    private var feedbackAttached = false
    private var notice: android.widget.TextView? = null
    private val dismissNotice = Runnable { hideNotice() }

    private fun hideNotice() {
        notice?.let { runCatching { manager.removeView(it) } }
        notice = null
    }

    private fun showGameNotice() {
        handler.removeCallbacks(dismissNotice)
        hideNotice()
        val density = resources.displayMetrics.density
        val text = android.widget.TextView(this).apply {
            setText(app.olauncher.R.string.game_gesture_repeat)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(40, 40, 40))
                cornerRadius = 12 * density
            }
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * .9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (80 * density).toInt()
            title = "BetterLauncher gesture confirmation"
        }
        runCatching { manager.addView(text, params); notice = text }
        handler.postDelayed(dismissNotice, 3000)
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) { refresh() }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    fun start() {
        if (receiverRegistered) stop()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, handler)
        connection = java.lang.ref.WeakReference(this)
        store.registerOnSharedPreferenceChangeListener(this)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("navigation_mode"), false, navigationObserver)
        contentResolver.registerContentObserver(Settings.Global.getUriFor("force_fsg_nav_bar"), false, navigationObserver)
        updateForeground()
        refresh()
    }

    private fun allowed(): Boolean = android.os.Build.VERSION.SDK_INT >= 29 && DeviceSettings.isXiaomi() && Prefs(this).xiaomiGestures &&
        isOlauncherDefault(this) && GestureNavigationMode.isGestural(this) &&
        (getSystemService(POWER_SERVICE) as PowerManager).isInteractive &&
        !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    @SuppressLint("RtlHardcoded") // These zones follow physical display edges, including in RTL.
    private fun refresh() {
        if (!allowed()) { detach(); return }
        val density = resources.displayMetrics.density
        val gesturalMode = GestureNavigationMode.isGestural(this)
        val geometry = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            Geometry(metrics.bounds.width(), metrics.bounds.height(), if (gesturalMode) android.graphics.Rect() else android.graphics.Rect(insets.left, 0, insets.right, insets.bottom), gesturalMode)
        } else {
            @Suppress("DEPRECATION")
            val display = android.util.DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
            @Suppress("DEPRECATION")
            val insets = windows.firstOrNull()?.rootWindowInsets
            @Suppress("DEPRECATION")
            val nav = if (gesturalMode || insets == null) android.graphics.Rect() else
                android.graphics.Rect(insets.stableInsetLeft, 0, insets.stableInsetRight, insets.stableInsetBottom)
            Geometry(display.widthPixels, display.heightPixels, nav, gesturalMode)
        }
        if (windows.size == 3 && geometry == lastGeometry) return
        if (windows.isNotEmpty()) detach()
        lastGeometry = geometry
        val (width, height, nav, gestural) = geometry
        for (edge in 0..2) {
            val thickness = ((if (edge == 2) 24 else 12) * density).toInt()
            val params = WindowManager.LayoutParams(
                if (edge == 2) width * 8 / 10 else thickness,
                if (edge == 2) thickness else height * 7 / 10,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = when (edge) {
                    0 -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                    1 -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                if (android.os.Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                if (edge == 0) x = if (gestural) 0 else nav.left
                if (edge == 1) x = if (gestural) 0 else nav.right
                // Keep system navigation buttons accessible. In gesture mode the zone follows the bottom edge.
                if (edge == 2) y = GestureNavigationMode.bottomOffset(gestural, nav.bottom)
                title = "BetterLauncher gesture $edge"
            }
            val view = View(this)
            view.setOnTouchListener(EdgeTouch(edge, density))
            view.setOnApplyWindowInsetsListener { _, insets ->
                handler.post { refresh() }
                insets
            }
            runCatching { manager.addView(view, params); windows += view }
        }
    }

    val isActive: Boolean get() = windows.size == 3 && allowed()

    private fun touchable(enabled: Boolean) {
        windows.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.flags = if (enabled) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.alpha = if (enabled) 1f else 0f
            runCatching { manager.updateViewLayout(view, params) }
        }
    }

    private inner class EdgeTouch(private val edge: Int, private val density: Float) : View.OnTouchListener {
        private val tracker = EdgeSwipeTracker(edge)
        private var x = 0f
        private var y = 0f
        private var time = 0L
        private var path = Path()
        private val hold = object : Runnable {
            override fun run() {
                if (!allowed()) return
                val now = SystemClock.uptimeMillis()
                if (tracker.takeHold(now)) { hideBackFeedback(); navigate(GLOBAL_ACTION_RECENTS) }
                else tracker.holdDelay(now)?.let { handler.postDelayed(this, it) }
            }
        }
        @SuppressLint("ClickableViewAccessibility") // Unused touches are replayed to the underlying app.
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (replaying || !allowed()) return false
                    x = event.rawX; y = event.rawY; time = event.eventTime
                    tracker.start(x / density, y / density, time)
                    path = Path().apply { moveTo(x, y) }
                    touchable(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.rawX, event.rawY)
                    tracker.move(event.rawX / density, event.rawY / density, event.eventTime)
                    val distance = if (edge == 0) event.rawX - x else x - event.rawX
                    if (edge != 2 && !tracker.cancelled && !tracker.fired && distance > 0) {
                        showBackFeedback(edge == 1, event.rawY, distance / (72 * density))
                    }
                    if (edge == 2 && !tracker.cancelled && !tracker.fired && y - event.rawY > 0) {
                        showBackFeedback(false, event.rawY, (y - event.rawY) / (96 * density), true)
                    }
                    if (tracker.cancelled) hideBackFeedback()
                    handler.removeCallbacks(hold)
                    tracker.holdDelay(SystemClock.uptimeMillis())?.let { handler.postDelayed(hold, it) }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    tracker.cancel(); hideBackFeedback(); handler.removeCallbacks(hold)
                }
                MotionEvent.ACTION_UP -> {
                    val hadFeedback = feedbackAttached
                    hideBackFeedback()
                    // Include the last sample: short, fast flicks may have few MOVE events.
                    tracker.move(event.rawX / density, event.rawY / density, event.eventTime)
                    handler.removeCallbacks(hold)
                    if (tracker.armed && !tracker.cancelled && !tracker.fired && allowed()) {
                        navigate(if (edge == 2) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                    } else if (!tracker.armed && !tracker.cancelled && !tracker.fired) {
                        path.lineTo(event.rawX + 0.1f, event.rawY + 0.1f)
                        replaying = true
                        val generation = ++replayGeneration
                        val elapsed = (event.eventTime - time).coerceAtLeast(0L)
                        val tapSlop = android.view.ViewConfiguration.get(this@XiaomiGestureController).scaledTouchSlop / density
                        val click = tracker.maxMotion <= tapSlop && elapsed < android.view.ViewConfiguration.getLongPressTimeout()
                        val duration = if (click) 60L else elapsed.coerceIn(50L, 3000L)
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
                        fun finish() {
                            if (!replaying || generation != replayGeneration) return
                            replaying = false
                            if (allowed()) touchable(true) else detach()
                        }
                        // Windows became untouchable at DOWN, so the real press already
                        // supplies most of the settling time. Feedback needs its own grace.
                        val settleDelay = if (hadFeedback) 70L else (70L - elapsed).coerceAtLeast(0L)
                        handler.postDelayed({
                            if (!replaying || generation != replayGeneration) return@postDelayed
                            if (!allowed()) { finish(); return@postDelayed }
                            if (!service.dispatchGesture(gesture, object : GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) { finish() }
                                override fun onCancelled(gestureDescription: GestureDescription?) { finish() }
                            }, handler)) finish()
                        }, settleDelay)
                        handler.postDelayed({ if (replaying) finish() }, settleDelay + duration + 1200)
                    }
                    if (!replaying) touchable(true)
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracker.cancel(); hideBackFeedback(); handler.removeCallbacks(hold); touchable(true)
                }
            }
            return true
        }
    }

    @SuppressLint("RtlHardcoded") // Physical edge of the incoming swipe.
    private fun showBackFeedback(right: Boolean, y: Float, progress: Float, bottom: Boolean = false) {
        val size = (72 * resources.displayMetrics.density).toInt()
        val params = (if (feedbackAttached) backFeedback.layoutParams as WindowManager.LayoutParams else null)
            ?: WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                title = "BetterLauncher back feedback"
                if (android.os.Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
            }
        val geometry = lastGeometry ?: return
        params.x = if (bottom) (geometry.width - size) / 2 else if (right) geometry.width - size else 0
        params.y = ((if (bottom) geometry.height - size * (0.6f + progress.coerceIn(0f, 1f)) else y - size / 2).toInt()).coerceIn(0, (geometry.height - size).coerceAtLeast(0))
        backFeedback.bottom = bottom
        backFeedback.fromRight = right
        backFeedback.progress = progress
        if (feedbackAttached) runCatching { manager.updateViewLayout(backFeedback, params) }
        else runCatching { manager.addView(backFeedback, params); feedbackAttached = true }
    }

    private fun hideBackFeedback() {
        if (feedbackAttached) runCatching { manager.removeView(backFeedback) }
        feedbackAttached = false
    }

    private fun navigate(action: Int) {
        if (!gameNavigation.allow(action, SystemClock.uptimeMillis())) {
            showGameNotice()
            return
        }
        handler.removeCallbacks(dismissNotice)
        hideNotice()
        if (!service.performGlobalAction(action)) {
            android.widget.Toast.makeText(this, app.olauncher.R.string.not_working,
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun detach() {
        handler.removeCallbacksAndMessages(null)
        replaying = false
        replayGeneration++
        gameNavigation.clearConfirmation()
        hideNotice()
        hideBackFeedback()
        windows.forEach { runCatching { manager.removeView(it) } }
        windows.clear()
        lastGeometry = null
    }
    @Suppress("DEPRECATION") // FLAG_IS_GAME supports older games without a category.
    private fun updateForeground(eventPackage: String? = null) {
        val pkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            ?: eventPackage ?: return
        if (pkg == foregroundPackage) return
        foregroundPackage = pkg
        val game = runCatching {
            val app = packageManager.getApplicationInfo(pkg, 0)
            (android.os.Build.VERSION.SDK_INT >= 26 && app.category == ApplicationInfo.CATEGORY_GAME) ||
                app.flags and ApplicationInfo.FLAG_IS_GAME != 0
        }.getOrDefault(false)
        gameNavigation.foreground(pkg, game)
    }

    fun onWindowChanged(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Our non-focusable edge/feedback windows are not a foreground app switch.
            val ownOverlay = event.packageName?.toString() == packageName &&
                event.className?.toString() in setOf("android.view.View", GestureBackFeedback::class.java.name, "android.widget.TextView")
            if (!ownOverlay) updateForeground(event.packageName?.toString())
        }
        refresh()
    }
    fun onConfigurationChanged() { refresh() }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) { handler.post { refresh() } }
    fun stop() {
        if (connected === this) connection.clear()
        store.unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(navigationObserver)
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        detach()
    }
    companion object {
        private var connection = java.lang.ref.WeakReference<XiaomiGestureController>(null)
        val connected: XiaomiGestureController? get() = connection.get()
    }
}

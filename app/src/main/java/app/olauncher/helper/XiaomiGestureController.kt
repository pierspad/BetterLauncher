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
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.olauncher.data.Prefs
import kotlin.math.abs

/** Experimental, opt-in navigation. No window contents, settings writes or native gesture claims. */
internal class XiaomiGestureController(private val service: AccessibilityService) : ContextWrapper(service), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = mutableListOf<View>()
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val store by lazy { getSharedPreferences("com.pierspad.betterlauncher", Context.MODE_PRIVATE) }
    private var receiverRegistered = false
    private var replaying = false
    private var lastGeometry: Triple<Int, Int, android.graphics.Rect>? = null
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
        refresh()
    }

    private fun allowed(): Boolean = android.os.Build.VERSION.SDK_INT >= 29 && DeviceSettings.isXiaomi() && Prefs(this).xiaomiGestures &&
        isOlauncherDefault(this) &&
        (getSystemService(POWER_SERVICE) as PowerManager).isInteractive &&
        !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    @SuppressLint("RtlHardcoded") // These zones follow physical display edges, including in RTL.
    private fun refresh() {
        if (!allowed()) { detach(); return }
        val density = resources.displayMetrics.density
        val geometry = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            Triple(metrics.bounds.width(), metrics.bounds.height(), android.graphics.Rect(insets.left, 0, insets.right, insets.bottom))
        } else Triple(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels, android.graphics.Rect())
        if (windows.size == 3 && geometry == lastGeometry) return
        if (windows.isNotEmpty()) detach()
        lastGeometry = geometry
        val (width, height, nav) = geometry
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
                if (edge == 0) x = nav.left
                if (edge == 1) x = nav.right
                // Keep system navigation buttons accessible. In gesture mode the zone follows the bottom edge.
                if (edge == 2) y = if (nav.bottom >= (32 * density).toInt()) nav.bottom else 0
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
        private var x = 0f
        private var y = 0f
        private var time = 0L
        private var armed = false
        private var fired = false
        private var cancelled = false
        private var anchorX = 0f
        private var anchorY = 0f
        private var path = Path()
        private val hold = Runnable {
            if (armed && !cancelled && !fired && allowed()) {
                fired = true
                navigate(GLOBAL_ACTION_RECENTS)
            }
        }
        @SuppressLint("ClickableViewAccessibility") // Unused touches are replayed to the underlying app.
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (replaying || !allowed()) return false
                    x = event.rawX; y = event.rawY; time = event.eventTime
                    armed = false; fired = false; cancelled = false
                    path = Path().apply { moveTo(x, y) }
                    touchable(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.rawX, event.rawY)
                    val dx = event.rawX - x
                    val dy = event.rawY - y
                    val distance = when (edge) { 0 -> dx; 1 -> -dx; else -> -dy }
                    val perpendicular = if (edge == 2) abs(dx) else abs(dy)
                    if (!armed && !cancelled && event.eventTime - time < 1000 &&
                        distance >= (if (edge == 2) 10 else 24) * density && perpendicular <= distance) {
                        armed = true
                        anchorX = event.rawX; anchorY = event.rawY
                        if (edge == 2) handler.postDelayed(hold, 350)
                    } else if (armed && !fired) {
                        if (distance < 8 * density) {
                            cancelled = true
                            handler.removeCallbacks(hold)
                        } else if (edge == 2 && (abs(event.rawX - anchorX) > 12 * density || abs(event.rawY - anchorY) > 12 * density)) {
                            anchorX = event.rawX; anchorY = event.rawY
                            handler.removeCallbacks(hold)
                            handler.postDelayed(hold, 350)
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> { cancelled = true; handler.removeCallbacks(hold) }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(hold)
                    if (armed && !cancelled && !fired && allowed()) {
                        navigate(if (edge == 2) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                    } else if (!armed && !cancelled && !fired) {
                        // The app underneath must receive ordinary edge touches too.
                        path.lineTo(event.rawX + 0.1f, event.rawY + 0.1f)
                        replaying = true
                        val duration = (event.eventTime - time).coerceIn(50L, 3000L)
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
                        fun finish() { replaying = false; if (allowed()) touchable(true) else detach() }
                        handler.postDelayed({
                            if (!allowed()) { finish(); return@postDelayed }
                            if (!service.dispatchGesture(gesture, object : GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) { finish() }
                                override fun onCancelled(gestureDescription: GestureDescription?) { finish() }
                            }, handler)) finish()
                        }, 70)
                        handler.postDelayed({ if (replaying) finish() }, duration + 1200)
                    }
                    if (!replaying) touchable(true)
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(hold); touchable(true) }
            }
            return true
        }
    }

    private fun navigate(action: Int) {
        if (!service.performGlobalAction(action)) {
            android.widget.Toast.makeText(this, app.olauncher.R.string.not_working,
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun detach() {
        handler.removeCallbacksAndMessages(null)
        replaying = false
        windows.forEach { runCatching { manager.removeView(it) } }
        windows.clear()
        lastGeometry = null
    }
    fun onWindowChanged() { refresh() }
    fun onConfigurationChanged() { refresh() }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) { handler.post { refresh() } }
    fun stop() {
        if (connected === this) connection.clear()
        store.unregisterOnSharedPreferenceChangeListener(this)
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        detach()
    }
    companion object {
        private var connection = java.lang.ref.WeakReference<XiaomiGestureController>(null)
        val connected: XiaomiGestureController? get() = connection.get()
    }
}

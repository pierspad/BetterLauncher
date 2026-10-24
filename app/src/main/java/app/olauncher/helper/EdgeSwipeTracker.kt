package app.olauncher.helper

import kotlin.math.abs
import kotlin.math.max

/** Coordinates are in dp; time uses Android's monotonic event clock. */
internal class EdgeSwipeTracker(private val edge: Int) {
    private var startX = 0f
    private var startY = 0f
    private var started = 0L
    private var anchorX = 0f
    private var anchorY = 0f
    private var settledAt = 0L
    var armed = false
        private set
    var cancelled = false
        private set
    var fired = false
        private set
    var maxMotion = 0f
        private set

    fun start(x: Float, y: Float, time: Long) {
        startX = x; startY = y; started = time
        anchorX = x; anchorY = y; settledAt = time
        armed = false; cancelled = false; fired = false; maxMotion = 0f
    }

    fun move(x: Float, y: Float, time: Long) {
        if (cancelled || fired) return
        val dx = x - startX
        val dy = y - startY
        maxMotion = max(maxMotion, max(abs(dx), abs(dy)))
        val distance = when (edge) { 0 -> dx; 1 -> -dx; else -> -dy }
        val sideways = if (edge == 2) abs(dx) else abs(dy)
        val threshold = if (edge == 2) 32f else 24f
        if (!armed) {
            if (time - started <= 1500 && distance >= threshold && sideways <= distance * 0.8f) {
                armed = true
                anchorX = x; anchorY = y; settledAt = time
            }
        } else if (distance < threshold / 2 || sideways > distance * 1.2f) {
            cancel()
        } else if (edge == 2 && max(abs(x - anchorX), abs(y - anchorY)) > 24f) {
            // Allow natural finger drift while holding; continuous travel still
            // reanchors the timer instead of accidentally opening Recents.
            anchorX = x; anchorY = y; settledAt = time
        }
    }

    fun holdDelay(now: Long): Long? = if (edge == 2 && armed && !cancelled && !fired)
        (300L - (now - settledAt)).coerceAtLeast(0L) else null

    fun takeHold(now: Long): Boolean {
        if (holdDelay(now) != 0L) return false
        fired = true
        return true
    }

    fun cancel() { cancelled = true }
}

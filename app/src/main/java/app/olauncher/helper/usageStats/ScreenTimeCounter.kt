package app.olauncher.helper.usageStats

/** Counts the union of foreground use, never the sum of overlapping app sessions.
 * Event numbers are Android UsageEvents.Event constants; kept Android-free for regression tests.
 */
internal class ScreenTimeCounter(private val start: Long, private val end: Long, private val launcher: String) {
    private var foreground: Pair<String, String?>? = null
    private var interactive = true
    private var unlocked = true
    private var previous = start
    private var total = 0L

    fun accept(timestamp: Long, type: Int, packageName: String?, className: String?) {
        if (timestamp > end) return
        val time = timestamp.coerceAtLeast(start).coerceAtLeast(previous)
        if (interactive && unlocked && foreground != null && foreground?.first != launcher) {
            total += time - previous
        }
        previous = time
        when (type) {
            1, 4 -> if (packageName != null) foreground = packageName to className
            2, 23 -> if (foreground == (packageName to className)) foreground = null
            3 -> foreground = null // daily rollover: wait for CONTINUE_PREVIOUS_DAY
            15 -> interactive = true
            16 -> interactive = false
            17 -> unlocked = false
            18 -> unlocked = true
            26, 27 -> { // shutdown/startup: no activity survives a reboot
                foreground = null
                interactive = false
                unlocked = false
            }
        }
    }

    fun finish(): Long {
        accept(end, 0, null, null)
        return total
    }
}

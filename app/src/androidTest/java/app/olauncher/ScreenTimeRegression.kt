package app.olauncher

import app.olauncher.helper.usageStats.ScreenTimeCounter

fun main() = runScreenTimeRegression()

fun runScreenTimeRegression() {
    fun count(start: Long = 0, end: Long = 100, events: ScreenTimeCounter.() -> Unit): Long =
        ScreenTimeCounter(start, end, "launcher").apply(events).finish()
    check(count { } == 0L) { "Empty history must not invent midnight-to-now use" }
    check(count {
        accept(10, 1, "a", "A")
        accept(20, 1, "b", "B") // missing pause for A
        accept(30, 2, "a", "A") // delayed pause must not stop B
        accept(40, 2, "b", "B")
    } == 30L) { "Overlapping packages must count once" }
    check(count {
        accept(0, 1, "a", "A")
        accept(10, 1, "a", "A") // duplicate resume
        accept(20, 16, null, null)
        accept(80, 15, null, null)
        accept(90, 2, "a", "A")
    } == 30L) { "Screen-off time must be excluded without losing the active app" }
    check(count {
        accept(0, 1, "a", "A")
        accept(20, 17, null, null)
        accept(80, 18, null, null)
    } == 40L) { "Locked time must be excluded" }
    check(count(start = 50) {
        accept(10, 1, "a", "A")
        accept(70, 2, "a", "A")
        accept(80, 23, "a", "A")
    } == 20L) { "Midnight carry-over must be clipped and duplicate closes ignored" }
    check(count {
        accept(10, 2, "a", "A")
        accept(20, 1, "launcher", "Home")
        accept(40, 1, "a", "A")
        accept(60, 26, null, null)
        accept(80, 27, null, null)
    } == 20L) { "Unmatched closes, launcher and reboot downtime must not inflate time" }
    check(count {
        accept(10, 1, "a", "A")
        accept(20, 1, "a", "B")
        accept(30, 23, "a", "A")
        accept(50, 2, "a", "B")
    } == 40L) { "Activity transitions must not double count or close the wrong activity" }
    check(count(start = 50) {
        accept(10, 1, "a", "A")
        accept(20, 16, null, null)
    } == 0L) { "A previous-day app on a sleeping phone must not count today" }
    val hour = 3_600_000L
    check(count(end = 10 * hour) {
        accept(0, 1, "a", "A")
        accept(hour, 1, "b", "B") // stale A must not add another nine hours
        accept(3 * hour, 16, null, null) // missing activity pause at screen-off
    } == 3 * hour) { "Three hours of use followed by seven hours asleep must remain three hours" }
    println("Screen time regressions passed")
}

package app.olauncher.helper

import app.olauncher.data.Prefs

/**
 * Soft "use it less" limiter. Unlike the biometric app-lock, this adds *friction*
 * rather than a hard wall: re-opening a limited app too soon triggers a cooldown,
 * and each impatient retry during that cooldown escalates the wait up an
 * (entirely parametric) ladder. Letting the timer run out and then opening the app
 * resets the ladder back to zero — good behaviour is rewarded.
 *
 * The whole policy is a small state machine evaluated at launch time. State lives in
 * [Prefs] keyed by "package|user"; this object stays stateless and side-effect-explicit.
 */
object AppLimiter {

    /** Outcome of a launch attempt for a limited app. */
    sealed interface Decision {
        /** Launch may proceed. */
        data object Allow : Decision
        /** Launch is blocked until [untilMillis] (epoch ms); show the countdown. */
        data class Block(val untilMillis: Long) : Decision
    }

    private fun ladderMs(prefs: Prefs): List<Long> =
        prefs.appLimitLadderMinutes
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0 }
            .map { it * 60_000L }
            .ifEmpty { listOf(60_000L) } // sane fallback: 1 minute

    // Duration for escalation [step] (1-based). Clamped to the ladder's last entry (the cap).
    private fun durationFor(ladder: List<Long>, step: Int): Long =
        ladder[(step - 1).coerceIn(0, ladder.lastIndex)]

    /**
     * Evaluates — and persists — the cooldown state for [key] at time [now].
     * Returns [Decision.Allow] (state already updated for a clean open) or
     * [Decision.Block] with the moment the app becomes available again.
     */
    fun evaluate(prefs: Prefs, key: String, now: Long): Decision {
        if (!prefs.appLimitEnabled) return Decision.Allow

        val ladder = ladderMs(prefs)
        val level = prefs.limitLevel(key)       // 0 = no cooldown currently in effect
        val until = prefs.limitUntil(key)
        val lastOpen = prefs.limitLastOpen(key)
        val windowMs = prefs.appLimitRecentWindowMin.coerceAtLeast(0) * 60_000L

        // 1) A cooldown is currently running and the user is knocking early → escalate.
        if (level >= 1 && until > now) {
            val newLevel = level + 1
            val newUntil = now + durationFor(ladder, newLevel)
            prefs.setLimitLevel(key, newLevel)
            prefs.setLimitUntil(key, newUntil)
            return Decision.Block(newUntil)
        }

        // 2) A cooldown ran and has fully elapsed → the user waited it out. Clean open, reset.
        if (level >= 1) {
            prefs.setLimitLevel(key, 0)
            prefs.setLimitUntil(key, 0L)
            prefs.setLimitLastOpen(key, now)
            return Decision.Allow
        }

        // 3) No cooldown active. Re-opening within the grace window starts the first one.
        if (windowMs > 0 && lastOpen > 0 && now - lastOpen < windowMs) {
            val newUntil = now + durationFor(ladder, 1)
            prefs.setLimitLevel(key, 1)
            prefs.setLimitUntil(key, newUntil)
            return Decision.Block(newUntil)
        }

        // 4) Free, well-behaved open: just remember when.
        prefs.setLimitLastOpen(key, now)
        return Decision.Allow
    }
}

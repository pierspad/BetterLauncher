package app.olauncher.helper

import android.os.Build
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
        /** Launch is blocked; show the countdown and reasons. */
        data class Block(
            val untilMillis: Long,
            val isSevere: Boolean,
            val penaltyMinutes: Int,
            val compulsivenessLevel: Int,
            val totalBanMinutes: Int
        ) : Decision
    }

    fun durationForStep(step: Int): Long {
        val minutes = when (step) {
            1 -> 5
            2 -> 10
            3 -> 20
            4 -> 35
            5 -> 60
            6 -> 120
            7 -> 240
            8 -> 480
            9 -> 960
            else -> 1440
        }
        return minutes * 60_000L
    }

    /**
     * Minutes added to a running cooldown for the [retryCount]-th impatient retry.
     * Single source of truth — also used by MainViewModel to re-derive the penalty
     * when the dialog is triggered from the accessibility service.
     */
    fun penaltyMinutesForRetry(retryCount: Int): Int = when (retryCount) {
        2 -> 15
        3 -> 30
        4 -> 60
        5 -> 120
        else -> 240
    }

    /**
     * Day counter that rolls over at *local* midnight (a plain `now / 86_400_000`
     * would roll over at UTC midnight — 1–2 AM in Italy). Used for the daily
     * severity decay, both here and in MainActivity.
     */
    fun localDayNumber(now: Long): Long =
        (now + java.util.TimeZone.getDefault().getOffset(now)) / (24 * 3600 * 1000L)

    /**
     * Evaluates — and persists — the cooldown state for [key] at time [now].
     * Returns [Decision.Allow] (state already updated for a clean open) or
     * [Decision.Block] with the moment the app becomes available again.
     */
    fun evaluate(prefs: Prefs, key: String, now: Long): Decision {
        if (!prefs.appLimitEnabled) return Decision.Allow

        val level = prefs.limitLevel(key)       // 0 = no severity currently in effect
        val until = prefs.limitUntil(key)

        // 1) A cooldown is currently running and the user is knocking early → escalate (Tier 1).
        if (until > now) {
            val retries = prefs.limitRetryCount(key)
            if (retries == 0) {
                // 1st click during this cooldown: Pena di Compulsività
                prefs.setLimitRetryCount(key, 1)
                val banMin = (durationForStep(level) / 60_000L).toInt()
                return Decision.Block(
                    untilMillis = until,
                    isSevere = false,
                    penaltyMinutes = 0,
                    compulsivenessLevel = level,
                    totalBanMinutes = banMin
                )
            } else {
                // 2nd or later click during this cooldown: Pena grave
                val newRetries = retries + 1
                prefs.setLimitRetryCount(key, newRetries)

                // Tier 1 Punishment: Add progressive penalty minutes to existing cooldown.
                val penaltyMin = penaltyMinutesForRetry(newRetries)
                val penaltyMs = penaltyMin * 60_000L
                val newUntil = minOf(now + 24 * 3600 * 1000L, until + penaltyMs)
                prefs.setLimitUntil(key, newUntil)

                // Increase the compulsiveness level (Pena grave increases compulsiveness)
                val newLevel = level + 1
                prefs.setLimitLevel(key, newLevel)

                return Decision.Block(
                    untilMillis = newUntil,
                    isSevere = true,
                    penaltyMinutes = penaltyMin,
                    compulsivenessLevel = newLevel,
                    totalBanMinutes = ((newUntil - now) / 60_000L).toInt()
                )
            }
        }

        // 2) No cooldown active (either it elapsed or was never set).
        // Check daily decay first (Tier 2):
        val today = localDayNumber(now)
        val lastDay = prefs.limitLastOpenDay(key)
        var currentLevel = level
        if (lastDay > 0 && today > lastDay) {
            val daysPassed = (today - lastDay).toInt()
            currentLevel = maxOf(0, currentLevel - daysPassed)
        }

        // Increment severity level for this successful open session (Tier 2).
        val newLevel = currentLevel + 1
        prefs.setLimitLevel(key, newLevel)
        prefs.setLimitLastOpenDay(key, today)

        // Mark that this limited app is now open
        prefs.lastOpenedLimitedApp = key

        return Decision.Allow
    }
}

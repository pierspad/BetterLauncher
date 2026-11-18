package app.olauncher.helper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WallpaperPeriod(val key: String, val evening: Boolean) {
    companion object {
        const val DEFAULT_MORNING_START = 6 * 60
        const val DEFAULT_EVENING_START = 18 * 60
        const val MINUTES_PER_DAY = 24 * 60

        fun nextOccurrence(now: Calendar, start: Int): Long =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, start / 60)
                set(Calendar.MINUTE, start % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DATE, 1)
            }.timeInMillis

        fun current(
            now: Calendar,
            twiceDaily: Boolean,
            morningStart: Int = DEFAULT_MORNING_START,
            eveningStart: Int = DEFAULT_EVENING_START,
        ): WallpaperPeriod {
            val valid = morningStart in 0 until MINUTES_PER_DAY &&
                eveningStart in 0 until MINUTES_PER_DAY && morningStart != eveningStart
            val morning = if (valid) morningStart else DEFAULT_MORNING_START
            val eveningBegin = if (valid) eveningStart else DEFAULT_EVENING_START
            val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val evening = twiceDaily && if (eveningBegin < morning) {
                minute in eveningBegin until morning
            } else {
                minute >= eveningBegin || minute < morning
            }
            val phaseStart = if (evening) eveningBegin else morning
            val date = (now.clone() as Calendar).apply {
                if (twiceDaily && minute < phaseStart) add(Calendar.DATE, -1)
            }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = date.timeZone
            }.format(date.time)
            val key = if (!twiceDaily) day else if (evening) "$day:evening" else "$day:morning"
            return WallpaperPeriod(key, evening)
        }
    }
}

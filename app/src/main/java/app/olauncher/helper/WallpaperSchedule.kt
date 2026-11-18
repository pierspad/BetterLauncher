package app.olauncher.helper

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object WallpaperSchedule {
    const val TOKEN = "wallpaper_schedule_token"
    const val START = "wallpaper_start_minute"
    const val DUE = "wallpaper_due_at"
    private const val TAG = "wallpaper_phase"

    fun restart(context: Context, prefs: Prefs) {
        prefs.wallpaperScheduleToken = UUID.randomUUID().toString()
        cancel(context)
        ensureScheduled(context, prefs)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
            cancelAllWorkByTag(TAG)
        }
    }

    fun ensureScheduled(context: Context, prefs: Prefs) {
        val token = prefs.wallpaperScheduleToken
        if (token.isEmpty()) {
            restart(context, prefs)
            return
        }
        val now = Calendar.getInstance()
        if (prefs.wallpaperTwiceDaily) {
            enqueueNext(context, token, prefs.wallpaperMorningStart, now)
            enqueueNext(context, token, prefs.wallpaperEveningStart, now)
        } else {
            enqueueNext(context, token, 0, now)
        }
    }

    fun enqueueNext(context: Context, token: String, start: Int, now: Calendar) =
        enqueue(context, token, start, WallpaperPeriod.nextOccurrence(now, start), now.timeInMillis)

    private fun enqueue(context: Context, token: String, start: Int, due: Long, now: Long) =
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$TAG:$token:$start:$due",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(workDataOf(TOKEN to token, START to start, DUE to due))
                .setInitialDelay((due - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
        )
}

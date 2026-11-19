package app.olauncher.widget

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import app.olauncher.helper.LocalPhotos
import app.olauncher.helper.WallpaperPeriod
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PhotoWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt(ID, -1)
        val token = inputData.getString(TOKEN)
        val start = inputData.getInt(START, -1)
        val due = inputData.getLong(DUE, -1)
        val settings = PhotoWidgetStore.get(applicationContext, id) ?: return Result.success()
        if (settings.token != token || settings.mode == PhotoWidgetSettings.FIXED ||
            start < 0 || due < 0 || !PhotoWidgetProvider.isBound(applicationContext, id)
        ) return Result.success()

        val now = Calendar.getInstance()
        if (now.timeInMillis < due) return Result.retry()
        enqueue(applicationContext, id, settings, start, now).await()
        val twice = settings.mode == PhotoWidgetSettings.TWICE_DAILY
        fun periodAt(time: Calendar) = WallpaperPeriod.current(time, twice, settings.morningStart, settings.eveningStart)
        val current = periodAt(now)
        val scheduled = periodAt((now.clone() as Calendar).apply { timeInMillis = due })
        if (current != scheduled || current.key == settings.lastPeriod) return Result.success()

        val (bitmap, lastPhoto) = LocalPhotos.nextInFolder(
            applicationContext, settings.source.toUri(), settings.lastPhoto, PhotoWidgetStore.MAX_IMAGE_SIDE
        ) ?: return Result.failure()
        try {
            if (periodAt(Calendar.getInstance()) == current) {
                PhotoWidgetStore.save(applicationContext, id, settings.copy(lastPhoto = lastPhoto, lastPeriod = current.key),
                    bitmap, expectedToken = settings.token)
            }
            return Result.success()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val ID = "widget_id"
        private const val TOKEN = "token"
        private const val START = "start"
        private const val DUE = "due"

        private fun tag(id: Int) = "photo-widget-$id"

        internal fun cancel(context: Context, id: Int) = WorkManager.getInstance(context).cancelAllWorkByTag(tag(id))

        internal fun schedule(context: Context, id: Int, settings: PhotoWidgetSettings) {
            val now = Calendar.getInstance()
            when (settings.mode) {
                PhotoWidgetSettings.DAILY -> enqueue(context, id, settings, 0, now)
                PhotoWidgetSettings.TWICE_DAILY -> {
                    enqueue(context, id, settings, settings.morningStart, now)
                    enqueue(context, id, settings, settings.eveningStart, now)
                }
            }
        }

        private fun enqueue(context: Context, id: Int, settings: PhotoWidgetSettings, start: Int, now: Calendar): androidx.work.Operation {
            val due = WallpaperPeriod.nextOccurrence(now, start)
            return WorkManager.getInstance(context).enqueueUniqueWork(
                "${tag(id)}:${settings.token}:$start:$due",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PhotoWidgetWorker>()
                    .setInputData(workDataOf(ID to id, TOKEN to settings.token, START to start, DUE to due))
                    .setInitialDelay(due - now.timeInMillis, TimeUnit.MILLISECONDS)
                    .addTag(tag(id))
                    .build()
            )
        }
    }
}

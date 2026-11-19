package app.olauncher.helper

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.await
import app.olauncher.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Calendar

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = inputData.getString(WallpaperSchedule.TOKEN)
        val start = inputData.getInt(WallpaperSchedule.START, -1)
        val due = inputData.getLong(WallpaperSchedule.DUE, -1)
        fun scheduleIsActive() = prefs.dailyWallpaper && token == prefs.wallpaperScheduleToken
        if (token == null || start < 0 || due < 0 || !scheduleIsActive()) return@withContext Result.success()

        val now = Calendar.getInstance()
        if (now.timeInMillis < due) return@withContext Result.retry()
        // Keep the next occurrence queued independently of this occurrence's image or result.
        WallpaperSchedule.enqueueNext(applicationContext, token, start, now).await()

        val twiceDaily = prefs.wallpaperTwiceDaily
        val morningStart = prefs.wallpaperMorningStart
        val eveningStart = prefs.wallpaperEveningStart
        fun periodAt(time: Calendar) = WallpaperPeriod.current(time, twiceDaily, morningStart, eveningStart)
        val period = periodAt(now)
        val scheduledPeriod = periodAt((now.clone() as Calendar).apply { timeInMillis = due })
        if (scheduledPeriod != period || prefs.dailyWallpaperLastDay == period.key) return@withContext Result.success()

        val mainFolder = prefs.dailyWallpaperFolder
        val eveningFolder = prefs.wallpaperEveningFolder
        if (mainFolder.isEmpty()) return@withContext Result.success()
        val separateEvening = period.evening && eveningFolder.isNotEmpty() && eveningFolder != mainFolder
        val folder = if (separateEvening) eveningFolder else mainFolder
        val lastPhoto = if (separateEvening) prefs.wallpaperEveningLastPhoto else prefs.dailyWallpaperLastPhoto
        val fit = prefs.wallpaperFitImage
        fun sourceIsActive() = scheduleIsActive() && periodAt(Calendar.getInstance()) == period &&
            prefs.dailyWallpaperFolder == mainFolder && prefs.wallpaperEveningFolder == eveningFolder &&
            prefs.wallpaperTwiceDaily == twiceDaily && prefs.wallpaperMorningStart == morningStart &&
            prefs.wallpaperEveningStart == eveningStart && prefs.wallpaperFitImage == fit
        val (width, height) = getScreenDimensions(applicationContext)
        val photo = LocalPhotos.nextInFolder(applicationContext, folder.toUri(), lastPhoto, maxOf(width, height, 1))
            ?: return@withContext Result.failure()
        val (bitmap, documentId) = photo
        try {
            if (!setWallpaper(applicationContext, bitmap, fit, ::sourceIsActive)) {
                return@withContext if (sourceIsActive()) Result.failure() else Result.success()
            }
            if (sourceIsActive()) {
                if (separateEvening) prefs.wallpaperEveningLastPhoto = documentId
                else prefs.dailyWallpaperLastPhoto = documentId
                prefs.dailyWallpaperLastDay = period.key
            }
            Result.success()
        } finally {
            bitmap.recycle()
        }
    }

}

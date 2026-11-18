package app.olauncher.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
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
        val photo = loadLocalPhoto(folder.toUri(), lastPhoto) ?: return@withContext Result.failure()
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

    private suspend fun loadLocalPhoto(tree: Uri, lastPhoto: String): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val resolver = applicationContext.contentResolver
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val imageIds = mutableListOf<String>()
            resolver.query(
                children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1)?.startsWith("image/") == true) imageIds.add(cursor.getString(0))
                }
            }
            imageIds.sort()
            val lastIndex = imageIds.indexOf(lastPhoto)
            for (offset in imageIds.indices) {
                ensureActive()
                val id = imageIds[(lastIndex + 1 + offset) % imageIds.size]
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val bitmap = try {
                    decodeLocalPhoto(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DailyWallpaper", "Could not decode local wallpaper", e)
                    null
                }
                if (bitmap != null) return@withContext bitmap to id
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DailyWallpaper", "Could not read the wallpaper folder", e)
            null
        }
    }

    private fun decodeLocalPhoto(uri: Uri): Bitmap? {
        val resolver = applicationContext.contentResolver
        val (width, height) = getScreenDimensions(applicationContext)
        val maxSide = maxOf(width, height, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sourceSide = maxOf(info.size.width, info.size.height)
                if (sourceSide > maxSide) {
                    val scale = maxSide.toDouble() / sourceSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize / 2 >= maxSide) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}

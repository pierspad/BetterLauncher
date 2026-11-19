package app.olauncher.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.core.content.edit
import androidx.core.net.toUri
import app.olauncher.data.Prefs
import app.olauncher.helper.WallpaperPeriod
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal data class PhotoWidgetSettings(
    val source: String,
    val mode: Int,
    val crop: Boolean,
    val token: String,
    val sourceName: String = "",
    val lastPhoto: String = "",
    val lastPeriod: String = "",
    val morningStart: Int = WallpaperPeriod.DEFAULT_MORNING_START,
    val eveningStart: Int = WallpaperPeriod.DEFAULT_EVENING_START,
) {
    companion object {
        const val FIXED = 0
        const val DAILY = 1
        const val TWICE_DAILY = 2
    }
}

internal object PhotoWidgetStore {
    const val MAX_IMAGE_SIDE = 640
    private fun prefs(context: Context) = context.getSharedPreferences("photo_widgets", Context.MODE_PRIVATE)
    private fun file(context: Context, id: Int) = AtomicFile(File(context.noBackupFilesDir, "photo-widget-$id.jpg"))

    fun get(context: Context, id: Int): PhotoWidgetSettings? {
        val raw = prefs(context).getString(id.toString(), null) ?: return null
        val json = JSONObject(raw)
        return PhotoWidgetSettings(
            json.getString("source"), json.getInt("mode"), json.getBoolean("crop"), json.getString("token"),
            json.optString("sourceName"), json.optString("lastPhoto"), json.optString("lastPeriod"),
            json.optInt("morningStart", WallpaperPeriod.DEFAULT_MORNING_START),
            json.optInt("eveningStart", WallpaperPeriod.DEFAULT_EVENING_START),
        )
    }

    fun usesFolder(context: Context, uri: String): Boolean = prefs(context).all.keys.any { key ->
        val config = key.toIntOrNull()?.let { get(context, it) }
        config != null && config.mode != PhotoWidgetSettings.FIXED && config.source == uri
    }

    @Synchronized
    fun read(context: Context, id: Int): Bitmap? = try {
        file(context, id).openRead().use(BitmapFactory::decodeStream)
    } catch (_: IOException) {
        null
    }

    @Synchronized
    fun render(context: Context, id: Int) {
        val bitmap = read(context, id)
        try {
            PhotoWidgetProvider.display(context, id, bitmap, get(context, id)?.crop == true)
        } finally {
            bitmap?.recycle()
        }
    }

    @Synchronized
    fun save(context: Context, id: Int, settings: PhotoWidgetSettings, bitmap: Bitmap, expectedToken: String? = null): Boolean {
        if (!PhotoWidgetProvider.isBound(context, id)) return false
        val previous = get(context, id)
        if (expectedToken != null && previous?.token != expectedToken) return false
        val file = file(context, id)
        val stream = try {
            file.startWrite()
        } catch (_: IOException) {
            return false
        }
        val saved = try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        } catch (_: IOException) {
            false
        }
        if (!saved) {
            file.failWrite(stream)
            return false
        }
        file.finishWrite(stream)
        val json = JSONObject()
            .put("source", settings.source).put("mode", settings.mode).put("crop", settings.crop)
            .put("token", settings.token).put("sourceName", settings.sourceName).put("lastPhoto", settings.lastPhoto).put("lastPeriod", settings.lastPeriod)
            .put("morningStart", settings.morningStart).put("eveningStart", settings.eveningStart)
        prefs(context).edit { putString(id.toString(), json.toString()) }
        if (previous != null && previous.mode != PhotoWidgetSettings.FIXED && previous.source != settings.source) {
            releaseUnusedFolder(context, previous.source)
        }
        PhotoWidgetProvider.display(context, id, bitmap, settings.crop)
        return true
    }

    @Synchronized
    fun remove(context: Context, id: Int) {
        val settings = get(context, id)
        prefs(context).edit { remove(id.toString()) }
        file(context, id).delete()
        if (settings != null && settings.mode != PhotoWidgetSettings.FIXED) releaseUnusedFolder(context, settings.source)
    }

    fun releaseUnusedFolder(context: Context, source: String) {
        val system = Prefs(context)
        if (source == system.dailyWallpaperFolder || source == system.wallpaperEveningFolder ||
            usesFolder(context, source)
        ) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(source.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

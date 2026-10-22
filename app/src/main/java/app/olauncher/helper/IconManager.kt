package app.olauncher.helper

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.util.Xml
import android.util.LruCache
import androidx.lifecycle.MutableLiveData
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import com.caverock.androidsvg.SVG
import org.xmlpull.v1.XmlPullParser

object IconManager {
    @Volatile
    private var appFilterIndex: LawniconIndex? = null

    @Volatile
    private var initialized = false
    private val initLock = Any()
    private var initializing = false
    val ready = MutableLiveData(false)

    private data class CachedIcon(val state: Drawable.ConstantState, val bytes: Int)
    private val iconCache = object : LruCache<String, CachedIcon>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedIcon) = value.bytes
    }

    private fun cached(context: Context, key: String): Drawable? =
        iconCache.get(key)?.state?.newDrawable(context.resources)?.mutate()

    private fun cache(key: String, drawable: Drawable) {
        val state = drawable.constantState ?: return
        val bytes = (drawable as? BitmapDrawable)?.bitmap?.allocationByteCount ?: 32 * 1024
        iconCache.put(key, CachedIcon(state, bytes))
    }

    val hasLawnicons: Boolean
        get() = true

    fun init(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized || initializing) return
            initializing = true
            val appContext = context.applicationContext
            Thread {
                try {
                    loadAppFilter(appContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                initialized = true
                ready.postValue(true)
            }.start()
        }
    }

    private fun loadAppFilter(context: Context) {
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("lawnicons/appfilter.xml").use { inputStream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream, null)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            val cleanComponent = component.substringAfter("{").substringBefore("}")
                            map[cleanComponent] = drawable
                        }
                    }
                    eventType = parser.next()
                }
            }
            appFilterIndex = LawniconIndex(map)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLawnicon(context: Context, packageName: String, activityName: String?): Drawable? {
        val index = appFilterIndex ?: return null
        val drawableName = index.drawableFor(packageName, activityName)

        if (drawableName != null) {
            try {
                val svgAssetPath = "lawnicons/svgs/$drawableName.svg"
                context.assets.open(svgAssetPath).use { inputStream ->
                    val svg = SVG.getFromInputStream(inputStream)
                    val size = (72 * context.resources.displayMetrics.density).toInt()
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    svg.renderToCanvas(canvas)
                    return BitmapDrawable(context.resources, bitmap)
                }
            } catch (e: Exception) {
                // SVG not found or fails to load/parse
            }
        }
        return null
    }

    fun getAppIcon(context: Context, packageName: String, userHandle: UserHandle, tintColor: Int? = null): Drawable? {
        init(context)
        val colorful = Prefs(context).useColorfulIcons
        val cacheKey = "$packageName|$userHandle|$tintColor|$colorful|$initialized"
        cached(context, cacheKey)?.let { return it }

        var drawable: Drawable? = null

        // 1. Try Lawnicons first
        if (!colorful) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val activityName = launchIntent?.component?.className
            val lawnicon = getLawnicon(context, packageName, activityName)
            if (lawnicon != null) {
                drawable = lawnicon
                if (tintColor != null) {
                    drawable.mutate().setTint(tintColor)
                }
            }
        }

        // 2. Fallback to system icon
        if (drawable == null) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val activities = launcherApps.getActivityList(packageName, userHandle)
                if (activities.isNotEmpty()) {
                    drawable = activities[0].getIcon(0)
                } else {
                    drawable = context.packageManager.getApplicationIcon(packageName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (drawable != null) {
            cache(cacheKey, drawable)
        }
        return drawable
    }

    fun getShortcutIcon(context: Context, packageName: String, shortcutId: String, userHandle: UserHandle): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val cacheKey = "$packageName|shortcut|$shortcutId|$userHandle"
        cached(context, cacheKey)?.let { return it }

        var drawable: Drawable? = null
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setShortcutIds(listOf(shortcutId))
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            }
            val shortcuts = launcherApps.getShortcuts(query, userHandle)
            val shortcutInfo = shortcuts?.firstOrNull()
            if (shortcutInfo != null) {
                drawable = launcherApps.getShortcutIconDrawable(shortcutInfo, context.resources.displayMetrics.densityDpi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (drawable != null) {
            cache(cacheKey, drawable)
        }
        return drawable
    }

    fun getModelIcon(context: Context, appModel: AppModel, tintColor: Int): Drawable? {
        return when (appModel) {
            is AppModel.App -> {
                getAppIcon(context, appModel.appPackage, appModel.user, tintColor)
            }
            is AppModel.PinnedShortcut -> {
                var icon = getShortcutIcon(context, appModel.appPackage, appModel.shortcutId, appModel.user)
                if (icon == null) {
                    icon = getAppIcon(context, appModel.appPackage, appModel.user, tintColor)
                }
                icon
            }
            is AppModel.SettingTile -> {
                val icon = ContextCompat.getDrawable(context, R.drawable.ic_sc_settings)
                icon?.mutate()?.apply { setTint(tintColor) }
            }
            is AppModel.Contact -> {
                val icon = ContextCompat.getDrawable(context, R.drawable.ic_sc_person)
                icon?.mutate()?.apply { setTint(tintColor) }
            }
            is AppModel.FolderHeader -> {
                val icon = ContextCompat.getDrawable(context, R.drawable.ic_sc_folder)
                icon?.mutate()?.apply { setTint(tintColor) }
            }
            else -> null
        }
    }

    /** Invalidates every tint/style and pinned shortcut cached for this package/profile. */
    fun invalidatePackage(packageName: String, userHandle: UserHandle) {
        val appPrefix = "$packageName|$userHandle|"
        val shortcutPrefix = "$packageName|shortcut|"
        val shortcutSuffix = "|$userHandle"
        for (key in iconCache.snapshot().keys) {
            if (key.startsWith(appPrefix) ||
                (key.startsWith(shortcutPrefix) && key.endsWith(shortcutSuffix))) {
                iconCache.remove(key)
            }
        }
    }

    fun clearCache() {
        iconCache.evictAll()
    }
}

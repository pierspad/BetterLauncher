package app.olauncher.helper

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.util.Xml
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import org.xmlpull.v1.XmlPullParser

object IconManager {
    private var lawniconsContext: Context? = null
    private var appFilterMap: Map<String, String>? = null

    @Volatile
    private var initialized = false
    private val initLock = Any()

    private val iconCache = mutableMapOf<String, Drawable>()

    fun init(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            val appContext = context.applicationContext
            Thread {
                val lawniconsPackages = listOf("app.lawnchair.lawnicons", "app.lawnchair.lawnicons.dev")
                for (pkg in lawniconsPackages) {
                    try {
                        lawniconsContext = appContext.createPackageContext(pkg, 0)
                        loadAppFilter()
                        if (appFilterMap != null) break
                    } catch (e: Exception) {
                        // Lawnicons package not found or fails to load
                    }
                }
                initialized = true
            }.start()
        }
    }

    private fun loadAppFilter() {
        val iconCtx = lawniconsContext ?: return
        val map = mutableMapOf<String, String>()
        try {
            iconCtx.assets.open("appfilter.xml").use { inputStream ->
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
            appFilterMap = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLawnicon(packageName: String, activityName: String?): Drawable? {
        val iconCtx = lawniconsContext ?: return null
        val filterMap = appFilterMap ?: return null
        val actName = activityName ?: ""

        val key = "$packageName/$actName"
        var drawableName = filterMap[key]
        if (drawableName == null) {
            // Fallback: match any key starting with "$packageName/"
            val prefix = "$packageName/"
            val firstMatch = filterMap.keys.firstOrNull { it.startsWith(prefix) }
            if (firstMatch != null) {
                drawableName = filterMap[firstMatch]
            }
        }

        if (drawableName != null) {
            try {
                val resId = iconCtx.resources.getIdentifier(drawableName, "drawable", iconCtx.packageName)
                if (resId != 0) {
                    return ContextCompat.getDrawable(iconCtx, resId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    fun getAppIcon(context: Context, packageName: String, userHandle: UserHandle, tintColor: Int? = null): Drawable? {
        init(context)
        val cacheKey = "$packageName|${userHandle.hashCode()}|${tintColor ?: 0}"
        synchronized(iconCache) {
            iconCache[cacheKey]?.let { return it }
        }

        var drawable: Drawable? = null

        // 1. Try Lawnicons first
        if (lawniconsContext != null) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val activityName = launchIntent?.component?.className
            val lawnicon = getLawnicon(packageName, activityName)
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
            synchronized(iconCache) {
                iconCache[cacheKey] = drawable
            }
        }
        return drawable
    }

    fun getShortcutIcon(context: Context, packageName: String, shortcutId: String, userHandle: UserHandle): Drawable? {
        val cacheKey = "$packageName|shortcut|$shortcutId|${userHandle.hashCode()}"
        synchronized(iconCache) {
            iconCache[cacheKey]?.let { return it }
        }

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
            synchronized(iconCache) {
                iconCache[cacheKey] = drawable
            }
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
}

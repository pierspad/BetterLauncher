package app.olauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.Gravity
import androidx.lifecycle.ViewModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WidgetPickerState : ViewModel() {
    var home: WidgetHomeSpace? = null
    var placed: PlacedWidgetSize? = null
    var entries: List<WidgetPickerEntry> = emptyList()
        private set
    val previews = LruCache<WidgetPreviewKey, Drawable.ConstantState>(64)

    suspend fun refresh(context: Context, manager: AppWidgetManager): List<WidgetPickerEntry> {
        val previous = entries.associateBy { it.key }
        val configuration = android.content.res.Configuration(context.resources.configuration)
        val current = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val versions = mutableMapOf<String, Long>()
            val labels = mutableMapOf<String, String>()
            manager.installedProviders.map { info ->
                val pkg = info.provider.packageName
                val version = versions.getOrPut(pkg) {
                    runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
                }
                val key = WidgetPreviewKey(info.provider, info.profile, version, configuration)
                val cached = previous[key]
                val app = cached?.app ?: labels.getOrPut(pkg) {
                    runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
                }
                val label = cached?.label ?: info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: app
                WidgetPickerEntry(info, label, app, key)
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { entry: WidgetPickerEntry -> entry.app }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
        entries = current
        return current
    }
}

internal data class WidgetPreviewKey(
    val provider: ComponentName,
    val profile: android.os.UserHandle,
    val updatedAt: Long,
    val configuration: android.content.res.Configuration,
)

internal data class WidgetPickerEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val app: String,
    val key: WidgetPreviewKey,
)

internal data class PlacedWidgetSize(val id: Int, val width: Int, val height: Int)

internal data class WidgetHomeSpace(
    val width: Int,
    val bottom: Int,
    val top: Int,
    val gap: Int,
    val rowHeight: Int,
    val apps: WidgetHomeColumn,
    val shortcuts: WidgetHomeColumn,
) {
    fun height(prefs: Prefs): Int {
        val appCount = prefs.homeAppsNum.coerceIn(0, Constants.SHORTCUT_COUNT)
        val iconCount = if (prefs.shortcutIconsEnabled) prefs.homeShortcutIconsNum.coerceIn(1, Constants.SHORTCUT_COUNT) else 0
        val count = maxOf(appCount, iconCount)
        val alignment = prefs.homeVerticalAlignment
        val appsTop = if (appCount > 0) apps.start(count * rowHeight, alignment) else bottom
        val iconsTop = if (iconCount > 0) {
            shortcuts.start(count * rowHeight, alignment) + (count - iconCount) * rowHeight
        } else bottom
        return (minOf(appsTop, iconsTop, bottom) - gap - top).coerceAtLeast(0)
    }
}

internal data class WidgetHomeColumn(val top: Int, val height: Int, val paddingTop: Int, val paddingBottom: Int) {
    fun start(contentHeight: Int, alignment: Int): Int = top + when (alignment) {
        Gravity.BOTTOM -> height - paddingBottom - contentHeight
        Gravity.CENTER_VERTICAL -> paddingTop + (height - paddingTop - paddingBottom - contentHeight) / 2
        else -> paddingTop
    }
}

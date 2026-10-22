package app.olauncher.helper

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.view.ViewGroup

/** Returns the attached host view for this binding, creating it when needed. */
internal fun ViewGroup.bindWidgetHostView(
    widgetId: Int,
    providerInfo: AppWidgetProviderInfo,
    create: () -> AppWidgetHostView,
): AppWidgetHostView {
    val attached = if (childCount == 1) getChildAt(0) as? AppWidgetHostView else null
    if (attached != null && attached.appWidgetId == widgetId &&
        attached.appWidgetInfo?.provider == providerInfo.provider) return attached
    removeAllViews()
    return create().also { addView(it) }
}

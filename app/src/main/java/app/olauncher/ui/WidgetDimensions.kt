package app.olauncher.ui

import android.appwidget.AppWidgetProviderInfo

internal fun minimumWidgetDimension(
    info: AppWidgetProviderInfo,
    axis: Int,
    defaultSize: Int,
    resizeMinimum: Int,
): Int = if (info.resizeMode and axis != 0 && resizeMinimum in 1..defaultSize) {
    resizeMinimum
} else {
    defaultSize
}

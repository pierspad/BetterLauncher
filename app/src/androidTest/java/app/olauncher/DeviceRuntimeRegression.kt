package app.olauncher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.widget.FrameLayout
import app.olauncher.helper.bindWidgetHostView

/** Run on the main thread: fixtures exercise actual attached Android host views. */
fun runDeviceRuntimeRegression(context: Context) {
    val container = FrameLayout(context)
    fun info(name: String) = AppWidgetProviderInfo().apply {
        provider = ComponentName(context.packageName, name)
    }
    var creations = 0
    fun bind(id: Int, provider: AppWidgetProviderInfo): AppWidgetHostView =
        container.bindWidgetHostView(id, provider) {
            creations++
            object : AppWidgetHostView(context) {
                override fun getAppWidgetId() = id
                override fun getAppWidgetInfo() = provider
            }
        }
    val provider = info("FixtureProvider")
    val first = bind(17, provider)
    check(first.parent === container)
    check(bind(17, info("FixtureProvider")) === first) {
        "Unchanged widget binding must retain its attached host view"
    }
    check(creations == 1)
    val changedId = bind(18, provider)
    check(changedId !== first && first.parent == null)
    val changedProvider = bind(18, info("ReplacementProvider"))
    check(changedProvider !== changedId && changedId.parent == null)
    check(container.childCount == 1 && container.getChildAt(0) === changedProvider)
    check(creations == 3)
    // Removing and re-enabling must not resurrect a detached host view.
    container.removeAllViews()
    check(bind(18, info("ReplacementProvider")) !== changedProvider)
    runDeviceClassificationRegression()
}

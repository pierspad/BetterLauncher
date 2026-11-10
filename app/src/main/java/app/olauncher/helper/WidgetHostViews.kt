package app.olauncher.helper

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
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

/** Observes presses even when an interactive widget child owns the touch sequence. */
internal class ResizableWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var handledLongPress = false
    private val gesture = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true

            override fun onLongPress(event: MotionEvent) {
                handledLongPress = isShown && hasWindowFocus() && performLongClick()
                if (handledLongPress) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super@ResizableWidgetHostView.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
        },
    )

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) handledLongPress = false
        if (isLongClickable) gesture.onTouchEvent(event)
        return handledLongPress || super.dispatchTouchEvent(event) || isLongClickable
    }

    // The gesture detector handles the host's long press, including on empty space.
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

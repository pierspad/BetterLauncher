package app.olauncher.helper

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Outline
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (appWidgetInfo?.provider?.isXiaomiSelectedPhotosWidget() == true) {
            val content = getChildAt(0)
            val imageId = content?.resources?.getIdentifier("image_view", "id", "com.miui.gallery") ?: 0
            val image = if (imageId != 0) content?.findViewById<View>(imageId) as? ImageView else null
            // The Gallery editor supplies the crop. Avoid a second center crop on the Home screen.
            if (image != null) {
                if (image.scaleType != ImageView.ScaleType.FIT_CENTER) image.scaleType = ImageView.ScaleType.FIT_CENTER
                if (image.outlineProvider != GalleryPhotoOutline) {
                    image.outlineProvider = GalleryPhotoOutline
                    image.clipToOutline = true
                }
                image.invalidateOutline()
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) handledLongPress = false
        if (isLongClickable) gesture.onTouchEvent(event)
        return handledLongPress || super.dispatchTouchEvent(event) || isLongClickable
    }

    // The gesture detector handles the host's long press, including on empty space.
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

private object GalleryPhotoOutline : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        val image = view as ImageView
        val drawable = image.drawable ?: return
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return
        val bounds = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        image.imageMatrix.mapRect(bounds)
        bounds.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        if (bounds.intersect(0f, 0f, image.width.toFloat(), image.height.toFloat())) {
            val pixels = android.graphics.Rect()
            bounds.roundOut(pixels)
            outline.setRoundRect(pixels, 6 * image.resources.displayMetrics.density)
        }
    }
}

internal fun ComponentName.isXiaomiSelectedPhotosWidget(): Boolean =
    packageName == "com.miui.gallery" &&
        className.startsWith("com.miui.gallery.gallerywidget.CustomWidgetProvider")

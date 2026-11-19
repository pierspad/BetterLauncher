package app.olauncher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import app.olauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) {
                    PhotoWidgetStore.render(context, id)
                    PhotoWidgetStore.get(context, id)?.let { PhotoWidgetWorker.schedule(context, id, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) {
                    PhotoWidgetWorker.cancel(context, id)
                    PhotoWidgetStore.remove(context, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun isBound(context: Context, id: Int): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ==
                ComponentName(context, PhotoWidgetProvider::class.java)

        fun display(context: Context, id: Int, bitmap: Bitmap?, crop: Boolean) {
            val config = Intent(context, PhotoWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val edit = PendingIntent.getActivity(context, id, config, PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.photo_widget).apply {
                setViewVisibility(R.id.photoWidgetPlaceholder, if (bitmap == null) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.photoWidgetFit, if (bitmap != null && !crop) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.photoWidgetCrop, if (bitmap != null && crop) View.VISIBLE else View.GONE)
                if (bitmap != null) setImageViewBitmap(if (crop) R.id.photoWidgetCrop else R.id.photoWidgetFit, bitmap)
                setOnClickPendingIntent(R.id.photoWidgetRoot, edit)
            }
            AppWidgetManager.getInstance(context).updateAppWidget(id, views)
        }
    }
}

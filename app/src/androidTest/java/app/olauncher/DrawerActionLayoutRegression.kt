package app.olauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.ui.AppDrawerAdapter
import java.io.File
import java.util.Locale

/** Measure real translated labels at small widths, large fonts and in RTL. */
fun runDrawerActionLayoutRegression(context: Context) {
    val languages = listOf("en", "ar", "ca", "de", "es-ES", "es-US", "fr", "he", "hr", "hu",
        "id", "it", "ja", "nl", "pl", "pt-BR", "ru", "sv", "tr", "uk", "zh")
    for (language in languages) for (scale in listOf(1f, 1.5f, 2f)) {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.AppTheme)
        val adapter = AppDrawerAdapter(Constants.FLAG_LAUNCH_APP, Gravity.START,
            appClickListener = {}, appInfoListener = {}, appDeleteListener = {},
            appHideListener = { _, _ -> }, appRenameListener = { _, _ -> })
        val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0) as AppDrawerAdapter.ViewHolder
        holder.bind(Constants.FLAG_LAUNCH_APP, Gravity.START, Process.myUserHandle(),
            AppModel.App("Fixture", null, context.packageName, "Main", user = Process.myUserHandle()),
            { false }, { false }, { 0 }, { 0L }, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        val root = holder.itemView
        root.layoutDirection = config.layoutDirection
        root.findViewById<View>(R.id.appTitle).performLongClick()
        // Also cover the longer no-free-slot label rather than only its numbered version.
        root.findViewById<TextView>(R.id.appAddToHome).text = themed.getString(R.string.add_to_home)
        val width = (320 * themed.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        val ids = listOf(R.id.appDelete, R.id.appRename, R.id.appAddToHome, R.id.appFolder,
            R.id.appHide, R.id.appInfo, R.id.appMenuClose)
        for (row in listOf(ids.take(4), ids.drop(4))) {
            val actions = row.map { root.findViewById<TextView>(it) }
            check(actions.map { it.top + it.compoundPaddingTop }.distinct().size == 1) {
                "Misaligned labels: $language at $scale"
            }
            for (action in actions) {
                checkNotNull(action.compoundDrawablesRelative[1]) { "Missing icon for ${action.text}" }
                val layout = checkNotNull(action.layout)
                check((0 until layout.lineCount).none { layout.getEllipsisCount(it) > 0 })
                check(layout.height <= action.height - action.compoundPaddingTop - action.compoundPaddingBottom) {
                    "Clipped label ${action.text}: $language at $scale"
                }
            }
        }
        if (language in listOf("it", "de", "ar") && scale == 1f) {
            val image = Bitmap.createBitmap(width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            File(context.cacheDir, "drawer-actions-$language.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
    }
}

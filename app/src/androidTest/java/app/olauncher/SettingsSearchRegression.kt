package app.olauncher

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.olauncher.ui.SettingsSearch
import java.io.File
import java.util.Locale

fun runSettingsSearchRegression(context: Context) {
    for (landscape in listOf(false, true)) {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ITALIAN)
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.AppTheme)
        val root = LayoutInflater.from(themed).inflate(R.layout.fragment_settings, null)
        check(root.findViewById<android.widget.TextView>(R.id.batteryShortcut).compoundDrawablesRelative[0] != null) {
            "Battery shortcut must display its icon"
        }
        check(root.findViewById<android.widget.TextView>(R.id.settingsSearch).compoundDrawablesRelative[0] != null) {
            "Settings search must display the magnifying glass"
        }
        val container = root.findViewById<LinearLayout>(R.id.scrollLayout)
        val conditional = root.findViewById<View>(R.id.widgetChooseRow)
        conditional.visibility = View.GONE
        val sectionContext = object : ContextWrapper(themed) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("regression-sections-$name", mode)
        }
        sectionContext.getSharedPreferences("settings_sections", Context.MODE_PRIVATE).edit().clear().commit()
        val search = SettingsSearch(sectionContext, container)
        fun visible(view: View): Boolean = view.visibility == View.VISIBLE &&
            ((view.parent as? View)?.let { visible(it) } ?: true)
        val generalBody = root.findViewById<View>(R.id.olauncherHiddenApps).parent as ViewGroup
        val generalHeader = (generalBody.parent as ViewGroup).getChildAt(0) as ViewGroup
        check(generalHeader.getChildAt(0).tag == "settings:settings_general") {
            "Both orientations must retain a separate General heading"
        }
        check(root.findViewById<View>(R.id.navigationShortcut).parent === generalBody) {
            "Navigation belongs in General in both orientations"
        }
        val battery = root.findViewById<View>(R.id.batteryShortcut)
        val batteryBody = battery.parent as ViewGroup
        val batteryCard = batteryBody.parent as ViewGroup
        val header = batteryCard.getChildAt(0) as ViewGroup
        check(header.getChildAt(1).performClick())
        check(!visible(battery)) { "Collapsing must hide the section body" }
        val recreated = LayoutInflater.from(themed).inflate(R.layout.fragment_settings, null)
        SettingsSearch(sectionContext, recreated.findViewById(R.id.scrollLayout))
        check(recreated.findViewById<View>(R.id.batteryShortcut).parent.let { it as View }.visibility == View.GONE) {
            "A recreated settings screen must remember collapsed sections"
        }
        for (query in listOf("batteria", "battery")) {
            check(search.filter(query) > 0) { "Missing translated query: $query" }
            check(visible(battery)) { "Search must include collapsed sections" }
            check(visible(header)) { "Results must retain their section title" }
        }
        for (query in listOf("solo orario", "date only")) {
            check(search.filter(query) > 0) { "Missing clock editor alias: $query" }
            check(visible(root.findViewById(R.id.clockAppearanceRow)))
        }
        check(search.filter("Bildschirmzeit") == 0) { "Do not index unrelated languages" }
        check(search.filter("dimensione testo") > 0)
        check(visible(root.findViewById(R.id.textSizeSeekBar)))
        check(search.filter("no-such-setting-xyz") == 0)
        search.filter("")
        check(!visible(battery)) { "Clearing search must restore the saved collapsed state" }
        check(conditional.visibility == View.GONE) { "Search must not reveal a conditionally hidden setting" }
        check(visible(root.findViewById(R.id.backupRestoreSettings)))
        search.filter("batteria")
        val width = (400 * themed.resources.displayMetrics.density).toInt()
        val height = (800 * themed.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.WHITE)
        root.draw(canvas)
        File(context.cacheDir, "settings-search-$landscape.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
    checkLocalizedContrastSearch(context)
    val prefs = app.olauncher.data.Prefs(context)
    check(prefs.homeContrastProtection) { "Home contrast protection must be enabled by default" }
    prefs.homeContrastProtection = false
    check(prefs.importFromJson(prefs.exportToJson()))
    check(!prefs.homeContrastProtection) { "Explicitly disabling Home contrast protection must survive restore" }
    prefs.homeContrastProtection = true
    check(!prefs.xiaomiGestures) { "Gesture navigation must be opt-in" }
    prefs.xiaomiGestures = true
    check(prefs.importFromJson(prefs.exportToJson()))
    check(!prefs.xiaomiGestures) { "Restoring a backup must not silently enable a global touch service" }
    check(SettingsSearch.normalize("ACCESSIBILITÀ") == "accessibilita")
}


private fun checkLocalizedContrastSearch(context: Context) {
    val localeTags = listOf(
        "en", "ar", "ca", "de", "es-ES", "es-US", "fr", "he", "hr", "hu", "id",
        "it", "ja", "nl", "pl", "pt-BR", "ru-RU", "sv", "tr", "uk", "zh", "bn", "hi", "ko",
    )
    for (tag in localeTags) {
        for (orientation in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
                this.orientation = orientation
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.AppTheme)
            val root = try {
                LayoutInflater.from(themed).inflate(R.layout.fragment_settings, null)
            } catch (failure: android.view.InflateException) {
                throw IllegalStateException("Settings inflation failed for $tag, orientation=$orientation", failure)
            }
            val toggle = root.findViewById<TextView>(R.id.switchHomeContrastProtection)
            val localizedLabel = themed.getString(R.string.home_contrast_protection)
            check(toggle.text.toString() == localizedLabel) { "$tag/$orientation: wrong contrast switch label" }
            val search = SettingsSearch(themed, root.findViewById(R.id.scrollLayout))
            for (query in listOf(localizedLabel, "adaptive home contrast")) {
                check(search.filter(query) > 0 && toggle.isVisible) {
                    "$tag/$orientation: contrast switch missing for query '$query'"
                }
                check(!root.findViewById<View>(R.id.drawerOpacitySeekBar).isVisible) {
                    "$tag/$orientation: drawer opacity must not match the Home contrast switch"
                }
            }
            check(search.filter("adaptive home contrast missing-test") == 0 && !toggle.isVisible)
            search.filter("")
            check(toggle.isVisible)
        }
    }
}

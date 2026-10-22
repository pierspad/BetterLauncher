package app.olauncher

import android.content.Context
import app.olauncher.data.Prefs
import app.olauncher.helper.FuzzySearch
import app.olauncher.helper.SearchMode
import org.json.JSONObject

fun runSearchDefaultMigrationRegression(context: Context) {
    val original = Prefs(context).exportToJson()
    val storage = context.getSharedPreferences("com.pierspad.betterlauncher", Context.MODE_PRIVATE)
    try {
        storage.edit().clear().putInt("SEARCH_MODE", 2).commit()
        val migrated = Prefs(context)
        check(migrated.searchMode == 0) { "An existing flexible install must migrate once to Precise" }
        val labels = listOf("Reddit Morphe", "Bandiere del mondo", "Video Downloader & Saver")
        check(labels.filter { FuzzySearch.matches(it, "Edd", SearchMode.fromValue(migrated.searchMode)) } == listOf("Reddit Morphe"))
        migrated.searchMode = 2
        check(Prefs(context).searchMode == 2) { "An explicit choice after migration must survive recreation" }
        val backup = migrated.exportToJson()
        migrated.resetToDefaults()
        check(migrated.searchMode == 0)
        check(migrated.importFromJson(backup))
        check(migrated.searchMode == 2) { "New backups must retain explicit user choices" }
        val legacy = JSONObject(backup).apply { remove("PRECISE_SEARCH_DEFAULT_APPLIED") }.toString()
        check(migrated.importFromJson(legacy))
        check(migrated.searchMode == 0) { "Legacy backups must also migrate to Precise" }
    } finally {
        check(Prefs(context).importFromJson(original))
    }
}

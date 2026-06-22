package app.olauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A user-defined group of apps.
 *
 * [apps] stores member keys in the canonical "packageName|userHandle" form already
 * used by hidden/locked apps. Storing the key (not a label or an activity class) means
 * a folder survives app renames/updates and resolves cleanly against the live app list;
 * the launch component is resolved at click time exactly like the home apps do.
 *
 * The whole list is serialized to a single JSON string preference, so it is picked up
 * automatically by the existing settings backup/restore (which walks every preference).
 */
data class Folder(
    val id: String,
    var name: String,
    val apps: MutableList<String> = mutableListOf(),
) {
    private fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_APPS, JSONArray().also { arr -> apps.forEach { arr.put(it) } })
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_APPS = "apps"

        fun listToJson(folders: List<Folder>): String =
            JSONArray().also { arr -> folders.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): MutableList<Folder> {
            if (json.isNullOrBlank()) return mutableListOf()
            return try {
                val arr = JSONArray(json)
                MutableList(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    val appsArr = obj.optJSONArray(KEY_APPS) ?: JSONArray()
                    Folder(
                        id = obj.getString(KEY_ID),
                        name = obj.optString(KEY_NAME),
                        apps = MutableList(appsArr.length()) { appsArr.getString(it) },
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }
        }
    }
}

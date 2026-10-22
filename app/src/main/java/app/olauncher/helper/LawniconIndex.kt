package app.olauncher.helper

/** Exact activity matches take precedence; a package fallback preserves asset order. */
class LawniconIndex(components: Map<String, String>) {
    private val byComponent = components.toMap()
    private val byPackage = buildMap {
        for ((component, drawable) in components) {
            putIfAbsent(component.substringBefore('/'), drawable)
        }
    }

    fun drawableFor(packageName: String, activityName: String?): String? =
        byComponent["$packageName/${activityName.orEmpty()}"] ?: byPackage[packageName]
}

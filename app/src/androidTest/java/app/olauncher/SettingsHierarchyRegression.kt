package app.olauncher

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import app.olauncher.ui.SettingsSearch

/** Reparenting must also be safe after LayoutTransition has seen an attached layout. */
fun runAttachedSettingsHierarchyRegression(instrumentation: android.app.Instrumentation) {
    val activity = instrumentation.startActivitySync(Intent(
        instrumentation.targetContext, SettingsLifecycleHostActivity::class.java
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun main(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    try {
        for (landscape in listOf(false, true)) {
            lateinit var root: ViewGroup
            lateinit var search: SettingsSearch
            main {
                val config = android.content.res.Configuration(activity.resources.configuration).apply {
                    orientation = if (landscape) 2 else 1
                }
                val context = android.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.AppTheme)
                root = android.view.LayoutInflater.from(context).inflate(R.layout.fragment_settings, null) as ViewGroup
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            main { search = SettingsSearch(activity, root.findViewById<LinearLayout>(R.id.scrollLayout)) }
            // Disappearing-view animations finish asynchronously and can clear a
            // reparented child's parent after it has already joined the new body.
            Thread.sleep(700)
            instrumentation.waitForIdleSync()
            main {
                fun verify(group: ViewGroup) {
                    for (i in 0 until group.childCount) {
                        val child = group.getChildAt(i)
                        check(child.parent === group) { "Broken settings parent for ${child.javaClass.simpleName} in landscape=$landscape" }
                        if (child is ViewGroup) verify(child)
                    }
                    group.createAccessibilityNodeInfo()?.recycle()
                }
                verify(root)
                root.addFocusables(arrayListOf(), View.FOCUS_FORWARD)
                val editor = root.findViewById<EditText>(R.id.settingsSearch)
                editor.requestFocus()
                editor.onCreateInputConnection(EditorInfo())
                for (query in listOf("Import", "battery", "missing-setting", "")) {
                    search.filter(query)
                    root.addFocusables(arrayListOf(), View.FOCUS_FORWARD)
                    verify(root)
                }
            }
        }
    } finally {
        main { activity.finish() }
        instrumentation.waitForIdleSync()
    }
}

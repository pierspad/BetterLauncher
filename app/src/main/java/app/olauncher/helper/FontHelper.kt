package app.olauncher.helper

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.data.Prefs
import java.io.File

/**
 * Applies a user-selected font across the whole UI.
 *
 * Predefined options are built-in Android font families ("serif", "monospace", …) so
 * nothing has to be bundled and there are no licensing concerns. The user can also
 * import a custom .ttf/.otf file, which is copied into the app's private storage and
 * loaded with [Typeface.createFromFile].
 *
 * The font is applied with a [LayoutInflater.Factory2] that rewrites the typeface of
 * every inflated TextView (and its subclasses: EditText, Button, SearchView's text…)
 * while preserving each view's original style (normal / bold / italic). Because the
 * factory delegates view creation to AppCompat's own delegate, AppCompat widget
 * substitution keeps working — it must therefore be installed *before*
 * `super.onCreate()`, otherwise AppCompat installs its factory first.
 */
object FontHelper {

    // The "custom:" prefix in Prefs.fontFamily marks a user-imported font.
    const val CUSTOM_PREFIX = "custom:"
    private const val CUSTOM_FONT_FILENAME = "custom_font"

    @Volatile
    private var typeface: Typeface? = null

    /** (Re)reads the selected font from prefs and caches the resolved typeface. */
    fun reload(context: Context): Typeface? {
        val prefs = Prefs(context)
        val family = prefs.fontFamily
        typeface = when {
            family.isBlank() -> null
            family.startsWith(CUSTOM_PREFIX) -> runCatching {
                prefs.customFontPath.takeIf { it.isNotBlank() }?.let { Typeface.createFromFile(it) }
            }.getOrNull()

            else -> runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
        }
        return typeface
    }

    /** Installs the typeface-rewriting factory on the activity's LayoutInflater. */
    fun install(activity: AppCompatActivity) {
        val inflater = activity.layoutInflater
        // Setting factory2 twice throws; only install when nothing else has claimed it.
        if (inflater.factory2 != null) return
        val delegate = activity.delegate
        inflater.factory2 = object : LayoutInflater.Factory2 {
            override fun onCreateView(
                parent: View?,
                name: String,
                context: Context,
                attrs: AttributeSet,
            ): View? {
                val view = delegate.createView(parent, name, context, attrs)
                val base = typeface
                if (base != null && view is TextView) {
                    val style = view.typeface?.style ?: Typeface.NORMAL
                    view.typeface = Typeface.create(base, style)
                }
                return view
            }

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                onCreateView(null, name, context, attrs)
        }
    }

    /**
     * Copies a picked font file into private storage and validates it.
     * Returns the absolute path on success, or null if the file is not a usable font.
     */
    fun importCustomFont(context: Context, uri: Uri): String? {
        return runCatching {
            val dest = File(context.filesDir, CUSTOM_FONT_FILENAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            // Validate: createFromFile throws (or returns the default) on a bad file.
            val tf = Typeface.createFromFile(dest)
            if (tf == null || tf == Typeface.DEFAULT) {
                dest.delete()
                return null
            }
            dest.absolutePath
        }.getOrNull()
    }
}

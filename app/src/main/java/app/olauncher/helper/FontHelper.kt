package app.olauncher.helper

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.data.Prefs

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

    /** Applies the selected font to a TextView created in code, preserving its style. */
    fun applyTo(view: TextView) {
        val base = typeface ?: return
        view.typeface = Typeface.create(base, view.typeface?.style ?: Typeface.NORMAL)
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
                    ?: if (typeface != null && name != "view") {
                        // AppCompat leaves some widgets to the platform inflater when it returns null.
                        // Create them here so explicit AppCompat tags and native TextClocks are styled too.
                        val prefix = if ('.' in name) null else "android.widget."
                        try {
                            inflater.cloneInContext(context).createView(name, prefix, attrs)
                        } catch (_: ClassNotFoundException) {
                            null
                        }
                    } else {
                        null
                    }
                if (view is TextView) applyTo(view)
                return view
            }

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                onCreateView(null, name, context, attrs)
        }
    }

}

package app.olauncher.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.children
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr

/** A separate body keeps collapsing independent of each setting's conditional visibility. */
internal class SettingsSection(context: Context, card: LinearLayout) {
    val title = card.getChildAt(0) as TextView
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val store = context.getSharedPreferences("settings_sections", Context.MODE_PRIVATE)
    private val key = title.tag.toString()
    private var collapsed = store.getBoolean(key, false)
    private var searching = false
    private val toggle = AppCompatImageButton(context)

    init {
        val rows = card.children.toList().drop(1)
        val titleMargins = title.layoutParams as LinearLayout.LayoutParams
        // Removed children are immediately reused in the header/body. A disappearing
        // LayoutTransition would keep them in the old card and later clear their new
        // parent, breaking framework focus and accessibility traversal (issue #7).
        val transition = card.layoutTransition
        card.layoutTransition = null
        card.removeAllViews()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = titleMargins.marginStart
            marginEnd = titleMargins.marginEnd
        }
        ViewCompat.setAccessibilityHeading(title, true)
        header.addView(title)
        val size = (48 * context.resources.displayMetrics.density).toInt()
        toggle.apply {
            val background = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true)
            setBackgroundResource(background.resourceId)
            setColorFilter(context.getColorFromAttr(R.attr.primaryColor))
            setOnClickListener {
                collapsed = !collapsed
                store.edit { putBoolean(key, collapsed) }
                // Search results stay open until the query is cleared.
                update(searching)
            }
        }
        header.addView(toggle, LinearLayout.LayoutParams(size, size))
        card.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = titleMargins.topMargin
            bottomMargin = titleMargins.bottomMargin
        })
        rows.forEach { body.addView(it) }
        card.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.layoutTransition = transition
        update(false)
    }

    fun update(searching: Boolean) {
        this.searching = searching
        val closed = collapsed && !searching
        body.visibility = if (closed) View.GONE else View.VISIBLE
        toggle.setImageResource(if (closed) R.drawable.ic_section_expand else R.drawable.ic_section_collapse)
        toggle.contentDescription = title.context.getString(
            if (closed) R.string.settings_expand_section else R.string.settings_collapse_section, title.text)
        // Keep results stable while typing; clearing the query restores the saved state.
        toggle.isEnabled = !searching
        toggle.alpha = if (searching) 0.4f else 1f
    }
}

package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import java.text.Normalizer
import java.util.Locale

/** Filters original settings rows, preserving their listeners, grouping and conditional visibility. */
internal class SettingsSearch(private val context: Context, container: LinearLayout) {
    private data class Group(val views: List<View>, val terms: String)
    private data class Card(val view: ViewGroup, val groups: List<Group>)
    private val resources = listOf(context.resources,
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).resources)
    private val termsCache = mutableMapOf<String, String>()
    private val original = mutableMapOf<View, Int>()
    private var filtering = false
    private val cards = container.children.filterIsInstance<ViewGroup>()
        .filter { it.id != app.olauncher.R.id.settingsIdentity }
        .map { card ->
            val groups = mutableListOf<Group>()
            var pending = mutableListOf<View>()
            fun flush() {
                if (pending.isNotEmpty()) groups += Group(pending.toList(), normalize(pending.joinToString(" ") { terms(it) }))
                pending = mutableListOf()
            }
            card.children.forEach { row ->
                if (row.javaClass == View::class.java) flush() else pending += row
            }
            flush()
            Card(card, groups)
        }.toList()

    fun filter(query: String): Int {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            original.forEach { (view, visibility) -> view.visibility = visibility }
            original.clear()
            filtering = false
            return cards.size
        }
        if (!filtering) {
            cards.forEach { card ->
                original[card.view] = card.view.visibility
                card.view.children.forEach { original[it] = it.visibility }
            }
            filtering = true
        }
        var matches = 0
        cards.forEach { card ->
            card.view.children.forEach { it.visibility = View.GONE }
            var cardMatches = false
            card.groups.forEach { group ->
                val eligible = original[card.view] == View.VISIBLE && group.views.any { original[it] == View.VISIBLE }
                if (eligible && tokens.all { group.terms.contains(it) }) {
                    group.views.forEach { it.visibility = original[it] ?: View.GONE }
                    matches++
                    cardMatches = true
                }
            }
            card.view.visibility = if (cardMatches && original[card.view] == View.VISIBLE) View.VISIBLE else View.GONE
        }
        return matches
    }

    @SuppressLint("DiscouragedApi") // XML labels retain resource names to index the active translation and English.
    private fun terms(view: View): String {
        val key = (view.tag as? String)?.takeIf { it.startsWith("settings:") }?.substringAfter(':')
        val own = if (key != null) termsCache.getOrPut(key) {
            val id = context.resources.getIdentifier(key, "string", context.packageName)
            if (id == 0) "" else resources.joinToString(" ") { it.getString(id) }
        } else (view as? TextView)?.text?.toString().orEmpty()
        return own + if (view is ViewGroup) view.children.joinToString(" ", prefix = " ") { terms(it) } else ""
    }

    companion object {
        internal fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT).trim()
    }
}

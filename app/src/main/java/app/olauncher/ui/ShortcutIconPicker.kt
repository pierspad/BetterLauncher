package app.olauncher.ui

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import app.olauncher.R
import app.olauncher.data.ShortcutIcons
import app.olauncher.databinding.DialogShortcutIconBinding
import app.olauncher.databinding.ItemShortcutIconBinding
import app.olauncher.helper.FuzzySearch
import java.util.Locale

internal fun showShortcutIconPicker(
    inflater: LayoutInflater,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val context = inflater.context
    val binding = DialogShortcutIconBinding.inflate(inflater)
    val english = context.createConfigurationContext(Configuration().apply { setLocale(Locale.ENGLISH) })
    val categories = listOf(null) + ShortcutIcons.Category.entries
    val categoryLabels = categories.map { context.getString(it?.label ?: R.string.shortcut_category_all) }
    binding.iconCategory.adapter = ArrayAdapter(
        context, R.layout.item_shortcut_icon_category, categoryLabels
    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    val items = ShortcutIcons.entries.mapIndexed { index, icon ->
        val label = context.getString(icon.label)
        val category = context.getString(icon.category.label)
        PickerIcon(
            index,
            icon,
            label,
            listOf(label, english.getString(icon.label), category, english.getString(icon.category.label))
                .map(FuzzySearch::normalize),
        )
    }
    val adapter = ShortcutIconAdapter(inflater, items, selectedIndex)
    val grid = binding.iconGrid
    grid.adapter = adapter
    grid.emptyView = binding.iconEmpty

    fun updateResults() {
        val query = FuzzySearch.normalize(binding.iconSearch.text?.toString().orEmpty())
        val category = categories.getOrNull(binding.iconCategory.selectedItemPosition)
        val results = items.filter { item ->
            (category == null || item.icon.category == category) &&
                item.searchTerms.any { query in it }
        }
        if (results != adapter.items) {
            adapter.items = results
            adapter.notifyDataSetChanged()
            grid.setSelection(0)
        }
    }
    binding.iconSearch.doAfterTextChanged { updateResults() }
    binding.iconCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            updateResults()
        }

        override fun onNothingSelected(parent: AdapterView<*>) = Unit
    }

    val dialog = AlertDialog.Builder(context).setView(binding.root).create()
    binding.iconPickerClose.setOnClickListener { dialog.dismiss() }
    grid.setOnItemClickListener { _, _, position, _ ->
        onSelect(adapter.getItem(position).index)
        dialog.dismiss()
    }
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
    dialog.show()
    binding.root.requestFocus()
    grid.setSelection(selectedIndex.coerceIn(items.indices))
}

private data class PickerIcon(
    val index: Int,
    val icon: ShortcutIcons.Icon,
    val label: String,
    val searchTerms: List<String>,
)

private class ShortcutIconAdapter(
    private val inflater: LayoutInflater,
    var items: List<PickerIcon>,
    private val selectedIndex: Int,
) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): PickerIcon = items[position]
    override fun getItemId(position: Int): Long = getItem(position).index.toLong()
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val selected = item.index == selectedIndex
        val cell = (convertView?.tag as? ItemShortcutIconBinding)
            ?: ItemShortcutIconBinding.inflate(inflater, parent, false).apply {
                root.tag = this
            }
        cell.root.setCompoundDrawablesRelativeWithIntrinsicBounds(0, item.icon.drawable, 0, 0)
        cell.root.text = item.label
        cell.root.isSelected = selected
        cell.root.setBackgroundResource(if (selected) R.drawable.rounded_settings_search else 0)
        return cell.root
    }
}

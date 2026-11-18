package app.olauncher.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Folder
import app.olauncher.databinding.DialogGroupAppsBinding
import app.olauncher.helper.FontHelper
import app.olauncher.helper.FuzzySearch
import app.olauncher.helper.SearchMode
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

internal fun showGroupAppsPicker(
    inflater: LayoutInflater,
    folder: Folder,
    candidates: List<AppModel.App>,
    searchMode: SearchMode,
    onSave: (Folder) -> Unit,
): Dialog {
    val binding = DialogGroupAppsBinding.inflate(inflater)
    val context = inflater.context
    val selected = folder.apps.toMutableSet()
    val apps = candidates.map { app ->
        GroupApp("${app.appPackage}|${app.user}", app.appLabel)
    }.distinctBy { it.key }

    val adapter = GroupAppsAdapter(selected)
    binding.groupAppsList.layoutManager = LinearLayoutManager(context)
    binding.groupAppsList.adapter = adapter
    binding.groupAppsTitle.text = folder.name
    ViewCompat.setAccessibilityHeading(binding.groupAppsTitle, true)
    binding.groupAppsSearch.doAfterTextChanged { query ->
        val prepared = FuzzySearch.prepareQuery(query.toString())
        adapter.submitList(if (query.isNullOrBlank()) apps else apps.filter {
            FuzzySearch.score(it.preparedLabel, prepared, searchMode) >= 0
        })
    }
    adapter.submitList(apps)

    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawable(InsetDrawable(Color.TRANSPARENT.toDrawable(), 10.dpToPx()))
    binding.root.requestFocus()
    binding.groupAppsSave.setOnClickListener {
        onSave(folder.copy(apps = selected.toMutableList()))
        dialog.dismiss()
    }
    binding.groupAppsCancel.setOnClickListener { dialog.dismiss() }
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    return dialog
}

private data class GroupApp(val key: String, val label: String) {
    val preparedLabel = FuzzySearch.prepareLabel(label)
}

private class GroupAppsAdapter(private val selected: MutableSet<String>) :
    ListAdapter<GroupApp, GroupAppsAdapter.Holder>(object : DiffUtil.ItemCallback<GroupApp>() {
        override fun areItemsTheSame(oldItem: GroupApp, newItem: GroupApp) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: GroupApp, newItem: GroupApp) = oldItem == newItem
    }) {

    class Holder(val checkbox: AppCompatCheckBox) : RecyclerView.ViewHolder(checkbox)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        AppCompatCheckBox(parent.context).apply {
            val color = context.getColorFromAttr(R.attr.primaryColor)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            minimumHeight = 52.dpToPx()
            textSize = 16f
            setPaddingRelative(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            setTextColor(color)
            buttonTintList = ColorStateList.valueOf(color)
            FontHelper.applyTo(this)
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)
        holder.checkbox.apply {
            setOnCheckedChangeListener(null)
            text = app.label
            isChecked = app.key in selected
            setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(app.key) else selected.remove(app.key)
            }
        }
    }
}

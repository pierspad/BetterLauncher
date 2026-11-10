package app.olauncher.ui

import android.content.res.Configuration
import android.view.ViewGroup
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import app.olauncher.R
import app.olauncher.data.Constants.DateTime
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogClockAppearanceBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

internal fun showClockAppearanceEditor(
    inflater: LayoutInflater,
    prefs: Prefs,
    onApply: (ClockAppearance) -> Unit,
): Dialog {
    val binding = DialogClockAppearanceBinding.inflate(inflater)
    val context = inflater.context
    val primary = context.getColorFromAttr(R.attr.primaryColor)
    val foreground = binding.clockCancel.typeface
    val selectedText = ContextCompat.getColor(context, R.color.dialogActionText)
    if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        (binding.clockPreviewSection.parent as ViewGroup).removeView(binding.clockPreviewSection)
        binding.clockOptions.addView(binding.clockPreviewSection)
    }
    var draft = ClockAppearance(prefs)

    fun label(text: Int) = TextView(context).apply {
        setText(text)
        setTextColor(primary)
        typeface = foreground
        textSize = 14f
    }

    val updateControls = mutableListOf<() -> Unit>()
    fun render() {
        draft.applyTo(binding.clockPreview, binding.previewTime, binding.previewDate, prefs)
        binding.previewHidden.isVisible = draft.mode == DateTime.OFF
        updateControls.forEach { it() }
    }

    fun options(
        title: Int,
        values: List<Pair<Int, Int>>,
        columns: Int,
        visible: (ClockAppearance) -> Boolean = { true },
        icons: Map<Int, Int> = emptyMap(),
        symbols: Map<Int, String> = emptyMap(),
        selected: (ClockAppearance) -> Int,
        select: (ClockAppearance, Int) -> ClockAppearance,
    ) {
        val section = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        binding.clockOptions.addView(section)
        section.addView(label(title).apply { setPadding(0, 12.dpToPx(), 0, 4.dpToPx()) })
        val chips = values.chunked(columns).flatMap { items ->
            val row = LinearLayout(context)
            section.addView(row)
            items.map { (value, text) ->
                val chip = label(text).apply {
                    gravity = Gravity.CENTER
                    minHeight = 48.dpToPx()
                    setPadding(6.dpToPx())
                    symbols[value]?.let { symbol -> this.text = "$symbol  ${this.text}" }
                    icons[value]?.let { icon ->
                        val drawable = ContextCompat.getDrawable(context, icon)?.mutate()
                        drawable?.setBounds(0, 0, 18.dpToPx(), 18.dpToPx())
                        setCompoundDrawablesRelative(null, drawable, null, null)
                        compoundDrawablePadding = 4.dpToPx()
                    }
                    setBackgroundResource(R.drawable.rounded_settings_search)
                    setOnClickListener {
                        draft = select(draft, value)
                        render()
                    }
                }
                row.addView(chip, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                })
                value to chip
            }
        }
        updateControls.add {
            val enabled = visible(draft)
            section.alpha = if (enabled) 1f else 0.45f
            for ((value, chip) in chips) {
                chip.isEnabled = enabled
                chip.isSelected = value == selected(draft)
                chip.setBackgroundResource(if (chip.isSelected) R.drawable.dialog_primary_action
                    else R.drawable.rounded_settings_search)
                val color = if (chip.isSelected) selectedText else primary
                chip.setTextColor(color)
                chip.compoundDrawablesRelative[1]?.setTint(color)
            }
        }
    }

    options(R.string.clock_visibility,
        listOf(DateTime.ON to R.string.clock_date_and_time, DateTime.TIME_ONLY to R.string.time_only,
            DateTime.DATE_ONLY to R.string.date_only, DateTime.OFF to R.string.clock_hidden),
        2, icons = mapOf(DateTime.TIME_ONLY to R.drawable.ic_sc_clock, DateTime.DATE_ONLY to R.drawable.ic_sc_calendar),
        selected = { it.mode }) { value, mode -> value.copy(mode = mode) }
    options(R.string.clock_arrangement,
        listOf(0 to R.string.clock_above_date, 1 to R.string.date_above_clock,
            2 to R.string.clock_beside_date, 3 to R.string.date_beside_clock),
        2, { it.showsClock && it.showsDate }, symbols = mapOf(0 to "↕", 1 to "↕", 2 to "↔", 3 to "↔"),
        selected = { it.arrangement }) { value, arrangement ->
        value.copy(arrangement = arrangement)
    }
    options(R.string.clock_alignment,
        listOf(Gravity.START to R.string.left, Gravity.CENTER_HORIZONTAL to R.string.center, Gravity.END to R.string.right),
        3, { it.mode != DateTime.OFF && !(it.showsDate && it.showsClock && it.arrangement in 2..3) },
        icons = mapOf(Gravity.START to R.drawable.ic_align_left, Gravity.CENTER_HORIZONTAL to R.drawable.ic_align_center,
            Gravity.END to R.drawable.ic_align_right), selected = { it.alignment }) { value, alignment -> value.copy(alignment = alignment) }
    val sizes = listOf(0 to R.string.clock_size_small, 1 to R.string.clock_size_default, 2 to R.string.clock_size_large)
    options(R.string.clock_time_size, sizes, 3, { it.showsClock }, selected = { it.clockSize }) { value, size ->
        value.copy(clockSize = size)
    }
    options(R.string.clock_date_size, sizes, 3, { it.showsDate }, selected = { it.dateSize }) { value, size ->
        value.copy(dateSize = size)
    }
    options(R.string.clock_precision,
        listOf(0 to R.string.clock_hours, 1 to R.string.clock_with_minutes, 2 to R.string.clock_with_seconds),
        3, { it.showsClock }, selected = { if (!it.minutes) 0 else if (it.seconds) 2 else 1 }) { value, precision ->
        value.copy(minutes = precision > 0, seconds = precision == 2)
    }

    render()

    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(binding.root)
    dialog.window?.setBackgroundDrawable(InsetDrawable(ColorDrawable(Color.TRANSPARENT), 10.dpToPx()))
    binding.clockApply.setOnClickListener { onApply(draft); dialog.dismiss() }
    binding.clockCancel.setOnClickListener { dialog.dismiss() }
    dialog.show()
    dialog.window?.let { window ->
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    return dialog
}

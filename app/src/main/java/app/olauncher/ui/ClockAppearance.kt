package app.olauncher.ui

import android.os.BatteryManager
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ClockAppearance(
    val mode: Int,
    val arrangement: Int,
    val alignment: Int,
    val clockSize: Int,
    val dateSize: Int,
    val minutes: Boolean,
    val seconds: Boolean,
) {
    constructor(prefs: Prefs) : this(
        prefs.dateTimeVisibility, prefs.clockArrangement, prefs.clockAlignment, prefs.clockSizeOption, prefs.dateSizeOption,
        prefs.clockMinutes, prefs.clockSeconds,
    )

    val showsClock get() = Constants.DateTime.isTimeVisible(mode)
    val showsDate get() = Constants.DateTime.isDateVisible(mode)

    fun applyTo(container: LinearLayout, clock: TextClock, date: TextView, prefs: Prefs) {
        val split = showsClock && showsDate && arrangement in 2..3
        val dateFirst = arrangement == 1 || arrangement == 3
        val first = if (dateFirst) date else clock
        val second = if (dateFirst) clock else date
        if (container.getChildAt(0) != first) {
            container.removeView(first)
            container.addView(first, 0)
        }
        container.orientation = if (split) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        container.gravity = alignment or Gravity.CENTER_VERTICAL
        container.setBaselineAligned(!split)
        for ((index, child) in listOf(first, second).withIndex()) {
            child.layoutParams = LinearLayout.LayoutParams(if (split) 0 else -2, -2, if (split) {
                if (child == clock) 1.2f else 1f
            } else 0f)
            child.gravity = if (split && index == 1) Gravity.END else Gravity.START
            child.maxLines = 1
            child.ellipsize = TextUtils.TruncateAt.END
        }
        container.isVisible = showsClock || showsDate
        clock.isVisible = showsClock
        date.isVisible = showsDate
        val clockScale = when (clockSize) { 0 -> 0.58f; 2 -> 1.18f; else -> 1f }
        val dateScale = when (dateSize) { 0 -> 0.8f; 2 -> 1.3f; else -> 1f }
        val showSeconds = minutes && seconds
        val clockWidthScale = (if (split) 0.67f else 1f) * (if (showSeconds) 0.8f else 1f)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,
            clock.resources.getDimension(R.dimen.time_size) * clockScale * clockWidthScale)
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, date.resources.getDimension(R.dimen.date_size) * dateScale)
        val suffix = if (showSeconds) ":mm:ss" else if (minutes) ":mm" else ""
        clock.format12Hour = "h$suffix"
        clock.format24Hour = "HH$suffix"

        val context = date.context
        var label = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        if (!prefs.showStatusBar) {
            val battery = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0) label = context.getString(R.string.day_battery, label, battery)
        }
        date.text = label.replace(".,", ",")
    }

    fun save(prefs: Prefs) {
        prefs.clockArrangement = arrangement
        prefs.clockAlignment = alignment
        prefs.clockSizeOption = clockSize
        prefs.dateSizeOption = dateSize
        prefs.clockMinutes = minutes
        prefs.clockSeconds = seconds
    }
}

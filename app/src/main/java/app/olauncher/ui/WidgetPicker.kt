package app.olauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import app.olauncher.R
import app.olauncher.data.Prefs
import kotlin.math.ceil
import kotlin.math.roundToInt
import app.olauncher.databinding.DialogWidgetPickerBinding
import app.olauncher.databinding.ItemWidgetPickerBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.isXiaomiSelectedPhotosWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun showWidgetPicker(
    inflater: LayoutInflater,
    manager: AppWidgetManager,
    state: WidgetPickerState,
    prefs: Prefs,
    onDismiss: () -> Unit,
    onSelect: (AppWidgetProviderInfo) -> Unit,
): AlertDialog {
    val context = inflater.context
    val binding = DialogWidgetPickerBinding.inflate(inflater)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val space = state.home
    val availableHeight = space?.height(prefs)
    val rowHeight = 64.dpToPx()
    if (space != null && availableHeight != null) {
        val density = context.resources.displayMetrics.density
        val homeLabel = context.getString(R.string.widget_home_space, 4, availableHeight / rowHeight,
            (space.width / density).roundToInt(), (availableHeight / density).roundToInt())
        val placed = state.placed?.takeIf { it.id == prefs.widgetId && it.width > 0 && it.height > 0 }
        binding.widgetSpace.text = if (placed == null) homeLabel else {
            val widgetLabel = context.getString(R.string.widget_placed_space,
                (placed.width / density).roundToInt(), (placed.height / density).roundToInt())
            "$homeLabel\n$widgetLabel"
        }
        binding.widgetSpace.visibility = View.VISIBLE
    }
    var entries = state.entries
    val adapter = WidgetPickerAdapter(inflater, manager, scope, state, space?.width, availableHeight, rowHeight, entries)
    val list = binding.widgetList
    list.adapter = adapter
    list.emptyView = binding.widgetEmpty
    list.setOnScrollListener(object : AbsListView.OnScrollListener {
        override fun onScrollStateChanged(view: AbsListView, state: Int) {
            adapter.isIdle.value = state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
        }

        override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
    })
    list.setRecyclerListener { view -> (view.tag as? Job)?.cancel() }
    fun filterEntries() {
        val query = binding.widgetSearch.text.toString().trim()
        adapter.entries = entries.filter {
            it.label.contains(query, ignoreCase = true) || it.app.contains(query, ignoreCase = true)
        }
        adapter.notifyDataSetChanged()
        list.setSelection(0)
    }
    binding.widgetSearch.doAfterTextChanged { filterEntries() }
    if (entries.isEmpty()) binding.widgetEmpty.setText(R.string.widget_loading)
    scope.launch {
        val refreshed = try {
            state.refresh(context, manager)
        } catch (e: RuntimeException) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            entries
        }
        binding.widgetEmpty.setText(if (refreshed.isEmpty()) R.string.no_widgets_available else R.string.widget_search_empty)
        if (entries.map { it.key } != refreshed.map { it.key }) {
            entries = refreshed
            filterEntries()
        }
    }

    val dialog = AlertDialog.Builder(context).setView(binding.root).create()
    dialog.setOnDismissListener {
        scope.cancel()
        list.adapter = null
        onDismiss()
    }
    // The opening row may be directly behind a widget. Ignore taps from the same burst.
    val acceptSelectionAfter = SystemClock.uptimeMillis() + 500
    list.setOnItemClickListener { _, _, position, _ ->
        if (dialog.isShowing && SystemClock.uptimeMillis() >= acceptSelectionAfter) {
            if (adapter.needsMoreRoom(position)) {
                Toast.makeText(context, R.string.widget_not_enough_space, Toast.LENGTH_SHORT).show()
            } else {
                val info = adapter.getItem(position).info
                dialog.dismiss()
                onSelect(info)
            }
        }
    }
    binding.widgetCancel.setOnClickListener { dialog.dismiss() }
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    dialog.show()
    binding.root.requestFocus()
    return dialog
}

private class WidgetPickerAdapter(
    private val inflater: LayoutInflater,
    private val manager: AppWidgetManager,
    private val scope: CoroutineScope,
    private val state: WidgetPickerState,
    private val availableWidth: Int?,
    private val availableHeight: Int?,
    private val rowHeight: Int,
    var entries: List<WidgetPickerEntry>,
) : BaseAdapter() {
    private val context = inflater.context
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val previewDispatcher = Dispatchers.IO.limitedParallelism(2)
    val isIdle = MutableStateFlow(true)
    private val renderLock = Mutex()

    override fun getCount() = entries.size
    override fun getItem(position: Int) = entries[position]
    override fun getItemId(position: Int) = position.toLong()

    fun needsMoreRoom(position: Int): Boolean = fitScale(position) < minimumFitScale(position)

    private fun minimumFitScale(position: Int): Float {
        val info = getItem(position).info
        return if (info.resizeMode == AppWidgetProviderInfo.RESIZE_NONE &&
            info.provider.isXiaomiSelectedPhotosWidget()) 0.85f else 1f
    }

    private fun fitScale(position: Int): Float {
        val width = availableWidth ?: return 1f
        val height = availableHeight ?: return 1f
        val entry = getItem(position)
        val info = entry.info
        val padding = entry.padding
        val minimumWidth = minimumWidgetDimension(info, AppWidgetProviderInfo.RESIZE_HORIZONTAL,
            info.minWidth, info.minResizeWidth) + padding.left + padding.right
        val minimumHeight = minimumWidgetDimension(info, AppWidgetProviderInfo.RESIZE_VERTICAL,
            info.minHeight, info.minResizeHeight) + padding.top + padding.bottom
        return minOf(1f, width.toFloat() / minimumWidth.coerceAtLeast(1),
            height.toFloat() / minimumHeight.coerceAtLeast(1))
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = if (convertView == null) ItemWidgetPickerBinding.inflate(inflater, parent, false)
            else ItemWidgetPickerBinding.bind(convertView)
        (row.root.tag as? Job)?.cancel()
        row.root.tag = null

        val (info, label, app, key) = getItem(position)
        row.widgetLabel.text = label
        row.widgetApp.text = app
        val targetWidth = if (Build.VERSION.SDK_INT >= 31) info.targetCellWidth else 0
        val targetHeight = if (Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0
        val columnWidth = ((availableWidth?.takeIf { it > 0 } ?: 320.dpToPx()) / 4f).coerceAtLeast(1f)
        val columns = targetWidth.takeIf { it > 0 } ?: ceil(info.minWidth / columnWidth).toInt().coerceAtLeast(1)
        val rows = targetHeight.takeIf { it > 0 } ?: ceil(info.minHeight.toDouble() / rowHeight).toInt().coerceAtLeast(1)
        val size = context.getString(
            if (targetWidth > 0 && targetHeight > 0) R.string.widget_cells else R.string.widget_cells_estimated,
            columns, rows,
        )
        val tooLarge = needsMoreRoom(position)
        row.root.alpha = if (tooLarge) 0.65f else 1f
        row.widgetSize.text = when {
            tooLarge -> context.getString(R.string.widget_size_no_room, size)
            fitScale(position) < 1f -> context.getString(R.string.widget_size_scaled, size)
            else -> size
        }
        val cached = state.previews[key]
        if (cached != null) {
            row.widgetPreview.setImageDrawable(cached.newDrawable(context.resources))
        } else {
            row.widgetPreview.setImageResource(R.drawable.ic_widget)
            row.root.tag = scope.launch {
                val preview = loadPreview(info)
                preview.constantState?.let { state.previews.put(key, it) }
                row.widgetPreview.setImageDrawable(preview)
            }
        }
        return row.root
    }

    private suspend fun loadPreview(info: AppWidgetProviderInfo): Drawable {
        if (info.previewImage != 0) {
            val image = withContext(previewDispatcher) {
                runCatching { info.loadPreviewImage(context, 0)?.let(::thumbnail) }.getOrNull()
            }
            if (image != null) return image
        }
        if (Build.VERSION.SDK_INT >= 35 &&
            info.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
        ) {
            val generated = withContext(previewDispatcher) {
                runCatching {
                    manager.getWidgetPreview(info.provider, info.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }.getOrNull()
            }
            generated?.let { renderPreview(it) }?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
            renderPreview(RemoteViews(info.provider.packageName, info.previewLayout))?.let { return it }
        }
        return withContext(previewDispatcher) {
            runCatching { info.loadIcon(context, 0)?.let(::thumbnail) }.getOrNull()
                ?: requireNotNull(context.getDrawable(R.drawable.ic_widget))
        }
    }

    private fun thumbnail(drawable: Drawable): Drawable {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return drawable
        val scale = minOf(1f, 96.dpToPx().toFloat() / bitmap.width, 64.dpToPx().toFloat() / bitmap.height)
        if (scale == 1f) return drawable
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return BitmapDrawable(context.resources, Bitmap.createScaledBitmap(bitmap, width, height, true))
    }

    private suspend fun renderPreview(remoteViews: RemoteViews): Drawable? {
        // RemoteViews layout and drawing use the UI thread. Defer them until scrolling stops
        // and let the UI draw between thumbnails.
        return renderLock.withLock {
            delay(16)
            isIdle.first { it }
            runCatching {
                val preview = remoteViews.apply(context, FrameLayout(context))
                preview.measure(
                    View.MeasureSpec.makeMeasureSpec(240.dpToPx(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(144.dpToPx(), View.MeasureSpec.AT_MOST),
                )
                val width = preview.measuredWidth.coerceAtLeast(1)
                val height = preview.measuredHeight.coerceAtLeast(1)
                preview.layout(0, 0, width, height)
                val scale = minOf(1f, 96.dpToPx().toFloat() / width, 64.dpToPx().toFloat() / height)
                val bitmap = Bitmap.createBitmap(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                preview.draw(Canvas(bitmap).apply { scale(scale, scale) })
                BitmapDrawable(context.resources, bitmap)
            }.getOrNull()
        }
    }
}

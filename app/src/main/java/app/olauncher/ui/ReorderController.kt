package app.olauncher.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Drag-to-reorder controller for a vertical [LinearLayout] whose visible children are
 * reorderable rows (home apps or shortcut icons).
 *
 * It performs a smooth, live reorder: the held row follows the finger while the other
 * rows slide to open a gap. Nothing is relayouted during the gesture — only
 * `translationY` is animated — so it stays cheap and jank-free even on weak devices.
 *
 * The controller is deliberately model-agnostic. It speaks only in *visible indices*
 * (0 until the number of visible rows, top to bottom). When the gesture ends it reports
 * the resulting permutation via [onCommit] as a list where `newOrder[destination]` is the
 * source visible index that should end up at that destination. The caller maps those
 * indices onto its own storage slots and persists them.
 *
 * @param rows every candidate row view (some may be GONE); only visible ones participate.
 * @param onCommit invoked once per gesture with the new order, only if it actually changed.
 * @param onLift visual hook called when a row is picked up (e.g. lift + pause wiggle).
 * @param onDrop visual hook called when a row is released (e.g. settle + resume wiggle).
 */
class ReorderController(
    private val rows: List<View>,
    private val onCommit: (newOrder: List<Int>) -> Unit,
    private val onLift: (View) -> Unit,
    private val onDrop: (View) -> Unit,
) {
    private companion object {
        const val SHIFT_DURATION = 140L
        const val LIFT_Z = 12f
    }

    private val touchListener = View.OnTouchListener { v, e -> handleTouch(v, e) }

    // ---- live drag state ----
    private var dragging = false
    private var visibleRows: List<View> = emptyList()
    private val tops = ArrayList<Int>()       // natural top of each visible row (parent coords)
    private val centers = ArrayList<Float>()  // natural vertical center of each visible row
    private var count = 0
    private var fromIndex = -1                 // fixed layout index of the held row
    private var currentPos = -1                // logical position the held row currently occupies
    private var dragView: View? = null
    private var startRawY = 0f

    /** Attaches touch handling to every currently visible row. Safe to call repeatedly. */
    fun enable() {
        rows.forEach { it.setOnTouchListener(if (it.visibility == View.VISIBLE) touchListener else null) }
    }

    /** Detaches handling and clears any leftover transforms. */
    fun disable() {
        dragging = false
        dragView = null
        rows.forEach {
            it.setOnTouchListener(null)
            it.animate().cancel()
            it.translationY = 0f
            it.translationZ = 0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(view: View, event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> startDrag(view, event.rawY)
        MotionEvent.ACTION_MOVE -> if (dragging) { onMove(event.rawY); true } else false
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) { endDrag(); true } else false
        else -> false
    }

    private fun startDrag(view: View, rawY: Float): Boolean {
        val visible = rows.filter { it.visibility == View.VISIBLE }
        val index = visible.indexOf(view)
        if (visible.size < 2 || index < 0) return false

        visibleRows = visible
        count = visible.size
        tops.clear(); centers.clear()
        visible.forEach {
            tops.add(it.top)
            centers.add(it.top + it.height / 2f)
        }
        fromIndex = index
        currentPos = index
        dragView = view
        startRawY = rawY
        dragging = true

        view.translationZ = LIFT_Z
        onLift(view)
        return true
    }

    private fun onMove(rawY: Float) {
        val view = dragView ?: return
        view.translationY = rawY - startRawY

        // Logical position = the slot whose natural center is nearest the held row's center.
        val heldCenter = centers[fromIndex] + view.translationY
        var pos = 0
        var best = Float.MAX_VALUE
        for (k in 0 until count) {
            val d = abs(heldCenter - centers[k])
            if (d < best) { best = d; pos = k }
        }
        if (pos != currentPos) {
            currentPos = pos
            shiftOthers()
        }
    }

    // Slides every non-held row to the position it would occupy with the held row at currentPos.
    private fun shiftOthers() {
        for (i in 0 until count) {
            if (i == fromIndex) continue
            val rankWithoutHeld = if (i < fromIndex) i else i - 1
            val displayPos = if (rankWithoutHeld < currentPos) rankWithoutHeld else rankWithoutHeld + 1
            val target = (tops[displayPos] - tops[i]).toFloat()
            val view = visibleRows[i]
            if (view.translationY != target) {
                view.animate().translationY(target).setDuration(SHIFT_DURATION).start()
            }
        }
    }

    private fun endDrag() {
        dragging = false
        val held = dragView
        val destPos = currentPos
        val from = fromIndex

        // Non-held rows keep their relative order; the held row is inserted at its drop slot.
        val newOrder = (0 until count).filter { it != from }.toMutableList()
        newOrder.add(destPos, from)

        // Snap everything back to baseline before the content is rebound to the new order.
        for (i in 0 until count) {
            val view = visibleRows[i]
            view.animate().cancel()
            view.translationY = 0f
            view.translationZ = 0f
        }
        held?.let { onDrop(it) }
        dragView = null

        val changed = newOrder.withIndex().any { (pos, src) -> pos != src }
        if (changed) onCommit(newOrder)
    }
}

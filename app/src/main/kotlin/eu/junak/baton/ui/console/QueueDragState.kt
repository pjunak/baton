package eu.junak.baton.ui.console

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

internal data class QueueDrag(
    val from: Int,
    val queue: List<Int>,
    val top: Float,
    val height: Int,
    val target: Int = from,
)

/** Pointer ownership stays on the list so dragging can continue after its source row scrolls away. */
internal class QueueDragState(val list: LazyListState) {
    var drag by mutableStateOf<QueueDrag?>(null)
        private set
    var coordinates: LayoutCoordinates? = null
    val handles = mutableMapOf<Int, LayoutCoordinates>()

    fun handleAt(position: Offset): Int? {
        val parent = coordinates?.takeIf { it.isAttached } ?: return null
        return handles.entries.firstOrNull { (_, handle) ->
            handle.isAttached && parent.localBoundingBoxOf(handle).contains(position)
        }?.key
    }

    fun start(index: Int, queue: List<Int>): Boolean {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { queueIndex(it.key) == index } ?: return false
        if (index !in queue.indices) return false
        drag = QueueDrag(index, queue.toList(), item.offset.toFloat(), item.size)
        return true
    }

    fun move(delta: Float) {
        val current = drag ?: return
        val layout = list.layoutInfo
        val top = (current.top + delta).coerceIn(
            layout.viewportStartOffset.toFloat(),
            (layout.viewportEndOffset - current.height).coerceAtLeast(layout.viewportStartOffset).toFloat(),
        )
        drag = current.copy(top = top)
        updateTarget()
    }

    fun updateTarget() {
        val current = drag ?: return
        val rows = list.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            queueIndex(item.key)?.let { QueueRowBounds(it, item.offset.toFloat(), item.size.toFloat()) }
        }
        drag = current.copy(target = queueDropTarget(current.top + current.height / 2f, rows) ?: current.from)
    }

    fun cancel() { drag = null }

    private fun queueIndex(key: Any): Int? = (key as? String)
        ?.takeIf { it.startsWith("q:") }?.split(':')?.getOrNull(1)?.toIntOrNull()
}

internal data class QueueRowBounds(val index: Int, val top: Float, val height: Float)

/** Pick an actual laid-out slot, including variable row heights and duplicate track IDs. */
internal fun queueDropTarget(center: Float, rows: List<QueueRowBounds>): Int? =
    rows.minByOrNull { kotlin.math.abs(center - (it.top + it.height / 2f)) }?.index

internal fun queueAutoScrollSpeed(top: Float, bottom: Float, start: Float, end: Float, edge: Float): Float = when {
    top < start + edge -> -((start + edge - top) / edge).coerceIn(0f, 1f) * 700f
    bottom > end - edge -> ((bottom - end + edge) / edge).coerceIn(0f, 1f) * 700f
    else -> 0f
}

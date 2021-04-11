package com.kmem.myplayer.ui.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 *  Используется адаптером RecyclerView для Drag'n'drop.
 *  Аргументы:
 *      Адаптер RecyclerView.
 */

class PlaylistItemTouchHelperCallback(private val adapter : ItemTouchHelperAdapter) : ItemTouchHelper.Callback() {
    private var dragFrom = -1
    private var dragTo = -1

    override fun isLongPressDragEnabled(): Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (viewHolder.itemViewType != target.itemViewType)
            return false

        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        if (dragFrom == -1)
            dragFrom = fromPosition
        dragTo = toPosition

        adapter.onItemMove(fromPosition, toPosition)

        return true
    }

    private fun onItemDrop(from: Int, to: Int) {
        adapter.updatePositions(from, to)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            onItemDrop(dragFrom, dragTo)
        }

        dragFrom = -1
        dragTo = -1
    }
}
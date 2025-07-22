package com.yenaly.han1meviewer.util

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView


class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val start: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view) - start
        if (position < 0) return
        val column = (position) % spanCount
        Log.i("TAG", "getItemOffsets: $column ${spacing - column * spacing / spanCount} ${(column + 1) * spacing / spanCount}")

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount
        } else {
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
        }
    }
}

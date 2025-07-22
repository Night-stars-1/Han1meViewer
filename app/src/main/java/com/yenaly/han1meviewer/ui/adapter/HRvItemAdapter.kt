package com.yenaly.han1meviewer.ui.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.yenaly.han1meviewer.ui.adapter.RvWrapper.Companion.wrappedWith

class HRvItemAdapter(
    private val titleAdapter: VideoColumnTitleAdapter,
    private val contentAdapter: RvWrapper<out ViewHolder>,
): RecyclerView.Adapter<ViewHolder>() {
    class HLinearLayoutViewHolder(val linearLayout: LinearLayout) : ViewHolder(linearLayout)

    companion object {
        fun RecyclerView.Adapter<*>.withTitleSection(
            @StringRes titleResId: Int,
            onMoreListener: (View) -> Unit
        ): HRvItemAdapter {
            val titleAdapter = VideoColumnTitleAdapter(titleResId).apply {
                onMoreHanimeListener = onMoreListener
            }
            val contentAdapter = this.wrappedWith {
                LinearLayoutManager(null, LinearLayoutManager.HORIZONTAL, false)
            }
            return HRvItemAdapter(titleAdapter, contentAdapter)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val linearLayout = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            isNestedScrollingEnabled = false
        }

        return HLinearLayoutViewHolder(linearLayout)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val linearLayout = (holder as HLinearLayoutViewHolder).linearLayout
        linearLayout.removeAllViews()

        val titleRecyclerView = RecyclerView(linearLayout.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            adapter = titleAdapter
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
        }
        linearLayout.addView(titleRecyclerView)

        val contentRecyclerView = RecyclerView(linearLayout.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            adapter = contentAdapter
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
        }
        linearLayout.addView(contentRecyclerView)
    }

    override fun getItemCount(): Int = 1
}
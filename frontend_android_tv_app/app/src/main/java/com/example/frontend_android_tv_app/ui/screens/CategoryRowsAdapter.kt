package com.example.frontend_android_tv_app.ui.screens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.Video

class CategoryRowsAdapter(
    private val categories: List<String>,
    private val videosForCategory: (String) -> List<Video>,
    private val onVideoSelected: (Video) -> Unit
) : RecyclerView.Adapter<CategoryRowsAdapter.RowVH>() {

    private val rowAdapters = mutableMapOf<Int, VideosRowAdapter>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_row, parent, false)
        return RowVH(v)
    }

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val category = categories[position]
        holder.title.text = category

        holder.rowList.layoutManager = LinearLayoutManager(holder.itemView.context, RecyclerView.HORIZONTAL, false)
        holder.rowList.isFocusable = false // focus on child cards, not the nested RV itself

        val adapter = VideosRowAdapter(
            videos = videosForCategory(category),
            onVideoSelected = onVideoSelected
        )

        rowAdapters[position] = adapter
        holder.rowList.adapter = adapter
    }

    override fun getItemCount(): Int = categories.size

    fun requestFocusFor(parentRecycler: RecyclerView, rowIndex: Int, colIndex: Int) {
        parentRecycler.scrollToPosition(rowIndex)
        parentRecycler.post {
            val rowHolder = parentRecycler.findViewHolderForAdapterPosition(rowIndex) as? RowVH ?: return@post
            val rowAdapter = rowAdapters[rowIndex] ?: return@post
            rowHolder.rowList.scrollToPosition(colIndex)
            rowHolder.rowList.post {
                val child = rowHolder.rowList.layoutManager?.findViewByPosition(colIndex)
                if (child != null) {
                    child.requestFocus()
                    rowAdapter.setFocusedIndex(colIndex)
                }
            }
        }
    }

    class RowVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.row_title)
        val rowList: RecyclerView = itemView.findViewById(R.id.row_list)
    }
}

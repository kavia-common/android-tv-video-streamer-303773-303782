package com.example.frontend_android_tv_app.ui.screens

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.ui.Theme

class VideosRowAdapter(
    private val videos: List<Video>,
    private val onVideoSelected: (Video) -> Unit
) : RecyclerView.Adapter<VideosRowAdapter.VideoVH>() {

    private var focusedIndex: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_video_card, parent, false)
        return VideoVH(v)
    }

    override fun onBindViewHolder(holder: VideoVH, position: Int) {
        // Use `position` only to bind current UI. Do not capture it for later.
        val video = videos[position]
        holder.title.text = video.title

        Glide.with(holder.thumbnail)
            .load(video.thumbnailUrl)
            .centerCrop()
            .into(holder.thumbnail)

        // Update focus visuals without relying on the potentially-stale `position` later.
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener

            if (hasFocus) {
                focusedIndex = adapterPos
                holder.applyFocused(true)
                // Scale slightly for TV emphasis.
                holder.itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
            } else {
                holder.applyFocused(false)
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return@setOnKeyListener false
                onVideoSelected(videos[adapterPos])
                true
            } else {
                false
            }
        }

        holder.applyFocused(position == focusedIndex && holder.itemView.hasFocus())
    }

    override fun getItemCount(): Int = videos.size

    fun setFocusedIndex(idx: Int) {
        focusedIndex = idx.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    class VideoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.card_thumbnail)
        val title: TextView = itemView.findViewById(R.id.card_title)
        private val overlay: View = itemView.findViewById(R.id.card_focus_overlay)

        fun applyFocused(focused: Boolean) {
            if (focused) {
                val drawable = GradientDrawable()
                drawable.setColor(0x00000000)
                drawable.setStroke(4, Theme.FocusRing)
                drawable.cornerRadius = 16f
                overlay.background = drawable
                overlay.alpha = 1f
                overlay.setPadding(0, 0, 0, 0)
            } else {
                overlay.background = null
                overlay.alpha = 0f
            }
        }
    }
}

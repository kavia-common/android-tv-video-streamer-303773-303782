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
    private val onVideoSelected: (Video) -> Unit,
    private val progressPercentForVideoId: ((videoId: String) -> Int?)? = null,
    private val isFavorite: ((videoId: String) -> Boolean)? = null
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

            val isFav = isFavorite?.invoke(videos[adapterPos].id) == true

            if (hasFocus) {
                focusedIndex = adapterPos
                holder.applyFocused(true)
                // Scale slightly for TV emphasis (favorites get a tiny extra pop).
                val targetScale = if (isFav) 1.08f else 1.06f
                holder.itemView.animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start()
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

        // Favorites overlay
        val fav = isFavorite?.invoke(video.id) == true
        holder.bindFavorite(fav)

        // Continue Watching progress overlay
        val pct = progressPercentForVideoId?.invoke(video.id)
        holder.bindProgressPercent(pct)

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

        private val favoriteBadge: View = itemView.findViewById(R.id.card_favorite_badge)

        private val progressContainer: View = itemView.findViewById(R.id.card_progress_container)
        private val progressFill: View = itemView.findViewById(R.id.card_progress_fill)
        private val progressRest: View = itemView.findViewById(R.id.card_progress_rest)

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

        fun bindFavorite(isFavorite: Boolean) {
            favoriteBadge.visibility = if (isFavorite) View.VISIBLE else View.GONE
        }

        fun bindProgressPercent(percent: Int?) {
            if (percent == null) {
                progressContainer.visibility = View.GONE
                return
            }

            val pct = percent.coerceIn(0, 100)
            // We use weights to represent fill/rest portions (no need to know exact width).
            val fillParams = progressFill.layoutParams as? android.widget.LinearLayout.LayoutParams
            val restParams = progressRest.layoutParams as? android.widget.LinearLayout.LayoutParams
            if (fillParams != null && restParams != null) {
                fillParams.weight = pct.toFloat()
                restParams.weight = (100 - pct).toFloat()
                progressFill.layoutParams = fillParams
                progressRest.layoutParams = restParams
            }
            progressContainer.visibility = View.VISIBLE
        }
    }
}

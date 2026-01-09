package com.example.frontend_android_tv_app.ui.screens.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.ui.screens.VideosRowAdapter

/**
 * Results adapter that reuses the existing video card layout and view holder logic
 * (favorite + progress overlays + focus visuals).
 *
 * Implementation approach:
 * - We wrap the existing VideosRowAdapter.VideoVH to avoid duplicating card code.
 */
class SearchResultsAdapter(
    private val onVideoSelected: (Video) -> Unit,
    private val progressPercentForVideoId: ((videoId: String) -> Int?)? = null,
    private val isFavorite: ((videoId: String) -> Boolean)? = null
) : RecyclerView.Adapter<VideosRowAdapter.VideoVH>() {

    private var videos: List<Video> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosRowAdapter.VideoVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_video_card, parent, false)
        return VideosRowAdapter.VideoVH(v)
    }

    override fun onBindViewHolder(holder: VideosRowAdapter.VideoVH, position: Int) {
        val video = videos[position]
        // Reuse the same binding logic pattern from VideosRowAdapter:
        // - title + thumbnail
        // - favorite + progress overlays
        // - focus ring
        // We cannot call VideosRowAdapter.onBind directly, so we replicate minimal bind here by
        // instantiating a helper adapter for focus/animations? That would be heavy.
        //
        // Instead, we implement a simple binding by leveraging the holder's public methods.
        // We still need to set thumbnail and title; those fields are public in VideoVH.
        holder.title.text = video.title
        com.bumptech.glide.Glide.with(holder.thumbnail)
            .load(video.thumbnailUrl)
            .centerCrop()
            .into(holder.thumbnail)

        holder.bindFavorite(isFavorite?.invoke(video.id) == true)
        holder.bindProgressPercent(progressPercentForVideoId?.invoke(video.id))

        holder.itemView.setOnClickListener { onVideoSelected(video) }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                onVideoSelected(video)
                true
            } else {
                false
            }
        }

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.applyFocused(hasFocus)
            if (hasFocus) {
                holder.itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
            } else {
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }

        holder.applyFocused(holder.itemView.hasFocus())
    }

    override fun getItemCount(): Int = videos.size

    // PUBLIC_INTERFACE
    fun submitList(newList: List<Video>) {
        /** Replace the current results list. */
        videos = newList
        notifyDataSetChanged()
    }

    // PUBLIC_INTERFACE
    fun getItem(position: Int): Video? {
        /** Get a result item at position (or null). */
        return videos.getOrNull(position)
    }
}

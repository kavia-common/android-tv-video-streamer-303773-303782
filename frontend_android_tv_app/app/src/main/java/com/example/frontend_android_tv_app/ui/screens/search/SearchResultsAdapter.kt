package com.example.frontend_android_tv_app.ui.screens.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
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

        holder.title.text = video.title
        com.bumptech.glide.Glide.with(holder.thumbnail)
            .load(video.thumbnailUrl)
            .centerCrop()
            .into(holder.thumbnail)

        val fav = isFavorite?.invoke(video.id) == true
        val pct = progressPercentForVideoId?.invoke(video.id)

        holder.bindFavorite(fav)
        holder.bindProgressPercent(pct)

        // Accessibility: ensure card root has a full description similar to Home cards.
        // Search results don't have a single "row category", so we use the video's own category.
        val ctx = holder.itemView.context
        val pctText = pct?.coerceIn(0, 100)?.let { ctx.getString(R.string.a11y_percent_watched, it) }
        val favText = if (fav) ctx.getString(R.string.a11y_favorited) else null
        holder.itemView.contentDescription = when {
            fav && pctText != null ->
                ctx.getString(R.string.a11y_video_card_favorited_progress, video.title, video.category, favText, pctText)
            fav ->
                ctx.getString(R.string.a11y_video_card_favorited, video.title, video.category, favText)
            pctText != null ->
                ctx.getString(R.string.a11y_video_card_progress, video.title, video.category, pctText)
            else ->
                ctx.getString(R.string.a11y_video_card, video.title, video.category)
        }

        // Treat as actionable.
        holder.itemView.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }

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

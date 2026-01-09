package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.ui.focus.GridFocusManager

/**
 * Home screen: horizontal category rows with video thumbnails.
 *
 * Adds an optional Continue Watching row at the top (local-only, based on saved playback progress).
 *
 * DPAD navigation:
 * - Left/Right: move within row (wrap-around)
 * - Up/Down: move between rows
 * - Center/Enter: open details (or resume playback for Continue Watching row)
 */
class HomeFragment : Fragment() {

    interface Host {
        fun openDetails(video: Video)
        fun playVideo(video: Video, resumePositionMs: Long)
    }

    private lateinit var rowsRecycler: RecyclerView
    private lateinit var adapter: CategoryRowsAdapter

    private lateinit var progressStore: ProgressStore

    private var categoriesForUi: List<String> = emptyList()
    private var continueWatching: List<ProgressStore.VideoProgress> = emptyList()

    private lateinit var focusManager: GridFocusManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onResume() {
        super.onResume()
        // Refresh whenever returning from Player (or anywhere) so row updates immediately.
        refreshRows()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progressStore = ProgressStore.from(requireContext())

        rowsRecycler = view.findViewById(R.id.rows_recycler)
        rowsRecycler.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        rowsRecycler.isFocusable = true
        rowsRecycler.isFocusableInTouchMode = true

        refreshRows()

        // Handle DPAD at the Home screen level to allow wrap-around and row switching.
        rowsRecycler.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focusManager.moveLeft(wrap = true)) {
                        adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focusManager.moveRight(wrap = true)) {
                        adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (focusManager.moveUp()) {
                        adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (focusManager.moveDown()) {
                        adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
                        true
                    } else false
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val selected = getSelectedVideo(focusManager.rowIndex, focusManager.colIndex)
                    if (selected != null) {
                        if (isContinueWatchingRow(focusManager.rowIndex)) {
                            val p = progressStore.getProgress(selected.id)
                            val resumePos = p?.positionMs ?: 0L
                            (activity as? Host)?.playVideo(selected, resumePos)
                        } else {
                            (activity as? Host)?.openDetails(selected)
                        }
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun refreshRows() {
        continueWatching = progressStore.listInProgress()
        categoriesForUi = if (continueWatching.isNotEmpty()) {
            listOf(CONTINUE_WATCHING_LABEL) + VideosRepository.categories
        } else {
            VideosRepository.categories
        }

        // Focus manager depends on row count + per-row columns.
        focusManager = GridFocusManager(
            rowCount = categoriesForUi.size,
            columnsForRow = { row -> getVideosForRow(row).size }
        )

        adapter = CategoryRowsAdapter(
            categories = categoriesForUi,
            videosForCategory = { cat ->
                if (cat == CONTINUE_WATCHING_LABEL) {
                    continueWatching.map { it.video }
                } else {
                    VideosRepository.videosForCategory(cat)
                }
            },
            onVideoSelected = { video ->
                // Default click behavior for cards: details, except continue-watching should resume.
                if (continueWatching.any { it.video.id == video.id }) {
                    val p = progressStore.getProgress(video.id)
                    (activity as? Host)?.playVideo(video, p?.positionMs ?: 0L)
                } else {
                    (activity as? Host)?.openDetails(video)
                }
            },
            progressPercentForVideoId = { videoId ->
                // Only show progress bar for Continue Watching items.
                continueWatching.firstOrNull { it.video.id == videoId }?.percent
            }
        )

        rowsRecycler.adapter = adapter

        // Set initial focus to first row/first item once layout is ready.
        rowsRecycler.post {
            focusManager.setFocus(0, 0)
            adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
        }
    }

    private fun getVideosForRow(rowIndex: Int): List<Video> {
        val cat = categoriesForUi.getOrNull(rowIndex) ?: return emptyList()
        return if (cat == CONTINUE_WATCHING_LABEL) {
            continueWatching.map { it.video }
        } else {
            VideosRepository.videosForCategory(cat)
        }
    }

    private fun getSelectedVideo(rowIndex: Int, colIndex: Int): Video? {
        val list = getVideosForRow(rowIndex)
        return list.getOrNull(colIndex)
    }

    private fun isContinueWatchingRow(rowIndex: Int): Boolean {
        return categoriesForUi.getOrNull(rowIndex) == CONTINUE_WATCHING_LABEL
    }

    companion object {
        private const val CONTINUE_WATCHING_LABEL = "Continue Watching"
    }
}

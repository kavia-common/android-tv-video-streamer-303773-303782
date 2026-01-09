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
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.ui.focus.GridFocusManager

/**
 * Home screen: horizontal category rows with video thumbnails.
 * DPAD navigation:
 * - Left/Right: move within row (wrap-around)
 * - Up/Down: move between rows
 * - Center/Enter: open details
 */
class HomeFragment : Fragment() {

    interface Host {
        fun openDetails(video: Video)
    }

    private lateinit var rowsRecycler: RecyclerView
    private lateinit var adapter: CategoryRowsAdapter

    private val focusManager = GridFocusManager(
        rowCount = VideosRepository.categories.size,
        columnsForRow = { row -> VideosRepository.videosForCategory(VideosRepository.categories[row]).size }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rowsRecycler = view.findViewById(R.id.rows_recycler)
        rowsRecycler.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        rowsRecycler.isFocusable = true
        rowsRecycler.isFocusableInTouchMode = true

        adapter = CategoryRowsAdapter(
            categories = VideosRepository.categories,
            videosForCategory = { cat -> VideosRepository.videosForCategory(cat) },
            onVideoSelected = { video ->
                (activity as? Host)?.openDetails(video)
            }
        )

        rowsRecycler.adapter = adapter

        // Set initial focus to first row/first item once layout is ready.
        rowsRecycler.post {
            focusManager.setFocus(0, 0)
            adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
        }

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
                    val cat = VideosRepository.categories[focusManager.rowIndex]
                    val list = VideosRepository.videosForCategory(cat)
                    val selected = list.getOrNull(focusManager.colIndex)
                    if (selected != null) {
                        (activity as? Host)?.openDetails(selected)
                        true
                    } else false
                }
                else -> false
            }
        }
    }
}

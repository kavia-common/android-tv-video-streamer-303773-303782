package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.FavoritesStore
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.ui.focus.GridFocusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Home screen: horizontal category rows with video thumbnails.
 *
 * Adds:
 * - Optional Continue Watching row at the top (local-only, based on saved playback progress).
 * - Optional Favorites row near the top (after Continue Watching if present).
 * - Search entry in the top bar.
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

        // PUBLIC_INTERFACE
        fun playVideoWithContext(
            video: Video,
            resumePositionMs: Long,
            rowKey: String,
            rowVideoIds: ArrayList<String>,
            currentIndex: Int
        )

        // PUBLIC_INTERFACE
        fun openSearch()
    }

    private lateinit var rowsRecycler: RecyclerView
    private lateinit var adapter: CategoryRowsAdapter
    private lateinit var btnOpenSearch: Button

    private lateinit var progressStore: ProgressStore
    private lateinit var favoritesStore: FavoritesStore

    private var categoriesForUi: List<String> = emptyList()
    private var continueWatching: List<ProgressStore.VideoProgress> = emptyList()

    private var favoritesIds: Set<String> = emptySet()
    private var favoritesVideos: List<Video> = emptyList()

    private lateinit var focusManager: GridFocusManager

    private var favoritesCollectJob: Job? = null

    // Remember focus so returning from Search restores the last focused item.
    private var lastFocusedRow: Int = 0
    private var lastFocusedCol: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progressStore = ProgressStore.from(requireContext())
        favoritesStore = FavoritesStore.from(requireContext())

        btnOpenSearch = view.findViewById(R.id.btn_open_search)
        btnOpenSearch.setOnClickListener {
            // Store current focus before navigating away.
            lastFocusedRow = if (this::focusManager.isInitialized) focusManager.rowIndex else 0
            lastFocusedCol = if (this::focusManager.isInitialized) focusManager.colIndex else 0
            (activity as? Host)?.openSearch()
        }

        rowsRecycler = view.findViewById(R.id.rows_recycler)
        rowsRecycler.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        rowsRecycler.isFocusable = true
        rowsRecycler.isFocusableInTouchMode = true

        // Keep Home in sync in real time with favorites changes.
        favoritesCollectJob?.cancel()
        favoritesCollectJob = viewLifecycleOwner.lifecycleScope.launch {
            favoritesStore.favoritesFlow().collectLatest { ids ->
                favoritesIds = ids
                favoritesVideos = resolveFavorites(ids)

                // Rebuild rows whenever favorites set changes (so row appears/disappears).
                refreshRows(restoreFocus = true)
            }
        }

        refreshRows(restoreFocus = false)

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
                    } else {
                        // At top: move focus to Search button for convenience.
                        btnOpenSearch.requestFocus()
                        true
                    }
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
                        val rowKey = getRowKey(focusManager.rowIndex)
                        val rowList = getVideosForRow(focusManager.rowIndex)
                        val ids = ArrayList(rowList.map { it.id })
                        val idx = focusManager.colIndex.coerceIn(0, (rowList.size - 1).coerceAtLeast(0))

                        if (isContinueWatchingRow(focusManager.rowIndex)) {
                            val p = progressStore.getProgress(selected.id)
                            val resumePos = p?.positionMs ?: 0L
                            (activity as? Host)?.playVideoWithContext(
                                video = selected,
                                resumePositionMs = resumePos,
                                rowKey = PlayerFragment.ROWKEY_CONTINUE_WATCHING,
                                rowVideoIds = ids,
                                currentIndex = idx
                            )
                        } else {
                            // For non-continue-watching rows, keep current behavior (details) to match UX.
                            // Autoplay context will be passed from details when play is pressed.
                            (activity as? Host)?.openDetails(selected)
                        }
                        true
                    } else false
                }

                else -> false
            }
        }

        // Let user DPAD down from Search into content.
        btnOpenSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh whenever returning from Player (or anywhere) so Continue Watching updates immediately.
        // Favorites are updated by Flow subscription, but refreshing is still cheap and keeps ordering fresh.
        refreshRows(restoreFocus = true)
    }

    private fun refreshRows(restoreFocus: Boolean) {
        val prevRow = if (this::focusManager.isInitialized) focusManager.rowIndex else lastFocusedRow
        val prevCol = if (this::focusManager.isInitialized) focusManager.colIndex else lastFocusedCol

        continueWatching = progressStore.listInProgress()
        val hasContinue = continueWatching.isNotEmpty()
        val hasFavorites = favoritesVideos.isNotEmpty()

        categoriesForUi = buildList {
            if (hasContinue) add(CONTINUE_WATCHING_LABEL)
            if (hasFavorites) add(FAVORITES_LABEL)
            addAll(VideosRepository.categories)
        }

        // Focus manager depends on row count + per-row columns.
        focusManager = GridFocusManager(
            rowCount = categoriesForUi.size,
            columnsForRow = { row -> getVideosForRow(row).size }
        )

        adapter = CategoryRowsAdapter(
            categories = categoriesForUi,
            videosForCategory = { cat ->
                when (cat) {
                    CONTINUE_WATCHING_LABEL -> continueWatching.map { it.video }
                    FAVORITES_LABEL -> favoritesVideos
                    else -> VideosRepository.videosForCategory(cat)
                }
            },
            onVideoSelected = { video ->
                // Default click behavior for cards: details, except continue-watching should resume.
                if (continueWatching.any { it.video.id == video.id }) {
                    val p = progressStore.getProgress(video.id)
                    val rowList = continueWatching.map { it.video }
                    val idx = rowList.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                    (activity as? Host)?.playVideoWithContext(
                        video = video,
                        resumePositionMs = p?.positionMs ?: 0L,
                        rowKey = PlayerFragment.ROWKEY_CONTINUE_WATCHING,
                        rowVideoIds = ArrayList(rowList.map { it.id }),
                        currentIndex = idx
                    )
                } else {
                    (activity as? Host)?.openDetails(video)
                }
            },
            progressPercentForVideoId = { videoId ->
                // Only show progress bar for Continue Watching items.
                continueWatching.firstOrNull { it.video.id == videoId }?.percent
            },
            isFavorite = { videoId ->
                favoritesIds.contains(videoId)
            }
        )

        rowsRecycler.adapter = adapter

        // Restore focus if requested; otherwise set initial focus to first row/first item.
        rowsRecycler.post {
            if (restoreFocus) {
                val safeRow = prevRow.coerceIn(0, (categoriesForUi.size - 1).coerceAtLeast(0))
                val cols = getVideosForRow(safeRow).size.coerceAtLeast(1)
                val safeCol = prevCol.coerceIn(0, cols - 1)
                focusManager.setFocus(safeRow, safeCol)
            } else {
                focusManager.setFocus(0, 0)
            }
            adapter.requestFocusFor(rowsRecycler, focusManager.rowIndex, focusManager.colIndex)
        }
    }

    private fun resolveFavorites(ids: Set<String>): List<Video> {
        if (ids.isEmpty()) return emptyList()
        val byId = VideosRepository.videos.associateBy { it.id }
        // Keep stable ordering (sorted by id) to avoid row shuffling on rebind.
        return ids.toList().sorted().mapNotNull { byId[it] }
    }

    private fun getVideosForRow(rowIndex: Int): List<Video> {
        val cat = categoriesForUi.getOrNull(rowIndex) ?: return emptyList()
        return when (cat) {
            CONTINUE_WATCHING_LABEL -> continueWatching.map { it.video }
            FAVORITES_LABEL -> favoritesVideos
            else -> VideosRepository.videosForCategory(cat)
        }
    }

    private fun getSelectedVideo(rowIndex: Int, colIndex: Int): Video? {
        val list = getVideosForRow(rowIndex)
        return list.getOrNull(colIndex)
    }

    private fun isContinueWatchingRow(rowIndex: Int): Boolean {
        return categoriesForUi.getOrNull(rowIndex) == CONTINUE_WATCHING_LABEL
    }

    private fun getRowKey(rowIndex: Int): String {
        val cat = categoriesForUi.getOrNull(rowIndex) ?: return ""
        return when (cat) {
            CONTINUE_WATCHING_LABEL -> PlayerFragment.ROWKEY_CONTINUE_WATCHING
            FAVORITES_LABEL -> PlayerFragment.ROWKEY_FAVORITES
            else -> cat
        }
    }

    companion object {
        private const val CONTINUE_WATCHING_LABEL = "Continue Watching"
        private const val FAVORITES_LABEL = "Favorites"
    }
}

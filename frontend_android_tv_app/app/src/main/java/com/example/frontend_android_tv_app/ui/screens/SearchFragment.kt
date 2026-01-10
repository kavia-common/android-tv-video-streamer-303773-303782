package com.example.frontend_android_tv_app.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.data.FavoritesStore
import com.example.frontend_android_tv_app.data.ProgressStore
import com.example.frontend_android_tv_app.data.RecentSearchesStore
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.data.VideoSearchIndex
import com.example.frontend_android_tv_app.data.VideosRepository
import com.example.frontend_android_tv_app.ui.screens.search.KeyboardAdapter
import com.example.frontend_android_tv_app.ui.screens.search.KeyboardKey
import com.example.frontend_android_tv_app.ui.screens.search.SearchResultsAdapter

/**
 * Search screen optimized for Android TV DPAD.
 *
 * Layout:
 * - Left: on-screen keyboard (grid)
 * - Right: live results grid (video cards)
 *
 * DPAD behavior:
 * - Arrow keys move focus within current zone.
 * - LEFT from results moves focus back to keyboard.
 * - RIGHT from keyboard attempts to move into results if results exist.
 * - OK/ENTER on a key appends / performs action (Backspace/Clear/Done).
 * - BACK key:
 *    - If query not empty: backspace
 *    - Else: exit (goBack)
 */
class SearchFragment : Fragment() {

    interface Host {
        fun openDetails(video: Video)
        fun goBack()
    }

    private lateinit var queryText: TextView
    private lateinit var hintText: TextView
    private lateinit var keyboardRecycler: RecyclerView
    private lateinit var resultsRecycler: RecyclerView

    private lateinit var progressStore: ProgressStore
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var recentSearchesStore: RecentSearchesStore

    private lateinit var keyboardAdapter: KeyboardAdapter
    private lateinit var resultsAdapter: SearchResultsAdapter

    private var query: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progressStore = ProgressStore.from(requireContext())
        favoritesStore = FavoritesStore.from(requireContext())
        recentSearchesStore = RecentSearchesStore.from(requireContext())

        queryText = view.findViewById(R.id.search_query_text)
        hintText = view.findViewById(R.id.search_hint_text)
        keyboardRecycler = view.findViewById(R.id.keyboard_recycler)
        resultsRecycler = view.findViewById(R.id.results_recycler)

        setupKeyboard()
        setupResults()

        // Root captures Back behavior.
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (query.isNotEmpty()) {
                        applyBackspace()
                        true
                    } else {
                        (activity as? Host)?.goBack()
                        true
                    }
                }

                else -> false
            }
        }

        // Initial state
        renderQuery()
        updateResults()
        keyboardRecycler.post { keyboardRecycler.requestFocus() }
    }

    private fun setupKeyboard() {
        val keys = buildKeyboardKeys()
        keyboardAdapter = KeyboardAdapter(keys) { key ->
            onKeyboardKeyPressed(key)
        }

        val spanCount = 6
        keyboardRecycler.layoutManager = GridLayoutManager(requireContext(), spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val k = keys[position]
                    // Make special keys wider.
                    return when (k) {
                        is KeyboardKey.TextKey -> when (k.action) {
                            KeyboardKey.Action.SPACE -> 3
                            KeyboardKey.Action.BACKSPACE -> 2
                            KeyboardKey.Action.CLEAR -> 2
                            KeyboardKey.Action.DONE -> 3
                        }

                        else -> 1
                    }
                }
            }
        }
        keyboardRecycler.adapter = keyboardAdapter
        keyboardRecycler.isFocusable = true
        keyboardRecycler.isFocusableInTouchMode = true

        // Allow RIGHT to jump into results.
        keyboardRecycler.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (resultsAdapter.itemCount > 0) {
                    resultsRecycler.requestFocus()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun setupResults() {
        resultsAdapter = SearchResultsAdapter(
            onVideoSelected = { video ->
                // When user opens a result, store query in recent list (if any).
                if (query.isNotBlank()) {
                    recentSearchesStore.push(query)
                }
                (activity as? Host)?.openDetails(video)
            },
            progressPercentForVideoId = { videoId ->
                progressStore.getProgress(videoId)?.percent
            },
            isFavorite = { videoId ->
                favoritesStore.isFavorite(videoId)
            }
        )

        // Use larger cards in a 3-column grid (fits within typical TV safe area).
        resultsRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        resultsRecycler.adapter = resultsAdapter
        resultsRecycler.isFocusable = true
        resultsRecycler.isFocusableInTouchMode = true

        // LEFT should return to keyboard.
        resultsRecycler.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    keyboardRecycler.requestFocus()
                    true
                }

                else -> false
            }
        }
    }

    private fun buildKeyboardKeys(): List<KeyboardKey> {
        val letters = ('A'..'Z').map { KeyboardKey.CharKey(it) }
        val digits = ('0'..'9').map { KeyboardKey.CharKey(it) }

        // Keep predictable ordering for DPAD muscle memory.
        return buildList {
            addAll(letters)
            addAll(digits)
            add(KeyboardKey.TextKey(label = "Space", action = KeyboardKey.Action.SPACE))
            add(KeyboardKey.TextKey(label = "Back", action = KeyboardKey.Action.BACKSPACE))
            add(KeyboardKey.TextKey(label = "Clear", action = KeyboardKey.Action.CLEAR))
            add(KeyboardKey.TextKey(label = "Done", action = KeyboardKey.Action.DONE))
        }
    }

    private fun onKeyboardKeyPressed(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.CharKey -> {
                query += key.c
                renderQuery()
                updateResults()
            }

            is KeyboardKey.TextKey -> {
                when (key.action) {
                    KeyboardKey.Action.SPACE -> {
                        query += " "
                        renderQuery()
                        updateResults()
                    }

                    KeyboardKey.Action.BACKSPACE -> {
                        applyBackspace()
                    }

                    KeyboardKey.Action.CLEAR -> {
                        query = ""
                        renderQuery()
                        updateResults()
                    }

                    KeyboardKey.Action.DONE -> {
                        // If query empty: behave like "exit".
                        if (query.isBlank()) {
                            (activity as? Host)?.goBack()
                        } else {
                            // Otherwise, move focus to results if available; else keep on keyboard.
                            if (resultsAdapter.itemCount > 0) {
                                resultsRecycler.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyBackspace() {
        if (query.isEmpty()) return
        query = query.dropLast(1)
        renderQuery()
        updateResults()
    }

    private fun renderQuery() {
        val display = if (query.isBlank()) "Search…" else query
        queryText.text = display

        // Accessibility: keep contentDescription in sync and let TalkBack announce changes
        // via the polite live region set in the XML.
        val spoken = if (query.isBlank()) "" else query
        queryText.contentDescription = getString(R.string.a11y_search_query, spoken)
    }

    private fun updateResults() {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            // Optional nicety: show recent searches or popular categories.
            val recent = recentSearchesStore.list()
            hintText.visibility = View.VISIBLE
            hintText.text = if (recent.isNotEmpty()) {
                "Recent: ${recent.joinToString(" • ")}"
            } else {
                "Type using the keyboard to search by title or category."
            }
            resultsAdapter.submitList(emptyList())
            return
        }

        hintText.visibility = View.GONE
        val results = VideoSearchIndex.search(trimmed)

        // If you search a category name exactly, optionally promote that category's items.
        val cat = VideosRepository.categories.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        val promoted = if (cat != null) {
            val byCat = VideosRepository.videosForCategory(cat)
            (byCat + results).distinctBy { it.id }
        } else {
            results
        }

        resultsAdapter.submitList(promoted)
    }
}

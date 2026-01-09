package com.example.frontend_android_tv_app.ui.screens.search

/**
 * Represents a single on-screen keyboard key.
 */
sealed class KeyboardKey {
    data class CharKey(val c: Char) : KeyboardKey()
    data class TextKey(val label: String, val action: Action) : KeyboardKey()

    enum class Action {
        SPACE,
        BACKSPACE,
        CLEAR,
        DONE
    }
}

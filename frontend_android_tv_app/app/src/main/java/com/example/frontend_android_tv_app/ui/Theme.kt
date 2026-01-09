package com.example.frontend_android_tv_app.ui

import android.graphics.Color

/**
 * Centralized theme colors for consistent light UI styling.
 */
object Theme {
    // Light theme
    const val Background = 0xFFF9FAFB.toInt()
    const val Surface = 0xFFFFFFFF.toInt()
    const val Text = 0xFF111827.toInt()
    const val SecondaryText = 0xFF64748B.toInt()

    // Accents
    const val Primary = 0xFF3B82F6.toInt()
    const val Success = 0xFF06B6D4.toInt()
    const val Error = 0xFFEF4444.toInt()

    // Focus
    const val FocusRing = Primary
    const val FocusShadow = 0x553B82F6

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}

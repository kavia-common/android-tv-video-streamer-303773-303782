package com.example.frontend_android_tv_app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.KeyEvent
import android.widget.TextView

/**
 * Main Activity for Android TV
 * Extends FragmentActivity for Leanback compatibility
 */
class MainActivity : FragmentActivity() {

    private lateinit var titleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        titleText = findViewById(R.id.title_text)
        titleText.text = "frontend_android_tv_app"
        
        // TODO: Initialize your rating screen components here
        // setupRatingOverlay()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle TV remote control inputs
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // Handle SELECT/OK button
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Handle BACK button
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

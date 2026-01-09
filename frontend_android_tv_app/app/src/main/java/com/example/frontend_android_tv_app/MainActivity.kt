package com.example.frontend_android_tv_app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.frontend_android_tv_app.data.Video
import com.example.frontend_android_tv_app.ui.screens.HomeFragment
import com.example.frontend_android_tv_app.ui.screens.PlayerFragment
import com.example.frontend_android_tv_app.ui.screens.VideoDetailsFragment

/**
 * Main Activity for Android TV app.
 *
 * Hosts 3 internal screens (Fragments):
 * - Home: browse category rows and thumbnails
 * - VideoDetails: show metadata and a Play button
 * - Player: video playback with DPAD controls + overlay
 */
class MainActivity : FragmentActivity(),
    HomeFragment.Host,
    VideoDetailsFragment.Host,
    PlayerFragment.Host {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.screen_container, HomeFragment())
                .commit()
        }
    }

    override fun openDetails(video: Video) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.screen_container, VideoDetailsFragment.newInstance(video))
            .addToBackStack("details")
            .commit()
    }

    override fun playVideo(video: Video) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.screen_container, PlayerFragment.newInstance(video))
            .addToBackStack("player")
            .commit()
    }

    override fun goBack() {
        if (!supportFragmentManager.popBackStackImmediate()) {
            finish()
        }
    }

    override fun onBackPressed() {
        goBack()
    }
}

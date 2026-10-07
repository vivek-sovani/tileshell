package com.tileshell.feature.livetiles

import android.app.Activity
import android.os.Bundle

/**
 * The phone-side entry for "play <something> on TileShell": the assistant sends
 * `android.media.action.MEDIA_PLAY_FROM_SEARCH` here (the same request Android
 * Auto's `onPlayFromSearch` handles), and declaring it is what lets the assistant
 * treat TileShell as a music app it can pick. No screen: it starts playback and
 * closes at once.
 */
class PlayFromSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent?.extras
        MusicMediaSession.playFromSearch(this, MusicMediaSession.voiceQuery(extras?.getString(android.app.SearchManager.QUERY), extras))
        finish()
    }
}

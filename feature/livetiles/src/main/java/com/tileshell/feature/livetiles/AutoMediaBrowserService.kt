package com.tileshell.feature.livetiles

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * What Android Auto sees of the music hub. It shares [MusicMediaSession] with
 * [LocalMusicPlaybackService] so the car controls the same player as the
 * phone. Only Android Auto, the assistant, the system and TileShell may bind
 * ([AutoCallerPolicy]); anyone else gets no root and so can neither browse
 * nor control playback.
 */
class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    override fun onCreate() {
        super.onCreate()
        sessionToken = MusicMediaSession.get(this).sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        val packagesForUid = packageManager.getPackagesForUid(clientUid)?.toList().orEmpty()
        if (!AutoCallerPolicy.isAllowed(clientPackageName, clientUid, packagesForUid, packageName)) return null
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}

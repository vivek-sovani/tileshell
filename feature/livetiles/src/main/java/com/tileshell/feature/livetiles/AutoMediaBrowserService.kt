package com.tileshell.feature.livetiles

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What Android Auto sees of the music hub. It shares [MusicMediaSession] with
 * [LocalMusicPlaybackService] so the car controls the same player as the
 * phone. Only Android Auto, the assistant, the system and TileShell may bind
 * ([AutoCallerPolicy]); anyone else gets no root and so can neither browse
 * nor control playback.
 */
class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val source by lazy { LocalLibrarySource(this) }

    override fun onCreate() {
        super.onCreate()
        sessionToken = MusicMediaSession.get(this).sessionToken
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        val packagesForUid = packageManager.getPackagesForUid(clientUid)?.toList().orEmpty()
        if (!AutoCallerPolicy.isAllowed(clientPackageName, clientUid, packagesForUid, packageName)) return null
        return BrowserRoot(AutoMediaId.Root.encode(), AutoMediaItems.rootExtras())
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val parent = AutoMediaId.parse(parentId)
        if (parent == null) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        scope.launch {
            val items = runCatching { AutoBrowseTree.children(parent, source) }.getOrDefault(emptyList())
            result.sendResult(items.map { AutoMediaItems.toMediaItem(packageName, it) }.toMutableList())
        }
    }
}

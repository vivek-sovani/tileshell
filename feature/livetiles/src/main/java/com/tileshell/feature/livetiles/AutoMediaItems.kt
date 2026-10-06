package com.tileshell.feature.livetiles

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat

/** Maps the pure [AutoItem]s to what Android Auto receives. */
internal object AutoMediaItems {
    const val GROUP_TITLE_HINT = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
    const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val STYLE_LIST = 1

    fun artAuthority(packageName: String) = "$packageName.autoart"

    fun artUri(packageName: String, art: AutoArt?): Uri? = when (art) {
        null -> null
        is AutoArt.LocalAlbum -> Uri.parse("content://${artAuthority(packageName)}/album/${art.albumId}")
        is AutoArt.Remote -> Uri.parse(art.url)
    }

    fun rootExtras() = Bundle().apply {
        putBoolean(CONTENT_STYLE_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE_HINT, STYLE_LIST)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, STYLE_LIST)
    }

    fun toMediaItem(packageName: String, item: AutoItem): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(item.id.encode())
            .setTitle(item.title)
            .setSubtitle(item.subtitle)
            .setIconUri(artUri(packageName, item.art))
            .apply { item.group?.let { setExtras(Bundle().apply { putString(GROUP_TITLE_HINT, it) }) } }
            .build()
        val flags = when (item.kind) {
            AutoItemKind.BROWSABLE -> MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            AutoItemKind.PLAYABLE -> MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            AutoItemKind.INFO -> 0
        }
        return MediaBrowserCompat.MediaItem(description, flags)
    }
}

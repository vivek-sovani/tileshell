package com.tileshell.feature.livetiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/**
 * The photos picked for the wallpaper slideshow: persistable content URIs that
 * `WallpaperSlideshowWorker` rotates the background wallpaper through on a timer.
 * Empty = nothing chosen, so the slideshow toggle has nothing to rotate. Kept in
 * its own DataStore (mirrors [PhotosStore]) so the feature is self-contained.
 */
data class WallpaperSlideshowData(val uris: List<String> = emptyList())

/**
 * Newline-separated URI codec for [WallpaperSlideshowData]. Pure / JVM-testable
 * and tolerant: blank lines are dropped, so a malformed file reads as "no photos".
 */
object WallpaperSlideshowCodec {
    fun encode(data: WallpaperSlideshowData): String = data.uris.joinToString("\n")

    fun decode(text: String): WallpaperSlideshowData =
        WallpaperSlideshowData(text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList())
}

private object WallpaperSlideshowSerializer : Serializer<WallpaperSlideshowData> {
    override val defaultValue = WallpaperSlideshowData()

    override suspend fun readFrom(input: InputStream): WallpaperSlideshowData =
        WallpaperSlideshowCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: WallpaperSlideshowData, output: OutputStream) {
        output.write(WallpaperSlideshowCodec.encode(t).encodeToByteArray())
    }
}

private val Context.wallpaperSlideshowDataStore: DataStore<WallpaperSlideshowData> by dataStore(
    fileName = "wallpaper_slideshow.pb",
    serializer = WallpaperSlideshowSerializer,
)

/** Reads/writes the wallpaper-slideshow selection. Backed by its own DataStore file. */
class WallpaperSlideshowStore(private val store: DataStore<WallpaperSlideshowData>) {

    val data: Flow<WallpaperSlideshowData> = store.data

    suspend fun read(): WallpaperSlideshowData = store.data.first()

    suspend fun setUris(uris: List<String>) {
        store.updateData { WallpaperSlideshowData(uris) }
    }

    companion object {
        fun create(context: Context): WallpaperSlideshowStore =
            WallpaperSlideshowStore(context.applicationContext.wallpaperSlideshowDataStore)
    }
}

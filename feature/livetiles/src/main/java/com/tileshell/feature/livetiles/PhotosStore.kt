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
 * The photos picked for the live photos tile (FR-2): persistable content URIs the
 * slideshow cross-fades through. Empty = nothing chosen yet, so the tile degrades
 * to static. Kept in its own DataStore (mirroring WeatherCache, DECISIONS S21) so
 * the feature is self-contained.
 */
data class PhotosData(val uris: List<String> = emptyList())

/**
 * Newline-separated URI codec for [PhotosData]. Pure / JVM-testable and tolerant:
 * blank lines are dropped, so a malformed file reads as "no photos". URIs never
 * contain a newline, so one per line round-trips exactly.
 */
object PhotosCodec {
    fun encode(data: PhotosData): String = data.uris.joinToString("\n")

    fun decode(text: String): PhotosData =
        PhotosData(text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList())
}

private object PhotosSerializer : Serializer<PhotosData> {
    override val defaultValue = PhotosData()

    override suspend fun readFrom(input: InputStream): PhotosData =
        PhotosCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: PhotosData, output: OutputStream) {
        output.write(PhotosCodec.encode(t).encodeToByteArray())
    }
}

private val Context.photosDataStore: DataStore<PhotosData> by dataStore(
    fileName = "photos_tile.pb",
    serializer = PhotosSerializer,
)

/** Reads/writes the photos-tile selection. Backed by its own DataStore file. */
class PhotosStore(private val store: DataStore<PhotosData>) {

    val data: Flow<PhotosData> = store.data

    suspend fun read(): PhotosData = store.data.first()

    suspend fun setUris(uris: List<String>) {
        store.updateData { PhotosData(uris) }
    }

    companion object {
        fun create(context: Context): PhotosStore =
            PhotosStore(context.applicationContext.photosDataStore)
    }
}

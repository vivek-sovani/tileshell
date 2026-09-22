package com.tileshell.feature.livetiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/** A radio station the user has favorited (persisted; not a search result). */
data class FavoriteStation(
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val favoritedAtMillis: Long,
)

/**
 * Favorited-station list for the music hub's "radio" tab — same shape and
 * codec as [PodcastStore]'s subscriptions, just for stations instead of
 * shows. Ordered most-recently-favorited first.
 */
object RadioFavoritesStore {

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun favorites(context: Context): Flow<List<FavoriteStation>> =
        context.applicationContext.radioFavoritesStore.data

    fun isFavorite(favorites: List<FavoriteStation>, stationId: String): Boolean =
        favorites.any { it.stationId == stationId }

    fun addFavorite(context: Context, station: FavoriteStation) {
        val app = context.applicationContext
        writeScope.launch {
            app.radioFavoritesStore.updateData { current ->
                listOf(station) + current.filterNot { it.stationId == station.stationId }
            }
        }
    }

    fun removeFavorite(context: Context, stationId: String) {
        val app = context.applicationContext
        writeScope.launch {
            app.radioFavoritesStore.updateData { current -> current.filterNot { it.stationId == stationId } }
        }
    }
}

/** Pipe-delimited codec, one line per [FavoriteStation] (tolerant of malformed
 * lines) — pure, so it round-trips in a plain JUnit test. */
object FavoriteStationCodec {

    fun encode(stations: List<FavoriteStation>): String = stations.joinToString("\n") { s ->
        "${clean(s.stationId)}|${clean(s.name)}|${clean(s.streamUrl)}|${clean(s.faviconUrl.orEmpty())}|${s.favoritedAtMillis}"
    }

    fun decode(text: String): List<FavoriteStation> = text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 5) return@mapNotNull null
            val favoritedAt = parts[4].toLongOrNull() ?: return@mapNotNull null
            FavoriteStation(
                stationId = parts[0],
                name = parts[1],
                streamUrl = parts[2],
                faviconUrl = parts[3].takeIf { it.isNotEmpty() },
                favoritedAtMillis = favoritedAt,
            )
        }
        .toList()

    private fun clean(value: String): String = value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ')
}

private object FavoriteStationSerializer : Serializer<List<FavoriteStation>> {
    override val defaultValue: List<FavoriteStation> = emptyList()

    override suspend fun readFrom(input: InputStream): List<FavoriteStation> =
        FavoriteStationCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: List<FavoriteStation>, output: OutputStream) {
        output.write(FavoriteStationCodec.encode(t).encodeToByteArray())
    }
}

private val Context.radioFavoritesStore: DataStore<List<FavoriteStation>> by dataStore(
    fileName = "radio_favorites.pb",
    serializer = FavoriteStationSerializer,
)

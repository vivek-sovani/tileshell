package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** One photo match for quick search: enough to render a thumbnail and open it. */
data class MediaMatch(val uri: Uri, val displayName: String)

/**
 * The runtime permission that gates [searchPhotos] — granular
 * `READ_MEDIA_IMAGES` on API 33+, the legacy `READ_EXTERNAL_STORAGE` below it
 * (matches the split Android itself made; querying *other* apps' photos, unlike
 * the in-app photo picker used elsewhere in personalize, needs one of these).
 */
fun mediaImagesPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Matches photos by filename against [query] via [MediaStore.Images.Media],
 * newest-first, capped at [limit]. Caller must hold [mediaImagesPermission] —
 * query failures (denied access, no provider) are swallowed and return an
 * empty list, degrading quick search's photos section to nothing rather than
 * crashing. Scoped to images only: a true system-wide "downloads/documents"
 * search would need `MANAGE_EXTERNAL_STORAGE`, a much heavier ask than this
 * launcher's other opt-in permissions (see docs/DECISIONS.md).
 */
fun searchPhotos(context: Context, query: String, limit: Int = 5): List<MediaMatch> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
    val args = arrayOf("%$q%")
    val sort = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
    val matches = mutableListOf<MediaMatch>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, sort,
        )?.use { cursor ->
            while (cursor.moveToNext() && matches.size < limit) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: continue
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                matches += MediaMatch(uri, name)
            }
        }
    }
    return matches
}

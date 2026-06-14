package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a package's launcher icon to an [ImageBitmap] off the main thread.
 * Returns null while loading or if the package can't be resolved (uninstalled /
 * not visible). The package is visible to the launcher via the LAUNCHER `<queries>`
 * entry, so this resolves for any pinned app. Reloads only when [packageName]
 * changes.
 */
@Composable
fun rememberAppIconBitmap(packageName: String, sizePx: Int = 96): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = if (packageName.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(width = sizePx, height = sizePx)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return image
}

/**
 * The posting app's launcher icon, drawn small in a tile corner so a live
 * notification tile still identifies its app (WP live tiles keep the app glyph
 * visible). Renders nothing until the icon loads / if it can't be resolved.
 */
@Composable
fun AppIconCorner(packageName: String, modifier: Modifier = Modifier) {
    val icon = rememberAppIconBitmap(packageName) ?: return
    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(18.dp),
    )
}

package com.tileshell.feature.livetiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.SquircleShape
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

private data class MaskableAppIcon(
    val bitmap: ImageBitmap,
    val unmaskedBitmap: ImageBitmap,
    val isAdaptive: Boolean,
    val monochromeBitmap: ImageBitmap?,
)

/**
 * Same masking trio as `:feature:start`'s `IconCellView.kt` (`MaskableIcon`/
 * `rememberMaskableIcon`/`unmaskedIconBitmap`/`toComposeShape`) and
 * `:feature:applist`'s `AppListIcon.kt` — duplicated here for the same reason
 * those two duplicate each other: `:feature:livetiles` depends on neither of
 * them (the dependency graph only runs the other way), and `:core:design`
 * doesn't depend on `:core:data` (where `IconShape` lives), so there's no
 * single module both can share this from without a new cross-module edge.
 * Keyed on [packageName] only (not an activity) — a live-tile corner badge
 * identifies "which app posted this," not a specific launch target, matching
 * [rememberAppIconBitmap]'s own `getApplicationIcon` call above.
 */
@Composable
private fun rememberMaskableAppIcon(packageName: String, sizePx: Int = 96): MaskableAppIcon? {
    val context = LocalContext.current
    return produceState<MaskableAppIcon?>(null, packageName, sizePx) {
        value = if (packageName.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val isAdaptive = drawable is AdaptiveIconDrawable
                    val osBitmap = drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
                    val rawBitmap = if (isAdaptive) unmaskedIconBitmap(drawable, sizePx) else osBitmap
                    MaskableAppIcon(osBitmap, rawBitmap, isAdaptive, monochromeIconBitmap(drawable, sizePx))
                }.getOrNull()
            }
        }
    }.value
}

/** See `IconCellView.kt`'s identical helper's doc comment — bypasses
 *  [AdaptiveIconDrawable]'s own OS-mask clipping by drawing its raw
 *  background/foreground layers directly. */
private fun unmaskedIconBitmap(drawable: Drawable, sizePx: Int): ImageBitmap {
    if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
        layer.setBounds(0, 0, sizePx, sizePx)
        layer.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/** See `:feature:applist`'s `AppListIcon.kt#monochromeIconBitmap` for the full
 *  rationale — flattens the Android 13+ themed-icon layer to an untinted alpha
 *  mask; the caller ([AppIconCorner]) tints it via [ColorFilter] at render
 *  time. Null below API 33, for a non-adaptive icon, or with no monochrome
 *  layer declared. */
private fun monochromeIconBitmap(drawable: Drawable, sizePx: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val mono = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    mono.setBounds(0, 0, sizePx, sizePx)
    mono.draw(canvas)
    return bitmap.asImageBitmap()
}

/** See `IconCellView.kt`'s identical mapping's doc comment for why this lives
 *  here rather than `:core:design` (which doesn't depend on `:core:data`,
 *  where [IconShape] is persisted). */
private fun IconShape.toComposeShape(): Shape? = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape()
    IconShape.ROUNDED -> RoundedCornerShape(percent = 30)
    IconShape.SQUARE -> RectangleShape
    IconShape.ORIGINAL -> null
}

/**
 * The posting app's launcher icon, drawn small in a tile corner so a live
 * notification tile still identifies its app (WP live tiles keep the app glyph
 * visible). Renders nothing until the icon loads / if it can't be resolved.
 *
 * In ICONS home style, masked to the user's chosen [iconShape] — matching the
 * SMALL 1x1 icon cell ([IconCellView]'s masking), which this corner badge
 * previously never picked up: a MEDIUM+ tile's live-face content (this badge
 * included) still renders exactly as in TILES mode, so this was the one real
 * icon left showing its native/OS shape regardless of the chosen shape. TILES
 * mode (or [IconShape.ORIGINAL]) draws the same unmasked bitmap as before —
 * no behaviour change there.
 *
 * [themedIcons] takes priority over both of those whenever the app has a
 * monochrome layer: instead of the badge, it draws the app's themed glyph
 * tinted to [LocalTileFaceColor] — the tile's own face text/icon colour
 * (white-on-accent by the WP convention every other face already follows) —
 * so the badge reads as part of the tile instead of a separate full-colour
 * icon sitting on top of it. Falls through to the normal badge whenever
 * there's no monochrome layer to show.
 */
// User-reported: 18dp read as too small for an actual app icon once a tile
// has real content to sit next to (mail/messages/music/notification data) —
// a general sizing fix, independent of themedIcons/iconShape/homeStyle;
// every branch below shares it so the badge stays the same size across modes.
private val APP_ICON_CORNER_SIZE = 24.dp

@Composable
fun AppIconCorner(
    packageName: String,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (themedIcons) {
        val loaded = rememberMaskableAppIcon(packageName)
        val mono = loaded?.monochromeBitmap
        if (mono != null) {
            Image(
                bitmap = mono,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(LocalTileFaceColor.current),
                modifier = modifier.size(APP_ICON_CORNER_SIZE),
            )
            return
        }
    }
    if (homeStyle == HomeStyle.ICONS) {
        val shape = iconShape.toComposeShape()
        if (shape != null) {
            val loaded = rememberMaskableAppIcon(packageName) ?: return
            Image(
                bitmap = if (loaded.isAdaptive) loaded.unmaskedBitmap else loaded.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier.size(APP_ICON_CORNER_SIZE).clip(shape),
            )
            return
        }
    }
    val icon = rememberAppIconBitmap(packageName) ?: return
    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(APP_ICON_CORNER_SIZE),
    )
}

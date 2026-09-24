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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.AppIconCache
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.SquircleShape
import com.tileshell.core.design.isUniformAlpha
import com.tileshell.core.design.opaqueBounds
import com.tileshell.core.design.paddedSquareCrop
import com.tileshell.core.design.synthesizeMonochromeMask
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
    // sizePx must be a key: it is used inside the block, so omitting it meant a
    // caller that changed only the requested size kept the previously decoded
    // bitmap at the old resolution (blurry when scaled up). rememberMaskableAppIcon
    // below already keys on both.
    // See :feature:start's rememberTileAppIcon (StartScreen.kt) — retries a
    // corner badge that lost the boot-time icon-resolution race instead of
    // being stuck on no icon at all until the process restarts.
    val retryEpoch by AppIconCache.retryEpoch.collectAsState()
    val image by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx, retryEpoch) {
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
 * [packageName]'s icon as an untinted white silhouette (the same monochrome
 * icon the "monochrome icons" setting uses), for tinting to the tile's face
 * colour. Null while loading or if the package can't be resolved.
 */
@Composable
fun rememberMonochromeAppIcon(packageName: String, sizePx: Int = 96): ImageBitmap? =
    rememberMaskableAppIcon(packageName, sizePx)?.monochromeBitmap

private data class MaskableAppIcon(
    val bitmap: ImageBitmap,
    val unmaskedBitmap: ImageBitmap,
    val isAdaptive: Boolean,
    val monochromeBitmap: ImageBitmap,
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
    val retryEpoch by AppIconCache.retryEpoch.collectAsState()
    return produceState<MaskableAppIcon?>(null, packageName, sizePx, retryEpoch) {
        value = if (packageName.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val isAdaptive = drawable is AdaptiveIconDrawable
                    val osBitmap = drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
                    val rawBitmap = if (isAdaptive) unmaskedIconBitmap(drawable, sizePx) else osBitmap
                    MaskableAppIcon(osBitmap, rawBitmap, isAdaptive, monochromeIconBitmap(drawable, sizePx, rawBitmap))
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
 *  rationale — prefers the app's own Android 13+ themed-icon layer (sanity-
 *  checked via [isUniformAlpha] before being trusted), else
 *  [synthesizeMonochromeMask] derives an equivalent silhouette from
 *  [rawBitmap] (the already-loaded full composite, not just the foreground
 *  layer — see that doc comment for why); the caller ([AppIconCorner]) tints
 *  the untinted result via [ColorFilter] at render time. Never null. */
private fun monochromeIconBitmap(drawable: Drawable, sizePx: Int, rawBitmap: ImageBitmap): ImageBitmap {
    val native = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (drawable as? AdaptiveIconDrawable)?.monochrome
    } else {
        null
    }
    if (native != null) {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        native.setBounds(0, 0, sizePx, sizePx)
        native.draw(canvas)
        val nativePixels = IntArray(sizePx * sizePx)
        bitmap.getPixels(nativePixels, 0, sizePx, 0, 0, sizePx, sizePx)
        if (!isUniformAlpha(nativePixels)) return cropToContent(bitmap, nativePixels, sizePx)
    }
    val pixels = IntArray(sizePx * sizePx)
    rawBitmap.asAndroidBitmap().getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    val masked = synthesizeMonochromeMask(pixels)
    val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    result.setPixels(masked, 0, sizePx, 0, 0, sizePx, sizePx)
    return cropToContent(result, masked, sizePx)
}

/** See `:feature:start`'s `IconCellView.kt#cropToContent` — applied to both the
 *  native and the synthesized monochrome source. */
private fun cropToContent(bitmap: Bitmap, pixels: IntArray, sizePx: Int): ImageBitmap {
    val bounds = opaqueBounds(pixels, sizePx, sizePx) ?: return bitmap.asImageBitmap()
    val crop = paddedSquareCrop(bounds, sizePx, sizePx)
    return Bitmap.createBitmap(bitmap, crop[0], crop[1], crop[2] - crop[0], crop[3] - crop[1]).asImageBitmap()
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
 * [themedIcons] takes priority over both of those: instead of the badge, it
 * draws the app's monochrome glyph (its own Android 13+ layer when declared,
 * else a synthesized equivalent — see [monochromeIconBitmap]) tinted to
 * [LocalTileFaceColor] — the tile's own face text/icon colour (white-on-accent
 * by the WP convention every other face already follows) — so the badge reads
 * as part of the tile instead of a separate full-colour icon sitting on top
 * of it. Falls through to the normal badge only if the icon itself fails to
 * resolve at all.
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

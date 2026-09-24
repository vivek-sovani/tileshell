package com.tileshell.feature.applist

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.AppIconCache
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.shortcutIconDrawable
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.data.settings.MonochromeIconTint
import com.tileshell.core.design.Glass
import com.tileshell.core.design.LocalAccent
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.core.design.SquircleShape
import com.tileshell.core.design.isLightBackground
import com.tileshell.core.design.isUniformAlpha
import com.tileshell.core.design.synthesizeMonochromeMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-list counterpart to `:feature:start`'s `IconCellView.kt` masking logic —
 * applies the same `IconShape` (circle/squircle/rounded/original) an ICONS
 * home style Start screen icon gets, so the app drawer matches. Deliberately
 * duplicated rather than shared: `:feature:applist` can't depend on
 * `:feature:start` (the dependency graph runs the other way — `:feature:start`
 * already depends on `:feature:applist`), and giving `:core:design` a
 * dependency on `:core:data` just to host this would reverse an earlier
 * deliberate decision to keep the two modules independent (see
 * docs/DECISIONS.md, "Icon shape masking"). In TILES home style this renders
 * a plain unmasked icon, same as before this feature existed.
 *
 * [plateColor] is only meaningful when [isAdaptive] is false (the "legacy
 * icon on a tinted plate" path — see [MaskedAppIcon]); computed once here,
 * off the main thread, rather than per-composition, since a per-pixel scan
 * over hundreds of scrolling app-list rows on the UI thread is real jank —
 * unlike Start's own `IconCellView`, which only ever has a couple dozen
 * on-screen icons at once, the app list can hold hundreds.
 */
/**
 * [bitmap] is exactly what the OS itself renders for this icon — used
 * whenever `HomeStyle.TILES` suppresses masking, or [IconShape.ORIGINAL] is
 * selected, since "original" means "however the device actually shows it."
 * [unmaskedBitmap] only differs for an adaptive icon (see
 * [unmaskedIconBitmap]): the raw background/foreground layers with no OS
 * mask applied, used only when our own [IconShape] clip needs clean content
 * to work with. See `:feature:start`'s `IconCellView.kt#MaskableIcon` for the
 * full rationale.
 */
internal data class MaskableAppIcon(
    val bitmap: ImageBitmap,
    val unmaskedBitmap: ImageBitmap,
    val isAdaptive: Boolean,
    val plateColor: Color?,
    val monochromeBitmap: ImageBitmap,
)

@Composable
internal fun rememberMaskableAppIcon(packageName: String, activityName: String): MaskableAppIcon? {
    val context = LocalContext.current
    // See :feature:start's rememberTileAppIcon (StartScreen.kt) — retries a
    // row that lost the boot-time icon-resolution race instead of being
    // stuck on the fallback glyph until the process restarts.
    val retryEpoch by AppIconCache.retryEpoch.collectAsState()
    return produceState<MaskableAppIcon?>(null, packageName, activityName, retryEpoch) {
        value = withContext(Dispatchers.IO) {
            fun load(drawable: Drawable): MaskableAppIcon {
                val isAdaptive = drawable is AdaptiveIconDrawable
                // Cached process-wide: this list recycles constantly, and without
                // a cache every scroll re-ran getActivityIcon + toBitmap (+ a
                // getShortcuts IPC for shortcut rows). See AppIconCache.
                val cacheKey = AppIconCache.iconCacheKey(packageName, activityName, 96)
                val osBitmap = (
                    AppIconCache.getOrPut(cacheKey) { drawable.toBitmap(width = 96, height = 96) }
                        ?: drawable.toBitmap(width = 96, height = 96)
                    ).asImageBitmap()
                val rawBitmap = if (isAdaptive) unmaskedIconBitmap(drawable) else osBitmap
                return MaskableAppIcon(
                    osBitmap,
                    rawBitmap,
                    isAdaptive,
                    if (isAdaptive) null else dominantColor(osBitmap),
                    monochromeIconBitmap(drawable, rawBitmap),
                )
            }
            // An app shortcut publishes its own icon and has no resolvable
            // ComponentName (see shortcutIconDrawable) — without this it fell
            // through to the parent app's icon below, so every shortcut looked
            // identical to the app it came from.
            shortcutIconDrawable(context, packageName, activityName)?.let { return@withContext load(it) }
            runCatching {
                load(context.packageManager.getActivityIcon(ComponentName(packageName, activityName)))
            }.recoverCatching {
                // A dead seasonal activity-alias can throw on getActivityIcon even
                // though the app itself is installed fine — fall back to its real icon.
                load(context.packageManager.getApplicationIcon(packageName))
            }.getOrNull()
        }
    }.value
}

/**
 * A flattened 96×96 bitmap of [drawable], bypassing [AdaptiveIconDrawable]'s
 * own `draw()` (which always clips to the OS's device-wide icon mask — a
 * circle on stock AOSP/Pixel — before our own [IconShape] ever gets applied).
 * See `:feature:start`'s `IconCellView.kt#unmaskedIconBitmap` for the full
 * on-device-confirmed rationale; duplicated here for the same reason the rest
 * of this file's masking logic is duplicated rather than shared.
 */
private fun unmaskedIconBitmap(drawable: Drawable): ImageBitmap {
    if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(width = 96, height = 96).asImageBitmap()
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
        layer.setBounds(0, 0, 96, 96)
        layer.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/**
 * An untinted alpha-mask glyph for [drawable] — the caller applies the
 * current accent via a Compose [ColorFilter] at render time (see
 * [MaskedAppIcon]), so retinting on an accent change never needs a reload.
 * Prefers the app's own Android 13+ "themed icon" layer
 * ([AdaptiveIconDrawable.monochrome], API 33+) when declared; otherwise
 * [synthesizeMonochromeMask] derives an equivalent silhouette from
 * [rawBitmap] (the already-loaded full composite) — see `:feature:start`'s
 * `IconCellView.kt#monochromeIconBitmap` for the full rationale, including
 * why this uses the full composite rather than isolating the foreground
 * layer alone, and why the native layer is sanity-checked
 * ([isUniformAlpha]) rather than trusted blindly (duplicated here for the
 * same reason the rest of this file's masking logic is duplicated). Never
 * null.
 */
private fun monochromeIconBitmap(drawable: Drawable, rawBitmap: ImageBitmap): ImageBitmap {
    val native = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (drawable as? AdaptiveIconDrawable)?.monochrome
    } else {
        null
    }
    if (native != null) {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        native.setBounds(0, 0, 96, 96)
        native.draw(canvas)
        val nativePixels = IntArray(96 * 96)
        bitmap.getPixels(nativePixels, 0, 96, 0, 0, 96, 96)
        if (!isUniformAlpha(nativePixels)) return bitmap.asImageBitmap()
    }
    val pixels = IntArray(96 * 96)
    rawBitmap.asAndroidBitmap().getPixels(pixels, 0, 96, 0, 0, 96, 96)
    val masked = synthesizeMonochromeMask(pixels)
    val result = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    result.setPixels(masked, 0, 96, 0, 0, 96, 96)
    // Deliberately *not* cropped to content, unlike Start's plate-less tile
    // faces (IconCellView.kt#cropToContent): every App List monochrome glyph
    // sits on its own accent plate (MaskedAppIcon), and the glyph's built-in
    // transparent margin is that plate's padding — cropping it made the glyph
    // fill the plate edge-to-edge (user-reported "app list icons have become
    // bigger").
    return result.asImageBitmap()
}

private fun IconShape.toShape(): Shape? = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape()
    IconShape.ROUNDED -> RoundedCornerShape(percent = 30)
    IconShape.SQUARE -> RectangleShape
    IconShape.ORIGINAL -> null
}

/** Saturation-weighted average colour, falling back to a plain average — same
 *  algorithm as `:feature:start`'s `dominantIconColor`. */
private fun dominantColor(bitmap: ImageBitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return null
    val px = IntArray(w * h)
    runCatching { bitmap.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h) }
        .getOrElse { return null }
    var wr = 0.0; var wg = 0.0; var wb = 0.0; var wSum = 0.0
    var ar = 0.0; var ag = 0.0; var ab = 0.0; var aN = 0
    for (p in px) {
        if ((p ushr 24 and 0xff) < 128) continue
        val r = p ushr 16 and 0xff
        val g = p ushr 8 and 0xff
        val b = p and 0xff
        ar += r; ag += g; ab += b; aN++
        val mx = maxOf(r, g, b)
        val sat = if (mx == 0) 0f else (mx - minOf(r, g, b)).toFloat() / mx
        if (sat > 0.25f && mx > 40) {
            wr += r * sat; wg += g * sat; wb += b * sat; wSum += sat
        }
    }
    return when {
        wSum > 0 -> Color((wr / wSum).toInt(), (wg / wSum).toInt(), (wb / wSum).toInt())
        aN > 0 -> Color((ar / aN).toInt(), (ag / aN).toInt(), (ab / aN).toInt())
        else -> null
    }
}

/**
 * An app-list row's already-loaded real launcher icon ([loaded], see
 * [rememberMaskableAppIcon]), masked to [shape] when [homeStyle] is
 * [HomeStyle.ICONS] (matches Start's own icon rendering); a plain unmasked
 * icon in [HomeStyle.TILES], same as before this feature existed.
 *
 * [themedIcons] takes priority over both of those: the icon renders as a
 * monochrome glyph (the app's own Android 13+ layer when it declared one,
 * else a synthesized equivalent — see [monochromeIconBitmap]) tinted to
 * either [LocalAccent] or a fixed neutral (see [monochromeIconTint]), on a
 * matching plate — independent of [homeStyle], since "themed" is a colour
 * choice a user can want in either style.
 */
@Composable
internal fun MaskedAppIcon(
    loaded: MaskableAppIcon,
    homeStyle: HomeStyle,
    shape: IconShape,
    size: Dp,
    themedIcons: Boolean = false,
    monochromeIconTint: MonochromeIconTint = MonochromeIconTint.ACCENT,
    modifier: Modifier = Modifier,
) {
    if (themedIcons) {
        val mono = loaded.monochromeBitmap
        val accent = when (monochromeIconTint) {
            MonochromeIconTint.ACCENT -> LocalAccent.current
            MonochromeIconTint.NEUTRAL -> LocalColorTokens.current.fg
        }
        // A monochrome glyph is a transparent silhouette, not a real icon's own
        // opaque bitmap — dropping the plate for ORIGINAL (tried along the way)
        // made it blend straight into whatever's behind it (user-reported:
        // "merged into background, no shape as such"). It needs *some* plate
        // regardless of shape. See IconCellView.kt's matching branch: the
        // plate's own shape for ORIGINAL went circle → square → rounded on
        // direct user request each round — rounded is the final, deliberate
        // choice.
        val plateShape = shape.toShape() ?: RoundedCornerShape(percent = 30)
        Box(
            modifier = modifier.size(size).clip(plateShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = mono,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Glass.faceTextColor(isLightBackground(accent))),
                // Full size, not shrunk further: a monochrome layer is itself
                // an AdaptiveIconDrawable layer, so it already carries the
                // same built-in safe-zone inset as the real full-colour icon
                // — matching the isAdaptive branch below, which also draws at
                // full [size] with no extra scale-down.
                modifier = Modifier.size(size),
            )
        }
        return
    }
    val composeShape = if (homeStyle == HomeStyle.ICONS) shape.toShape() else null
    when {
        composeShape == null -> {
            Image(
                bitmap = loaded.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier.size(size),
            )
        }
        loaded.isAdaptive -> {
            Image(
                bitmap = loaded.unmaskedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier.size(size).clip(composeShape),
            )
        }
        else -> {
            val plateColor = loaded.plateColor ?: Color.DarkGray
            Box(
                modifier = modifier.size(size).clip(composeShape).background(plateColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = loaded.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size * 0.65f),
                )
            }
        }
    }
}

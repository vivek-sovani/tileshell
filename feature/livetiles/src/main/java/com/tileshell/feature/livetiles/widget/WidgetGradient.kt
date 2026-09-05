package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap

/**
 * A small diagonal gradient bitmap for a widget's background — the same
 * "+15% light top-left, -30% dark bottom-right" recipe as the in-app gradient
 * tile fill ([com.tileshell.core.design.tileGradientBrush]), just rendered to
 * a plain [Bitmap] instead of a Compose `Brush`: `RemoteViews` has no way to
 * reference a Brush or a dynamically-parameterised drawable — pushing a raw
 * bitmap into an `ImageView` is the one RemoteViews-safe way to get a custom-
 * computed gradient onto a widget. Deliberately tiny (default 32px) and
 * upscaled by the `ImageView`'s own `fitXY` scaleType — a plain 2-colour
 * gradient has no fine detail to lose stretching up to a full widget's size,
 * and a small bitmap keeps the `RemoteViews` payload cheap to push.
 *
 * **Memoized.** Every one of the 14 widget kinds calls this on every single
 * push, but the result depends only on `(accent, size)` — an accent changes
 * solely by explicit user action, so recomputing the gradient per refresh was
 * pure waste (an allocation + `Canvas` fill per widget instance per tick).
 * The cache is bounded by construction: `size` takes one or two values and
 * `accent` comes from the fixed 14-colour palette plus any wallpaper-derived
 * accent, so it cannot grow without bound; [CACHE_LIMIT] is only a backstop.
 * The returned bitmap is treated as read-only by every caller (it goes
 * straight into `RemoteViews.setImageViewBitmap`), so sharing one instance
 * across callers and threads is safe.
 */
private val gradientCache = ConcurrentHashMap<Long, Bitmap>()

private const val CACHE_LIMIT = 32

fun accentGradientBitmap(accent: Int, size: Int = 32): Bitmap {
    val key = (accent.toLong() shl 32) or (size.toLong() and 0xffffffffL)
    gradientCache[key]?.let { return it }
    val bitmap = buildAccentGradientBitmap(accent, size)
    // Not atomic with the get above, but a benign race: two threads may both
    // build one and the later put wins — both are identical by construction.
    if (gradientCache.size >= CACHE_LIMIT) gradientCache.clear()
    gradientCache[key] = bitmap
    return bitmap
}

private fun buildAccentGradientBitmap(accent: Int, size: Int): Bitmap {
    val accentColor = Color(accent)
    val light = lerp(accentColor, Color.White, 0.15f).toArgb()
    val dark = lerp(accentColor, Color.Black, 0.30f).toArgb()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawRect(
        0f, 0f, size.toFloat(), size.toFloat(),
        Paint().apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                light, dark,
                Shader.TileMode.CLAMP,
            )
        },
    )
    return bitmap
}

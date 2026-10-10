package com.tileshell.feature.livetiles

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * How far the camera is from the screen, as a multiple of the screen height.
 * Windows Phone's flip is mild (the tile is small next to the screen), but not
 * flat: every tile is seen from the same eye, so ones near the top or sides
 * lean toward the middle of the screen as they turn.
 */
internal const val FLIP_CAMERA_DISTANCE_FACTOR = 4f

/**
 * Owns the flip of one whole tile (plate, outline and face together), so a
 * [FlipTile] inside it can drive the turn while [tileFlip] — applied to the
 * tile's outer box — draws it. [rotation] runs 0° (front) → 180° (back).
 */
@Stable
class TileFlipHost {
    val rotation = Animatable(0f)
    internal var coordinates: LayoutCoordinates? = null
}

/** Set by `TileView` around a tile's content; a [FlipTile] without one turns its own box. */
val LocalTileFlipHost = staticCompositionLocalOf<TileFlipHost?> { null }

/**
 * Draws [host]'s flip: the whole box turns about its own horizontal centre line,
 * seen by a camera at the middle of the screen (a global perspective, not one
 * per tile). Draw-only — layout, hit-testing and semantics are untouched, and
 * nothing is applied while the tile is at rest.
 *
 * Past 90° the back face is showing, so the turn continues as `rotation − 180°`
 * (the back face, upright, coming to rest at 0°).
 */
fun Modifier.tileFlip(host: TileFlipHost): Modifier = this
    .onGloballyPositioned { host.coordinates = it }
    .drawWithContent {
        val rotation = host.rotation.value
        val coordinates = host.coordinates
        if (rotation <= 0f || rotation >= 180f || coordinates == null || !coordinates.isAttached) {
            drawContent()
            return@drawWithContent
        }
        val root = coordinates.findRootCoordinates()
        val eye = coordinates.localPositionOf(
            root,
            Offset(root.size.width / 2f, root.size.height / 2f),
        )
        val matrix = flipMatrix(
            degrees = if (rotation > 90f) rotation - 180f else rotation,
            width = size.width,
            height = size.height,
            eyeX = eye.x,
            eyeY = eye.y,
            cameraDistance = root.size.height * FLIP_CAMERA_DISTANCE_FACTOR,
        )
        withTransform({ transform(matrix) }) { this@drawWithContent.drawContent() }
    }

/**
 * The transform, in the tile's own coordinates, for a tile of [width]×[height]
 * turned [degrees] about its horizontal centre line and seen by an eye at
 * ([eyeX], [eyeY]) — the screen's centre — [cameraDistance] in front of the
 * screen. Pure, so it is unit-tested.
 */
internal fun flipMatrix(
    degrees: Float,
    width: Float,
    height: Float,
    eyeX: Float,
    eyeY: Float,
    cameraDistance: Float,
): Matrix {
    val centreX = width / 2f
    val centreY = height / 2f
    val radians = Math.toRadians(degrees.toDouble())
    val sin = kotlin.math.sin(radians).toFloat()
    val cos = kotlin.math.cos(radians).toFloat()
    // Column-major 4x4s, multiplied explicitly: Compose's Matrix.translate and
    // rotateX compose in different orders, which is easy to get wrong.
    fun translation(x: Float, y: Float) = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        x, y, 0f, 1f,
    )
    val rotation = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, cos, sin, 0f,
        0f, -sin, cos, 0f,
        0f, 0f, 0f, 1f,
    )
    // w = 1 − z / d: points toward the viewer (z > 0) are drawn larger.
    val perspective = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, -1f / cameraDistance,
        0f, 0f, 0f, 1f,
    )
    var m = translation(eyeX, eyeY)
    m = times(m, perspective)
    m = times(m, translation(centreX - eyeX, centreY - eyeY))
    m = times(m, rotation)
    m = times(m, translation(-centreX, -centreY))
    return Matrix(m)
}

/** a · b for column-major 4x4s. */
private fun times(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(16)
    for (col in 0..3) {
        for (row in 0..3) {
            var sum = 0f
            for (k in 0..3) sum += a[k * 4 + row] * b[col * 4 + k]
            out[col * 4 + row] = sum
        }
    }
    return out
}

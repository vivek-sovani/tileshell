package com.tileshell

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.tileshell.core.design.Hinge
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug builds only (MainActivity `debug.hinge=v|h|fold|off`): a made-up hinge
 * for checking dual-screen layouts on a phone emulator. `v` is a Duo-style
 * vertical hinge that hides a strip in the middle, `h` the same horizontal,
 * `fold` a vertical half-open fold that hides nothing, `off` removes it.
 */
object DebugHinge {
    val request = MutableStateFlow<String?>(null)

    fun hingeFor(kind: String?, widthPx: Int, heightPx: Int): Hinge? = when (kind) {
        "v" -> Hinge(widthPx / 2f - 42f, 0f, widthPx / 2f + 42f, heightPx.toFloat(), vertical = true, occluding = true)
        "h" -> Hinge(0f, heightPx / 2f - 42f, widthPx.toFloat(), heightPx / 2f + 42f, vertical = false, occluding = true)
        "fold" -> Hinge(widthPx / 2f, 0f, widthPx / 2f, heightPx.toFloat(), vertical = true, occluding = false)
        else -> null
    }
}

/**
 * The hinge that splits [activity]'s window into two screens, or null while the
 * window is whole (a phone, a flat unfolded Galaxy Z Fold, a tablet). Only a
 * hinge that separates the window counts: one that hides part of the display
 * (Surface Duo) or a fold that is half open (a Fold in book or tabletop
 * posture), which is when the two halves behave as two screens.
 */
@Composable
fun rememberHinge(activity: Activity): Hinge? {
    val hinge by produceState<Hinge?>(initialValue = null, activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
                ?.let { feature ->
                    val b = feature.bounds
                    Hinge(
                        left = b.left.toFloat(),
                        top = b.top.toFloat(),
                        right = b.right.toFloat(),
                        bottom = b.bottom.toFloat(),
                        vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL,
                        occluding = feature.occlusionType == FoldingFeature.OcclusionType.FULL,
                    )
                }
        }
    }
    val debug by DebugHinge.request.collectAsState()
    if (debug != null) {
        val metrics = activity.resources.displayMetrics
        return DebugHinge.hingeFor(debug, metrics.widthPixels, metrics.heightPixels)
    }
    return hinge
}

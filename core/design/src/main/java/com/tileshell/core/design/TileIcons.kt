package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The original monoline icon family ported from design/.../launcher/icons.js —
 * thin geometric outline glyphs in the Windows-Phone visual language (not
 * Microsoft assets). Every glyph is a 24×24 stroke-only path, stroke width 1.6,
 * round caps/joins, matching the prototype's `.ico` styling.
 *
 * Icons are stroke-coloured black here and meant to be drawn with the Compose
 * `Icon` composable, which tints them to the current content colour (white on
 * accent tiles, foreground elsewhere).
 *
 * SVG primitives from the prototype (circle / rect / line / ellipse) are
 * converted to equivalent path data so the entire set flows through a single
 * `addPathNodes` builder.
 */
object TileIcons {

    /** All icons keyed by the prototype `ic` name. */
    val byName: Map<String, ImageVector> by lazy { build() }

    /** Icon for a name, falling back to the generic "app" glyph. */
    operator fun get(name: String?): ImageVector =
        byName[name] ?: byName.getValue("app")

    // ---- primitive → path-data helpers ----------------------------------

    private fun f(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double): String =
        "M${f(x1)} ${f(y1)}L${f(x2)} ${f(y2)}"

    private fun circle(cx: Double, cy: Double, r: Double): String =
        "M${f(cx - r)} ${f(cy)}" +
            "a${f(r)} ${f(r)} 0 1 0 ${f(2 * r)} 0" +
            "a${f(r)} ${f(r)} 0 1 0 ${f(-2 * r)} 0"

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): String =
        "M${f(cx - rx)} ${f(cy)}" +
            "a${f(rx)} ${f(ry)} 0 1 0 ${f(2 * rx)} 0" +
            "a${f(rx)} ${f(ry)} 0 1 0 ${f(-2 * rx)} 0"

    private fun rect(x: Double, y: Double, w: Double, h: Double, r: Double = 0.0): String =
        if (r <= 0.0) {
            "M${f(x)} ${f(y)}h${f(w)}v${f(h)}h${f(-w)}z"
        } else {
            "M${f(x + r)} ${f(y)}" +
                "h${f(w - 2 * r)}" +
                "a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(r)}" +
                "v${f(h - 2 * r)}" +
                "a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(r)}" +
                "h${f(-(w - 2 * r))}" +
                "a${f(r)} ${f(r)} 0 0 1 ${f(-r)} ${f(-r)}" +
                "v${f(-(h - 2 * r))}" +
                "a${f(r)} ${f(r)} 0 0 1 ${f(r)} ${f(-r)}z"
        }

    private val strokeColor = SolidColor(Color(0xFF101014))

    /** Build an icon from one or more sub-paths, each optionally alpha-scaled. */
    private fun vector(name: String, vararg paths: Pair<String, Float>): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for ((d, alpha) in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = strokeColor,
                strokeAlpha = alpha,
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private fun p(d: String): Pair<String, Float> = d to 1f

    private fun build(): Map<String, ImageVector> = buildMap {
        put("phone", vector("phone", p("M21 16.5v2.6a1.9 1.9 0 0 1-2.1 1.9 18.8 18.8 0 0 1-8.2-2.9 18.5 18.5 0 0 1-5.7-5.7A18.8 18.8 0 0 1 2.1 4.1 1.9 1.9 0 0 1 4 2h2.6a1.9 1.9 0 0 1 1.9 1.6c.12.9.34 1.8.66 2.66a1.9 1.9 0 0 1-.43 2L7.5 9.6a15 15 0 0 0 5.7 5.7l1.36-1.22a1.9 1.9 0 0 1 2-.43c.86.32 1.76.54 2.67.66A1.9 1.9 0 0 1 21 16.5z")))

        put("camera", vector("camera",
            p(rect(3.0, 6.5, 18.0, 13.5, 1.0)),
            p("M8.4 6.5l1.4-2.5h4.4l1.4 2.5"),
            p(circle(12.0, 13.2, 3.4)),
            p(circle(6.0, 9.6, 0.6)),
        ))

        put("maps", vector("maps",
            p("M12 21s7-6.5 7-11a7 7 0 1 0-14 0c0 4.5 7 11 7 11z"),
            p(circle(12.0, 10.0, 2.5)),
        ))

        put("store", vector("store",
            p("M5 8h14l-1 12H6L5 8z"),
            p("M9 8a3 3 0 0 1 6 0"),
        ))

        put("settings", vector("settings",
            p(circle(12.0, 12.0, 3.2)),
            p("M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"),
        ))

        put("files", vector("files", p("M3 7a1 1 0 0 1 1-1h5l2 2h8a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7z")))

        put("web", vector("web",
            p(circle(12.0, 12.0, 9.0)),
            p(ellipse(12.0, 12.0, 4.0, 9.0)),
            p(line(3.0, 12.0, 21.0, 12.0)),
        ))

        put("note", vector("note",
            p(rect(5.0, 3.0, 14.0, 18.0, 1.5)),
            p(line(8.0, 8.0, 16.0, 8.0)),
            p(line(8.0, 12.0, 16.0, 12.0)),
            p(line(8.0, 16.0, 13.0, 16.0)),
        ))

        put("bank", vector("bank",
            p("M4 9l8-5 8 5"),
            p(line(4.0, 9.0, 20.0, 9.0)),
            p(line(6.0, 9.0, 6.0, 18.0)),
            p(line(10.0, 9.0, 10.0, 18.0)),
            p(line(14.0, 9.0, 14.0, 18.0)),
            p(line(18.0, 9.0, 18.0, 18.0)),
            p(line(4.0, 18.0, 20.0, 18.0)),
        ))

        put("fitness", vector("fitness", p("M3 12h4l2-5 3 10 2-5h7")))

        put("pay", vector("pay",
            p(rect(3.0, 6.0, 18.0, 12.0, 1.5)),
            p(line(3.0, 10.0, 21.0, 10.0)),
        ))

        put("cast", vector("cast",
            p(rect(4.0, 5.0, 16.0, 12.0, 1.5)),
            p("M4 19a3 3 0 0 1 3 3M4 16a6 6 0 0 1 6 6"),
        ))

        put("mail", vector("mail",
            p(rect(3.0, 5.0, 18.0, 14.0, 1.5)),
            p("M3 7l9 6 9-6"),
        ))

        put("messages", vector("messages", p("M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-4 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z")))

        put("music", vector("music",
            p("M9 18V6l10-2v12"),
            p(circle(6.5, 18.0, 2.5)),
            p(circle(16.5, 16.0, 2.5)),
        ))

        put("photos", vector("photos",
            p(rect(3.0, 5.0, 18.0, 14.0, 1.5)),
            p(circle(8.0, 10.0, 1.6)),
            p("M3 17l5-4 4 3 4-4 5 5"),
        ))

        put("people", vector("people",
            p(circle(9.0, 8.0, 3.0)),
            p("M3 20a6 6 0 0 1 12 0"),
            p("M16 6a3 3 0 0 1 0 6M15 14a6 6 0 0 1 6 6"),
        ))

        put("clock", vector("clock",
            p(circle(12.0, 12.0, 9.0)),
            p("M12 7v5l3 2"),
        ))

        put("weather", vector("weather",
            p(circle(8.0, 9.0, 3.2)),
            p("M6.5 18h10.5a3.2 3.2 0 0 0 0-6.4 4.8 4.8 0 0 0-9.2-1"),
        ))

        put("calendar", vector("calendar",
            p(rect(3.0, 5.0, 18.0, 16.0, 1.5)),
            p(line(3.0, 9.0, 21.0, 9.0)),
            p(line(8.0, 3.0, 8.0, 6.0)),
            p(line(16.0, 3.0, 16.0, 6.0)),
        ))

        put("battery", vector("battery",
            p(rect(3.0, 8.0, 16.0, 9.0, 1.0)),
            p(line(21.0, 11.0, 21.0, 14.0)),
        ))

        put("search", vector("search",
            p(circle(11.0, 11.0, 6.0)),
            p(line(15.5, 15.5, 20.0, 20.0)),
        ))

        put("pin", vector("pin",
            p(circle(12.0, 9.0, 4.0)),
            p(line(12.0, 13.0, 12.0, 21.0)),
        ))

        put("unpin", vector("unpin",
            p(circle(12.0, 9.0, 4.0)),
            p(line(12.0, 13.0, 12.0, 21.0)),
            p(line(4.0, 4.0, 20.0, 20.0)),
        ))

        put("close", vector("close",
            p(line(6.0, 6.0, 18.0, 18.0)),
            p(line(18.0, 6.0, 6.0, 18.0)),
        ))

        put("resize", vector("resize",
            p("M4 10V4h6M20 14v6h-6"),
            p(line(4.0, 4.0, 10.0, 10.0)),
            p(line(20.0, 20.0, 14.0, 14.0)),
        ))

        put("check", vector("check", p("M5 12l5 5 9-11")))

        put("chevron", vector("chevron", p("M9 6l6 6-6 6")))

        put("back", vector("back", p("M15 6l-6 6 6 6")))

        put("plus", vector("plus",
            p(line(12.0, 5.0, 12.0, 19.0)),
            p(line(5.0, 12.0, 19.0, 12.0)),
        ))

        put("palette", vector("palette",
            p("M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2 0-1.5 1-2 2-2h2a3 3 0 0 0 3-3c0-5-4-9-9-9z"),
            p(circle(7.5, 11.0, 1.0)),
            p(circle(11.0, 7.5, 1.0)),
            p(circle(15.0, 8.5, 1.0)),
        ))

        put("image", vector("image",
            p(rect(3.0, 5.0, 18.0, 14.0, 1.5)),
            p(circle(8.0, 10.0, 1.6)),
            p("M3 17l5-4 4 3 4-4 5 5"),
        ))

        put("contacts", vector("contacts",
            p(circle(12.0, 8.0, 3.5)),
            p("M5 20a7 7 0 0 1 14 0"),
        ))

        put("alarm", vector("alarm",
            p(circle(12.0, 13.0, 7.0)),
            p("M12 10v3l2 2"),
            p("M5 4L2 7M19 4l3 3"),
        ))

        put("calc", vector("calc",
            p(rect(5.0, 3.0, 14.0, 18.0, 1.5)),
            p(line(5.0, 9.0, 19.0, 9.0)),
            p(line(9.0, 13.0, 9.0, 13.0)),
            p(line(13.0, 13.0, 13.0, 13.0)),
            p(line(9.0, 17.0, 9.0, 17.0)),
            p(line(13.0, 17.0, 13.0, 17.0)),
        ))

        put("game", vector("game",
            p(rect(3.0, 8.0, 18.0, 9.0, 4.5)),
            p(line(8.0, 11.0, 8.0, 14.0)),
            p(line(6.5, 12.5, 9.5, 12.5)),
            p(circle(16.0, 12.0, 1.0)),
            p(circle(18.0, 14.0, 1.0)),
        ))

        put("video", vector("video",
            p(rect(3.0, 6.0, 13.0, 12.0, 1.5)),
            p("M16 10l5-3v10l-5-3z"),
        ))

        put("podcast", vector("podcast",
            p(circle(12.0, 9.0, 4.0)),
            p("M8 20a4 4 0 0 1 8 0"),
            p(line(12.0, 13.0, 12.0, 16.0)),
        ))

        put("health", vector("health", p("M12 20s-7-4.5-7-9.5A3.5 3.5 0 0 1 12 7a3.5 3.5 0 0 1 7 3.5C19 15.5 12 20 12 20z")))

        put("wallet", vector("wallet",
            p(rect(3.0, 6.0, 18.0, 13.0, 2.0)),
            p("M16 12h3"),
            p("M3 9h13a2 2 0 0 1 2 2"),
        ))

        put("cloud", vector("cloud", p("M6.5 18h10.5a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.7-1.2A3.5 3.5 0 0 0 6.5 18z")))

        put("doc", vector("doc",
            p("M6 3h8l4 4v14H6z"),
            p("M14 3v4h4"),
        ))

        put("mic", vector("mic",
            p(rect(9.0, 3.0, 6.0, 11.0, 3.0)),
            p("M6 11a6 6 0 0 0 12 0"),
            p(line(12.0, 17.0, 12.0, 21.0)),
        ))

        put("app", vector("app",
            p(rect(4.0, 4.0, 7.0, 7.0)),
            p(rect(13.0, 4.0, 7.0, 7.0)),
            p(rect(4.0, 13.0, 7.0, 7.0)),
            p(rect(13.0, 13.0, 7.0, 7.0)),
        ))

        put("home", vector("home",
            p("M3 11l9-8 9 8"),
            p("M6 10v10h12V10"),
        ))

        put("recents", vector("recents", p(rect(5.0, 5.0, 14.0, 14.0, 2.0))))

        put("folder", vector("folder", p("M3 7a1 1 0 0 1 1-1h5l2 2h8a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7z")))

        put("blur", vector("blur",
            circle(12.0, 12.0, 3.0) to 1f,
            circle(12.0, 12.0, 7.0) to 0.5f,
        ))
    }
}

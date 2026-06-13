package com.tileshell.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The complete set of screen colour tokens, ported verbatim from the prototype
 * theme blocks in design/.../launcher/styles.css (`#screen` dark and
 * `#screen.light`). Alpha values are the CSS rgba fractions converted to 8-bit.
 */
@Immutable
interface ColorTokens {
    /** Default accent (`--accent`), overridable per personalization. */
    val accent: Color

    /** Screen background (`--bg`). */
    val bg: Color

    /** Foreground / primary text (`--fg`). */
    val fg: Color

    /** Dimmed foreground (`--fg-dim`). */
    val fgDim: Color

    /** Empty-cell / drop-ghost fill (`--tile-empty`). */
    val tileEmpty: Color

    /** Hairline outline on tiles / ghosts (`--tile-line`). */
    val tileLine: Color

    /** Static transparent-tile fill (`--glass`); dynamic value via [Glass]. */
    val glass: Color

    /** Glass tile inner hairline (`--glass-line`). */
    val glassLine: Color

    /** Sheet / edit-bar surface (`--sheet`). */
    val sheet: Color

    /** Sheet separators (`--sheet-line`). */
    val sheetLine: Color

    /** Chip / search field fill (`--chip`). */
    val chip: Color
}

/** Dark theme tokens (`#screen`). */
object DarkColorTokens : ColorTokens {
    override val accent = Color(0xFF2B78E4)
    override val bg = Color(0xFF0A0A0D)
    override val fg = Color(0xFFF6F6F8)
    override val fgDim = Color(0x9EF6F6F8)      // rgba(246,246,248,.62)
    override val tileEmpty = Color(0x0DFFFFFF)  // rgba(255,255,255,.05)
    override val tileLine = Color(0x1AFFFFFF)   // rgba(255,255,255,.10)
    override val glass = Color(0x6B14141A)      // rgba(20,20,26,.42)
    override val glassLine = Color(0x29FFFFFF)  // rgba(255,255,255,.16)
    override val sheet = Color(0xFF16161C)
    override val sheetLine = Color(0x1AFFFFFF)  // rgba(255,255,255,.10)
    override val chip = Color(0x14FFFFFF)       // rgba(255,255,255,.08)
}

/** Light theme tokens (`#screen.light`). */
object LightColorTokens : ColorTokens {
    override val accent = Color(0xFF2B78E4)
    override val bg = Color(0xFFECE9E4)
    override val fg = Color(0xFF14141A)
    override val fgDim = Color(0x9914141A)      // rgba(20,20,26,.6)
    override val tileEmpty = Color(0x0D000000)  // rgba(0,0,0,.05)
    override val tileLine = Color(0x1F000000)   // rgba(0,0,0,.12)
    override val glass = Color(0x75FAFAFC)      // rgba(250,250,252,.46)
    override val glassLine = Color(0x24000000)  // rgba(0,0,0,.14)
    override val sheet = Color(0xFFF7F5F1)
    override val sheetLine = Color(0x1A000000)  // rgba(0,0,0,.10)
    override val chip = Color(0x0F000000)       // rgba(0,0,0,.06)
}

/** Tokens for the active theme. */
fun colorTokens(dark: Boolean): ColorTokens = if (dark) DarkColorTokens else LightColorTokens

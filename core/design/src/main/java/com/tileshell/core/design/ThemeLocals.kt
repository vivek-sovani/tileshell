package com.tileshell.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The active screen [ColorTokens] for the chosen theme (FR-7), provided at the
 * Start root from the persisted setting so chrome surfaces — sheet, edit bar,
 * app list — re-skin live when the theme flips. Defaults to dark (the prototype
 * default and what previews want). `static` because the theme changes rarely.
 */
val LocalColorTokens = staticCompositionLocalOf<ColorTokens> { DarkColorTokens }

/**
 * The active global accent colour (prototype `state.accent`), used by app-list
 * tiles, the segmented control and other accent chrome. Start tiles keep their
 * own per-tile colours (see docs/DECISIONS.md S11), so this never recolours them.
 */
val LocalAccent = staticCompositionLocalOf<Color> { TileAccents.Blue }

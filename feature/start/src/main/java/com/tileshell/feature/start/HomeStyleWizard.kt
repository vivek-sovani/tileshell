package com.tileshell.feature.start

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.livetiles.NotificationSnapshot

/**
 * One-shot flag: has this device ever seen the home-style choice wizard?
 * Independent of app version, following the same shape as every other
 * one-shot flag in this app (`FirstRunHintPrefs`/`SettingsAppMigration`,
 * both backed by the shared `tileshell.prefs` file) — a fresh install has
 * this unset, and so does an *existing* install upgrading to the version
 * that introduced `HomeStyle` at all, since the flag itself didn't exist
 * before either. Shown exactly once, ever, regardless of how many further
 * updates follow — no version-number comparison needed.
 */
internal object HomeStyleWizardPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "home_style_wizard_shown"

    fun shown(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun markShown(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Fabricated sample apps for the wizard's live previews — never real installed
 * apps. Deliberately restricted to iconKeys with **no** `LiveFace` mapping at
 * all (`LiveFace.forIconKey`) — "phone"/"camera"/"store"/"settings" — so both
 * `TileView` and `IconCellView` always take the plain static-glyph path with a
 * blank `packageName` (verified safe: `TileIcons.hasIcon` is true for all
 * four, so neither renderer ever calls a real `PackageManager` lookup).
 */
private val SAMPLE_APPS = listOf(
    TileModel.App(
        id = "wizard-sample-phone", position = 0, size = TileSize.MEDIUM, colorId = "blue",
        packageName = "", activityName = "", label = "phone", iconKey = "phone",
    ),
    TileModel.App(
        id = "wizard-sample-camera", position = 1, size = TileSize.SMALL, colorId = "magenta",
        packageName = "", activityName = "", label = "camera", iconKey = "camera",
    ),
    TileModel.App(
        id = "wizard-sample-store", position = 2, size = TileSize.SMALL, colorId = "green",
        packageName = "", activityName = "", label = "store", iconKey = "store",
    ),
    TileModel.App(
        id = "wizard-sample-settings", position = 3, size = TileSize.SMALL, colorId = "orange",
        packageName = "", activityName = "", label = "settings", iconKey = "settings",
    ),
)

private val SAMPLE_ACCENTS = listOf("blue", "magenta", "green", "orange").map { TileAccents.forId(it) }

/**
 * A real, non-interactive [TileView] driven by a fabricated sample tile — the
 * TILES-mode preview's building block. Every wallpaper/glass param is an inert
 * placeholder (`tiledWallpaper = false`, `glass = false`, `wallpaperPhoto =
 * null`), so `TileView` always lands on its plain `Modifier.background(accent)`
 * fill; every callback is a no-op since this is a picture, not a real tile.
 */
@Composable
private fun SampleTileView(tile: TileModel.App, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size)) {
        TileView(
            tile = tile,
            index = 0,
            editMode = false,
            selected = false,
            dragging = false,
            mergeTarget = false,
            accent = accent,
            glass = false,
            transparency = 0.55f,
            glassLine = Color.Transparent,
            tiledWallpaper = false,
            wallpaper = Wallpapers.Mono,
            wallpaperPhoto = null,
            wallpaperAlignX = 0.5f,
            wallpaperAlignY = 0.5f,
            wallpaperZoom = 1f,
            wallpaperOrigin = { Offset.Zero },
            fullWidth = 0f,
            fullHeight = 0f,
            jigglePhase = 0f,
            flipped = false,
            liveActive = false,
            notifications = NotificationSnapshot.EMPTY,
            badgeCount = 0,
            darkTheme = true,
            canMoveBack = false,
            canMoveForward = false,
            onTap = {},
            onLongPress = {},
            onResize = {},
            onUnpin = {},
            onSelect = {},
            onExitEdit = {},
            onMove = {},
        )
    }
}

/**
 * A real, non-interactive [IconCellView] driven by a fabricated sample tile —
 * the ICONS-mode preview's building block. [shape] is fixed to
 * [IconShape.CIRCLE] regardless of the app's actual current setting (still
 * its own [IconShape.ORIGINAL] default at this point in first-run), since a
 * masked shape reads as more recognisably "Android-style" for this one-time
 * comparison than an unmasked square icon would.
 */
@Composable
private fun SampleIconCell(tile: TileModel.App, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size)) {
        IconCellView(
            tile = tile,
            editMode = false,
            selected = false,
            dragging = false,
            index = 0,
            jigglePhase = 0f,
            darkTheme = true,
            columns = 4,
            badgeCount = 0,
            notifications = NotificationSnapshot.EMPTY,
            onTap = {},
            onLongPress = {},
            onSelect = {},
            onExitEdit = {},
            onUnpin = {},
            onMove = {},
            canMoveBack = false,
            canMoveForward = false,
            iconShape = IconShape.CIRCLE,
            accent = accent,
        )
    }
}

/**
 * The first-run home-style choice screen (see [HomeStyleWizardPrefs]): a
 * full-screen, non-dismissible-by-tap-outside choice between the two home
 * styles, each illustrated with a real, live mini preview built from
 * [SAMPLE_APPS] — not a drawn mockup — so what's shown is pixel-for-pixel
 * what the real renderer produces. Tapping a card picks that style
 * ([onChoose]); "skip for now" ([onSkip]) leaves the default (TILES) as-is.
 */
@Composable
internal fun HomeStyleWizardScreen(
    onChoose: (HomeStyle) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "choose your start screen style",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "you can always switch later in personalize.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))

        HomeStyleOption(
            title = "windows-phone style",
            subtitle = "live tiles, dense grid, classic look",
            onClick = { onChoose(HomeStyle.TILES) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SampleTileView(SAMPLE_APPS[0], SAMPLE_ACCENTS[0], 76.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SampleTileView(SAMPLE_APPS[1], SAMPLE_ACCENTS[1], 36.dp)
                    SampleTileView(SAMPLE_APPS[2], SAMPLE_ACCENTS[2], 36.dp)
                }
                SampleTileView(SAMPLE_APPS[3], SAMPLE_ACCENTS[3], 76.dp)
            }
        }

        Spacer(Modifier.height(20.dp))

        HomeStyleOption(
            title = "android-style icons",
            subtitle = "shaped app icons, folders, free placement",
            onClick = { onChoose(HomeStyle.ICONS) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SAMPLE_APPS.forEachIndexed { i, app ->
                    SampleIconCell(app, SAMPLE_ACCENTS[i], 60.dp)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "skip for now",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSkip)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun HomeStyleOption(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview()
        }
    }
}

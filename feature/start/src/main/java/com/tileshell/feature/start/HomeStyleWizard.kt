package com.tileshell.feature.start

import android.content.Context
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.SolidColor
import com.tileshell.core.data.AppEntry
import com.tileshell.feature.livetiles.rememberAppIconBitmap
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
private fun HomeStyleWizardScreen(
    reset: Boolean,
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
            text = if (reset) "reset start layout · step 1 of 2" else "step 1 of 2",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
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
            text = if (reset) "cancel" else "skip for now",
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

private enum class SetupStep { STYLE, SETUP, APPS }

/** The wizard's theme pick; AUTO follows the system. */
internal enum class SetupTheme { DARK, LIGHT, AUTO }

/** Wizard background and text, fixed dark like the rest of first run. */
private val WizardBg = Color(0xFF0A0A0D)
private val WizardDim = Color.White.copy(alpha = 0.65f)
private val WizardLine = Color.White.copy(alpha = 0.18f)
private val ResetRed = Color(0xFFD6262B)

/**
 * The Start setup wizard, on first run and from "reset start layout":
 * (1) tiles or icons, (2) tile colour (single or multicolour) and default or
 * custom apps, (3) for custom only, a checklist of apps with the default ones
 * pre-ticked. The hubs and live tiles are always kept. [onFinish] gets the
 * picked style, colour, whether custom was chosen and the ticked packages.
 * On reset the last button is red, since it replaces the current Start.
 */
@Composable
internal fun StartSetupWizard(
    reset: Boolean,
    initialStyle: HomeStyle,
    initialMulticolor: Boolean,
    initialTheme: SetupTheme,
    accent: Color,
    apps: List<AppEntry>,
    defaultPackages: Set<String>,
    onFinish: (style: HomeStyle, theme: SetupTheme, multicolor: Boolean, custom: Boolean, picked: Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.STYLE) }
    var style by rememberSaveable { mutableStateOf(initialStyle) }
    var multicolor by rememberSaveable { mutableStateOf(initialMulticolor) }
    var theme by rememberSaveable { mutableStateOf(initialTheme) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<String>?>(null) }
    // Pre-tick the defaults once they've loaded.
    LaunchedEffect(defaultPackages) {
        if (picked == null && defaultPackages.isNotEmpty()) picked = defaultPackages
    }

    BackHandler {
        when (step) {
            SetupStep.STYLE -> onCancel()
            SetupStep.SETUP -> step = SetupStep.STYLE
            SetupStep.APPS -> step = SetupStep.SETUP
        }
    }

    when (step) {
        SetupStep.STYLE -> HomeStyleWizardScreen(
            reset = reset,
            onChoose = { style = it; step = SetupStep.SETUP },
            onSkip = onCancel,
        )
        SetupStep.SETUP -> SetupChoiceScreen(
            reset = reset,
            accent = accent,
            theme = theme,
            onTheme = { theme = it },
            multicolor = multicolor,
            onMulticolor = { multicolor = it },
            custom = custom,
            onCustom = { custom = it },
            onBack = { step = SetupStep.STYLE },
            onNext = {
                if (custom) step = SetupStep.APPS
                else onFinish(style, theme, multicolor, false, emptySet())
            },
        )
        SetupStep.APPS -> SetupAppsScreen(
            reset = reset,
            accent = accent,
            apps = apps,
            defaultPackages = defaultPackages,
            picked = picked ?: defaultPackages,
            onToggle = { pkg ->
                val cur = picked ?: defaultPackages
                picked = if (pkg in cur) cur - pkg else cur + pkg
            },
            onBack = { step = SetupStep.SETUP },
            onDone = { onFinish(style, theme, multicolor, true, picked ?: defaultPackages) },
        )
    }
}

@Composable
private fun WizardFrame(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        content = content,
    )
}

@Composable
private fun WizardButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun WizardBackRow(onBack: () -> Unit) {
    Text(
        text = "‹ back",
        color = WizardDim,
        fontSize = 14.sp,
        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
    )
}

@Composable
private fun SelectableCard(
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else WizardLine),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) { content() }
}

private val MULTI_SWATCHES = listOf("cobalt", "teal", "green", "purple", "orange").map { TileAccents.forId(it) }

@Composable
private fun SetupChoiceScreen(
    reset: Boolean,
    accent: Color,
    theme: SetupTheme,
    onTheme: (SetupTheme) -> Unit,
    multicolor: Boolean,
    onMulticolor: (Boolean) -> Unit,
    custom: Boolean,
    onCustom: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) = WizardFrame {
    WizardBackRow(onBack)
    Text(if (reset) "reset start layout · step 2 of 2" else "step 2 of 2", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Text("set up your start", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(24.dp))

    Text("theme", color = WizardDim, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(SetupTheme.DARK to "dark", SetupTheme.LIGHT to "light", SetupTheme.AUTO to "auto").forEach { (t, label) ->
            SelectableCard(theme == t, accent, Modifier.weight(1f), onClick = { onTheme(t) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                when (t) {
                                    SetupTheme.DARK -> Color(0xFF0A0A0D)
                                    SetupTheme.LIGHT -> Color(0xFFECE9E4)
                                    SetupTheme.AUTO -> Color(0xFF6E6C68)
                                },
                            )
                            .border(1.dp, WizardLine, RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Text("tile colour", color = WizardDim, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectableCard(!multicolor, accent, Modifier.weight(1f), onClick = { onMulticolor(false) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(accent)) }
                }
                Spacer(Modifier.height(8.dp))
                Text("single colour", color = Color.White, fontSize = 14.sp)
            }
        }
        SelectableCard(multicolor, accent, Modifier.weight(1f), onClick = { onMulticolor(true) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MULTI_SWATCHES.take(3).forEach { Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
                }
                Spacer(Modifier.height(8.dp))
                Text("multicolour", color = Color.White, fontSize = 14.sp)
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Text("apps", color = WizardDim, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    SelectableCard(!custom, accent, Modifier.fillMaxWidth(), onClick = { onCustom(false) }) {
        Column {
            Text("default", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("hubs, live tiles and common apps", color = WizardDim, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    SelectableCard(custom, accent, Modifier.fillMaxWidth(), onClick = { onCustom(true) }) {
        Column {
            Text("custom", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("choose which apps go on start", color = WizardDim, fontSize = 13.sp)
        }
    }

    Spacer(Modifier.weight(1f))
    if (reset && !custom) {
        Text(
            "this replaces your current start. save a snapshot first if you may want it back.",
            color = WizardDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
    WizardButton(
        text = when {
            custom -> "next"
            reset -> "reset"
            else -> "start"
        },
        color = if (reset && !custom) ResetRed else accent,
        onClick = onNext,
    )
}

@Composable
private fun SetupAppsScreen(
    reset: Boolean,
    accent: Color,
    apps: List<AppEntry>,
    defaultPackages: Set<String>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) = WizardFrame {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(apps, defaultPackages, query) { setupAppRows(apps, defaultPackages, query) }

    WizardBackRow(onBack)
    Text("apps on start", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(4.dp))
    Text("${picked.size} picked · hubs and live tiles are always included", color = WizardDim, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, WizardLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (query.isEmpty()) Text("search apps", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(rows, key = { it.packageName }) { app ->
            SetupAppRow(app, app.packageName in picked, accent) { onToggle(app.packageName) }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (reset) {
        Text(
            "this replaces your current start. save a snapshot first if you may want it back.",
            color = WizardDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
    WizardButton(if (reset) "reset" else "done", if (reset) ResetRed else accent, onDone)
}

@Composable
private fun SetupAppRow(app: AppEntry, checked: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) accent else Color.Transparent)
                .border(1.5.dp, if (checked) accent else Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp)),
        ) {
            if (checked) Text("✓", color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        val icon = rememberAppIconBitmap(app.packageName)
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Box(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(app.label.lowercase(), color = Color.White, fontSize = 16.sp)
    }
}

/**
 * The custom list's rows: the default apps first (in their seeded order is
 * not needed, alphabetical is), then every other installed app
 * alphabetically, one row per package, filtered by [query]. Pure, unit-tested.
 */
internal fun setupAppRows(apps: List<AppEntry>, defaultPackages: Set<String>, query: String): List<AppEntry> {
    val q = query.trim().lowercase()
    return apps
        .distinctBy { it.packageName }
        .filter { q.isEmpty() || it.label.lowercase().contains(q) }
        .sortedWith(compareBy<AppEntry>({ it.packageName !in defaultPackages }, { it.label.lowercase() }))
}

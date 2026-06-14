# TileShell — Windows Mobile 10–style Android Launcher

## What this project is
A production Android launcher (default-HOME replacement) recreating the Windows Phone / Windows Mobile 10 Start screen: live tiles, dense 4-column grid, app list with jump grid, WP-style personalization. The authoritative spec is `docs/TileShell-Feature-Specification.docx`; the visual/behavioural reference is the HTML prototype in `design/windows-mobile-launcher-for-android/project/` (read the relevant JS/CSS file when implementing a feature — do NOT guess values).

## Stack & architecture (do not deviate without asking)
- Kotlin + Jetpack Compose, MVVM, unidirectional flow: Room/DataStore → Repository → ViewModel(StateFlow) → Compose
- Modules: `:app`, `:core:design`, `:core:data`, `:feature:start`, `:feature:livetiles`, `:feature:applist`, `:feature:personalize`, `:feature:system`
- minSdk 26, targetSdk latest stable. No QUERY_ALL_PACKAGES — use `<queries>` with LAUNCHER category. No analytics SDKs.
- Persistence: Room for tiles/folders, Proto DataStore for settings. All layout writes debounced + transactional.

## Normative behaviour values (from prototype — treat as constants)
- Grid: 4 columns, dense packing, sizes small 1×1 / medium 2×2 / wide 4×2 / large 4×4; ref unit 90px, gap 3px, side 9px on 393px width → derive dp proportionally
- Long-press: 430ms (tiles), 450ms (app list pin); move-cancel threshold 7px
- Merge zone: inner 22–78% of target tile, both axes
- Pager: app list slides in; Start translates −22% and fades to 0.4; commit at 50%; activate when |dx|>12px and |dx|>1.2|dy|
- Live tiles: random tile flips every ~2.6s; photos cross-fade ~3.0s (never flips); people mosaic cell refresh ~2.1s; all paused in edit mode / off-screen / battery saver
- Glass alpha: a = 0.62·(1−t)+0.05, t = transparency slider 0–1; dark rgb(18,18,24), light rgb(250,250,252)
- Screen tokens (styles.css): dark bg #0a0a0d / fg #f6f6f8; light bg #ece9e4 / fg #14141a
- 14 accents: #2B78E4 #1452CC #6B3FD4 #C4287E #D6262B #E5641E #E2A200 #7CB518 #1F9E57 #0F9B9B #1399C6 #5A6B7B #9B6A8F #3A4554
- Labels lowercase via styling; original monoline icons (port from `design/windows-mobile-launcher-for-android/project/launcher/icons.js`), never Microsoft assets

## Workflow rules (important — Pro plan, limited session budget)
1. One session = one SESSION-PLAN.md item (see `design/SESSION-PLAN.md`). Do not start the next item.
2. Read only the files needed for the current task. Do not explore the whole repo or re-read the spec docx; this file + the named prototype file is enough context.
3. Every session must end with: `./gradlew :app:assembleDebug` passing, relevant unit tests passing, and a git commit (`feat(sN): <summary>`).
4. If something is ambiguous, make the WP-faithful choice, note it in `docs/DECISIONS.md`, and continue — don't stall.
5. Prefer editing existing files over creating parallel ones. No TODO stubs left uncommitted.
6. Tests: pure logic (packer, merge rules, alpha formula, search filter) gets JUnit tests in the same session it's written.

## Commands
- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Set as home (test): `adb shell cmd package set-home-activity com.tileshell/.MainActivity`

## Current status
<!-- Update this block at the end of every session -->
- Last completed session: S18 — glass, blur, wallpapers (FR-7). `LauncherSettings` grew `glass`/`transparency`/`blur`/`wallpaperId`/`customWallpaperUri` (flat codec value runs to end-of-line so content URIs with `=` round-trip; transparency clamped 0..1; empty custom URI → null). `SettingsRepository` + `StartViewModel` gained the matching setters plus `resetLayout()` — new `LayoutRepository.resetLayout()` always `replaceLayout`s the WP default (shares `writeDefaultLayout` with `seedIfEmpty`). `PersonalizeSheet` now renders the full prototype groups below theme+accent: transparent-tiles + blur-wallpaper **pill toggles**, a tile-transparency `Slider`, the **wallpaper row** (custom-photo button + 6 gradient previews) and a reset-start-layout action. New `WallpaperBackground` composable in `:feature:start` draws the selected gradient **or** a custom photo (decoded off-main-thread via `produceState`), applying `blur(18dp)` + scale 1.12 (+ `saturate(1.1)` on photos only) when blur is on. Glass mode swaps the Start grid tile fill for `Glass.fill(dark, transparency)` + inset `glassLine`, with a per-tile-colour accent dot on small tiles (folder overlay stays solid-accent). Custom wallpaper uses **`ACTION_OPEN_DOCUMENT`** (persistable URI, not PickVisualMedia — see DECISIONS S18); `:feature:start` now also depends on `androidx.activity.compose`. `SettingsCodecTest` extended for all new fields. Build + all unit tests green; not yet verified on emulator.
- Previously: S17 — personalize sheet: theme + accent (FR-7). New `:feature:personalize` source: a stateless `PersonalizeSheet(visible, dark, accentId, onThemeChange, onAccentChange, onDismiss)` — fading scrim + slide-up panel (grip, lowercase thin "personalize" title) with a dark/light **segmented toggle** and the **14 accent swatches** (7×2), depending only on `:core:design`. Settings persist via a typed `DataStore<LauncherSettings>` in `:core:data` (`SettingsRepository`/`SettingsSerializer`/`SettingsCodec` — a flat `key=value` codec, **not** protobuf; see DECISIONS S17). `StartViewModel` exposes `settings: StateFlow<LauncherSettings>` + `setTheme`/`setAccent` and owns `personalizeOpen` (edit bar → `openPersonalize`; Back/Home close it first). Live application via two new `staticCompositionLocalOf`s in `:core:design`: **`LocalColorTokens`** (active theme, provided at the Start root; the sheet, edit bar and app list re-skin live) and **`LocalAccent`** (global chrome accent — app-list tiles/headers/jump grid/seg; **Start tiles keep their per-tile `colorId`**, so accent does not recolour the grid). Wallpaper stays theme-independent (Aurora). `SettingsCodecTest` covers the round-trip + tolerance. Build + all unit tests green; not yet verified on emulator.
- Next session: No further SESSION-PLAN items defined (S1–S18 done). All prototype personalize groups now implemented; awaiting next scope.
- Known issues: pre-Q (API 26–28) default-launcher prompt falls back to Settings ACTION_HOME_SETTINGS; isDefault uses RoleManager on Q+, resolveActivity heuristic below. Wallpaper radial layers approximate the CSS ellipse radius with a circle (reads identically). Wallpaper `blur(18dp)` is a RenderEffect → no-op below API 31 (same as folder overlay). Custom-wallpaper persistable grant is best-effort (`runCatching`); a revoked/deleted URI falls back to the selected gradient. `app_cache` table + DAO ops defined but not yet populated (S9). `:core:design` keeps a separate private preview-only size enum (intentional). Tiles for non-default/pinned apps with no role have a null `iconKey` → fall back to the generic "app" glyph (real-app-icon fallback not implemented; revisit if mixing looks off). `MIGRATION_1_2` is wired and schema-exported but only exercised on upgrade; fresh installs create v2 directly.

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
- Last completed session: S20 — flip engine + clock tile (Phase 5 begins, live tiles). `:feature:livetiles` now has real sources: `LiveFace.forIconKey(iconKey, size)` maps a tile's monoline icon key to its live face (prototype `app.live`) — null for small tiles / unmapped keys (stays static); S20 ships `CLOCK` only. `FlipTile` is a real X-axis 3D flip (`rotationX` 0→180, shallow `cameraDistance`, faces swap at 90°, back counter-rotated), 500 ms / `cubic-bezier(.5,.05,.2,1)` — chosen over the prototype's `translateY` slide (DECISIONS S20). `rememberFlipState(liveIds, active)` runs the prototype `setInterval(flipOne, 2600)` as a gated `LaunchedEffect` loop toggling one random flippable tile/2.6 s (prunes scrolled-out ids). `rememberLiveTilesActive(suspended)` ANDs the caller's suspend flag (edit mode, app-list >50%, open folder/personalize) with three live system signals — lifecycle resumed, battery saver off (`ACTION_POWER_SAVE_MODE_CHANGED`), animator duration scale ≠ 0 (`ContentObserver`). `ClockTileFace` renders front (time/weekday/date) + back (date/alarm) per FR-2.1, ticking on the minute boundary while active; `clockFace(...)` is a pure, unit-tested formatter (24-hour, unpadded hours, lowercase names; `alarm` is a placeholder). `:feature:start` depends on `:feature:livetiles` and renders the clock face via `AppTileContent`. Build + all unit tests green (11 new livetiles tests).
- Previously: S19 — persistence hardening + first run (Phase 4 complete, tag `v0.4`). All Start-layout writes in `StartViewModel` now run on a single-thread `Dispatchers.IO.limitedParallelism(1)` `writeContext` (serialized, ordered, non-interleaving over the already-`@Transaction` DAO ops); reorder commits are **debounced 120 ms** via a `DROP_OLDEST` `MutableSharedFlow` → `.debounce()` collector (resize/unpin/merge/rename/reset/prune write immediately). `TileShellDatabase.build()` is now **corruption-safe**: `fallbackToDestructiveMigration()` for schema/downgrade mismatch + a force-open of `openHelper.readableDatabase` at startup, and an explicit `deleteDatabase` + rebuild on an unrecoverable `SQLiteException` — the DB always comes up (empty if wiped) and `seedIfEmpty()` re-seeds the WP default; settings live in a separate DataStore file, unaffected. New `FirstRunHint` composable in `:feature:start` shows the prototype `.hint` text (same bolded spans) as a one-time bottom card over Start, gated by a `first_run_hint_shown` flag in `tileshell.prefs`. `MainActivity` default-launcher prompt polished (early-return when already HOME, record the ask before launching, `runCatching` the launch). New `docs/RESTORE-CHECKLIST.md` captures the manual kill/reboot/corruption verification steps. Build + all unit tests green; checklist executed manually on device (not CI).
- Previously: S18 — glass, blur, wallpapers (FR-7). `LauncherSettings` grew `glass`/`transparency`/`blur`/`wallpaperId`/`customWallpaperUri`; `PersonalizeSheet` renders the full prototype groups (transparent-tiles + blur pill toggles, transparency `Slider`, 6-gradient wallpaper row + custom-photo picker, reset-layout). `WallpaperBackground` draws gradient or custom photo with optional `blur(18dp)`+scale 1.12. Glass mode swaps Start tile fill for `Glass.fill(dark, transparency)` + inset `glassLine` + per-tile accent dot on small tiles. Custom wallpaper via `ACTION_OPEN_DOCUMENT` (persistable URI). S17 added the theme/accent sheet + the flat `key=value` settings codec; see DECISIONS S17/S18.
- Next session: S21 — weather + calendar tiles (Phase 5). Phase 0–4 (S1–S19) done + S20; `v0.4` tagged.
- Known issues: live face text is `Color.White` regardless of glass/light theme (matches the existing static `AppTileContent`; revisit when glass + light + live overlap looks off). The clock `alarm` value is a static "7:00" placeholder until an alarm provider lands. A clock tile dragged into a folder would render the live clock face (folder children reuse `AppTileContent`); harmless and never happens in the default layout. pre-Q (API 26–28) default-launcher prompt falls back to Settings ACTION_HOME_SETTINGS; isDefault uses RoleManager on Q+, resolveActivity heuristic below. Wallpaper radial layers approximate the CSS ellipse radius with a circle (reads identically). Wallpaper `blur(18dp)` is a RenderEffect → no-op below API 31 (same as folder overlay). Custom-wallpaper persistable grant is best-effort (`runCatching`); a revoked/deleted URI falls back to the selected gradient. `app_cache` table + DAO ops defined but not yet populated (S9). `:core:design` keeps a separate private preview-only size enum (intentional). Tiles for non-default/pinned apps with no role have a null `iconKey` → fall back to the generic "app" glyph (real-app-icon fallback not implemented; revisit if mixing looks off). `MIGRATION_1_2` is wired and schema-exported but only exercised on upgrade; fresh installs create v2 directly.

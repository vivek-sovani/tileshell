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
- Last completed session: S6 — real Start screen + launching. `:feature:start` `StartScreen` (MVVM via `StartViewModel`: `AndroidViewModel`, `repo.tiles.stateIn` StateFlow, `seedIfEmpty()` on `Dispatchers.IO`) renders persisted tiles through the dense packer over the aurora wallpaper: monoline glyphs (`TileIcons[iconKey]`) tinted white on accent fills (`TileAccents.forId`), lowercase lower-left labels, icon-only centered on small; folder tile shows a 2×2 child-glyph mini-grid. Tap launches via `LauncherApps.startMainActivity` with a fallback toast; folder tap inert until S16. Status-bar + nav-bar insets + 74dp bottom (FR-1). Added `iconKey` to tiles/folder_children (Room **v2** + `MIGRATION_1_2` ALTER TABLE, schema 2.json) set at seed time via `DefaultLayout.iconFor`. Deleted `DebugAppListScreen` (S2) and `GridSpikeScreen` (S3); MainActivity now shows `StartScreen`. 10 seeder tests (incl. iconKey). Verified on emulator: fresh install → WP default layout with real apps over aurora; phone tile opens dialer; Home returns to Start.
- Next session: S7 · Tilt feedback + Home behaviour
- Known issues: pre-Q (API 26–28) default-launcher prompt falls back to Settings ACTION_HOME_SETTINGS; isDefault uses RoleManager on Q+, resolveActivity heuristic below. Wallpaper radial layers approximate the CSS ellipse radius with a circle (reads identically). `app_cache` table + DAO ops defined but not yet populated (S9). `:core:design` keeps a separate private preview-only size enum (intentional). Tiles for non-default/pinned apps with no role have a null `iconKey` → fall back to the generic "app" glyph (real-app-icon fallback not implemented; revisit if mixing looks off). `MIGRATION_1_2` is wired and schema-exported but only exercised on upgrade; fresh installs create v2 directly.

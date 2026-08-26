# TileShell — Windows Mobile 10–style Android Launcher

## What this project is
A production Android launcher (default-HOME replacement) recreating the Windows Phone / Windows Mobile 10 Start screen: live tiles, dense 4-column grid, app list with jump grid, WP-style personalization. The authoritative spec is `docs/TileShell-Feature-Specification.docx`; the visual/behavioural reference is the HTML prototype in `design/windows-mobile-launcher-for-android/project/` (read the relevant JS/CSS file when implementing a feature — do NOT guess values).

## Stack & architecture (do not deviate without asking)
- Kotlin + Jetpack Compose, MVVM, unidirectional flow: Room/DataStore → Repository → ViewModel(StateFlow) → Compose
- Modules: `:app`, `:core:design`, `:core:data`, `:feature:start`, `:feature:livetiles`, `:feature:applist`, `:feature:personalize`, `:feature:system`
- minSdk 26, targetSdk latest stable. No QUERY_ALL_PACKAGES — use `<queries>` with LAUNCHER category. No analytics SDKs.
- Persistence: Room for tiles/folders (v6 schema, `tileshell.db`), flat key=value DataStore for settings (custom `SettingsCodec`, not Proto DataStore). All layout writes debounced + transactional.

## Normative behaviour values (from prototype — treat as constants)
- Grid: 4 columns default (user-selectable 4/5/6 via `columns` setting), dense packing, sizes small 1×1 / medium 2×2 / wide 4×2 / large 3×3 (large is offered for **any** app on **any** column count, including 4 — `AppCategories.allowsLargeTile` now always returns `true`; news app's `NotificationTileFace` gets a full-area hero layout at LARGE; **a folder becomes a widget stack whenever every member is uniformly WIDE or LARGE** — merge two large tiles directly, or use the folder overlay's "make stack · wide/large" shortcut — see status); ref unit 90px, gap 3px, side 9px on 393px width → derive dp proportionally
- Long-press: 430ms (tiles), **700ms (app list pin — deliberate deviation from the prototype's
  450ms, raised after on-device testing; see DECISIONS "App list long-press raised to 700ms")**;
  move-cancel threshold 7px
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
- **`main` — resize/reorder follow-up #5: fixed a stale-closure bug where a
  SECOND resize/reorder on the same tile/card silently did nothing.**
  User-reported: "within a single edit session suppose I resize a tile from
  small to long and again I want to resize the same tile to small it doesn't
  happen" — then, unprompted, confirmed it's symmetric ("and a vice a
  versa"), which was the key clue pointing away from any square↔wide-specific
  math bug and toward a general "second gesture on the same handle" bug.
  Root cause, present in every drag handle built/touched this session
  (`QuickPanelMoveHandle`/`QuickPanelWidthHandle` in `QuickPanelOverlay.kt`;
  `DragHandlePill`/`ResizeHandle`/`CornerArcHandle` in `feed/WidgetSlot.kt`):
  each wraps `detectDragGestures` inside `pointerInput(Unit)` or
  `pointerInput(widgetId)` — for a *given* tile/card, that key never changes
  across repeated gestures in one edit session, so the gesture coroutine
  launches exactly once and never restarts. `detectDragGestures`'s own
  `onDragStart`/`onDrag`/`onDragEnd` callback closures — plain `{
  onDragStart() }`-style wrappers around the handle's function parameters —
  are therefore captured **once, at that first launch**, and never updated on
  later recompositions even though fresh callback lambdas are passed in every
  time (this is the standard, documented Compose `pointerInput` gotcha).
  The very first drag on a given handle instance works correctly (it
  genuinely *is* the fresh state); every subsequent drag on that *same*
  instance still invokes the **original, frozen** closure — which itself
  closed over whatever `sizesMap`/`sizeOf`/`commitResize` (Quick Panel) or
  `WidgetSection`'s equivalent state existed back at that first drag. In
  `commitResize`'s case specifically, the frozen closure's `sizeOf(id).cols`
  read the tile's *pre-first-resize* size forever after, so the size-vs-
  settled comparison silently concluded "no change needed" on every later
  attempt — nothing ever got persisted again, for either direction. Fixed
  with `rememberUpdatedState` (the standard fix for exactly this class of
  bug) on every drag callback in all five handle composables — the gesture
  coroutine still never restarts, but each callback invocation now reads
  through to the *current* reference instead of the one frozen at first
  launch. This is a foundational fix underneath the whole session's resize/
  reorder work — every earlier follow-up in this log was layered on top of
  code that could only ever reliably handle *one* gesture per tile/card per
  edit session; this is what makes repeated use actually work. Build + full
  unit test suite green; installed on the physical device, launched with no
  crash in `adb logcat` — the actual "resize back and forth repeatedly" check
  still needs the user's own hands-on confirmation.
- **`main` — resize/reorder follow-up #4: fixed Quick Panel resizing the
  wrong (usually adjacent) tile.** User-reported: "when I try to resize
  certain tile another tile is resized some times. mostly adjacent." Real
  bug, not a hit-testing/positioning issue this time: the `QuickPanelTile(...)`
  calls in the row-packing loop (`QuickPanelOverlay.kt`) were never wrapped in
  `key(tile.id)`, so Compose identified each tile instance — and the
  `pointerInput(Unit)` gesture coroutines inside its move/width handles, which
  never restart since their key is a constant `Unit` — by *position* in the
  loop, not by the tile's own stable id. A resize or reorder commit reflows
  which tile occupies which row/slot on the very next recomposition (that's
  the whole point of `packQuickPanelRows`); without a data-identity key, a
  tile that shifts into a position a neighbour used to occupy could inherit
  that neighbour's still-running gesture coroutine — and its stale captured
  `tile.id` closures — misattributing a drag on "whatever's now at this
  position" to the tile that used to be there. `feed/WidgetSlot.kt`'s
  `WidgetView`/`WidgetStackView` already key by `widgetId`/`stackId` for
  exactly this reason (a pattern from earlier in the project's history); this
  was a real gap in the Quick Panel tile grid added this session that never
  got the same treatment. Fixed with `key(tile.id) { QuickPanelTile(...) }`.
  Build + full unit test suite green; installed on the physical device,
  launched with no crash in `adb logcat` — the actual "does it now always
  resize the tile I meant" check still needs the user's own hands-on
  confirmation.
- **`main` — resize/reorder follow-up #3: redesigned horizontal resize as a
  scale-preview during drag, commit-on-release, for both Quick Panel and
  glance.** Direct same-day follow-up after follow-up #2's fix still wasn't
  "smooth and visually improved" enough on either surface. Root causes this
  time were structural, not just tuning: **Quick Panel had NO live resize
  preview at all** — `QuickPanelTile`'s real layout only ever read the
  *committed* `sizesMap`, so `resizeDeltaPx` was tracked but never fed back
  into anything visible; the tile just sat still for the whole drag and hard-
  snapped to its new size only once released. **Glance cards had a live
  preview, but it drove a real relayout every drag frame** (`.width(liveWidth
  .dp)` directly) — for a *paired* half-width card that's still wrapped in
  `Modifier.weight(1f)` (put back after follow-up #2 tried removing it), a
  real relayout to a wider value just gets silently coerced back to the fixed
  weighted share every frame; for a *real hosted widget*, a live relayout also
  meant calling `AppWidgetHostView.updateAppWidgetSize` — a real IPC call to
  the widget's own process — on every single pixel of drag. Both explain "not
  smooth" precisely. Fixed with the standard live-resize-preview technique
  instead: the REAL layout (`WidgetView`/`BuiltinCardView`'s outer `.width()`/
  `.height()`, and Quick Panel's `.weight(cols)`/`.aspectRatio(cols)`) now
  stays pinned at the last-*committed* size for the entire drag — nothing
  reflows, no real-widget IPC, no `weight(1f)` capping, because it's the
  same value that constraint would produce anyway. The live drag feedback is
  a `graphicsLayer { scaleX = live/committed }` (Quick Panel: `scaleX` only —
  height is provably unchanged, since a WIDE tile is exactly a SQUARE tile at
  2× width and the same one-row height, so a pure horizontal scale is exact,
  not an approximation; glance cards: `scaleX` **and** `scaleY`, since a real
  widget's height genuinely does change) with `transformOrigin = (0,0)`
  (glance) `/ (0, 0.5)` (Quick Panel, matching its centre-anchored width
  handle) and a `zIndex` bump while resizing, so the growing tile/card paints
  above its neighbour instead of composition order arbitrarily deciding which
  one's on top. This is pure paint-layer scaling — cheap, GPU-composited, immune
  to layout constraints, and exactly the same class of fix as the graphicsLayer
  translation the MOVE/reorder drag already used for its own smoothness. On
  release, the existing `settleWidth()`/`settleQuickPanelTileSize()` commit
  logic is unchanged — it reads the (always-correctly-tracked, never actually
  broken) live delta and persists the real size, which repacks the row/grid
  exactly once instead of every frame. Build + full unit test suite green;
  installed on the physical device, launched with no crash in `adb logcat` —
  the actual drag *feel* (the whole point of this round) still needs the
  user's own on-device confirmation.
- **`main` — resize/reorder follow-up #2: fixed a real "paired half-width
  resize isn't smooth" bug, and matched the glance move handle to Quick
  Panel's exactly.** Direct same-day follow-up. (1) **"horizontal resizing is
  not yet smooth on app widgets, like calendar, weather, and now playing"** —
  root-caused as a genuine bug, not just a feel/polish issue: the two-card
  `WidgetRow.Pair` `Row` in `WidgetSection` (`feed/WidgetSlot.kt`) gave each
  card `Modifier.weight(1f)` (default `fill = true`), which hands the card
  *fixed* (min=max) width constraints equal to its 50% share — a
  `SizeModifier` from `.width(liveWidth.dp)` deeper inside (`WidgetView`/
  `BuiltinCardView`) coerces its requested width into whatever constraints it's
  given, so the live drag's `liveWidth` state kept updating correctly (the
  gesture wasn't literally broken) but the card's actual on-screen width was
  pinned at exactly 50% no matter how far past that the finger dragged; only
  on release did `settleWidth()` read the (correctly, silently, uncapped)
  tracked state and snap straight to full width — reading as an unresponsive,
  discontinuous jump rather than a smooth resize. Fixed by dropping
  `Modifier.weight(1f)` for both cards in a `Pair` row in favour of each
  card's own unconstrained `.width(liveWidth.dp)` — an un-weighted `Row` gives
  each child its own requested size instead of an equal fixed share, so a
  growing card now smoothly pushes its (un-shrinking) neighbour along via
  `Arrangement.spacedBy`'s sequential placement, tracking the finger in real
  time; the commit-and-repack behaviour on release is unchanged. Applies to
  real hosted widgets too, sharing the same `Pair`-row code path. (2) **"the
  glance movement handle should use the same handle type and position as Quick
  Panel's (already good)"** — `WidgetEditOverlay`'s drag handle used to share a
  cramped top-left corner `Row` with the edit/remove/unstack action pills
  (`SpaceBetween`); it's now a self-positioned `BoxScope.DragHandlePill`
  (`.align(Alignment.TopCenter).offset(y = -5.dp)`, 22×12dp grip-dot pill) —
  same shape, size, and border-straddling position as
  `QuickPanelOverlay.kt`'s `QuickPanelMoveHandle`, byte-for-byte matched per
  explicit request rather than just "similar." The action pills now sit alone
  in their own top-right row. Build + full unit test suite green; installed on
  the physical device, launched with no crash in `adb logcat` — the actual
  smoothness/positioning still needs the user's own on-device confirmation
  (this is exactly the kind of live-drag feel that's hard to fully verify
  without a real finger).
- **`main` — resize/reorder follow-up: seven on-device-reported fixes to the
  Quick Panel/glance handle work below.** Direct same-day follow-up after the
  user tried the feature on their physical device. Reported, and fixed:
  (1) **glance width-handle "not working properly" + handles sitting inside the
  card instead of at the border** — root-caused as the same issue: Part C's
  restyle had enlarged every `ResizeHandle`'s touch target to a flat `maxOf(dim,
  40dp)` on its thin axis (a 4dp-thick bar got a 40dp hit box) while ALSO moving
  from inward `padding` to (in this same follow-up) an outward border offset —
  combined, the oversized hit box reached well into a paired half-width
  neighbour's own content/bounds, competing for the touch. Fixed by switching to
  an *additive* `dimension + 16.dp` hit enlargement (not a flat 40dp minimum)
  plus a modest 4dp outward offset (not 8dp inward) — comfortably bigger than
  the visible bar without reaching into a neighbour. (2) **corner handle
  "slightly bigger"** — `CornerArcHandle`'s visual arc grew 18dp→24dp, stroke
  2.5dp→3dp, hit box 40dp→44dp. (3) **the move handle "should visually look
  different, otherwise it looks like a resize handle"** (both surfaces) — was a
  real design gap: the move handle and the resize bars were both plain thin
  straight bars, differing only by position. Both `feed/WidgetSlot.kt`'s
  `DragHandlePill` and `QuickPanelOverlay.kt`'s `QuickPanelMoveHandle` now
  render a small 3×2 grip-dot pattern (`GripDots`, drawn with `Canvas`/
  `drawCircle`) inside a pill instead of a straight bar — a different *shape*,
  not just a different colour. (4) **vertical resize for the built-in glance
  cards** (weather/agenda/now-playing — user: "if I have a big set of
  appointments") — `WidgetEditOverlay`'s `resizableHeight = false` restriction
  for built-ins was lifted (now defaults `true`, matching real hosted widgets);
  `BuiltinCardView` gained a real `heightDp`/`defaultHeightDp` (three new
  per-card-type starting-height constants, since a built-in has no provider to
  size from the way a real widget does) and switched its outer sizing from an
  unbounded-parent-incompatible `heightIn(min=)` to a bounded `.height()`,
  matching `WidgetView`'s own established pattern exactly; `AccentCard`
  (`FeedPage.kt`, shared by all three built-in cards) gained an optional
  `modifier` param + centred content alignment so a taller card's accent fill
  actually covers the resized area instead of leaving blank space below a
  natural-height card. **Explicitly out of scope, noted for the user**: making
  `AgendaCard` show *more* appointments when taller — `CalendarFace`/
  `queryUpcomingEvents` (`:feature:livetiles`) are hardcoded to the next 2
  events and also back the Start-screen calendar live tile, so widening that
  shape is a real, separate follow-up, not attempted here to avoid an
  unreviewed change to an already-shipped live tile. (5) **Quick Panel: a
  separate "done" button at the edit icon's position while editing** — was
  already the same tap target/position, but only recoloured the pencil icon;
  now swaps to the existing "check" glyph (`TileIcons`) so "tap here to finish"
  reads as a distinct action, not just a tint change. (6) **Quick Panel handle
  position** — user confirmed already correct, no change needed there.
  (7) **vertical/horizontal-resizable sliders (brightness/ring/media)** —
  explicitly deferred again, unchanged from the original plan's scoping
  (`QuickPanelSliders` are still three separate fixed-orientation Material3
  `Slider` rows below the tile grid); genuinely new, larger work (no vertical-
  slider precedent anywhere in this codebase — would need the Compose rotated-
  slider trick, plus folding sliders into the same order/size grid system as
  the toggle tiles) — flagged to the user as a distinct next step rather than
  attempted in this same pass. Build + full unit test suite green; installed on
  the physical device, launched without crash (confirmed via `adb logcat`), but
  the specific gesture fixes (handle position/hit-testing, height-resize visual
  result) are **not yet visually re-confirmed on-device** — the user had asked
  to check manually rather than continue iterating via ADB-synthesized swipes/
  screenshots this session.
- **`main` — Quick Panel tiles and glance cards get One-UI-inspired resize +
  reorder, via a single global edit toggle.** New ask, not in the WP prototype/
  spec — user drew an explicit parallel to One UI's resizable quick-settings
  tiles and orientation-flexible sliders (sliders themselves stayed out of scope
  this session — separate future work). Planned in Plan Mode after two rounds of
  code-level research + a design pass; visual language (thin bars for move/
  height/width, a quarter-circle arc for the corner both-axes handle, no per-tile
  labels) was iterated live with the user via the visualize tool before
  implementation, including one correction ("it is round not a line in corner")
  and one proportions fix ("handle width is just like horizontal and vertical
  handle. it is shown thin"). Three parts:
  **(A) Quick Panel** (`QuickPanelOverlay.kt`) — toggle tiles resize square↔wide
  and drag-reorder via a new header "edit" toggle (new pencil glyph in
  `TileIcons`) instead of per-tile long-press; every tile shows its handles at
  once when editing. `QuickPanelTileSpec` gained a stable `id` (independent of a
  tile's conditional presence, e.g. "allow access" only exists pre-grant). New
  `QuickPanelLayout.kt`: pure, unit-tested `applyQuickPanelOrder`/
  `reorderQuickPanelTiles`/`settleQuickPanelTileSize`/`packQuickPanelRows`/
  `encode·decodeQuickPanelSizes`, mirroring `feed/WidgetSlot.kt`'s
  `reorderWidgets`/`packWidgetRows` shape; a local `QuickPanelTileSize` enum
  (`SQUARE`/`WIDE`) deliberately doesn't reuse Start's `core/data/TileSize` (an
  11-preset, Room-coupled, two-axis enum — massive overkill for a one-row-tall,
  two-state panel tile). Height/corner handles were deliberately dropped for
  Quick Panel specifically: its tiles are laid out in `Row`s with forced
  `aspectRatio`, so height is derived from width, not an independent draggable
  axis — only move (top) + width (right edge) handles apply. Persisted via two
  new `LauncherSettings` fields (`quickPanelTileOrder`, `quickPanelTileSizes`),
  following the existing `edgeStripApps` pipe-joined `List<String>` codec idiom.
  Edit-mode state is local `remember` (not a `StartViewModel` `StateFlow` like
  Start's own `editMode`) since it only affects rendering inside the panel
  composable, which fully unmounts on close.
  **(B) Shared handle restyle** (`feed/WidgetSlot.kt`) — the existing generic
  `ResizeHandle`/`DragHandlePill` (used by the feed's real hosted Android
  widgets) were restyled to the approved thin-bar design (replacing a "≡" text
  pill and a rounded-square/dot corner handle) — a new `CornerArcHandle` draws
  the corner as a `Canvas`-drawn quarter-circle stroke arc instead. Deliberately
  upgrades already-shipped hosted-widget resize/move handles too, since they're
  small, self-contained, provider-agnostic composables reused by (C) below — one
  visual language across the whole app instead of two. Every handle's real
  `pointerInput` hit area stays padded to a comfortable ≥40dp regardless of how
  thin the visible bar/arc is, so the touch target didn't shrink along with the
  visuals.
  **(C) Glance page built-in cards** (`WeatherCard`/`AgendaCard`/`NowPlayingCard`,
  `feed/FeedPage.kt`) — folded into the feed's *existing* hosted-widget sizing/
  packing/persistence/edit-overlay system (`feed/WidgetSlot.kt`,
  `feed/WidgetStore.kt`) rather than a parallel one, via three reserved negative
  sentinel `widgetId`s (`BUILTIN_WEATHER_WIDGET_ID` -1 / `BUILTIN_AGENDA_WIDGET_ID`
  -2 / `BUILTIN_NOWPLAYING_WIDGET_ID` -3, `WidgetStore.kt`) — nothing in
  `WidgetCodec`/`packWidgetRows`/`reorderWidgets`/persistence actually requires a
  `widgetId` to resolve to a real `AppWidgetHost` widget, only to be a stable
  unique `Int`, so reuse needed zero schema changes and no duplicated packing/
  reorder logic. `WidgetStore.seedBuiltinsIfAbsent()` (pure core factored out as
  `seedMissingBuiltinWidgets`, unit-tested) is a one-time migration inserting any
  missing built-in ahead of existing hosted widgets, preserving an upgrading
  install's current visual order. New `BuiltinCardView` mirrors `WidgetView`'s
  shape minus everything `AppWidgetHost`-specific (no real provider to resolve,
  so `WidgetEditOverlay`'s `info` param is now nullable, gating only the
  "edit"/reconfigure pill and the width-drag provider-minimum floor) — built-ins
  never join a stack (new `dragged.widgetId < 0 || target.widgetId < 0 -> false`
  merge-candidate guard in `WidgetSection`) and never resize height (new
  `WidgetEditOverlay` params `resizableHeight`/`removable`, both false for a
  built-in — fixed content-sized height, and reorderable/resizable but never
  removable). The **global edit toggle also replaces hosted widgets' old
  per-card "edit" tap-in pill** (`WidgetEditPill`, deleted — dead code once every
  card is globally driven) — one `editMode`/`onEditModeChange` pair, threaded
  from a new "edit"/"done" action in `WidgetSection`'s own header (`SectionHeader`
  gained an optional `secondaryActionText`/`onSecondaryAction`), now drives every
  card (built-in and real) simultaneously — the "single button, no per-tile
  entry point" design applies to the whole glance section, not just the three
  new cards. Now-playing's card keeps its reserved slot in the *persisted* order
  even while nothing's playing — only the render pass filters the sentinel out
  when `nowPlaying == null`, so it reappears in the same relative position once
  playback resumes rather than losing its place; an orphaned half-width neighbor
  just gets its own solo row for that pass, reusing `packWidgetRows`'s existing
  orphan handling. `WeatherCard`/`AgendaCard`/`NowPlayingCard` widened from
  `private` to `internal` so `WidgetSlot.kt` can render them.
  Build + full unit test suite green throughout (new `QuickPanelLayoutTest.kt`;
  `WidgetSlotTest.kt`/`SettingsCodecTest.kt` extended, including sentinel-id
  packing/reordering cases); installed on the physical device with no crash —
  Start screen (icons mode, live weather/music/calendar tiles) verified working
  post-install, but swiping to the glance/feed page to visually confirm the new
  edit-mode UI there wasn't completed this session (adb-synthesized swipes
  weren't registering as a page change — a known class of issue in this
  codebase's own history with ADB-driven drag gestures near screen edges/on the
  pager axis; needs a real finger or further investigation, not yet a confirmed
  app bug). **Manual on-device verification of the actual resize/reorder/handle
  gestures is still pending** — build/tests passing is not itself proof the
  gestures feel right; treat this entry as implementation-complete but
  gesture-unverified until confirmed by hand.
- **`main` — app list didn't pick up the wallpaper-derived accent colour.** User-reported: "when
  accent color is picked up from wallpaper same is not applied to app list screen." Root cause:
  `StartScreen.kt`'s `CompositionLocalProvider` fed `LocalAccent` (consumed by the app list's section
  headers, letter headers, and jump grid for their accent tint — `AppListScreen.kt`'s
  `LocalAccent.current`) the plain global `accent` unconditionally, never the `wallpaperAccent`
  computed a few lines above for `TileColorSource.WALLPAPER_ACCENT` — so switching tile colour source
  to "wallpaper" recoloured Start's own tiles (which read `wallpaperAccent ?: accent` directly) but
  left the app list (and any other `LocalAccent` consumer) on the old accent. One-line fix:
  `LocalAccent provides (wallpaperAccent ?: accent)`, matching the same fallback tiles already use.
  Build + full unit test suite green; installed on the user's physical device and confirmed the app
  list's letters/headers now pick up the same wallpaper-derived tan/gold accent as Start. **Same-day
  follow-up, user-spotted while verifying on-device**: with that light wallpaper accent now correctly
  reaching the app list's jump grid (`AppListScreen.kt`'s `JumpGrid`), its "present" letter tiles were
  still hardcoded to `Color.White` text — fine against the old fixed dark-blue accent, unreadable
  against a light accent colour. Fixed the same way `QuickPanelOverlay.kt`/`FeedPage.kt` already
  pick text colour for an arbitrary accent-filled surface: `Glass.faceTextColor(useDarkText =
  isLightBackground(accent))` instead of a fixed white. Build + tests green; installed on-device.
- **`android-home-style` branch — notification tile content was top-aligned instead of centred.**
  Direct same-day follow-up, user-reported: "though full space is utilised now displayed on top. top
  aligned. it should be centrally aligned." The previous session's fix made bigger notification tiles
  actually use their real height, but several no-picture layouts (`NotificationFaceContentLarge`/
  `XLarge`/`TallMedium`, `feature/livetiles/ConversationTile.kt`) anchored their header+snippet block
  to the top via a trailing `Spacer(Modifier.weight(1f))`, so a short snippet on a tall tile left
  visible empty space below the text instead of centering it; `NotificationFaceContentWide` (now also
  serving `WIDE_MEDIUM`) had the same problem via a fixed `top=28.dp` padding. Fixed by removing the
  trailing spacers and switching to `Arrangement.Center` for the no-picture case (a picture's own
  `weight(1f)` already fills all remaining space regardless of arrangement, so picture-present
  behaviour is unaffected); `Wide` swapped its asymmetric top/bottom padding for symmetric padding +
  `Center`. `Banner`/`WideSmall`/`Medium` were already correctly centred and needed no change. See
  DECISIONS "Notification tile content was top-aligned instead of centred...". Build + full unit test
  suite green; installed on both the emulator and the physical device — same caveat as the prior
  session, no real pending notification was available in the sandbox to visually confirm centring.
- **`android-home-style` branch — non-standard notification tile sizes now use their full available
  space.** User-requested: mail/messages/generic-notification live tiles at sizes other than MEDIUM/
  WIDE/LARGE (the 6 drag-resize-only presets `WIDE_SMALL`/`WIDE_MEDIUM`/`TALL_MEDIUM`/`XLARGE`/
  `BANNER`, plus TALL/COLUMN already handled) all silently fell into `NotificationFaceContent`'s
  `else` catch-all (`feature/livetiles/ConversationTile.kt`), rendering the same fixed cramped
  MEDIUM-sized text regardless of how much bigger/differently-shaped the actual tile was — e.g.
  `XLARGE` (4×4, the biggest tile) showed identical text to `MEDIUM` (2×2). Gave every size its own
  tuned branch: `WIDE_MEDIUM` reuses `WIDE`'s layout (same shape, narrower); new
  `NotificationFaceContentTallMedium`/`Banner`/`WideSmall`/`XLarge` composables scale avatar size,
  font size, and snippet `maxLines` to each shape (up to 18 lines on `XLARGE` vs `LARGE`'s 10; a
  combined "sender: snippet" single line on the too-small-for-two-lines `WIDE_SMALL`). Both
  `ConversationTileFace` (mail/messages) and `NotificationTileFace` (any other app) share this one
  dispatcher, so the fix covers every live notification tile at once. `SMALL` was confirmed to never
  reach this code at all (`LiveFace.forIconKey` returns `null` for `TileSize.SMALL`), so it needed no
  branch. See DECISIONS "Non-standard notification tile sizes now use their full available space."
  Build + full unit test suite green; installed on both the emulator and the physical device — no
  real pending notification was available in the sandbox to resize through and visually verify each
  new size, so this is a code-review-level check against this file's own already-verified MEDIUM/
  WIDE/LARGE patterns, not an on-device visual pass.
- **`android-home-style` branch — real "square" icon shape added; fixed a regression the previous
  masking fix introduced in "original."** Same-day follow-up, user-requested: "last shape is square
  but showing as original... also need option for original icon." Added a genuine 5th `IconShape.
  SQUARE` (`core/data/settings/LauncherSettings.kt`) mapping to `RectangleShape`, keeping `ORIGINAL`
  as its own distinct 5th option — the Personalize swatch row (both preview as a plain rectangle)
  distinguishes them by fill: `SQUARE` solid/accent-filled, `ORIGINAL` outline-only/unfilled. **Caught
  a real regression while verifying "original" on-device**: the prior session's adaptive-icon masking
  fix (below) had made *every* consumer use the raw, un-OS-masked bitmap, including the `ORIGINAL`/
  `HomeStyle.TILES` branch — so "original" no longer showed each icon's true device shape. Fixed by
  giving `MaskableIcon`/`MaskableAppIcon` two bitmaps (`bitmap` = OS-accurate, for ORIGINAL/TILES and
  legacy icons; `unmaskedBitmap` = raw layer composite, only for our own shape-clip branch). See
  DECISIONS "Real 'square' option added; fixed a regression...". Verified on an emulator: all 5 shapes
  (circle/squircle/rounded/square/original) render distinctly correct; original restores true device
  icon appearance. Build + tests green; installed on both the emulator and the physical device.
- **`android-home-style` branch — icon shape row was unlabeled + adaptive icons weren't actually
  masked to the chosen shape.** User-reported with screenshots: home style "icons," icon shape set to
  what looked like "square," but icons kept their native app shapes (WhatsApp circle, Maps teardrop).
  Two bugs, one UX and one real: (1) `IconShape`'s 4th/last value is `ORIGINAL` (deliberately
  unmasked/native-shape), not "square" — no such value exists — but the Personalize swatch row had
  **no text labels**, and `ORIGINAL`'s swatch previews as a flat rectangle, reading exactly like
  "pick this for square." Fixed by labeling each swatch (`circle`/`squircle`/`rounded`/`original`).
  (2) Verifying the fix on-device surfaced a real masking bug: selecting an actual shape (e.g.
  squircle) still rendered adaptive icons fully circular, because `IconCellView.kt`
  (`:feature:start`) and its duplicate `AppListIcon.kt` (`:feature:applist`) called
  `drawable.toBitmap()` directly on the `AdaptiveIconDrawable`, which always clips itself to the OS's
  own device-wide icon mask (a circle on stock AOSP) before our own `IconShape` clip ever runs — so
  re-clipping to a squircle just trimmed an already-circular bitmap. Fixed with the standard
  adaptive-icon re-masking technique: new `unmaskedIconBitmap()` (duplicated in both files) draws the
  icon's raw background/foreground layers directly with no OS mask applied, then our own shape clips
  that genuinely-square bitmap. See DECISIONS "Icon shape row was unlabeled...; adaptive-icon masking
  was silently a no-op." Verified on an emulator: labels render correctly, and selecting "squircle"
  now visibly re-masks adaptive icons (camera/contacts/files/personalize) into soft-rounded squares;
  legacy icons (Chrome/YouTube Music) unaffected, still shown on a shaped tinted plate. Build + tests
  green.
- **Post-v2.5.1 — picking "icons" in the wizard now actually shrinks the default apps to icons.**
  User-reported: picking "icons" still showed a Start screen dominated by big live tiles — 61% of
  `DefaultLayout.DEFAULT_TILES`' ~18 seeded tiles are MEDIUM/WIDE, seeded before the wizard even
  opens, and `setHomeStyle` deliberately never resizes anything (a pure renderer flag). New
  `StartViewModel.shrinkDefaultAppsToIcons()`, called only from `chooseHomeStyle(ICONS)`: resizes
  every top-level app tile with a real package down to SMALL and clears every tile's `gridSlot` so
  the grid re-flows compact instead of leaving holes from STICKY's already-anchored larger
  footprints. `liveOnly` tiles (clock/weather/calendar/personalize) and folders are left untouched.
  Scoped to the one-shot wizard only — a later Personalize toggle is completely unaffected, so it can
  never clobber a customized layout. Verified end-to-end on a fresh emulator install: mostly icons,
  clock/weather stayed live, and — a nice emergent result from composing with the earlier "live icons
  at 1×1" fix, not separately coded — calendar (whose role resolved to a real app on that emulator)
  automatically became a compact live "day of month" mini tile. See DECISIONS "Picking 'icons' in the
  wizard now actually shrinks the default apps to icons." Build + tests green.
- **Post-v2.5.1 — closed folder's mini-grid shows the real app icon in ICONS mode too.** User-reported
  with a real-launcher screenshot: a folder's default apps (contacts/mail/messages) showed the
  generic WP glyph in the closed mini-grid instead of each app's real icon — `FolderChildIcon` had
  never picked up the earlier "icon mode shows the real app icon" fix, since it lives on a separate
  code path (`FolderTileContent`'s mini-grid, not the top-level `IconCellView`). Threaded a
  `homeStyle: HomeStyle = HomeStyle.TILES` param down `TileView` → `FolderTileContent` →
  `FolderChildIcon`; ICONS mode now prefers the real icon whenever the child has a real package,
  matching `IconCellView`'s own rule, while TILES mode's WP-authentic glyph-first look is untouched.
  See DECISIONS "Closed folder's mini-grid shows the real app icon in ICONS mode too." Build + tests
  green. **Two same-day follow-ups on the same screenshot thread**: (1) each mini-grid cell's tinted
  background plate (`FolderTileContent`'s `cellFill`) is now skipped entirely in ICONS mode too
  ("only icon should be shown - dont show inside square") — TILES mode's tinted-square look is
  unchanged, and `IconFolderCell` never had a plate to begin with; (2) the mini-grid icon itself grew
  from 18dp to 26dp in ICONS mode to fill the space the plate used to occupy ("icon size should be
  bigger... as there is no square around") — TILES mode keeps 18dp. See DECISIONS' two follow-up
  entries. Build + tests green.
- **Post-v2.5.1 — first-run home-style (tiles vs icons) choice wizard, real live preview.**
  User-requested, scoped down via `AskUserQuestion` to a single choice screen (no bundled
  restore-backup step, no multi-step flow) — see DECISIONS "First-run home-style (tiles vs icons)
  choice wizard, with a real live preview". Shown once, ever, per device: on a fresh install, and
  once for an existing install upgrading to the version that introduced `HomeStyle` at all, since the
  new one-shot flag (`HomeStyleWizardPrefs`, `tileshell.prefs`, following the exact shape of
  `FirstRunHintPrefs`/`SettingsAppMigration`) is unset in both cases — no versionCode check needed.
  `StartViewModel` gained a `homeStyleWizardOpen` sheet-gate StateFlow (same shape as `aboutOpen`
  etc.), checked in `init{}`, wired into `goHome()`'s close-every-sheet chain. The two option cards in
  new `HomeStyleWizardScreen` (`:feature:start`) are a **real live preview**, not a drawn mockup — per
  explicit user choice, built from fabricated sample `TileModel.App`s rendered through the actual
  `TileView`/`IconCellView` composables (`TileView` widened private→internal for this), restricted to
  iconKeys with zero `LiveFace` mapping so a blank/fake package always takes the safe static-glyph
  path. Drawn last in the overlay stack, suppressing the existing `FirstRunHint` while open so a fresh
  install never sees both at once. Build + tests green; installed on the physical device (no crash in
  logcat), full on-screen verification pending since the device's lock screen blocked a screenshot
  check this session.
- **Post-v2.5.1 — weather/calendar/clock icons stay live at 1×1 in ICONS mode, rendered exactly like
  a tile-mode SMALL tile.** User-requested, a real Android launcher's dynamic calendar/weather icons
  were the explicit precedent — see DECISIONS "Weather/calendar/clock icons stay live at 1×1 in ICONS
  mode — rendered exactly like a tile-mode SMALL tile" for the two-round on-device correction trail
  (a smaller purpose-built first pass was tried and replaced after user feedback). `IconCellView`
  (`:feature:start`) branches on `tile.iconKey` in place of always calling the generic masked icon:
  weather/calendar/clock render as a genuine `accent`-filled, rounded mini tile (new `LiveIconTile`)
  filling the *entire* cell — no label underneath, matching tile mode's own SMALL tile exactly —
  holding the real `WeatherSmallFace`/`CalendarSmallFace`/`ClockSmallFace` composables (`:feature:
  livetiles`) verbatim, same data plumbing and font sizes tile mode already uses. Every other app
  still falls back to the ordinary icon+label ICONS-mode layout at 1×1. `IconCellView` gained an
  `accent: Color` param (wired from the same `tileAccent` already computed for `TileView`) alongside
  `liveActive: Boolean`. Build + tests green.
- **Post-v2.5.1 — icon shape masking extended to the App List.** User-requested: the `IconShape`
  setting (circle/squircle/rounded/original) only masked Start-screen icons (ICONS home style), not
  the App List. `:feature:applist` can't depend on `:feature:start` (dependency graph runs the other
  way), so rather than giving `:core:design` a dependency on `:core:data` to share code (reversing an
  earlier deliberate decision), the masking logic is duplicated in a new `AppListIcon.kt` — gated on
  `homeStyle == HomeStyle.ICONS`, plain unmasked in TILES mode. `AppListViewModel` gained a
  `settings: StateFlow<LauncherSettings>` (mirrors `StartViewModel`). **Caught a real perf bug before
  shipping**: the first pass's plate-colour scan (per-pixel, 96×96 bitmap) ran synchronously on the
  main thread per row — fine on Start's couple-dozen on-screen tiles, not fine on the App List's
  `LazyColumn` of potentially hundreds of apps. Fixed by computing it once in the background icon-load
  coroutine and caching it, never touching the UI thread. Build + tests green.
- **Post-v2.5.1 — widget stacks: any stackable size + explicit "show as stack"/"show as folder"
  toggle.** Direct follow-up on the icons-mode arc, user-requested — see DECISIONS "Widget stacks:
  any stackable size, explicit 'show as stack'/'show as folder' toggle" for the full mechanism.
  `TileSize.stackable` widens stack eligibility from "uniform WIDE or LARGE" to every size except the
  four smallest/thinnest (SMALL/WIDE_SMALL/TALL/COLUMN). Since MEDIUM (the default pinned size) is now
  stackable, `TileModel.Folder.isStack` could no longer stay purely derived from children uniformity
  (an ordinary never-customized folder would auto-become a stack) — `FolderEntity` gained a real
  persisted `showAsStack: Boolean` (schema v6→v7 migration, backfilled `true` for any folder that's
  currently a uniform WIDE/LARGE stack), with `isStack = showAsStack && stackSize != null`. The
  folder-overlay's two fixed "make stack · wide"/"make stack · large" action tiles are now one
  contextual toggle ("show as stack" / "show as folder"), applying identically in TILES and ICONS home
  style. Drag-merge (the default folder-creation gesture) deliberately stays LARGE-only/unchanged, so
  merging two ordinary MEDIUM tiles still forms a plain folder, not a stack. Build + full unit test
  suite green; installed on the physical device over its existing v6 database — migration verified
  clean against real, non-empty data. **Three same-day on-device refinements** (see DECISIONS "Widget
  stacks: three on-device refinements"): `TileSize.stackable` tightened to `cols > 1 && rows > 1`
  (also excludes `BANNER` now, not just the original four); a widget stack's corner controls are now
  identical to a plain folder's (resize icon + colour dot always shown, `StackEditControls` deleted;
  dragging a stack's corner resizes the whole thing via a new `onResizeStack` → `convertFolderToStack`,
  homogenizing every member); and the "show as stack"/"show as folder" toggle moved from a standalone
  action tile next to the expanded folder's children into the per-tile colour picker sheet
  (`TileColorPicker` gained `stackToggleLabel`/`onToggleStack`), with the whole `FolderAction`/
  `FolderActionTile`/`expandedFolderActions` mechanism deleted. Build + tests green.
- **Post-v2.5.1 — Android-style icons home style (`android-home-style` branch, 5-stage arc, not yet
  merged to main).** New user ask, not in the WP prototype/spec: let someone who doesn't want the
  Windows Phone interface turn TileShell into a normal Android-style launcher — shaped app icons,
  folders, free placement — while keeping live tiles and widget stacks on the same screen. Full
  design and rationale in DECISIONS.md ("Android-style icons home style" and its five supporting
  entries); summarized here. New `LauncherSettings.homeStyle: HomeStyle { TILES, ICONS }` is the only
  new top-level flag — layout, persistence, gestures, folders, the app drawer and backup are all
  shared unmodified between the two styles. Icon vs. live tile is derived purely from a tile's own
  `size`: SMALL renders as a shaped icon (`IconCellView`/`IconFolderCell`, new in `:feature:start`);
  MEDIUM+ (live tiles, folders, widget stacks) renders exactly as in TILES mode with zero new code,
  since the whole mixed-content mechanism is one condition at the single `TileView` call site.
  Growing/shrinking a tile across the SMALL boundary is therefore the icon↔live-tile conversion
  gesture — which is why this arc also shipped **gesture-based drag resize** and **eleven total
  `TileSize` presets** (seven drag-only: `WIDE_SMALL`/`TALL`/`WIDE_MEDIUM`/`TALL_MEDIUM`/`XLARGE`/
  `BANNER`/`COLUMN`; the tap-cycle stays on the original four). A new **`FREE` tile arrangement
  mode** (alongside `DENSE`/`STICKY`) is the placement engine ICONS mode defaults to — nothing moves
  unless the user moves it; dropping onto an occupied cell swaps the two tiles rather than pushing
  anything down — and does not reverse `STICKY`'s own "never leave a fully empty row" invariant,
  which stays exactly as it was. Icon masking (`IconShape`: circle/squircle/rounded/original) uses a
  real superellipse (`core/design/Squircle.kt`), not a `RoundedCornerShape` approximation, with a
  genuine adaptive-icon vs. legacy-icon rendering split. Verified on both an emulator (home-style/
  icon-shape rows, real device icons rendering unfilled with wallpaper showing through, and — the
  load-bearing behaviour — a SMALL icon correctly converting to a filled live tile when grown past
  SMALL, persisted after exiting edit mode) and a physical device (Samsung SM-S938B; fresh install,
  edit mode, resize handles and Personalize all confirmed working). **Three on-device follow-up
  fixes after physical-device testing** (see DECISIONS "Icons-mode resize: three on-device fixes
  after real-hardware testing"): (1) the resize gesture — which had grown a two-finger-stretch
  alternative alongside the corner-drag during on-device gesture debugging — was simplified back to
  **corner-drag only** ("corner stetch work well," the two-finger path added nothing); (2) a folder's
  **closed mini-grid children now always render `IconShape.ORIGINAL`**, never the ambient icon shape,
  fixing a "square border around icon in collapsed folder" report traced to a legacy-icon colour
  plate that reads as clutter at 18dp (top-level icons and expanded children are unaffected); (3) the
  two newest presets, **`BANNER` (4×1) and `COLUMN` (1×4), are reachable by drag from both app tiles
  and folder children** — folder children previously had no drag-resize at all
  (`resizeHandlesEnabled` was gated off for them), now removed in favour of a new
  `resizeFolderChildTo` (ViewModel/repository) direct-set write path sharing the existing tap cycle's
  stack-collapse/-promote bookkeeping. Build + full unit test suite green throughout every stage and
  every follow-up fix; new tests: `ResizeSnapTest`, `SquircleTest`, `IconCellShapeTest`, plus
  `GridPackerTest` and `SettingsCodecTest` extensions. **Not yet merged to `main`** — this status
  entry documents the branch's state for continuity, not a shipped release.
- **`android-home-style` branch — narrow live tiles (TALL/COLUMN, 1 column wide) show their data
  stacked vertically instead of clipping.** Direct on-device follow-up, user-reported: drag-resizing
  a clock/weather tile down to 1 column wide (`TALL`/`COLUMN`) clipped their text, since
  `ClockFront`/`WeatherFront`/`CalendarDateColumn`/the shared `ConversationCountFace`/
  `NotificationFaceContent` (mail/messages/generic-notification tiles) only ever branched font size
  on height, never width — only true `SMALL` short-circuited to a compact face. New
  `TileSize.narrowLive` (`cols == 1 && this != SMALL`) is checked inside each face composable (not
  routed to the `SMALL` path — the user wants the full data, just reflowed): centered text,
  width-safe font sizes reused from the existing `*SmallFace` composables, 3-letter weekday/month
  abbreviations, ellipsis safety nets, and `Arrangement.SpaceEvenly` (replacing `Center` + manual
  `Spacer`s) so the lines spread across whatever height the tile has — `TALL`'s 2 rows or `COLUMN`'s
  4 — instead of bunching in the middle. All changes are additive; MEDIUM/WIDE/LARGE rendering is
  unchanged. See DECISIONS "Narrow live tiles (TALL/COLUMN, 1 column wide) show their data stacked
  vertically." Verified on an emulator: drag-resized a weather tile and a clock tile to COLUMN,
  both show every field fully readable and evenly spaced, in and out of edit mode. Build + tests
  green.
- **`android-home-style` branch — tile colour source gains a "wallpaper" option, matching the feed/
  Quick Panel's own wallpaper-derived accent.** User-requested, drawing an explicit parallel: "glance
  and quick settings use their background and gadget/tile from accent picked up from wallpaper.
  similarly keep another color option for tiles to pickup from wallpaper, make provision in
  personalisation with added color tile." `TileColorSource` (`core/data/settings/LauncherSettings.kt`)
  gained `WALLPAPER_ACCENT` alongside `GLOBAL_ACCENT`/`APP_ICON` (codec round-trips it for free by
  name, no migration). Reuses the feed page's existing `rememberFeedPalette` (already called
  cross-package by `QuickPanelOverlay.kt`, so no new dependency) — computed once in `StartScreen.kt`,
  gated by the same `noWallpaper` fallback-to-accent guard the feed/Quick Panel use (`Wallpapers.
  forId("none")` otherwise falls back to the bundled Aurora gradient, which would wrongly tint tiles
  when no wallpaper is actually set), and threaded into the existing per-tile colour priority chain
  (`tileOverride → iconColor → wallpaperAccent → accent`) at `StartPage`'s `tileAccent` and
  `FolderTileContent`'s per-child `cellBg` — `StackTileContent` needed no new param since its own
  fallback chain already ends at the already-wallpaper-aware `accent`. Personalize's "tile color
  source" row gained a third pill with a live swatch dot sampled from the wallpaper (the "added color
  tile"), filling the whole pill when selected like "accent" already does. See DECISIONS "Tile colour
  source: 'wallpaper' option, tiles read the same accent as the feed/Quick Panel." Verified on an
  emulator (via `uiautomator dump` for exact tap coordinates): swatch/tiles show the plain accent with
  no wallpaper set, and all switch together to a picked stock gradient's sampled colour once selected.
  Build + tests green.
- **`android-home-style` branch — tile colour source row: fixed a real bug where the "wallpaper" cell
  wrapped its label vertically, one letter per line.** User-reported with a screenshot. The row was a
  bespoke same-line label+3-pills layout, different from every other selector on this sheet (home
  style, arrangement, wallpaper type) — once "wallpaper" grew a leading swatch dot, its unweighted
  `Text` no longer reliably had enough leftover width and wrapped character-by-character instead of
  overflowing, reading as a tall vertical strip. Rebuilt to match the shared `SettingGroup` + `SegCell`
  convention every other selector already uses (label above, bordered `Row` of equal-`weight(1f)`
  cells below); `SegCell` gained an optional `swatch: Color?` param (the wallpaper preview dot) plus
  an explicit `maxLines = 1` backstop. See DECISIONS "Tile colour source row: real bug — 'wallpaper'
  pill wrapped its label vertically, one letter per line." Verified on an emulator: three equal cells,
  no wrapping in either state. Build + tests green.
- **`android-home-style` branch — the guide and about sheets now describe home style, icon shapes,
  and the drag-resize tile sizes.** User asked directly whether these had been documented — they
  hadn't. `PersonalizeGuideSheet.kt` ("how to personalize") gained a new "home style" `FeatureGroup`
  (with a matching visual: a plain tile swatch next to the four `IconShape` outlines, reusing
  `SquircleShape` from `:core:design`) covering the first-run wizard, the tiles↔icons switch, icon
  shapes, the icon↔live-tile size-boundary conversion, and icons mode's "free" arrangement default;
  "organizing tiles" gained the drag-corner/11-size detail. `AboutSheet.kt`'s "start screen" group got
  the same content in its plain-text convention, plus a "sticky/free/dense" arrangement bullet (a
  pre-existing, also-undocumented setting). Both sheets' duplicate one-line subject summaries (the
  guide's own header, and a second copy in `PersonalizeSheet.kt`'s "how to personalize" nav-row
  subtitle) were updated to mention "home style" too. See DECISIONS "Guide and about sheets never
  mentioned home style, icon shapes, or the drag-resize tile sizes." Verified on an emulator: both new
  sections render correctly (visual + all bullets). Build + tests green.
- **`android-home-style` branch — guide/about docs still described the old "make stack ·
  wide/large" widget-stack mechanism.** Direct follow-up, user-flagged: the "show as stack"/"show as
  folder" toggle moved into the per-tile colour picker sheet, and stack eligibility widened from
  "uniform WIDE or LARGE" to any `TileSize.stackable` size (all but five 1-dimensional presets) —
  neither doc had been updated for that rework. Rewrote `PersonalizeGuideSheet.kt`'s "organizing
  tiles" and `AboutSheet.kt`'s "widget stacks" groups: merging two large tiles still forms a stack
  directly, but any folder with 2+ children at a stackable size can now become one via the colour
  picker's toggle; named exactly which five presets are excluded; added the drag-corner-homogenizes-
  every-member detail; removed the stale "wide ↔ large"/"back to folder" text (that whole mechanism,
  `StackEditControls`, was deleted in an earlier session). See DECISIONS "Guide and about sheets
  still described the old 'make stack · wide/large' widget-stack mechanism." Verified on an emulator.
  Build + tests green.
- **Post-v2.5.0 — feed widget stacks: four fixes from on-device testing.** User-reported after real
  hardware use as two symptoms ("stack position can't be changed", "another widget can not be placed
  next to the stack"), which were four separate defects — see DECISIONS "Feed widget stacks — four
  fixes from on-device testing". (1) **The drag handle was hidden under the action pills**: the
  overlay aligned the handle `TopStart` and the actions `TopEnd` as independent Box children with
  nothing reserving space between them, so once a stack added a third pill ("unstack") on top of
  edit + remove they covered the handle outright on a narrow card and the stack literally could not
  be dragged ("no handle to move on stack"). Now one `Row(SpaceBetween)` with the actions in a
  `FlowRow`, so they wrap instead of encroaching — the same collision the old ↑/↓ reorder buttons
  had with the edit/remove pills on half-width widgets. (2) **Dragging a stack destroyed it**:
  `reorderWidgets` was block-aware but `mergeIntoStack` never was (`out.removeAt(di)` removes one
  widget), so a drop in the wide 22–78% zone ripped the anchor member out, dissolved the rest, and
  merged that member into the target; merging is inherently per-widget, so **a drag starting on a
  stacked widget now never merges, only reorders** (stack+stack stays unsupported). (3) **A widget
  dropped near a stack was absorbed instead of placed beside it**: `isInMergeZone` now takes the
  band as params and the call site tightens it to the centre third (`STACK_MERGE_ZONE_MIN/MAX`,
  0.34–0.66) when the target is already a stack — joining is the rarer intent, so it's the one that
  must be aimed at; loose-onto-loose keeps the normative 22–78% zone. Chosen over dwell-to-merge or
  capping stacks at two members (both offered; user picked the tighter zone as it adds no new
  gesture). (4) **A half-width stack hogged a whole row**: rows are now packed from **cards**
  (`WidgetCard.Solo`/`Stack` via `cardsOf`) rather than raw widgets, so a stack packs on equal
  footing with a lone widget — `WidgetRow` collapsed from three cases to two (`Single`/`Pair`) and a
  half-width stack pairs beside a half-width widget (or another half-width stack). Also fixed while
  verifying: **the card visibly resized as it rotated**, since `halfContentWidthDp`'s provider
  min-width floor was taken from whichever member was showing — `WidgetStackView` now resolves every
  member's info up front (keyed, so slots stay stable) and takes the max, also removing a duplicate
  `rememberWidgetInfo`. Build + tests green (`WidgetSlotTest` 51 cases); fixes 1/4 + width stability
  verified on an emulator at the failing geometry, 2/3 unit-tested but their gestures still need a
  real finger per the ADB drag-synthesis limit below.
- **Post-v2.5.0 — feed widget stacks: merge two hosted widgets into one swipeable card.** New ask,
  not in the WP prototype/spec — see DECISIONS "Feed widget stacks". Brings Start's own widget-stack
  *pattern* (`StackTileContent`) to the feed's real `AppWidgetHostView`s, but not its code, since three
  things differ: (1) **gesture confinement is load-bearing** — swipe-to-flip is granted only to touches
  starting in a 40dp right-edge strip (`WIDGET_STACK_EDGE_ZONE_DP`, where the position indicator sits);
  anything starting elsewhere leaves `awaitEachGesture` without consuming anything, so it reaches the
  hosted widget's own taps/scrolling/buttons exactly as on an un-stacked card (no tap-to-launch or
  long-press-to-edit competing — the existing "edit" pill covers that); (2) **hidden members need no
  keep-alive plumbing** — `AppWidgetHost.startListening()` already caches every *bound* widget's latest
  `RemoteViews` whether or not a view is inflated, so only the visible member is composed and flipping
  back shows current content; (3) **only same-width widgets may merge** (`dragged.halfWidth ==
  target.halfWidth`), mirroring Start's uniform-member-size rule — a mismatched hover is a plain
  reorder, never highlighted as mergeable. Trigger is drag-onto-centre like Start's tile merge:
  `onWidgetDragBy` tests the drop point against the target's inner 22–78% band (`isInMergeZone`, the
  same normative merge zone as the Start grid) and `onWidgetDragEnd` routes to `mergeIntoStack` vs.
  `reorderWidgets`, with an accent outline previewing the merge before release. Grouping is a new
  `stackId: Int?` on `HostedWidget` — members share the founding widget's own `widgetId` and stay
  contiguous in the persisted order, so the packer scans outward instead of re-grouping;
  **no schema migration** (`WidgetCodec` gained a 5th column, written blank when null so an un-stacked
  widget is byte-identical to an older 4-column file — verified on-device against a real pre-existing
  `feed_widget.pb`). `packWidgetRows` now returns a `WidgetRow` sealed type (`Solo`/`HalfPair`/`Stack`)
  and `reorderWidgets` is **block-aware** (a stack moves as one unit, so a reorder can't slice a group
  apart); a group that drops to one member is packed as `Solo` and `WidgetStore.remove` clears a
  stranded survivor's `stackId`, so a "stack of one" can't exist. `WidgetView`'s AndroidView hosting,
  info resolution (the ~15s OEM grace period), and edit overlay were extracted into shared pieces
  (`rememberWidgetInfo`/`WidgetHostedView`/`WidgetEditOverlay`, the last taking live sizes as
  `MutableState` so the resize handles' once-captured gesture callbacks still read current values)
  reused by both the plain card and the new `WidgetStackView`; the overlay's `extraActions` slot is how
  a stack adds "unstack". **Two real bugs found during on-device verification, both in new code:** the
  position indicator was invisible because Start's `fillMaxHeight(0.5f)` idiom resolves to nothing in
  the feed's *unbounded-height* scrolling column (fixed with an explicit `(liveHeight / 2).dp`), and it
  would have been invisible anyway on light widgets since Start's white-on-accent track vanished on
  near-white ones (track is now dark, borrowing the "edit" pill's proven backing). Verified on an
  emulator: one card sized to the tallest member, auto-rotation with the indicator thumb tracking the
  index exactly (sampled 8 frames/32s, never out of sync), overlay acting on the visible member and
  correctly omitting "edit" for a widget with no configure activity, and "unstack" dissolving the pair
  (both `stackId`s cleared, re-rendered as two rows). **Drag-to-merge and swipe-to-flip still need a
  real finger** — both `adb shell input swipe` and `input motionevent` deliver only a single 1–2px move
  to `detectDragGestures` before the gesture ends (confirmed via temporary logging, against correct
  bounds), the same ADB limitation already recorded for the feed's drag-to-reorder work; worth a
  specific look at merging two *side-by-side* half-width widgets, whose mostly-horizontal drag shares
  an axis with the Start↔feed pager. Build + tests green (`WidgetSlotTest`/`WidgetCodecTest`, 26
  new/updated cases).
- **v2.5.0 (versionCode 250) — release cut.** Rolls up everything below: the "hide status bar"
  toggle (default on, with a real display-cutout fix and an auto-rehide timer for the swipe-to-
  peek reveal), the Quick Panel redocked to the top with a device-style status header (clock/date,
  wifi/bluetooth/cellular-or-airplane, colour-coded battery fill) and a shortcuts row below it, the
  swapped two-finger gesture directions (down → Quick Panel, up → quick search, with quick search's
  box moved to the bottom), full-screen Personalize/guide/about sheets, device-status stats removed
  from the glance page entirely (superseded by the Quick Panel header), real drag sliders with a
  tap-to-mute icon for brightness/ring/media volume, the Quick Panel's wallpaper-gradient background
  now respecting the glance page's own "no background" toggle too, haptic feedback throughout Quick
  Panel/quick search/the App List long-press menu, and a new "share a photo into TileShell to set
  it as wallpaper" entry point (`ACTION_SEND image/*` on `MainActivity`, reusing the existing
  import-then-crop wallpaper flow). Signed release APK + AAB built and verified (`apksigner
  verify`/`jarsigner -verify`, real release keystore, not the debug fallback); release notes added
  to `docs/PLAY_STORE.md`. Build + tests green.
- **v2.4.0 (versionCode 240) — release cut.** Rolls up every Post-v2.3.1 change below (Quick
  Panel square-tile redesign + all its on-device refinement rounds, the personalize/settings-
  tile reorganization, the rating prompt, and the new edge-swipe gestures) into a signed release.
  Signed release APK + AAB built and verified (`apksigner verify`, real release keystore, not the
  debug fallback); release notes added to `docs/PLAY_STORE.md`. Build + tests green.
- **Post-v2.3.1 — Quick Panel: media volume moved to extreme right of row two.** Direct
  follow-up, user-requested: row two's media volume tile is now added last (after ring volume),
  so it sits at the row's extreme right. New row two: `brightness, rotation lock, screen timeout,
  ring volume, media volume`. See DECISIONS "Quick Panel: media volume moved to extreme right of
  row two". Build + tests green; installed on the physical device.
- **Post-v2.3.1 — Quick Panel: rotation lock/brightness and volume/screen-timeout swapped.**
  Direct follow-up, user-requested: row two's order (`rotation lock, brightness, screen timeout,
  media volume, ring volume`) had two adjacent pairs swapped — brightness now comes before
  rotation lock, and screen timeout now comes after media volume instead of right after
  brightness. New row two: `brightness, rotation lock, media volume, screen timeout, ring
  volume`. See DECISIONS "Quick Panel: rotation lock/brightness and volume/screen-timeout
  swapped". Build + tests green; installed on the physical device.
- **Post-v2.3.1 — Quick Panel: bluetooth accent bug fix + tile sequence reorganization.**
  User-reported: the bluetooth tile never accent-filled even when bluetooth was actually on (its
  `active` state was hardcoded `false` — a deliberate scoping choice to avoid needing the dangerous
  `BLUETOOTH_CONNECT` permission on API 31+). Fixed with `rememberBluetoothOn()`
  (`:feature:livetiles`), which reads the public, permission-free `Settings.Global.BLUETOOTH_ON` key
  and listens for the unprotected `ACTION_STATE_CHANGED` broadcast — same pattern as the existing
  airplane-mode reader — so the tile now shows real on/off state without adding any new permission;
  it still deep-links to Bluetooth settings on tap rather than toggling directly. Also reorganized
  `quickPanelTiles()`'s order per explicit request: grouped by kind (connectivity → device-mode
  toggles → adjustable levels → dnd → theme → shortcuts) instead of the reference WP photo's literal
  order, with location moved to third in the top row and dnd moved down to sit just before the
  theme tile. See DECISIONS "Quick Panel bluetooth accent bug fix + tile sequence reorganization".
  Build + tests green; installed on the physical device.
- **Post-v2.3.1 — Quick Panel landscape fix: dock to the right half.** Bug fix, user-reported: the
  Quick Panel was the one Start-launched sheet whose call site in `StartScreen.kt` never passed
  `rightHalf = isLandscape` (`QuickPanelOverlay` and `SheetStage` already supported it — every
  sibling sheet already did) — so in landscape it spanned the full two-panel width instead of
  docking to Start's half, squeezing/misaligning its 5-column square-tile grid. One-line fix.
  Verified on a physical device: panel now docks bottom-right, above Start, feed panel undisturbed.
  See DECISIONS "Quick Panel landscape fix". Build + tests green.
- **Post-v2.3.1 — Quick Panel redesigned as true square tiles (real WP Action Center), settings
  tile, screen lock relocated, theme tiles.** New asks, not in the WP prototype/spec — see DECISIONS
  "Quick Panel redesigned as true square tiles..." and its three follow-up entries for the full trail
  (four rounds of live on-device correction — including a reversal: the personalize tile briefly got
  its own distinct `"palette"` icon in round three, then explicitly reverted back to the shared gear
  glyph in round four "to keep consistency," with the real Settings tile distinguished instead by
  showing its actual device-resolved icon). Tile identity was decoupled from icon choice along the way
  (`List<TileModel>.hasPersonalizeTile()` checks `label`, not `iconKey`), and every icon-mapping change
  needed an explicit backfill (`LayoutDao.updateTileIconKey`) for tiles already seeded before it, since
  Room doesn't retroactively apply a new `iconFor` mapping on its own. The App List's synthetic
  "personalize" entry can now be "pinned to start" (in case the real tile is accidentally removed) via
  a dedicated `pinPersonalize()` that avoids the generic pin flow's blank-package dedup bug; hide/
  uninstall stay disabled for it. The real Android Settings app — never actually removed from Start on
  the user's device, only from fresh-install seeding — is unhidden from the App List again
  (`SettingsAppMigration` rewritten from a one-shot hide flag to a one-shot unhide flag). The
  Personalize sheet's "android settings" nav row moved to the top of the sheet with the real
  device-resolved Settings icon and a proper title/subtitle, per explicit request. Quick Panel (`QuickPanelOverlay.kt`) is now one
  unified 5-column grid of true `aspectRatio(1f)` squares — toggles (wifi/bluetooth/flashlight/dnd/
  airplane/location/rotation-lock), brightness/media-volume/ring-volume/screen-timeout (all
  tap-to-step through fixed levels, no more drag sliders), a single tap-to-cycle theme tile
  (dark/light/auto, accent-highlighted), "personalize" (opens this app's Personalize sheet),
  "android settings" (real device icon, opens the real Settings app), and "lock screen". The
  floating corner settings-gear icon is gone — Personalize now opens via a normal, draggable/
  resizable/unpinnable Start tile (`DefaultTile("t-personalize", ..., liveOnly = true)`, same
  blank-package pattern as weather/calendar; existing installs get it backfilled once via
  `LayoutRepository.addDefaultTile`). The real Android Settings app is retired as a separate Start
  pin and hidden from the App List (one-shot `SettingsAppMigration` flag, not just "currently
  hidden" — so a user un-hiding it later isn't silently undone); it's also added as its own
  synthetic, non-installed App List entry so it's searchable/tappable there too
  (`PERSONALIZE_APP_ENTRY`). Screen lock moved into the Quick Panel's own tile, reusing the same
  `onLockScreen` disclosure-dialog flow the removed gear's long-press used. Theme (dark/light/auto)
  also became tiles in Personalize itself (3 square tiles, `ThemeTile`). A real bug was found and
  fixed along the way: volume tap-to-step got stuck because reading back the hardware's coarse
  native step count (often 15 or 7 levels) after writing a target percent rounds to a different
  percent than requested, making the tile think it's still below target forever —
  `rememberSteppedPercent` fixes this by cycling its own remembered state instead of re-deriving it
  from a hardware readback. A single-finger swipe up from either screen edge also now opens the
  Quick Panel, alongside the existing two-finger gesture (`isEdgeSwipeUp`, mirrors the existing
  edge-swipe-down machinery). The About sheet's "features & info" and the Personalize "how to
  personalize" guide (`AboutSheet.kt`/`PersonalizeGuideSheet.kt`, `:feature:personalize`) were updated
  to match this whole redesign — the "quick panel" groups in both now describe the square-tile grid,
  tap-to-step brightness/volume/timeout, the single theme tile, and the personalize/android-settings/
  lock-screen tiles (previously stale, describing the old drag-slider version); "screen lock" now
  points at the Quick Panel's tile instead of the removed corner gear's long-press; "personalization"
  mentions the theme tiles and the top-of-sheet android-settings row; "system shortcuts" mentions the
  new edge-swipe-up gesture; "start screen" notes the personalize tile is a normal, unpinnable-and-
  repinnable Start tile now. `PersonalizeGuideSheet.kt`'s `QuickPanelVisual` illustration swapped its
  old partially-filled slider-bar mockup for a third true square tile showing "60%," matching the
  tap-to-step redesign it illustrates. An earlier draft of this entry (and a matching line in this
  file) incorrectly attributed a Samsung One UI panel appearing during testing to a device/OS-level
  gesture-priority conflict; the user clarified it was their own concurrent interaction with the
  physical device, not a real collision, and both docs were corrected to remove the claim. Build +
  tests green (`PercentLevelTest`, `ThemeChoiceTest` new; `EdgeSwipeGestureTest`, `LayoutSeederTest`
  extended); verified live on both an emulator and a physical device via adb screenshots at every step.
- **Post-v2.3.1 — occasional "enjoying tileshell?" rating prompt.** New ask, not in the WP
  prototype/spec — see DECISIONS "Occasional 'enjoying tileshell?' rating prompt" for the full
  debugging trail. Day-interval gated, not "app open count" (TileShell is the launcher itself, so
  it has no discrete per-launch lifecycle) — `isRatingPromptCheckWindowOpen`/`RatingPromptPrefs`
  (`:core:data`): no ask before 3 days since first launch, then at most one check window every 5
  days, each with only a 30% chance of showing; evaluated on every Start `ON_RESUME`, with the
  "last asked" clock advanced the moment a window opens regardless of the roll's outcome so a
  resume storm can't turn one interval into several rolls. Answering either way stops it forever;
  dismissing without answering lets it resurface next window. "Enjoying it" calls Play's native
  in-app review overlay (`InAppReview.launch`, `:feature:system`, new `play-inapp-review-ktx` dep),
  falling back to a direct Play Store listing deep link only on a genuine request failure — the
  native overlay only ever renders for a Play-channel install (never a plain adb-sideloaded debug
  build, this project's whole local test loop), which is expected Play behaviour, not a bug.
  "Not really" opens a second dialog offering email feedback instead of a dead end. Build + tests
  green (`RatingPromptTest` new, 7 cases); dialogs + the store-listing fallback verified live
  on-device via screenshots.
- **Post-v2.3.1 — single-finger edge-swipe-down opens system notifications (left) /
  quick settings (right).** New ask, not in the WP prototype/spec — see DECISIONS
  "Edge swipe-down for notifications/quick settings". Reuses the existing
  `LockAccessibilityService` (already backs the gear long-press screen-lock and the
  edge-strip's recents button) with two new `performGlobalAction` one-liners —
  `expandNotifications()`/`GLOBAL_ACTION_NOTIFICATIONS` and
  `expandQuickSettings()`/`GLOBAL_ACTION_QUICK_SETTINGS` — both available since API
  16/17 (below minSdk 26), so no version-gating/fallback needed. New
  `EdgeSwipeGesture.kt` (`:feature:start`) is a single-finger sibling of the existing
  two-finger quick-search/quick-panel swipe gestures in `StartScreen.kt`: same
  never-consume-until-triggered shape, but classifies the touch's *starting X
  position* against a 32dp strip on each screen edge (`edgeZoneFor`) rather than
  pointer count, and deliberately isn't restricted to the top of the screen — it
  fires from any height along the left/right edge, per explicit user correction.
  `MainActivity` wires it through the same attempt-then-disclosure-dialog pattern as
  `onLockScreen`/`onRecents`. Build + tests green (`EdgeSwipeGestureTest` new).
- **Post-v2.2.2 — feed widgets: half/full sizing, side-by-side pairing, drag-to-reorder.**
  Direct follow-up to the glance-screen redesign below, on the widget hosting
  `WidgetSection`/`WidgetView` already has (`feed/WidgetSlot.kt`). Previously every
  added widget was forced to the full feed content width, with a narrow
  `isSquareWidget` special case centering square widgets at half width — no way for
  two widgets to sit side by side even though the feed's own built-in weather+today
  row already does exactly that. Replaced with a `halfWidth: Boolean` classification
  on `HostedWidget` (`WidgetStore.kt`, new codec column, tolerant of old 3-column save
  files): a widget defaults to half-row width when its declared natural width is
  comfortably under half the row (`isHalfWidthWidget`, unit-tested), else full width;
  the user can still drag to resize, now flipping between exactly those two sizes
  (crossing the row's midpoint) rather than a continuous custom width. New pure
  `packWidgetRows` (unit-tested) packs the ordered widget list into rows: full
  widgets get their own row, consecutive half widgets pair up (mirroring the
  weather+today `Row`/`weight(1f)` pattern), and — per explicit user correction — a
  half-width widget left without a partner (odd count, or its partner just removed)
  stays at half width on its own row rather than stretching to fill it. **Reordering
  is now drag-and-drop** (a single drag-handle pill, "≡") instead of ↑/↓ buttons: the
  old buttons reordered on every tap but that could reshuffle which row a widget was
  packed into (reparenting its composable), silently dropping it out of edit mode —
  user-reported after the sizing change shipped. Root-caused and fixed two ways:
  (1) the drag only *commits* a reorder once, on release (`onWidgetDragEnd`), not
  continuously while the live hit-target changes — committing mid-drag risked
  reparenting the very composable hosting the drag gesture out from under the
  in-progress touch; (2) `editing` (which widget is in edit mode) is now hoisted to
  `WidgetSection` keyed by widget id instead of `WidgetView`'s local `remember`
  state, so it survives a reorder-triggered reparent regardless. Each widget reports
  its own live on-screen bounds (`onGloballyPositioned`/`boundsInRoot`); dragging
  hit-tests the live drag point against every other widget's bounds, mirroring
  Start's own tile-drag pattern (`reorderTiles`/`onReorderTo`) — a new
  `reorderWidgets` pure function does the same splice-and-reinsert-at-target algorithm,
  unit-tested with the same case shapes as `TileReorderTest`. Also fixed, from the
  same round of on-device bug reports: the edit-mode overlay (scrim + move/edit/remove
  controls + resize handles) used a window-level `Popup`, which doesn't reliably
  track a widget's true position inside a scrolling page — controls could render
  detached from a widget lower on the page, and scrolling *or* reordering while
  editing could silently exit edit mode outright. Replaced with a plain in-place
  `Box` (scrolls/reorders with the rest of the widget's own content, since it's real
  Compose layout, not a separate window) plus an explicit `BackHandler` for back-press
  dismiss (no longer free from `PopupProperties.dismissOnBackPress`). A second reported
  overlap bug — the ↑/↓ buttons colliding with the edit/remove pills on a half-width
  widget, no room for two side-by-side pill groups — is moot now that reordering is a
  single drag handle instead of two buttons. Verified end-to-end on an emulator
  (instrumented logging confirmed bounds-tracking/hit-testing/commit-on-release all
  work correctly; a purely-vertical test drag reordered successfully — a wide/diagonal
  ADB-synthesized test swipe near the screen's left edge kept getting intercepted by
  Android's own system edge-back gesture, a testing artifact of how close the drag
  handle sits to the screen edge, not an app bug). Build + tests green (`WidgetSlotTest`
  new: 19 cases covering `isHalfWidthWidget`/`packWidgetRows`/`reorderWidgets`;
  `WidgetCodecTest` extended for the `halfWidth` column).
- **Post-v2.2.2 — glance screen background: synthesized colour gradient (never the
  raw photo), independent of Start's own wallpaper, plus a real Palette bug fix.**
  Follow-up to the redesign below: the feed's background was showing Start's actual
  (blurred) wallpaper photo, which the user felt competed with the feed's text/cards
  and didn't want. Rebuilt so the glance screen *never* shows the literal photo —
  always a synthesized abstract colour gradient built from the wallpaper's own
  prominent colours (a stock gradient's own layer colours are already abstract and
  pass through unchanged; a custom photo's palette is extracted via
  `androidx.palette`, `feed/FeedPage.kt`'s new `photoGradient`/`rememberFeedPalette`).
  **Real bug found and fixed along the way**: Android's `Palette` can return every
  named swatch (vibrant/muted/dominant/etc.) as null for a near-flat/low-variance
  photo even though `generate()` itself succeeds (confirmed via a synthetic flat-colour
  test photo + temporary logging) — silently falling back to an unrelated stock
  gradient (Aurora's teal) instead of the photo's actual colour. Fixed with a manual
  average-colour fallback reusing the existing `dominantIconColor` helper. Separately,
  **per explicit user request, the glance screen also got its own independent "no
  background" toggle** (`LauncherSettings.feedNoBackground`, new Personalize row under
  "feed & glance") — decoupled from Start's wallpaper choice entirely, since the
  feed is a denser reading surface where a colourful background behind text can be
  unwanted even when the same wallpaper looks fine behind Start's tiles; when on
  (or when Start genuinely has no wallpaper), the feed renders flat `tokens.bg` and
  falls back to the plain global accent for its cards/chrome instead of any
  synthesized colour. Build + tests green (`SettingsCodecTest` extended).
- **Post-v2.2.2 — Personalize: live-tile permission ask explains before jumping to
  settings; consolidated permissions sheet.** Enabling "live tile updates" while
  notification access isn't granted used to jump straight to the system notification-
  access settings screen — user-reported as too abrupt; wanted an explanation of
  *why* (notification access + background activity) with an explicit choice to
  proceed or not first. Now shows an in-app confirmation dialog ("allow live tile
  updates?", listing both permissions) before navigating anywhere; declining just
  leaves live tiles on without requesting access, no forced follow-through.
  `NotificationsPermissionsSheet` (contacts/calendar/location + the old inline
  notification/battery rows) was slimmed to `PermissionsSheet.kt` (contacts/calendar/
  location only) — "badges & live mail" and the battery-exemption row moved into the
  "live tiles" Personalize group directly, alongside the toggle they actually gate.
  Build + tests green.
- **Post-v2.2.2 — feed/glance page + Personalize sheet redesigned to match new
  external mockups ("Metro Reforged"); widgets-on-Start-grid idea researched then
  dropped.** Three-part plan, user-approved via plan mode, worked one part at a time
  on a dedicated branch (`feed-glance-redesign`) so it could be reverted if unwanted:
  **(A) Feed page** (`feed/FeedPage.kt`) — dropped the old glance/news tab switcher
  for one continuous scroll: personalized "good morning, `<name>`" greeting (new
  `userName` setting, auto-seeded once from the device contact profile if granted,
  editable in Personalize — `greetingFor(hour)` unit-tested time-of-day buckets),
  date/clock row, search pill, weather + today's agenda condensed side by side
  (mirrors this session's later half-width widget pairing), now-playing, widgets,
  device status, then news inline with the settings gear moved into its section
  header (the separate quick-filter chip row shipped then was explicitly removed
  again per user request — a separate per-feed region picker already exists).
  Adaptive text colour (`feedFg`/`feedFgDim`) reads the *actual* rendered background's
  brightness rather than assuming dark, after a reported black-on-dark invisible-text
  bug. Fixed a real clipping bug found in the same pass: the feed panel's blurred
  wallpaper bled onto Start because Compose's `graphicsLayer` doesn't clip by default,
  needed an explicit `clipToBounds()`. **(B) Personalize sheet** (`PersonalizeSheet.kt`)
  — reordered/restyled into the mockup's flow (theme collapsed to one flat
  dark/light/auto segmented row, tile-color-source and arrangement/tile-pack-mode
  each collapsed to compact inline segmented pills, tile background + tile style
  groups merged); new `liveTilesEnabled` master on/off switch (folded into the
  existing `rememberLiveTilesActive` gate); the "+ clock/+ weather/+ calendar"
  re-add buttons moved into `CategoryFolderSheet`; new `NotificationsPermissionsSheet`
  and `NewsRegionSheet` sub-sheets (both later revised further — see above/below).
  **(C) Widgets on the Start grid** — traced the full grid-packing/Room/gesture stack
  and planned a `TileModel.Widget` tile kind, but **dropped after discussion**: giving
  widgets arbitrary footprints conflicts with the tile grid's fixed small/medium/wide/
  large visual identity, and tile-stacking's carousel model doesn't carry over to real
  interactive widgets (no shared fixed footprint, no simple glance-content to cycle).
  The one still-relevant idea from that discussion — size a widget to its own
  preferred footprint instead of one fixed width — was redirected to the feed's
  *existing* widget hosting instead (see the half/full-width entry above). Build +
  tests green throughout (`FeedFormatTest` extended for `greetingFor`).
- **Post-v2.2.2 — fixed a real regression: merge-to-folder was silently unreachable
  in sticky (WP-style gap-preserving) tile arrangement mode.** User report: "app merge
  in folder and another app to create folder functionality is lost in windows phone
  style tile arrangement." Two rounds: a first fix (gating the merge-tracking block on
  `inCentre` so the push-down preview is cleared at dwell start) was committed but the
  user reported it still didn't work — "it pushes the destination tile, not allowing to
  stable." **Root-caused for real via on-device diagnostic logging** (temporary
  `Log.d` in `editDragGesture`, `adb logcat`, reproduced by hand): the merge-target
  hit-test (`hovered`) was computed from `othersPacked()`, whose doc comment claims the
  layout is "invariant for the whole gesture" — but in sticky mode that's **false**. It
  packs using the shared `slotOf` closure, which reads `stickyPreview` — the very live
  push-down preview this same gesture rewrites on every tick. So a tile the drag brushed
  earlier stays rendered (and hit-tested) at a *displaced* position even once the finger
  lines up over its true cell; the merge-zone check compares the drag centre against a
  hitbox that has drifted elsewhere and never matches. Fixed by adding
  `othersPackedStable()` — identical to `othersPacked()` but in sticky mode packs from
  each tile's real persisted `gridSlot` (never the live `slotOf`/preview) — and using it
  for merge hit-testing only; the push-down preview itself still works normally for its
  own (drop-onto-occupied) purpose. `TileMerge.computeMerge` and the write path
  (`onDrop(mergeId)`) were always intact; the whole bug was that the hitbox used to
  *detect* a merge target moved out from under the finger. Dense mode was never affected
  (no `slotOf`, so `othersPacked` genuinely is invariant there). The first-round
  `inCentre` gating was kept — it's a correct, complementary guard. **Verified working
  on the user's physical device** after the second fix. All temporary logging removed.
  Build + tests green; gesture-timing/hit-test bug with no unit-test harness for touch
  dwell sequences, so verify by hand in sticky mode: edit mode → drag one tile onto
  another → hold briefly → releases into a folder.
- **Post-v2.2.2 — fixed a real bug: one high-volume region crowded out every other
  selected region's articles.** User report after multi-select landed: "though multi
  select is allowed. feed only loads one country at a time check." Verified by pulling
  the actual `news_feed.pb` off the connected physical device (`adb shell run-as
  com.tileshell cat files/datastore/news_feed.pb`) with India + UK + US all selected:
  every region's feeds *were* correctly enabled and subscribed (multi-select itself
  works), but of the 40 cached articles, 39 were Indian and only 1 was American — zero
  from the UK, even though live-curling the BBC UK feed directly confirmed it fetches
  fine and has recent articles. Root cause: `mergeFeedArticles` (`FeedWork.kt`) did a
  flat global top-40 sort purely by recency with no per-source ceiling — India's 10
  default feeds post so frequently that their own newest articles alone exceed 40,
  so slower-posting regions' articles never survive the cut regardless of how many
  regions are actively subscribed. Fixed by capping each individual feed's
  contribution to `FEED_PER_SOURCE_CAP` (8) newest articles *before* the global
  merge/sort/final-cap — every enabled source now gets a chance to place in the
  cache, however many other high-volume sources are also active. Unit-tested
  directly (`FeedCodecTest`: a 50-article prolific feed no longer fully crowds out a
  3-article slow feed). Build + tests green.
- **Post-v2.2.2 — news-region picker is multi-select, not single-choice.** User
  request: "multiple country selection should be allowed" — until now, tapping a
  region chip in `FeedSettingsSheet` replaced the whole subscribed-feed list with
  that one region's preset (India *or* UK *or* US, never several). `FeedData.region:
  String` → `regions: Set<String>`; `FeedCodec` now writes/reads one `R` line per
  active region instead of at most one. `FeedStore.toggleRegion(code, enabled)`
  replaces `applyRegionPreset`: turning a region **on** additively merges its preset's
  feeds into `sources` (skipping urls already present, so it never disturbs another
  active region's feeds or custom feeds); turning one **off** removes only the urls
  unique to that region's preset — anything still claimed by another currently-active
  region stays. `reconcileDefaults` now reconciles against the **union** of all active
  regions' presets (deduped by url via `distinctBy`), not just one.
  `seedRegionDefaults` seeds a single-element set at first run as before, but now
  explicitly resolves an unlisted/blank device country to `INTERNATIONAL_REGION_CODE`
  rather than storing the raw locale code — a latent gap the old single-`region`
  design had (an unrecognised locale like `"CN"` would've persisted as-is, and since
  the picker only ever renders chips for India/International/the 19 named countries,
  no chip would ever show as selected for that install). `StartViewModel.feedRegion:
  StateFlow<String>` → `feedRegions: StateFlow<Set<String>>`,
  `setFeedRegion(region)` → `setFeedRegionEnabled(region, enabled)`; the
  `FeedSettingsSheet` chip row (`FeedPage.kt`) is now a true toggle set (each chip's
  `on` state = `code in feedRegions`, independent of the others) instead of a
  single-selection switch. Build + tests green (`FeedCodecTest` updated for repeated
  `R` lines / multiple regions round-tripping).
- **Post-v2.2.2 — real image-bearing top-stories source for US/UK/Australia/Canada/
  UAE + a live-verified cleartext bug fix.** Direct follow-up: user asked "google feed
  is without pictures.. will it be useful?" — confirmed by fetching a live Google News
  RSS feed that it truly carries **no per-article image** at all (no `media:content`/
  `enclosure`, description is just a text link list), so every country-preset article
  rendered as a bare text card in `ArticleCard` (whose image block — and the category
  tag chip nested inside it — only renders when `imageUrl != null`). Rather than fixing
  this everywhere, only the 5 requested countries' "nation" slot in `countryFeedSources`
  now overrides to a real, hand-picked source (`CURATED_TOP_STORIES` map in
  `RssFeed.kt`): NYT Home Page (US), BBC UK (GB), ABC News "Just In" (AU), CBC Top
  Stories (CA), Gulf Today (AE) — every one live-curled during this session to confirm
  reachability over https and the presence of `media:content`/`media:thumbnail`/
  `enclosure` or (CBC's case) an inline `<img>` the existing parser already extracts.
  CNN was tried first for the US and rejected: its feed only serves over plain http
  (the https handshake fails outright), which Android's default cleartext policy
  blocks. **That check surfaced a real, pre-existing bug**: `INTERNATIONAL_FEED_SOURCES`
  (added last session) had four BBC feeds on `http://`, which — same cleartext policy —
  would have silently never populated on a real device (`FeedRefreshWorker` wraps the
  fetch in `runCatching`, so a `CleartextNotPermittedException` there just degrades to
  "no articles," with no visible error); switched all to `https://`, and separately
  found + fixed a wrong URL path (`entertainment_arts` → `entertainment_and_arts` —
  the old one 302-redirected to a 404 over https). Added a permanent regression test,
  `all built-in feed source urls are https`, over every built-in `FeedSource` (India +
  international + all 19 country presets) so this class of bug fails the build next
  time instead of silently degrading on-device. See DECISIONS "Feed sources must be
  https and dead-link verified" + "Curated top-stories override for the highest-value
  country presets." Build + tests green (`RssFeedTest` extended).
- **Post-v2.2.2 — feed region picker expanded from India/International to ~20
  named countries.** Direct follow-up to the locale-aware region entry below,
  after the user asked for "default country + select other countries" rather
  than just a binary choice. Rather than hand-curating a source list per
  country (dead-URL risk, real curation effort for 20 countries), `RssFeed.kt`
  adds `SELECTABLE_COUNTRIES` (19 major markets — US/UK/Australia/Canada/
  Germany/France/Japan/Brazil/Singapore/UAE/Pakistan/Bangladesh/South Africa/
  Nigeria/Indonesia/Philippines/Mexico/Italy/Spain) with feeds **generated**
  per country via `countryFeedSources(countryCode)`: five Google News RSS
  URLs (`googleNewsFeed`, private — plain top-stories + BUSINESS/TECHNOLOGY/
  ENTERTAINMENT/SPORTS topic sections, `hl` pinned to `en-US` since the app's
  UI/parsing assumes English throughout, only `gl`/`ceid` vary by country) —
  zero manual curation, since it's all Google's own domain. `defaultFeedSourcesForCountry`
  now routes India → its existing rich 10-feed list, any `SELECTABLE_COUNTRIES`
  code → `countryFeedSources`, anything else (including the manual
  `INTERNATIONAL_REGION_CODE`/unresolved locale) → the existing generic
  `INTERNATIONAL_FEED_SOURCES` fallback — `FeedStore`/`StartViewModel`'s
  region-seeding and reconcile logic needed **no changes**, since they already
  treated the region as an opaque string key. `FeedSettingsSheet`'s two-chip
  india/international row (`FeedPage.kt`) is now a `FlowRow` of ~21 chips
  (India, International, then each named country by `regionDisplayName`),
  wrapping onto multiple lines. Build + tests green (`RssFeedTest` extended:
  `countryFeedSources` URL/category shape, `regionDisplayName`, and
  `defaultFeedSourcesForCountry` routing updated now that named countries like
  US/GB no longer fall through to the generic international list).
- **Post-v2.2.2 — news feed gets a locale-aware region preset (India vs.
  international), first step toward feed-placement ad monetization.** User
  context: before adding AdMob native ads to the feed, wanted the feed itself
  localized beyond the hardcoded 10 India RSS sources — both for relevance to
  non-India users and because ad eCPM is far higher outside India. `RssFeed.kt`
  gains `INTERNATIONAL_FEED_SOURCES` (BBC World/Entertainment/Sport/Tech/
  Business + Google News global edition + NYT Food, tagged with the same
  `FEED_CATEGORIES` as the India list; "state"/"cricket" have no international
  equivalent and are simply left empty) and a pure `defaultFeedSourcesForCountry
  (countryCode)` (India → `DEFAULT_FEED_SOURCES`, everything else →
  international; unit-tested). `FeedData` gains a persisted `region: String`
  field (`""` = unresolved; round-tripped by `FeedCodec` as a new `R` line,
  omitted from output when unset so existing exports don't grow a stray line).
  `FeedStore.seedRegionDefaults(deviceCountryCode)` resolves the region **once
  per install** from `Locale.getDefault().country` (called from
  `StartViewModel.init`, before `reconcileDefaults` — order matters, since
  reconcile now reads the resolved region): a no-op if the region was already
  set (never re-seeds after a manual choice or later travel), and only
  replaces `sources` wholesale when they still exactly match the built-in
  India default (i.e. genuinely never customized) — otherwise it just records
  the region so future reconciles target the right preset, leaving any
  existing customization untouched. `reconcileDefaults` itself was a **latent
  bug fix**: it always diffed against the India list regardless of region, so
  a hypothetical international install would have had every India feed
  silently re-added as "missing" on first reconcile — now diffs against
  `defaultFeedSourcesForCountry(current.region)`. A manual **"news region"**
  chip pair (india / international) in `FeedSettingsSheet` (`FeedPage.kt`)
  calls the new `StartViewModel.setFeedRegion(region)` → `FeedStore
  .applyRegionPreset`, replacing the subscribed list outright — the
  user-facing override for whenever the locale guess is wrong, with no
  confirmation dialog (matches this project's existing un-confirmed reset
  actions, e.g. "reset tile style"). Deliberately scoped to **exactly two**
  presets this session (India + one generic international set), not a
  per-country library — extend `INTERNATIONAL_FEED_SOURCES`/add more presets
  only when a specific country is actually requested. **Ad monetization
  itself (AdMob native ads in the feed) is explicitly out of scope this
  session** — this was step one of that plan; adding the SDK, AdMob
  registration/app-ads.txt, UMP consent flow, and the Play Data Safety +
  Accessibility-disclosure updates it requires are still to come. Build +
  tests green (`RssFeedTest`/`FeedCodecTest` extended).
- **Post-v2.2.2 — closed folder's mini-grid shows a per-app badge alongside the
  existing aggregate.** User-requested: a closed folder tile already summed its
  children's notification counts into one badge (`TileView`'s `badgeCount`),
  but couldn't tell you which app the count came from. `FolderTileContent`'s
  icon mini-grid now also draws a small `FolderChildBadge` (shrunk
  `NotificationBadge` pill) on each child cell whose package has a pending
  count > 0, via a newly-threaded `NotificationSnapshot` (`TileView` →
  `FolderTileContent`). The folder's own aggregate badge is unchanged — this is
  additive. Out of scope (already covered by the existing per-tile badge logic,
  since both render children as full `TileView`/`AppTileContent` tiles rather
  than mini-grid cells): the widget-stack carousel and the inline-expanded
  folder view. See DECISIONS "Closed folder's mini-grid shows a per-app badge,
  not just the folder's total." Build + tests green.
- **Post-v2.2.1 — second Accessibility API rejection: the demo video, not the
  disclosure text, was the actual problem.** After the v2.2.0 disclosure fix
  (below), Play rejected the resubmission again under the same policy, but
  this time flagged only **Calendar events** and **Contacts** as missing from
  the prominent disclosure — even though `AccessibilityDisclosureDialog`
  already itemized both, at positions 2–3 of a 6-item list. Confirmed with
  the developer: reviewers grade this from the demo video required in the
  Play Console submission, not by running the app themselves, and the
  uploaded video scrolled past the calendar/contacts bullets too quickly to
  read while the other four items happened to stay on-screen long enough.
  **v2.2.2 (versionCode 222)**: reordered the six-item list so Contacts and
  Calendar come first, tightened the wording so less scrolling is needed
  overall, and split the dialog's one giant concatenated string into three
  `Text()` calls matching its actual sections — a defensive improvement, not
  the real fix. **The real fix is process, not code**: re-record the
  disclosure-dialog walkthrough video, scrolling slowly and pausing on every
  bullet (especially Contacts and Calendar), and upload it with this build
  when resubmitting. See DECISIONS.md "Second Accessibility API rejection."
  Build green.
- **Post-v1.9 — Play Console rejection fixed: accessibility prominent
  disclosure now itemizes all data the app collects.** Google Play rejected
  a release with "Accessibility API policy: Insufficient data use declaration
  in the prominent disclosure," flagging that the existing
  `AccessibilityDisclosureDialog` (`MainActivity.kt`) only described what the
  Accessibility Service itself does (nothing — it's `performGlobalAction`
  only) and never mentioned the app's other data practices, even though Play
  requires the disclosure shown before the Accessibility Settings redirect to
  explain *all* data the app collects. Rewrote the dialog to itemize
  approximate location (weather), calendar events, contacts, notification
  content, the installed-apps list (inherent to being a launcher), and the
  locally-tracked "recent apps" tap history — each with its purpose and
  whether it leaves the device — plus a "Privacy policy" button linking to
  the hosted policy (`https://vivek-sovani.github.io/tileshell/`, matches
  `docs/PRIVACY_POLICY.md`). Verified on a physical device: dialog renders,
  scrolls, and all three buttons (Go to Settings / Privacy policy / Not now)
  work. **Still needed before resubmitting**: bump `versionCode`/`versionName`
  (Play won't accept a re-upload at the same versionCode) and build/upload a
  new signed release; separately, review the Play Console Data Safety form —
  it flagged "Precise location" and "Page views and taps in app," but the app
  only ever requests `ACCESS_COARSE_LOCATION` (never fine/precise), so that
  checkbox may be a stale/incorrect declaration worth correcting there.
- **Post-v1.9 — sticky-mode drag-drop onto an occupied cell now pushes it
  down instead of rejecting the drop.** Direct user follow-up on the sticky
  (gap-preserving) tile arrangement below: dropping a dragged tile onto a cell
  that already held another tile used to silently snap back to the drag's
  start (`editDragGesture` only ever set a placement when the target cell was
  entirely free) — the only way to actually place a tile somewhere occupied
  was to first clear a free cell by hand. Real WP makes room instead: the
  occupant is pushed down, the same way growing a tile via resize already
  displaces a neighbor, but this must never turn into a full dense-repack
  auto-arrange (the whole point of sticky mode is that unrelated tiles never
  move). Fixed by extracting `StartViewModel.stickyResizeSlots`'s push-down +
  empty-row-collapse body into a shared `stickySlotsForPlacement(movedId,
  size, targetCol, targetRow)`, called both by resize (target = the tile's own
  current cell) and by the drag-drop write path, `setTileGridSlot` (target =
  wherever the drag was released — replacing the old `collapseEmptyRowsAfterMove`,
  which repositioned only the dragged tile with no push-down, silently
  overlapping two tiles if the target was occupied). `editDragGesture` no
  longer computes a "is the cell free" check at all — it always reports the
  cell under the finger; occupied-or-not is resolved entirely on the write
  side. Verified on an emulator (`adb shell input swipe` drag one medium tile
  onto another's cell + `uiautomator dump` bounds checks): the dropped tile
  lands exactly where released, the displaced tile(s) cascade down the minimum
  amount needed, no overlaps, no fully-empty row left standing, and unrelated
  tiles (folder, clock, tiles in the other column) are untouched. Dropping
  onto a genuinely free cell is unaffected. See DECISIONS "Sticky-mode
  drag-drop onto an occupied cell pushes it down, instead of rejecting the
  drop." Build + tests green.
- **Post-v1.9 — folders: inline expand-in-place replaces the modal
  FolderOverlay.** Deliberately
  deferred during the tile-arrangement session below ("we will do gap
  preserving grid first, folders later"); user requested it as a direct
  follow-up. Tapping a folder no longer opens a full-screen overlay — it
  expands in place on the Start grid: the folder tile becomes an up-arrow
  placeholder at its existing cell, its children appear as extra rows directly
  below it (pushing everything further down to make room), and tapping the
  placeholder again collapses it. New pure `GridPacker.expandFolderInline`
  applies this as a render-time-only transform *after* the normal
  `pack`/`packSticky` computation (added as an optional `postProcess` hook to
  both `DenseTileGrid` and `editDragGesture`) — the expanded folder's own
  placement never moves, its children pack as their own local block right
  below it, everything at/below that row shifts down, and nothing is
  persisted, so it works identically in dense or sticky mode and collapsing
  is free. Children get synthetic ids (`folderChildTileId`) and render as a
  stand-in `TileModel.App` (`FolderChild.asTileModel`), so they flow through
  the *exact* same `TileView`/`AppTileContent` rendering, corner controls, and
  accessibility semantics as any pinned app — no parallel rendering path.
  Resize, colour-picker, and pull-out-to-Start all work on expanded children
  (routed via a new `folderChildRef(id)` lookup at the three spots that
  already existed for top-level tiles); merging is disabled while any folder
  is expanded (a child is never a valid merge target). **Deliberately not
  carried over**: drag-to-reorder within an expanded section, rename, and the
  "make stack" chip — the existing `order` list never contains synthetic child
  ids, so a drag-lift on a child harmlessly snaps back rather than corrupting
  anything, but it's not full parity with the old overlay yet. `FolderOverlay`
  + its exclusive helpers (`StackModeChip`, `FolderTitleEditor`) are deleted
  outright. Verified on an emulator via both a screenshot and cross-checked
  `uiautomator dump` accessibility-tree snapshots: expand shows the up-arrow +
  children inline with neighbors undisturbed, collapse cleanly reverts (dump
  matches the pre-expansion state exactly), no crashes. See DECISIONS "Folders:
  inline expand-in-place replaces the modal FolderOverlay" for the full
  mechanism. Build + tests green (`GridPackerTest` extended, 304 total).
- **Post-v1.9 — tile arrangement: dense repack vs. WP-style gap-preserving grid
  (user-selectable, awaiting on-device verification before commit).** User
  checked a real Windows Phone device: unlike this launcher's always-repack
  dense grid (`GridPacker.pack`, mirroring the prototype's CSS
  `grid-auto-flow: dense`), real WP leaves a gap open where a tile was
  removed until the user drags something into it. Added a new "tile
  arrangement" toggle in Personalize (`LauncherSettings.tilePackMode`:
  `STICKY` default / `DENSE` — user later asked for sticky to be the
  out-of-the-box default now that it's verified), scoped to the top-level
  Start grid only this
  session — folder overlays are deferred to a follow-up session that also
  replaces the modal `FolderOverlay` with real WP's inline-expand-in-place
  folder model (per the user's own request/sequencing). New nullable
  `TileEntity.gridSlot: Int?` (schema v5→v6 migration) holds an anchored
  absolute grid cell, independent of the 4/5/6 column-count setting;
  `GridPacker.packSticky` renders anchored tiles at their stored cell and
  auto-places never-anchored tiles after the frontier row (new tiles always
  append at the bottom in both modes — confirmed as real WP's own behaviour
  too). `editDragGesture` gained a sticky variant (drop onto any free cell
  instead of list-splice reorder). `StartViewModel.resize`'s collision handling
  went through two user-reported wrong turns: blocking the resize outright
  (growing failed almost everywhere in a normally-packed layout), then
  un-anchoring the colliding tile entirely (it flew to the bottom of the grid
  instead of staying nearby). Landed on **push-down**
  (`StartViewModel.stickyPushDown`): a colliding tile shifts straight down —
  same column, cascading to whatever it in turn newly overlaps — so two
  adjacent smalls, one resized to medium, leave the other sitting directly
  below instead of teleported away. Two more on-device-reported fixes in the
  same uncommitted pass: **(1)** a *fully* empty row (no tile touching any
  column) is now never left standing — new pure `GridPacker.collapseEmptyRows`
  (unit-tested) shifts everything below a vacated row up to close it, wired
  into drag-drop, resize (after push-down settles), and unpin; a *partial*
  row gap (some columns empty) is still fine, only a wholly empty row
  collapses. **(2)** edit-mode tile-switch: user reported tapping a different
  tile while editing exited edit mode instead of switching the selection to
  it. Root-caused with temporary `Log.d` calls + `adb shell input tap`
  reproduction on an emulator: `editDragGesture`'s tap handling never called
  `change.consume()`, so a plain tap-release was *also* seen by the sibling
  `emptySpaceExit` gesture (attached higher up), which exits edit mode on any
  unconsumed, un-moved release — it fired right behind the correct
  selection-switch and undid it every time. Fixed by consuming the change
  whenever a tile (not empty space) is tapped. Verified on-device: switching
  tiles now stays in edit mode; tapping the same tile (or real empty space)
  exits, as intended. **(3)** a related, separate bug in the same corner-control
  hit-test: the unpin/resize/colour zone checks were one-sided thresholds
  against the *selected* tile's edges with no bound tying them to actually
  being inside that tile — e.g. `x <= r.left + zone` matched any x all the way
  to the screen edge, not just near that corner — so tapping a *different*
  tile up-left or down-right of the selected one could misfire unpin/resize/
  colour on the previously-selected tile ("many times it opens colour palette
  or resizes or removes the tile"). Fixed by requiring
  `r.contains(down.position)` before any zone check. **(4)** a fourth
  on-device-reported bug in the same pass: resizing a tile in sticky mode
  "finds first available space on top or bottom instead of expanding in
  place" — but *only* when the tile was on the right with another tile to its
  left, never on the left. Cause: `stickyPushDown` bailed out entirely
  whenever the tile's own anchored column didn't leave room for the wider
  size — the common case for any tile not at column 0 growing to WIDE (needs
  the full grid width from *any* other starting column). With no push/collapse
  computed, the DB grew the tile's size at its old column, `packSticky`
  couldn't place that oversized cell there, and silently re-flowed it via its
  unanchored-tile fallback — reads exactly as "teleports to the top/bottom."
  Fixed by shifting the tile's own *effective column* left just enough to fit
  (`col.coerceAtMost(columns - w)`) instead of bailing out, so a former
  left-neighbor inside the shifted footprint gets pushed down like any other
  collision (`StartViewModel.stickyResizeSlots`, replacing `stickyPushDown`'s
  model/nextSize-driven variant with a lower-level box-collision helper).
  Verified on an emulator: camera (column 1, phone at column 0) resized
  straight to WIDE now shifts to column 0 and pushes phone down a row, instead
  of jumping to the bottom of the grid. User then asked for sticky to be the
  **fresh-install default** (was `DENSE`) — but reported the very first launch
  still behaved like auto-arrange, only starting to work correctly after
  toggling the setting off and back on. Cause: gap preservation depends on
  tiles having an anchored `gridSlot`, and the "anchor every currently-unslotted
  tile at its present cell" step (`seedStickySlots`, extracted out of
  `setTilePackMode`) only ever ran as a side effect of an explicit user
  toggle — never merely because sticky was already the active mode. A fresh
  install's default layout starts every tile unanchored, so with nothing ever
  anchored, `packSticky`'s fallback degenerates to the same append-only scan
  `pack` uses — plain auto-arrange, indistinguishable until a toggle
  round-trip happened to seed everything at once. Fixed by also calling
  `seedStickySlots` once at `StartViewModel` init, right after
  `repository.seedIfEmpty()`, whenever the persisted setting is already
  `STICKY`. Verified with `pm clear` on an emulator: unpinning a tile on a
  truly fresh install now leaves the gap open on the first try. See DECISIONS
  "Sticky mode wasn't actually active until the setting was toggled off and
  on", "Windows-phone-style tile arrangement is now the default on a fresh
  install", "Sticky-mode resize: shift the growing tile's own column instead
  of bailing out", "Corner-control zones weren't bounded to the selected
  tile's own rect", "Sticky mode: a full empty row is never allowed;
  edit-mode tap-to-exit fix", and "Tile arrangement: user-selectable dense
  repack vs. WP-style gap-preserving grid" for the full set of WP-faithful
  calls made and the debugging trail. Build + tests green
  (`GridPackerTest`/`SettingsCodecTest` extended, 298 total); each fix
  verified individually on an emulator via `adb shell input` taps/swipes +
  screenshots (not just code review). Also installed on the user's physical
  device for real-hardware testing (required uninstalling a differently-signed
  prior build first, with the user's explicit go-ahead, since Android won't
  update over a mismatched signature — wiped that device's existing layout).
  **Committed and pushed** (`feat(start): windows-phone-style gap-preserving
  tile arrangement`).
- **v1.9.0 (versionCode 100) — glass tiles now tint by their own accent + wallpaper blend
  retuned.** Follow-up in the same pre-release polish pass, after on-device testing of the
  light-theme wallpaper fix below turned up two more issues. (1) **Wallpaper blend was too
  strong**: the light-theme base/layer blend (82%/30% toward the light bg/white) washed the
  gradients almost all the way to a flat light colour, losing their identity — retuned to 45%
  (base) / 12% (layers) in `Wallpapers.kt`'s `themedBase`/`themedLayer`, which keeps a
  recognisable mid-tone version of each gradient instead of near-black or near-white. (2)
  **Glass (transparent tiles) never tinted by the tile's own colour, in either theme** —
  `Glass.fill(dark, transparency)` always returned one of two fixed neutral colours (dark
  charcoal / near-white) at an alpha derived from the transparency slider, so every tile —
  whatever its resolved accent — rendered the identical grey/white glass square; a real bug,
  not a WP-fidelity choice (deliberate deviation from the prototype, which is accent-blind
  here — see DECISIONS "Glass tint follows tile accent"). `Glass.fill` now takes an `accent:
  Color` param and blends it 65% into the neutral frost before applying alpha. Fixing this
  required moving the fill computation from once-per-screen (`StartScreen.kt`'s top-level
  `glassFill`) to per-tile: `StartPage`/`TileView`/`StackTileContent`/`FolderTileContent` now
  take `glass: Boolean` + `transparency: Float` instead of a precomputed `Color?`, and each
  composable computes its own tint from its own already-resolved accent (`tileAccent`,
  `memberAccent` per stack member, `cellBg` per folder-mini-grid cell) — so a blue tile, a red
  stack member, and a folder cell with its own override colour each render their own tinted
  glass instead of one shared value. Build + tests green; verified signed release APK + AAB
  (versionCode 100/1.9.0) built off this state, release notes added to `docs/PLAY_STORE.md`.
- **Pre-release polish — wallpaper light-theme adaptation + banding fix.** User-reported ahead
  of production release (closed testing complete): the 6 bundled gradient wallpapers
  (`Wallpapers.kt`, `:core:design`) are dark-base-first by design, so in light theme the
  near-black base showed through unchanged wherever a glow layer hadn't reached — most of the
  gaps between tiles read as flat black instead of the light theme's `#ece9e4`. Fixed
  algorithmically rather than hand-authoring 6 new palettes: new `themedBase`/`themedLayer`
  helpers blend each gradient's base and each glow layer toward the light theme's tone (see the
  v1.9.0 entry above for the final, retuned blend amounts) via a new `dark: Boolean = true`
  param on `wallpaperBackground`/`wallpaperWindow` (default preserves the personalize picker's
  swatch previews, which intentionally always show the dark identity look). `TiledScreenDark`
  (a hardcoded `#0A0A0D` used by "wallpaper behind tiles" mode's screen fill and both
  `photoWindow` `darkBase` call sites) is removed entirely in favour of
  `colorTokens(darkTheme).bg`, so tiled mode now respects theme too. Also fixed a separate,
  unrelated polish issue on the same gradients: each radial layer's 2-stop color→transparent
  falloff banded visibly on 8-bit panels; added a third half-alpha mid-stop to smooth it. See
  DECISIONS "Wallpaper theming: light-theme adaptation + gradient banding fix." Build + tests
  green.
- **Post-S27 — LARGE tile allowed on 4-column grids too.** User-requested: dropped the
  `columns >= 5` gate on the 3×3 LARGE size — `AppCategories.allowsLargeTile` now always returns
  `true` (a 3-wide tile still fits inside the minimum 4-column grid). Removed the now-dead
  `demoteLargeTiles` (`LayoutRepository`/`LayoutDao`) and the `setColumns` call that shrank every
  LARGE tile back to MEDIUM whenever the grid dropped to 4 columns — no column transition needs
  that anymore. `StartViewModel.resizeFolderChild` and the folder overlay's resize-indicator check
  in `StartScreen.kt` (both previously hardcoded `columns >= 5`) now call
  `AppCategories.allowsLargeTile` too, so folder children get the same treatment. See DECISIONS
  "LARGE tile allowed on 4-column grids too." Build + tests green (updated `AppCategoriesTest`).
- **Post-S27 — widget stack: swipe-to-flip confined to a right-edge zone (supersedes an
  in-session long-press-then-drag attempt).** Known issue on the widget-stack tile
  (`StackTileContent`, `StartScreen.kt`): its manual member-cycling gesture used to detect a vertical
  drag at plain touch-slop, immediately consuming the touch — since the Start grid's own scroll is on
  the same (vertical) axis, any swipe that started on a stack tile always flipped the stack instead of
  scrolling the screen. A first attempt moved the drag-to-flip capture to after the tile's long-press
  timeout (so a plain swipe always bailed unconsumed); on-device testing found this felt sluggish —
  requiring a ~430ms hold before a flip-drag engages reads as unresponsive for a gesture users expect
  to be instant. Replaced with a **spatial** fix instead of a *temporal* one: the gesture now only
  grants instant, no-wait vertical drag-to-flip to touches that start within `STACK_EDGE_DRAG_ZONE_DP`
  (40dp) of the tile's right edge — the same corner as the existing position indicator, so the
  affordance and the hit zone line up. A touch starting anywhere else on the tile (the vast majority of
  its area) never captures vertical movement at all: it bails unconsumed the instant it exceeds
  touch-slop, so the Start grid's own scroll always wins there, and only supports tap-to-launch /
  long-press-to-select (no drag-to-flip fallback in the main body — that's the edge zone's job now).
  Net effect: flipping is still an immediate one-finger swipe, just anchored to a small corner instead
  of the whole tile, so an ordinary scroll swipe starting on the tile body is never intercepted. Tap,
  auto-rotate, and long-press-to-edit are unchanged. Both attempts landed under v1.8.1 (versionCode 91)
  — the long-press-then-drag version was never tagged/shipped separately. Build + tests green.
- **Post-S27 — Play Store update check + prompt on Start.** New ask, not in the WP prototype/spec
  — see DECISIONS "Play Store update prompt on Start". Uses Google Play Core's In-App Updates API,
  **flexible flow only** (never immediate/blocking — TileShell is Home, so a full-screen update
  takeover over the launcher isn't acceptable). `rememberAppUpdateState()` (new
  `AppUpdateChecker.kt`, `:feature:system`, which gained a Compose dependency for this) wraps
  `AppUpdateManagerFactory`: checks `appUpdateInfo` on first composition and every `ON_RESUME`
  (mirrors `rememberNotificationAccess`'s re-check pattern), registers an
  `InstallStateUpdatedListener` for background-download progress, and exposes a plain
  `(AppUpdateState, () -> Unit)` pair — `NONE`/`AVAILABLE`/`DOWNLOADING`/`READY_TO_INSTALL` — with
  no Play Core types leaking past the module boundary. `UpdateAvailableBanner.kt` (`:feature:start`,
  new one-directional `implementation(project(":feature:system"))` dependency, same pattern as
  `:feature:applist` → `:feature:livetiles`) renders a thin dismissible strip pinned to the top of
  Start (not a `FirstRunHint`-style full-screen scrim, since this can recur every session) — "a new
  version of tileshell is available · update" while downloading, "update downloaded — restart to
  finish · restart" once ready (taps `completeUpdate()`, which restarts the app). Silent while
  `DOWNLOADING`. Gated off (`showUpdateBanner` in `StartScreen.kt`) whenever edit mode, the app
  list, an open folder, personalize, or quick search is showing. New `com.google.android.play:
  app-update-ktx` dependency (`playAppUpdate` version catalog entry); no new manifest permission
  (reuses the existing `INTERNET` grant). Build + tests green.
- **Post-S27 — notification package alias for OEM companion-service splits.** Debugged on a
  physical Samsung device: a "gallery" widget stack (Samsung Gallery + Google Photos, both
  pinned as LARGE tiles) wasn't showing a pending Gallery notification. Root cause: Samsung's
  Gallery "story"/highlights feature posts its notifications under a separate companion package
  (`com.samsung.storyservice`), not the Gallery app's own package (`com.sec.android.gallery3d`)
  — every notification-to-tile match in this app is by exact package name, so it silently found
  nothing. Fixed with a small, explicit alias table (`NOTIFICATION_PACKAGE_ALIASES` in
  `TileNotificationListenerService.kt`, `:feature:livetiles`): `StatusBarNotification.packageName`
  is remapped through it (`tilePackageName()`) before being grouped into badges/previews/images,
  so the story notification now surfaces on the Gallery tile. The remap only touches the grouping
  key — `NotificationActionRow.key` (used to actually cancel the notification on tap) still holds
  the real underlying key, so tap-to-open/clear is unaffected. Deliberately a tiny hardcoded table,
  not a heuristic — extend only for a confirmed, specific OEM split, per DECISIONS. Build + tests
  green.
- **Post-S27 — quick search follow-up: call/message/pin-to-start on contacts, recent/suggested.**
  Three additions to quick search (a fourth, photo search, was built then deliberately removed —
  see below), all UI-only (see DECISIONS "Quick search follow-up"):
  (1) **contact quick actions** — long-press a contact result (450ms, mirrors the app list's
  pin gesture) for a menu: "call"/"message" (`ACTION_DIAL`/`ACTION_SENDTO`, only shown once a
  number resolves via new `ContactsSource.primaryPhoneNumber`) and "pin to start"; a plain tap
  still opens the contact card. (2) **pin a contact to Start** — `LayoutRepository.pinContact`
  stores it as a plain `TileModel.App` with a blank `packageName` (like the weather/calendar
  liveOnly tiles) whose `activityName` encodes the contact's identity (new `ContactTile.encode`/
  `decode`, `:core:data`, unit-tested round-trip) — no schema change, and the tile gets merge/
  resize/drag/accent-override for free by reusing the App tile machinery. `AppTileContent` gained
  a `ContactTileFace` (photo full-bleed + name over a bottom scrim, or the "people" glyph over
  the tile's normal accent when there's no photo) and `onTileClick`/`launchFolderChild` reopen the
  contact card instead of the liveOnly weather/calendar fallback. Fixed a latent merge-dedup bug
  this surfaced: `TileMerge.mergeKey()` keyed blank-package tiles on `iconKey` alone, which is fine
  for weather/calendar/clock (one of each) but every contact shares the `"contact"` iconKey, so
  merging two would silently drop one — now also keys on `activityName`. (3) **recent searches +
  suggested apps** — new `RecentSearches` DataStore (`:core:data`, mirrors `RecentApps`) records
  each acted-on query (not abandoned/cancelled ones); before typing anything the overlay now shows
  up to 5 recent queries (tap to reuse, "×" to remove) and up to 5 frequently-launched apps (reuses
  `AppListFilter.topApps`, already tested) instead of being blank. **Photo search removed**: it
  shipped once (`MediaSearch.kt`, `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`) but Google Play's
  Photos and Videos Permissions policy requires a Play Console declaration to request either of
  those for Play Store distribution — deliberately dropped rather than take on that obligation for
  a personal-launcher feature; contacts/apps/web don't carry that requirement (`READ_CONTACTS`
  isn't one of Play's restricted-permission categories). Build + tests green (new
  `ContactTileTest`).
- **Post-S27 — quick search (two-finger swipe-down): apps, contacts, web.** New gesture entry
  point on Start, not in the WP prototype/spec — see DECISIONS "Quick search". A two-finger
  swipe-down (tracked in `StartScreen.kt`'s outer `pointerInput`, pure threshold check
  `isQuickSearchSwipe` in `QuickSearchGesture.kt`, unit-tested) opens `QuickSearchOverlay.kt`
  (`:feature:start`) — a top-slide-down overlay (mirrors `AboutSheet`'s bottom-sheet animation,
  direction negated) with a search box that shows up to 5 matching apps (`AppListFilter.filter`,
  reused as-is), up to 5 matching contacts (new `ContactsSource.searchContacts`, `:feature:livetiles`,
  via `ContactsContract.Contacts.CONTENT_FILTER_URI` — same lookup as Dialer/People), and a
  "search the web for '&lt;query&gt;'" row (reuses `launchWebSearch`, widened to `internal`).
  Tapping an app launches it (`AppLauncher.launch`); tapping a contact opens its contact card
  (`ContactsContract.Contacts.getLookupUri` + `ACTION_VIEW`). No new permission — contacts
  reuses the existing `READ_CONTACTS` grant (the people tile's), degrading to a single "allow
  contacts access" row when denied; apps/web still work either way. Gated off during edit mode,
  an open folder, or any personalize sub-sheet (`StartViewModel.searchOpen`/`openSearch()`/
  `closeSearch()`, closed from `goHome()`, same convention as `aboutOpen`/`backupOpen`). Hidden
  apps are excluded from the apps section. Build + tests green (new `QuickSearchGestureTest`).
- **Post-S27 — unpinned apps with pending notifications surface in the app list's "recent" section.** A notification badge is only ever visible on a Start tile — an app with a pending notification that isn't pinned to Start had nowhere to show it. `AppListViewModel` now derives `pinnedPackages` (top-level tiles + folder children, from `LayoutRepository.tiles`) and `notifiedPackages` (`NotificationCenter.snapshot.badges.keys - pinnedPackages`), folded into the existing `topApps` combine (now 5 flows) and passed to `AppListFilter.topApps`'s new `notifiedPackages` param: after the up-to-5 recent + up-to-5 newly-installed apps, any not-already-shown notified app is appended (pure, unit-tested — `AppListFilterTest`). `AppListScreen.kt`'s recent-section `AppRow` gained a `badgeCount` param rendering a small accent-coloured count pill (top-end of the icon, mirrors Start's `NotificationBadge`) and its `onTap` now calls `NotificationCenter.openAndClear(context, app.packageName)` first — opens the notification's content intent and clears it, falling back to a plain `AppLauncher.launch` when nothing is pending — matching the existing Start-tile tap pattern (`StartScreen.kt`'s `onTileClick`/`launchFolderChild`). `:feature:applist` gained a new `implementation(project(":feature:livetiles"))` dependency for `NotificationCenter` (one-directional; `:feature:livetiles` doesn't depend back). Build + tests green.
- **Post-S27 — hide / unhide apps from the app list.** Long-press an app row for a new "hide" action alongside pin/uninstall (`DropdownMenu` + `CustomAccessibilityAction`, `AppListScreen.kt`). Hidden packages persist in a new `HiddenApps` DataStore (`:core:data`, mirrors `RecentApps`) and are excluded from both the alphabetical list and the "recent" section (`AppListViewModel`'s `filteredApps`/`topApps` combines); hiding toasts with a pointer to where to undo it. A new "hidden apps" row under personalize's "app visibility" group opens `HiddenAppsSheet` (`:feature:personalize`) — lists every hidden app with a "show" action, following the existing `AboutSheet`/`BackupRestoreSheet` sub-sheet pattern (`StartViewModel.hiddenAppsOpen`/`openHiddenApps()`/`closeHiddenApps()`, closed from `goHome()`). Verified end-to-end on an emulator: hide → gone from list + toast → personalize → hidden apps → show → reappears in the list. Build + tests green.
- **Post-S27 — gallery live tile flips to show pending notifications.** The photos/gallery tile (`LiveFace.PHOTOS`, `flips=false`) resolves to the real gallery package via `APP_GALLERY` but never surfaced its notifications even though `NotificationCenter` already had the data. `PhotosTileFace` now self-flips (independent of the shared random flip scheduler, which still never touches this tile) between the photo slideshow and an Android-notification-style back face whenever the tile's package has an active notification (~4 s photos / ~3 s notification while `active`, gated to settle back on the slideshow when the live gate pauses); with nothing pending it behaves exactly as before. When no photos are picked at all there's no slideshow to flip back to, so a pending notification renders directly (no flip) instead of falling back to the static glyph. Build + tests green.
- **Post-S27 — wide tile stacks + folder overlay per-tile colour, full resize cycle.** `TileModel.Folder.isStack`/`stackSize` generalized: a folder is now a stack whenever every member is uniformly **WIDE or LARGE** (previously LARGE-only), so any folder can become a stack via the overlay's new "make stack · wide"/"make stack · large" shortcuts (plus a "keep as folder" bail-out) instead of only by merging two large tiles by hand. Folder children also gained a **full resize cycle** (small→medium→wide→large on 5/6-column grids, small↔medium on 4 cols, mirroring top-level tiles) and their own **colour picker** (`LayoutDao.updateFolderChildAccent`, same picker sheet pattern as top-level tiles) — previously folder children could only toggle small/medium with no palette control. Follow-up fixes landed the same session: stack members now keep their own per-tile colour while rotating (`StackTileContent` previously painted only the folder's own colour once behind the whole carousel, ignoring each member's `accentOverride`); merging a `liveOnly` tile (weather/calendar/clock with no resolved app — all share a blank identity) into an existing stack no longer silently drops it (`computeMerge` deduped by `"package/activity"`, colliding every liveOnly tile onto the same key; blank-package children now dedup on `iconKey` instead); calendar/weather tiles no longer clip their enlarged text at WIDE size (the "big text" sizing was keyed off `size == WIDE`, but WIDE and MEDIUM share the same 2-row height — only true LARGE has the extra room). Also fixed tapping a `liveOnly` folder child (weather/clock) from the mini-grid/overlay, which always attempted a direct `AppLauncher.launch` and errored instead of falling back to the live-tile intent top-level tiles already use. Build + tests green (new `TileModelStackTest`/`TileMergeTest` cases). Tagged `v1.5.1` (versionCode 61).
- **Post-S27 — app list search now clears on launch/back.** The search query typed into the app list persisted indefinitely across open/close cycles because `AppListScreen` is never disposed (the pager just translates it off-screen; see `renderAppList` in `StartScreen.kt`), so its default-scoped `AppListViewModel` — and its `_query` StateFlow — stayed alive with the old text. Fix: `AppListViewModel` gains `resetQuery()`; `AppListScreen.kt`'s two launch-tap handlers (`AppRow` `onTap` in the "recent" section and the alphabetical list) call it right before `AppLauncher.launch(...)`, and a new `visible: Boolean = true` param on `AppListScreen` drives `LaunchedEffect(visible) { if (!visible) viewModel.resetQuery() }`. `StartScreen.kt` now collects `viewModel.isAppList` (the *committed* open/close flag set only by `settleTo`'s post-animation call and the `homeRequests` collector — deliberately not the continuously-updating drag-derived `appListShown`, to avoid resetting mid-swipe) and passes it as `visible` into `AppListScreen`. Covers all three paths: tapping an app to launch it, back button/gesture, and swipe-back-to-Start. Verified on-device: search "camera" → tap Camera → home → reopen list → empty; search "weather" (no match) → back → reopen list → empty. Build + tests green.
- **Post-S27 — backup & restore polish: cloud-save hint, about entry, accent-tinted first-run hint.** `BackupRestoreSheet.kt` gains a tip below the export/restore rows: "save the exported file to google drive (or another cloud folder) so it's there to restore on your next device." `AboutSheet.kt` gains a "backup & restore" `FeatureGroup` (auto-save, layout history, manual snapshot, export/restore, the Drive tip) — the feature previously had no entry in "what you can do" at all. `FirstRunHint.kt` now takes an `accentId: String` param (`StartScreen.kt` passes `settings.accentId`) and tints its bold gesture-hint spans + "got it" action with the resolved accent colour instead of a fixed lavender-gray, so the card visually ties to the (now blue-by-default) tiles behind the scrim rather than reading as a generic dark dialog — matches the existing convention (`WallpaperNavRow`/`PermissionRow` etc. already colour their action text with `accent`). Verified on-device with a fresh install. Build + tests green.
- **Post-S27 — classic Nokia-blue first-run defaults + feature-brief hint.** A fresh install previously landed on the "aurora" gradient wallpaper with glass (translucent) tiles; changed `LauncherSettings` defaults (`core/data/settings/LauncherSettings.kt`) to `wallpaperId = "none"` (flat theme-bg fill, mirrors `Wallpapers.NONE_ID`) and `glass = false`, so first launch now renders solid `accentId = "blue"` (`#2B78E4`) tiles on a plain dark background — the classic Nokia Lumia/WP8 Start screen look. `accentId` itself was already `"blue"`/`#2B78E4`, so no change needed there; per-tile `colorId` on seeded seed tiles is legacy/unread for rendering (global `accentId` is the only colour input absent an explicit override), so this one settings-default change is sufficient — no seeding code touched. Only affects genuinely fresh installs (no settings file yet); existing users' persisted choices are untouched. Also expanded `FirstRunHint.kt` (`:feature:start`) with a new `featureText` paragraph (live tiles, feed, folders/widget stacks, personalization) shown above the existing gesture-hint text in the same one-time first-run card — still a single dismissible overlay, no new architecture. Verified end-to-end with `pm clear` to simulate a true fresh install. Build + tests green.
- **Post-S27 — one-sheet-at-a-time personalize navigation.** Sub-sheets opened from `PersonalizeSheet` (about/folders/backup) and from `BackupRestoreSheet` (layout history) used to stack visibly — since each sheet is less than full height with a full-screen scrim, the parent sheet stayed dimly visible behind/above the child, looking cluttered. `StartScreen.kt` now derives `personalizeVisible = personalizeOpen && !aboutOpen && !foldersOpen && !backupOpen` and `backupVisible = backupOpen && !historyOpen`, passed as each sheet's `visible` param instead of the raw ViewModel flag. A parent slides away the moment a child opens and slides back in when the child closes — for free, via each sheet's existing `BackHandler(enabled = visible)`, so Android back naturally unwinds one level at a time. Verified on-device. Build + tests green.
- **Post-S27 — backup & restore extracted into its own sub-sheet.** `PersonalizeSheet` had grown too long; the "backup & restore" group (layout history nav, auto-save toggle + frequency pills, save-now, file-transfer divider, export/restore rows — ~85 lines) is now a separate `BackupRestoreSheet.kt` (`:feature:personalize`), following the same pattern as `AboutSheet`/`LayoutHistorySheet`: its own slide-up sheet with a `ViewModel`-owned `backupOpen: StateFlow<Boolean>` + `openBackup()`/`closeBackup()` (`StartViewModel.kt`, alongside `aboutOpen`/`historyOpen`; wired into `goHome()`). `PersonalizeSheet` now just shows a single "manage backups ›" nav row (`onBackupRestore` callback) in its "backup & restore" `SettingGroup`, matching the existing "folders"/"about" row style. `WallpaperNavRow` (previously `private` in `PersonalizeSheet.kt`) is now `internal` so the new sheet file can reuse it. `StartScreen.kt` composes `BackupRestoreSheet` as a sibling to `AboutSheet`/`LayoutHistorySheet`, closing both `personalizeOpen` and `backupOpen` before `onSaveSnapshot` triggers the PixelCopy capture (so neither sheet is in the screenshot). Verified end-to-end on an emulator: sheet opens/stacks/closes correctly, "layout history" nav from within it opens `LayoutHistorySheet` on top showing the existing snapshot with its thumbnail. Build + tests green.
- **Post-S27 — auto-backup screenshot cache.** Auto-backup snapshots (`LayoutAutoBackupWorker`, `:feature:livetiles`) previously always had `screenshotPath = null` — the worker is a headless WorkManager job with no Activity/Window, and `captureSnapshotJpeg`'s `PixelCopy.request(activity.window, ...)` (`StartScreen.kt`) can only read from a live, on-screen window, so it could never be called from there (not a screen-on/off toggle — off-screen capture is structurally impossible). Fix: a new `CachedScreenshotPrefs` (`:core:data`, backed by the existing `tileshell.prefs` SharedPreferences) caches the most recent screenshot path + the layout's `contentHash` at capture time. `StartScreen` now opportunistically captures on `ON_PAUSE` (window still attached/visible then, unlike `ON_STOP`) via a `DisposableEffect` on the lifecycle, throttled to once per 10 min (`CachedScreenshotPrefs.claimAttempt`) since `ON_PAUSE` fires on every app switch; skipped mid-edit. `StartViewModel.cacheForegroundScreenshot` computes the hash and saves it, deleting the previously-cached file unless a saved history entry still references it. `LayoutAutoBackupWorker.doWork()` now calls `CachedScreenshotPrefs.pathFor(context, hash)` — reuses the cached screenshot only if its hash matches the layout being backed up right now, else stays `null` (a stale thumbnail would misrepresent the snapshot). Build + tests green.
- **Post-S27 — widget stack (merge two large tiles).** Any app can now be resized to LARGE on a 5/6-column grid (`AppCategories.allowsLargeTile` dropped the media/news restriction → `columns>=5`). Dropping a LARGE tile onto another LARGE tile (or onto an existing stack) forms a **widget stack**: a 3×3 folder whose members all stay LARGE, rendered as a swipeable carousel of full-size live tiles instead of an icon mini-grid. "Stack" is a **derived render mode**, not stored — `TileModel.Folder.isStack = children.isNotEmpty() && children.all { size == LARGE }`, so **no DB migration**. `computeMerge` keeps members LARGE + tile LARGE only when both sides are stackable (LARGE app or existing stack); any other merge is a normal folder (members clamped to MEDIUM), which is also the reversion path. `StackTileContent` (`StartScreen.kt`) renders the current member via the existing `AppTileContent` at the tile's size (reusing every live face — music/notifications/etc.), auto-rotates every `STACK_ROTATE_MS` (3 s, gated by `liveActive`/edit/single-member); members **slide vertically** (`AnimatedContent`, direction = travel) so each reads as a distinct tile, with a thin right-edge scroll indicator. **Management via folder overlay**: tapping a stack (like any folder) opens the standard `FolderOverlay` — members can be dragged out (pull-out → re-pins to Start via `removeFolderChild`), reordered, or renamed; no resize/colour controls (`TileControls` gated off via `isStackTile`; `StartViewModel.resize` already no-ops for stacks). Reversion: resizing any member down (`resizeFolderChild`→`dao.collapseStackToFolder`: all children MEDIUM, tile WIDE) or merging in a non-large tile collapses it. Stack tile resize is a no-op. Edge: a 4-col `demoteLargeTiles` shrinks the stack tile to MEDIUM (renders as a smaller stack; one-way). Build + tests green (new `TileMergeTest`/`AppCategoriesTest` cases). See DECISIONS "Widget stack".
- **Post-S27 — landscape two-panel layout.** In landscape (`isLandscape` = `LocalConfiguration.orientation == ORIENTATION_LANDSCAPE`) the feed↔Start swipe is dropped and both render as side-by-side panels in a `Row` (50/50 `weight(1f)`): feed left (always `active=true`), Start right at half width (`widthPx/2f`) so the responsive grid stays portrait-sized instead of ballooning. The app list slides in over the **Start panel only** (its slide Box lives inside the right panel, translating by `panelWidthPx`). The `pager` val became `fun pagerModifier(pageWidthPx, lower)` so each layout drives the gesture with its own width + lower bound (portrait −1 reaches the feed; landscape 0). Page bodies hoisted into `renderStartPage(pageWidthPx)`/`renderAppList()`/`renderFeed(active)` composable lambdas shared by both layouts. Feed-off landscape fallback: Start centred at capped portrait width (`min(widthPx, 460dp)`), app list full width. `LaunchedEffect(isLandscape)` clamps `progress` to ≥0 on rotation. Caveat: tiled-wallpaper window mapping uses panel width as full screen (cosmetic, landscape + tiled mode only). Build + tests green. See DECISIONS "Landscape: two-panel layout".
- **Post-S27 — per-tile accent colour + tile colour from app icon.** Each tile can carry its own colour override independent of the global accent. In edit mode, tapping the palette icon on a selected tile opens an inline colour picker (`colorPickerFor` state in `StartScreen`) showing: the exact dominant colour extracted from the app's launcher icon, the nearest palette accent to that colour, and all 14 accent swatches. Stored as `accentOverride: String?` on `TileModel.App`, `TileModel.Folder`, and `FolderChild`; persisted via `LayoutRepository.setTileAccent` → `dao.updateTileAccent`. Render priority per tile: `accentOverride` → app-icon dominant colour (when `TileColorSource.APP_ICON`) → global `settings.accentId`. `rememberDominantIconColor(packageName, activityName)` decodes the launcher icon off-thread and extracts the dominant hue. `accentOverride` is preserved through folder merges, folder dissolves, and child pull-outs. A **"tile colour from app icon"** toggle in PersonalizeSheet switches `TileColorSource` between `GLOBAL_ACCENT` and `APP_ICON`; icon-derived colour is suppressed when a per-tile override is set. Folder overlay also uses each child's `accentOverride` (fixed in recent commits).
- **Post-S27 — adjustable tile spacing.** `tileGap: Float` added to `LauncherSettings` (default 3 dp, range 0–16 dp, persisted in the flat DataStore codec). A "tile spacing" slider in `PersonalizeSheet`'s tile style group passes the value as `tileGapPx` to `DenseTileGrid` and `editDragGesture` on the Start screen. Spacing is suppressed (forced to the prototype default) when "wallpaper behind tiles" is on, so wider gaps never fragment the show-through wallpaper. The folder overlay now also inherits `tileGap` from settings (folder overlay gap fix, same session as personalisation reorganisation).
- **Post-S27 — personalisation UX fixes.** Two fixes to `StartScreen` + `PersonalizeSheet`: (1) **folder overlay gap** — `FolderOverlay` now accepts `tileGap: Float` passed from `settings.tileGap` at the call site; `tileGapPx` is computed from density and forwarded to both `editDragGesture` and `DenseTileGrid` inside the overlay, so the folder grid respects the user's spacing setting instead of always using the prototype default 3 px. (2) **personalisation sheet reorganisation** — sections reordered into a logical flow (theme → accent colour → wallpaper → tile style → typography → grid columns → live tiles → live photos → folders → permissions → notifications → system → about); the formerly ungrouped glass/blur/tiled-wallpaper toggles and the standalone "tile transparency" section are merged into the "tile style" group (transparency slider sits directly below the transparent tiles toggle); corner radius slider gains a "corner radius" label; "reset tile style" moves to the bottom of the tile style group; "reset start layout" row and its `onResetLayout` parameter are removed entirely; "folders" moved above "permissions". Build green.
- **Post-S27 — bing daily wallpaper.** New "bing daily wallpaper" toggle in `PersonalizeSheet`'s wallpaper group. When on, `BingWallpaperWorker` (`:feature:livetiles`, mirrors `WeatherRefreshWorker`) fetches Microsoft's image-of-the-day JSON (`HPImageArchive.aspx?format=js&idx=0&n=1&mkt=<locale>`), downloads the JPEG into `filesDir/bing_wallpaper.jpg`, and writes its `file://` URI (with a cache-busting `?v=<ts>` query) into `customWallpaperUri` via `SettingsRepository.setBingImage` — so the existing custom-wallpaper render path shows it with no new rendering code. New `bingWallpaper: Boolean` flag in `LauncherSettings`/`SettingsCodec`; selecting a gradient/own-photo/none clears it. Scheduling: daily periodic (24 h, network constraint, KEEP) + immediate one-off on enable; `ensureScheduled` re-armed from `StartScreen` on every launch while on, `cancel` on disable. Pure `parseBingImageUrl`/`bingMarket` unit-tested; codec round-trip test extended. **Adjust + history (follow-up):** the wallpaper group gains a **"recent bing wallpapers › browse"** row opening `BingHistorySheet` (`:feature:start`) — a slide-up grid of the last ~8 days (`fetchBingImages` merges idx 0/8, `parseBingImages` resolves full+thumb URLs, `rememberRemoteImage` loads thumbs); tapping one calls `BingWallpaperWorker.applyImage` (input-data `KEY_IMAGE_URL` → downloads + `setCustomWallpaper`, pinning that day and turning daily mode off). And an **"adjust position › reframe"** row (shown when a photo/Bing image is active) reopens `WallpaperCropOverlay` seeded from the saved focal point, writing alignment only via `setWallpaperAlignment` (image/daily-mode untouched). `setBingImage` no longer resets alignment, so framing persists across daily refreshes. The bing-off path now also `cancel`s the worker from `StartScreen`. Pure `parseBingImages`/`bingDateLabel` unit-tested. **Build run on GitHub Actions CI (`.github/workflows/build-apk.yml`) — `dl.google.com` is blocked in the dev sandbox (403), so verify Gradle there or locally.**
- **Post-S27 — about screen.** `AboutSheet` added to `:feature:personalize` — a slide-up bottom sheet (same animation + tokens as `PersonalizeSheet`) with two sections: **what you can do** (7 feature groups: start screen, feed & news, widgets, live tiles, personalization, screen lock, accessibility — bulleted plain-language list) and **built with** (dev stack cards + module list + © footer). Version reads live from `PackageManager.getPackageInfo` — no BuildConfig needed. Triggered via a new "about · features & info ›" row at the bottom of `PersonalizeSheet` (`onAbout` callback → `StartViewModel.openAbout()`/`closeAbout()`, `aboutOpen: StateFlow<Boolean>`). Build green.
- **Post-S27 — screen lock.** Long-press the settings gear icon on the Start screen to lock the device. API 29+: `LockAccessibilityService` issues `GLOBAL_ACTION_LOCK_SCREEN` — preserves biometric unlock. API 26–28 fallback: `LockAdminReceiver` (DevicePolicyManager.lockNow()) — clears PIN. Two-path setup: accessibility service settings or device admin activation. Wired in `StartScreen` as `onLockScreen` callback on the gear long-press. See recent commits (`feat: long-press settings gear to lock screen`, `fix: use AccessibilityService lock to preserve biometric unlock`).
- **Post-S27 — feed page (glance + news).** Third pager position (−1, swipe right from Start). `FeedPage` composable in `:feature:start` hosts two tabs: **glance** (Google search pill, date + live 12-hour clock, `WeatherCard` with live Open-Meteo data, `AgendaCard` for upcoming calendar events, `NowPlayingCard` with album art + prev/play-pause/next transport controls, widget slot) and **news** (RSS article list with thumbnail, source, time-ago via `ArticleCard`). `FeedSettingsSheet` manages per-category + per-feed toggles and custom URL entry. `feedEnabled` setting (default on); when disabled the pager hides the −1 page entirely. Media poll interval on feed: 1.5 s (vs 2 s on Start). Start suspends live-tile flip scheduler while the feed is the foreground page.
- **Post-S27 — RSS/Atom feed engine.** `RssFeed.kt` is a pure RSS 2.0 + Atom parser (javax.xml DOM, namespace-unaware). Extracts title, link, source/channel, category, image, and published time. Helpers: `parseFeedDate` (RFC-822 + RFC-3339), `stripHtml`, `feedAgo` (now/Xm/Xh/Xd). `FeedStore` persists articles + per-feed settings in a DataStore (`news_feed.pb`, tolerant tab-delimited codec). `FeedRefreshWorker` (CoroutineWorker) runs as a unique 30-min periodic job + immediate one-off; dedupes by link, newest-first, caps at 40 articles. 10 India feeds seeded by default: The Hindu, NDTV, Indian Express, Gadgets 360, TOI Tech, ESPNcricinfo, NDTV Sports, Moneycontrol, ET Markets, NDTV Food + Google News aggregator. 8 categories: nation, state, entertainment, cricket, sports, tech, business, food, custom. Remote image loading via `HttpURLConnection` (follows redirects, upgrades http→https, browser-like User-Agent, `LruCache`). All guarded with `runCatching`. Feed categories + custom feed URLs configurable from `FeedSettingsSheet` in the personalize sheet and directly from the feed tab header.
- **Post-S27 — Android widget hosting.** `FeedAppWidgetHost` (custom `AppWidgetHost`) lives on the feed/glance tab. `FeedWidgetHostView` detects long-press without blocking widget tap events. Supports multiple widgets (list of `HostedWidget(widgetId, heightDp)`). In-app widget picker dialog shows provider preview + label; binds via `ACTION_APPWIDGET_PICK` or `ACTION_APPWIDGET_BIND` fallback. Optional configure activity invoked after binding. Vertical resize by drag (±24 dp steps, 72–720 dp range). Long-press reveals an edit overlay (edit/remove buttons + drag handle). Default heights from provider `minHeight` clamped 96–480 dp (taller for calendar/collection widgets). Persisted in `WidgetStore` DataStore (`widget_store.pb`). Uninstalled providers self-remove on next load (error-guarded).
- **Post-S27 — tile style personalization.** `PersonalizeSheet` exposes three additional style controls: **corner radius** slider (0–20 dp, stored as `cornerRadius: Float` in `LauncherSettings`, clamped to 0..20f by `SettingsCodec`, injected via `LocalTileCornerRadius`); **gradient fill** toggle (flat solid accent vs per-tile gradient, `TileFill.FLAT`/`GRADIENT`, stored as `tileFill`); **typography** selector (System / Outfit / Nunito, `FontStyle` enum, injected via `LocalTileFont`). All three settings are persisted in the flat Proto DataStore codec alongside existing keys and applied live without restart.
- **Post-S27 — tiled wallpaper (show-through) mode.** Toggled via "wallpaper behind tiles" in personalize. When on, the background is a solid dark fill (`#0A0A0D`) and each tile draws its wallpaper as a screen-anchored window — gradient layers shift by `−tileOrigin` so the wallpaper appears fixed to the screen while tiles scroll over it (parallax-correct). `wallpaperWindow()` handles gradients; `photoWindow()` handles custom photos (cover-scaled, clipped to the tile rect). A 1 px hairline (`#66000000`) separates adjacent windows. Accent tiles still render their per-tile colour in tiled mode. Glass mode is suppressed when tiled wallpaper is active.
- **S27 — accessibility + compatibility (release candidate, tag `v0.9`).** TalkBack: each tile is a single `clearAndSetSemantics` button (name + unread count; launch/open `onClick`) with the drag-only edit ops as `CustomAccessibilityAction`s (resize/unpin/move back-forward/done; activate = select; "customize" enters edit); edit label adds size + selection. `AppRow` gains launch `onClick` + "pin to start" custom action. 48dp targets (chevron 40→48, folder close 34→48 + real `clickable`, edit-bar `defaultMinSize(48,48)`). Animations-off: `rememberJigglePhase` returns 0 when `ANIMATOR_DURATION_SCALE==0` (transient anims already honour `MotionDurationScale`; flips gated by `liveActive`). `displayCutoutPadding()` on Start + app-list columns. Font scale ✓ to 1.3×. RTL: standard layouts mirror via `LayoutDirection`; the dense grid stays LTR by design (absolute-offset packing). Pure `tileAccessibilityLabel` unit-tested; verified on emulator via the a11y node dump. See DECISIONS S27.
- Post-S26: **single tile colour (accent, default blue) + sticky folder-merge + calendar date-only + resize/clock/edit fixes.** Start tiles render `settings.accentId` not per-tile `colorId`; `heldAsMergeTarget` holds a merge target while the finger stays on the tile; calendar tile dropped the time; resize cycle medium→small→wide→medium (wraps back to medium, per `TileSize.next()`); clock time restored to 64/42px non-clipping; edit-mode tap switches selection / open space exits. See DECISIONS "Post-S24 follow-up" + S26.
- S26 — **performance.** `:macrobenchmark` module (baseline-profile plugin on `:app` + profileinstaller, `<profileable>`); cold start ≈260 ms (≤800 ms budget) with a shipped baseline profile; recomposition audit via `compose_stability.conf` (tiles `restartable skippable`); bitmap downsampling already sound. Jank percentiles need a physical device. See DECISIONS S26.
- Post-S24: **now-playing on music app tiles + bigger clock + distinct people photos.** New process-wide `MediaCenter` (StateFlow package→`NowPlaying`) published by a single `MediaSessionsEffect` on Start (one `MediaSessionManager` listener + poll, replacing per-tile listeners). `MusicTileFace` reads it + takes optional `packageName`: dedicated music tile = null (any playing); generic app tiles pass their package so **Apple Music / YT Music tiles show their own now-playing**. `face == null` branch falls through now-playing→notification→static. Clock time bumped to 84 sp wide / 54 sp medium. People mosaic refresh rotates in only **off-screen** contacts (no repeated photos; disabled when ≤cellCount contacts). See DECISIONS "S24 follow-up".
- Post-S24: **app icon on notification tiles + calendar AM/PM time.** Live notification tiles (mail/messages `ConversationTileFace` + generic `NotificationTileFace`) now draw the posting app's launcher icon (18 dp, top-left; badge stays top-right) via new `rememberAppIconBitmap`/`AppIconCorner` (decodes `getApplicationIcon` off-thread; package visible via LAUNCHER `<queries>`). Calendar date face shows `"<month> · <h:mm AM/PM>"` — pure unit-tested `formatClock12` folded into `calendarToday(...)`; the date/time loop now ticks on the **minute boundary** (events still poll every 5 min). See DECISIONS "S24 follow-up".
- Post-S24: **drop large size + photos-only people tile.** `TileSize` reduced to SMALL/MEDIUM/WIDE (resize cycle small→medium→wide→small); default photos tile LARGE→WIDE; legacy `LARGE` rows decode to MEDIUM via the Room converter fallback (no migration). People mosaic now shows **profile photos only, randomly** — `queryContacts` filters to contacts with a `PHOTO_THUMBNAIL_URI`, `Person.photoUri` non-null, initial mosaic `shuffled()`; avatar renders the photo (plain tint while loading, **never initials**); degrades to glyph when no contact has a photo. Verified mail/messages faces (bind to the tile's package, show latest sender/snippet + unread/new count + badge — gated on notification access) and the photos slideshow (≥2 picked photos + live tiles active) are correct. See DECISIONS "S24 follow-up".
- Post-S24: **live location-specific weather (FR-2).** Real forecasts from `OpenMeteoWeatherProvider` (free, no API key) via `HttpURLConnection` + `org.json` — current temp + WMO `weather_code` + today's high/low + precip chance for the resolved coords. `WeatherRefreshWorker` now uses it (was `SampleWeatherProvider`); a coarse fix is **reverse-geocoded** with Android `Geocoder` for the **location name** (shown on both tile faces), a typed city is forward-geocoded by Open-Meteo. Pure parsers (`parseOpenMeteoForecast`/`parseOpenMeteoGeocode`/`weatherCodeToCondition`/`weatherDetail`) unit-tested with real `org.json` (`testImplementation` added). New `INTERNET` permission. **Tap:** blank-package weather tile opens `google.com/search?q=weather` (like the calendar tap fallback). See DECISIONS "S24 follow-up".
- Post-S24: **drag an app out of a folder (FR-4) + calendar fixes (FR-2).** Folder overlay children now support a pull-out **drag** (`detectDragGesturesAfterLongPress`): long-press lifts the tile, releasing it >~70% of a tile away from its slot calls `onPullOut` → `viewModel.removeFolderChild(folderId, child)`; a quick tap still launches. `LayoutDao.removeFolderChild` now **re-pins** the removed app as a fresh Start tile (`newTileId`/`newTileColorId` from the repo, like `pinApp`) before collapsing the folder (≥2 left renumber/keep; 1 left dissolve in place to survivor; 0 left delete tile+meta) — pulling an app out returns it to Start instead of deleting it. **Calendar open:** `roleFor("calendar")` resolves via `ACTION_VIEW content://com.android.calendar/time` (more reliable than `APP_CALENDAR`); `onTileClick` also fires that VIEW intent as a fallback for a blank-package calendar tile. **Calendar tile date:** `CalendarTileFace` always renders a today's-date face (lowercase weekday + big day number + month, no permission needed) and flips to the next event only when READ_CALENDAR is granted + an event exists; pure `calendarToday(...)` unit-tested. No schema change. See DECISIONS "S24 follow-up".
- Last completed session: S24 — music tile + degradation matrix (Phase 5, FR-2.3 — **feature complete, tag `v0.5`**). `LiveFace` gains `MUSIC` (flippable; icon key `music`). **Music:** `MusicTileFace` binds to the active media session via `MediaSessionManager.getActiveSessions(component)` using our notification-listener `ComponentName` as the access token (no new permission — same grant as badges/faces); prefers a `STATE_PLAYING` controller else the first priority-ordered one. A `DisposableEffect` registers `OnActiveSessionsChangedListener`; a light 2 s `LaunchedEffect` poll (gated on `active`) catches in-session track/playback changes that don't fire the callback. Front = gated EQ bars (5 bars step every 240 ms only while `active && playing`, else flat) + title/artist; back = "paused / tap to resume". Pure, unit-tested `nowPlayingFrom(title, artist, state)` (trim, "now playing" placeholder, playing = playing|buffering, null when no title+artist). All manager calls `runCatching`-guarded → denied access falls back to the static glyph. **Notification tiles for all other apps (FR-2.3):** new `NotificationTileFace` generalises the mail/messages face — any medium+ app tile with no dedicated `LiveFace` whose package has an active notification shows the newest sender + snippet (same `NotificationCenter` snapshot), else the static glyph; wired in `AppTileContent`'s `face == null` branch (size ≠ small). It doesn't flip (badge carries the count; not in the flip scheduler's `liveIds`) and isn't gated by `liveActive`. **Weather + calendar at first run:** `DefaultTile.liveOnly` marks the two self-contained live tiles so the `LayoutSeeder` seeds them even when their role doesn't resolve (weather has no role; `APP_CALENDAR` may be absent), using a blank inert launch component (`onTileClick` no-ops a blank package — no error toast); a resolvable role is still preferred so tapping opens the app. **Degradation matrix verified:** all permissions denied → every face routes through its `fallback` static glyph, empty notification snapshot → no badges, all provider/manager calls guarded → zero crashes; no code gaps found. Build + all unit tests green (10 new tests: music mapping/formatter, liveOnly seeding). See DECISIONS S24.
- Previously: S23 — people + photos tiles (Phase 5, FR-2). `LiveFace` gains `PEOPLE` (flippable) and `PHOTOS` (the only `flips=false` face → excluded from the flip scheduler, the prototype `data-noflip`); icon keys `people`/`photos`. **People:** `PeopleTileFace` requests `READ_CONTACTS` once via `rememberOptInPermission`, `queryContacts` reads ≤12 distinct contacts (name + `PHOTO_THUMBNAIL_URI`) from `ContactsContract`; mosaic is 2×2 at medium / 4×2 at wide+large; while the live gate is active a gated loop swaps **one random cell every 2.1 s** (prototype `peopleStep`) via per-cell `Crossfade(tween 300)`; back face = one large avatar + "<first> posted". Pure, unit-tested `mosaicCells` (cycles to fill cells) + `colorFor` (deterministic initials tint); avatar shows the contact photo if present else initials. **Photos:** `PhotosTileFace` reads `PhotosStore` (own DataStore `photos_tile.pb`, newline URI codec à la WeatherCache) and cross-fades every 3.0 s (`Crossfade(tween 800)`, prototype `slideshowStep`) while active, with a bottom-left shadowed "photos" label; never flips. Photos picked via a new personalize "live photos · choose photos" row launching `OpenMultipleDocuments` (persistable grant, S18-consistent) → `PhotosStore.setUris`. `rememberTileBitmap` decodes content URIs off-thread, down-sampled (`sampleSizeFor`, unit-tested) to tile size. **Degrade:** contacts denied / no contacts, or no photos picked → static glyph. New `READ_CONTACTS` permission in the app manifest. Build + all unit tests green (13 new livetiles tests: mosaic fill, avatar colour, photos codec, sample size, people/photos mapping). See DECISIONS S23.
- Previously: S22 — notification listener: badges + mail/messages (Phase 5, FR-1.2 / FR-2). `TileNotificationListenerService` (declared in the `:feature:livetiles` library manifest so it merges into `:app`; `BIND_NOTIFICATION_LISTENER_SERVICE` + the listener intent-filter) recomputes the whole picture from `getActiveNotifications()` on every connect/post/removal and publishes a `NotificationSnapshot` to the process-wide `NotificationCenter` `StateFlow`. Pure, unit-tested `summarizeNotifications` drops ongoing (`!isClearable`) + group-summary rows, then counts remaining notifications per package (badge) and keeps the newest as the conversation preview (sender/snippet/count). **Badges (FR-1.2):** Start collects the snapshot; `TileView` overlays a `NotificationBadge` pill on app tiles keyed by `tile.packageName` (prototype `.badge`: 22dp / 18dp small, white-on-dark / inverted on light, `>99`→`99+`). **Mail/messages faces (FR-2):** `LiveFace` gains `MAIL`/`MESSAGES` (icon keys `mail`/`messages`, both flippable); `ConversationTileFace` binds to the tile's *own* package (no default-app resolution), front = sender avatar + snippet, back = count with "unread"/"new". **Opt-in:** notification access isn't a runtime permission, so the personalize sheet gains a "notifications" row deep-linking to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`; `rememberNotificationAccess` re-checks `getEnabledListenerPackages` on `ON_RESUME`. **Degrade/opt-out:** access off / listener disconnected → empty snapshot → no badges + faces fall back to the static glyph. **Reconnect:** `onListenerDisconnected` clears + `requestRebind`s; `onListenerConnected` republishes. Build + all unit tests green (13 new livetiles tests: summary aggregation, initials, mail/messages mapping). See DECISIONS S22.
- Previously: S21 — weather + calendar tiles (Phase 5, FR-2). `LiveFace` now maps `weather`→`WEATHER` and `calendar`→`CALENDAR` (both flippable; mapping stays permission-agnostic). **Calendar:** `queryUpcomingEvents` reads the next two events from `CalendarContract.Instances` (36-h window); `CalendarTileFace` requests `READ_CALENDAR` once via `rememberOptInPermission`, then re-queries every 5 min while the live gate is active (front = next event, back = following). Pure `eventTimeLine`/`calendarEvent` formatters (24-h start + compact `30m`/`1h`/`1h 30m`; all-day drops duration). **Weather:** `WeatherProvider` is a `fun interface` (offline `SampleWeatherProvider` returns the prototype 23°/partly cloudy/26-17/"rain by 6pm · 40%"); `WeatherRefreshWorker` (CoroutineWorker) is enqueued as a unique 30-min periodic job + immediate one-off, scheduled lazily from `WeatherTileFace` only when a weather tile shows. `resolveWeatherQuery` picks granted coarse location (best-effort `LocationManager.getLastKnownLocation`, no Play Services) → `manualCity` fallback → null (skip, static). Snapshot persisted in own DataStore `weather_cache.pb` via tolerant `WeatherCacheCodec` (S17-style). **Degrade:** both tiles take a `fallback` slot from `AppTileContent` (the static `StaticTileGlyph`) and render it when the permission is denied / no data cached. New permissions `READ_CALENDAR` + `ACCESS_COARSE_LOCATION` in the app manifest; new catalog dep `androidx-work-runtime-ktx`. Build + all unit tests green (16 new livetiles tests: weather format/query/codec, calendar format, LiveFace mapping). See DECISIONS S21.
- Previously: S20 — flip engine + clock tile (Phase 5 begins, live tiles). `:feature:livetiles` now has real sources: `LiveFace.forIconKey(iconKey, size)` maps a tile's monoline icon key to its live face (prototype `app.live`) — null for small tiles / unmapped keys (stays static); S20 ships `CLOCK` only. `FlipTile` is a real X-axis 3D flip (`rotationX` 0→180, shallow `cameraDistance`, faces swap at 90°, back counter-rotated), 500 ms / `cubic-bezier(.5,.05,.2,1)` — chosen over the prototype's `translateY` slide (DECISIONS S20). `rememberFlipState(liveIds, active)` runs the prototype `setInterval(flipOne, 2600)` as a gated `LaunchedEffect` loop toggling one random flippable tile/2.6 s (prunes scrolled-out ids). `rememberLiveTilesActive(suspended)` ANDs the caller's suspend flag (edit mode, app-list >50%, open folder/personalize) with three live system signals — lifecycle resumed, battery saver off (`ACTION_POWER_SAVE_MODE_CHANGED`), animator duration scale ≠ 0 (`ContentObserver`). `ClockTileFace` renders front (time/weekday/date) + back (date/alarm) per FR-2.1, ticking on the minute boundary while active; `clockFace(...)` is a pure, unit-tested formatter (24-hour, unpadded hours, lowercase names; `alarm` is a placeholder). `:feature:start` depends on `:feature:livetiles` and renders the clock face via `AppTileContent`. Build + all unit tests green (11 new livetiles tests).
- Previously: S19 — persistence hardening + first run (Phase 4 complete, tag `v0.4`). All Start-layout writes in `StartViewModel` now run on a single-thread `Dispatchers.IO.limitedParallelism(1)` `writeContext` (serialized, ordered, non-interleaving over the already-`@Transaction` DAO ops); reorder commits are **debounced 120 ms** via a `DROP_OLDEST` `MutableSharedFlow` → `.debounce()` collector (resize/unpin/merge/rename/reset/prune write immediately). `TileShellDatabase.build()` is now **corruption-safe**: `fallbackToDestructiveMigration()` for schema/downgrade mismatch + a force-open of `openHelper.readableDatabase` at startup, and an explicit `deleteDatabase` + rebuild on an unrecoverable `SQLiteException` — the DB always comes up (empty if wiped) and `seedIfEmpty()` re-seeds the WP default; settings live in a separate DataStore file, unaffected. New `FirstRunHint` composable in `:feature:start` shows the prototype `.hint` text (same bolded spans) as a one-time bottom card over Start, gated by a `first_run_hint_shown` flag in `tileshell.prefs`. `MainActivity` default-launcher prompt polished (early-return when already HOME, record the ask before launching, `runCatching` the launch). New `docs/RESTORE-CHECKLIST.md` captures the manual kill/reboot/corruption verification steps. Build + all unit tests green; checklist executed manually on device (not CI).
- Previously: S18 — glass, blur, wallpapers (FR-7). `LauncherSettings` grew `glass`/`transparency`/`blur`/`wallpaperId`/`customWallpaperUri`; `PersonalizeSheet` renders the full prototype groups (transparent-tiles + blur pill toggles, transparency `Slider`, 6-gradient wallpaper row + custom-photo picker, reset-layout). `WallpaperBackground` draws gradient or custom photo with optional `blur(18dp)`+scale 1.12. Glass mode swaps Start tile fill for `Glass.fill(dark, transparency)` + inset `glassLine` + per-tile accent dot on small tiles. Custom wallpaper via `ACTION_OPEN_DOCUMENT` (persistable URI). S17 added the theme/accent sheet + the flat `key=value` settings codec; see DECISIONS S17/S18.
- Next session: feature complete through S27 + post-S27 additions (feed page, widget hosting, RSS engine, tiled wallpaper, tile style controls, screen lock, about sheet). Phase 0–4 (S1–S19) done + S20–S27; `v0.4`/`v0.5` tagged earlier, **`v0.9` tagged (release candidate)**. Remaining polish: manual-city UI, alarm provider, glass+light live text colours, folder badge aggregation, on-device jank numbers (macrobenchmark needs a physical device), optional RTL grid mirroring.
- Known issues: the people mosaic **queries contacts once per grant** (no live re-query as contacts change — fine for a launcher; revisit if stale); ordering is alphabetical (`DISPLAY_NAME_PRIMARY ASC`), not by frequency/starred. The cell refresh uses a **`Crossfade`** rather than the prototype's fade-out-then-scale-bounce (cosmetic; reads the same). Photos tile **decodes per visible step** (no decoded-bitmap cache across slides) — acceptable for a handful of picked photos; a revoked/deleted photo URI shows the tile's accent fill for that step rather than the static glyph (only an *empty* selection degrades to static). The people/photos opt-in (contacts ask, photos picker) follows the same **one-shot-per-process** ask as calendar/weather. notification **badges sit on app tiles only** — folder tiles don't aggregate child badge counts this session (revisit if a folder of mail/chat apps should sum). The mail/messages face reads the **tile's own package**, so a tile pinned for a non-mail app but given the `mail` icon key would show that app's notifications (never happens in the default layout). The notification **count is "active dismissable notifications," not a true unread count** — apps that don't post-per-item (e.g. one collapsed summary) under-count; good enough for FR-1.2. `ConversationTileFace` shows live content whenever the snapshot has data — it is **not gated by `liveActive`** (badges/mail shouldn't pause); only the *flip* is gated (via the scheduler's `liveIds`). live face/badge text colours don't yet adapt to glass+light (badge pill does invert by theme; face text stays white — same caveat as S21). weather has **no manual-city entry UI** yet — without a coarse-location grant or a set city the weather tile stays static (faithful opt-in; the `manualCity` plumbing exists in `WeatherCache`, settings UI deferred). Weather uses `OpenMeteoWeatherProvider` (real forecasts, free, no key) — `SampleWeatherProvider` exists for offline/test builds only. The opt-in permission ask is **one-shot per process** (`rememberSaveable`); a denial isn't re-prompted until the next process — a settings re-prompt is a later pass. Calendar/weather degraded (static) tiles still sit in `liveIds` so the flip scheduler toggles their flip state — harmless no-op since the fallback ignores `flipped`. live face text is `Color.White` regardless of glass/light theme (matches the existing static `AppTileContent`; revisit when glass + light + live overlap looks off). The clock `alarm` value is a static "7:00" placeholder until an alarm provider lands. A clock tile dragged into a folder would render the live clock face (folder children reuse `AppTileContent`); harmless and never happens in the default layout. pre-Q (API 26–28) default-launcher prompt falls back to Settings ACTION_HOME_SETTINGS; isDefault uses RoleManager on Q+, resolveActivity heuristic below. Wallpaper radial layers approximate the CSS ellipse radius with a circle (reads identically). Wallpaper `blur(18dp)` is a RenderEffect → no-op below API 31 (same as folder overlay). Custom-wallpaper persistable grant is best-effort (`runCatching`); a revoked/deleted URI falls back to the selected gradient. `app_cache` table + DAO ops defined but not yet populated (S9). `:core:design` keeps a separate private preview-only size enum (intentional). Tiles for non-default/pinned apps with no role have a null `iconKey` → fall back to the generic "app" glyph (real-app-icon fallback not implemented; revisit if mixing looks off). Room DB is schema **v5** with four migrations (1→2 iconKey, 2→3 child size, 3→4 tile accentOverride, 4→5 child accentOverride); fresh installs create v5 directly; all four migrations are wired in `TileShellDatabase.MIGRATIONS`. The music **EQ bars are random levels, not a real audio spectrum** (no `Visualizer`/audio-record permission — cosmetic, matches the prototype). The music tile **polls every 2 s while active even with no session** (sets the same null repeatedly — no recomposition; cheap). The generic `NotificationTileFace` applies to **folder children at MEDIUM** too (they reuse `AppTileContent`) — harmless, degrades to the glyph when the child app has nothing pending. Self-contained `liveOnly` tiles (weather/calendar with no resolved app) carry a **blank package**; the launcher's package-removed pruning can't match them (correct — there's no app to track) and tapping is inert. The music tile's media binding reads the **first/playing session only** — a second background player isn't shown (faithful to a single now-playing tile). Feed page **RSS images load on demand** with no pre-fetch — first scroll may show placeholders briefly; the `LruCache` warms up as articles are viewed. Widget host **does not survive process death** without the DataStore — on a fresh cold start `WidgetStore` is re-read and widgets re-bound, which can cause a brief blank slot. Feed **article read-state is not tracked** — all articles appear unread on every refresh. **Gradient tile fill is fully wired**: when `tileFill == GRADIENT`, `TileView` paints `tileGradientBrush(accent)` (a diagonal accent gradient, +15% light → −30% dark; `core/design/TileStyle.kt`) instead of the flat `background(accent)`; the gradient shades whichever colour wins the per-tile priority chain (`accentOverride` → app-icon dominant → global `accentId`). The **screen lock accessibility service** requires a one-time manual enable in Android Settings → Accessibility; the launcher cannot prompt for it directly (OS restriction).

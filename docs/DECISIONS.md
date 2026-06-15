# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

## S24 — music tile + degradation matrix (FR-2.3, feature complete)

- **Music face reads the active media session, not a notification.** `MusicTileFace`
  uses `MediaSessionManager.getActiveSessions(component)` with our notification-
  listener `ComponentName` as the access token — the same grant that powers
  badges/faces, so the tile needs no new permission. It prefers a `STATE_PLAYING`
  controller, else the first (priority-ordered) one. A `DisposableEffect` registers
  `OnActiveSessionsChangedListener`; because metadata/playback changes *within* a
  session don't fire that callback, a light `LaunchedEffect` poll (2 s, gated on
  `active`) keeps the face current. Every manager call is `runCatching`-guarded, so
  denied access surfaces as a null face → `fallback` (static glyph), never a crash.
  `nowPlayingFrom(title, artist, state)` is pure + unit-tested (trim, placeholder
  title, playing = playing|buffering, null when no title and no artist). Front = EQ
  bars + title/artist; back = "paused / tap to resume" (prototype `liveFace('music')`).
- **EQ bars are gated, not an infinite transition.** Five bars step to fresh random
  levels every 240 ms via a `LaunchedEffect` that runs only while `active && playing`
  and settles flat otherwise — so an idle/off-screen launcher does no per-frame EQ
  work (consistent with the other gated live loops). Smoothed with a 180 ms
  `animateFloatAsState` per bar.
- **Notification live tiles for *all* apps.** `NotificationTileFace` generalises the
  mail/messages face to every app tile with no dedicated live face: medium+ tiles
  whose package has an active notification show the newest sender + snippet (reading
  the same `NotificationCenter` snapshot), falling back to the static glyph when the
  app has nothing pending or access is off. It does **not** flip — the per-app badge
  already carries the count and a generic tile isn't registered with the flip
  scheduler (its icon key maps to no `LiveFace`) — and it isn't gated by `liveActive`
  (content shouldn't pause). Wired in `AppTileContent`'s `face == null` branch for
  size ≠ small; small tiles keep the badge only.
- **Weather + calendar always seed (liveOnly).** Their live faces are self-contained
  (WeatherProvider / CalendarContract), so they shouldn't be gated on resolving an
  external launcher app — yet `roleFor("weather")` is null and `APP_CALENDAR` may not
  resolve, so before S24 they were skipped at first run. `DefaultTile.liveOnly`
  marks them; the seeder seeds a liveOnly tile even when its role doesn't resolve,
  using a **blank, inert launch component** (the live face renders from its provider).
  A resolvable role is still preferred when present (tapping opens the app); a blank
  package makes `onTileClick` a no-op rather than an error toast.
- **Degradation matrix (FR-2.3) verified.** With every permission denied / access off:
  clock renders (no permission to deny); weather (no location/city), calendar (no
  READ_CALENDAR), people (no READ_CONTACTS), photos (no selection), mail/messages and
  the generic notification face (no listener access), and music (no media access) all
  return their `fallback` static glyph; badges read an empty snapshot → none. All
  provider/manager calls are `runCatching`-guarded, so the all-denied path produces a
  plain static grid with zero crashes. No code gaps found — each face already routed
  through a fallback slot; music and the generic face were built to the same contract.

## S24 follow-up — now-playing on music app tiles + bigger clock + distinct people

- **Any music app tile shows its own now-playing (Apple Music, YT Music, …).** A new
  process-wide `MediaCenter` (StateFlow of package → `NowPlaying`) is published by a
  single `MediaSessionsEffect` mounted on Start (one `MediaSessionManager` listener +
  light poll, replacing the per-tile listener). `MusicTileFace` reads it and takes an
  optional `packageName`: the dedicated music tile passes null (shows whatever is
  playing, prototype behaviour); a generic app tile passes its own package. The
  `face == null` branch now falls through **now-playing (for this package) →
  notification → static glyph**, so a pinned music app surfaces its track, a chat/mail
  app surfaces its notification, and everything else stays static. One shared listener
  avoids N per-tile binder polls.
- **Bigger clock.** The clock tile's time scales up to 84 sp on wide / 54 sp on medium
  (was 64/42) for a more WP-like oversized clock.
- **People mosaic never repeats a photo.** The refresh now rotates in a contact that is
  *not already on screen* (swap a random cell with a random off-screen contact) and is
  disabled when there are ≤ cellCount contacts (nothing new to show), so the same photo
  no longer appears in multiple cells. The initial arrangement was already a distinct
  shuffled subset.

## S24 follow-up — app icon on notification tiles + calendar AM/PM time

- **App icon in the notification tile's top-left corner.** A live notification tile
  (mail/messages `ConversationTileFace` and the generic `NotificationTileFace`) now
  draws the posting app's launcher icon small (18 dp) in the top-left corner, so the
  tile still identifies its app — the count badge already sits top-right. New
  `rememberAppIconBitmap(packageName)` decodes `PackageManager.getApplicationIcon`
  off-thread (the package is visible via the LAUNCHER `<queries>` entry); `AppIconCorner`
  renders nothing until it loads / if it can't resolve. The faces wrap their content
  in a `Box` so the icon overlays both flip sides.
- **Calendar tile shows the AM/PM time alongside the date.** The date face's third
  line is now `"<month> · <h:mm AM/PM>"` (e.g. `june · 2:30 PM`). Pure `formatClock12`
  (12-hour, padded minutes, midnight/noon → 12) is unit-tested and folded into
  `calendarToday(...)`. Because the face now shows a live clock time, its refresh loop
  ticks on the **minute boundary** (like the clock tile) instead of every 5 min;
  events still poll every 5 min.

## S24 follow-up — drop large size + photos-only people tile

- **Large (4×4) tile size removed.** `TileSize` now has only SMALL/MEDIUM/WIDE; the
  resize cycle is small → medium → wide → small. The default photos tile drops from
  LARGE to WIDE. The enum value is gone rather than merely hidden — a legacy `LARGE`
  row decodes to MEDIUM via the Room converter's tolerant `getOrDefault`, so old
  installs degrade gracefully without a migration. The packer is size-agnostic
  (consumes `cols`/`rows`), so removing the value needed no packer change; the 4×4
  packing test was dropped.
- **People tile shows profile photos only, randomly.** Per request the mosaic no
  longer draws initials: `queryContacts` filters to contacts that *have* a
  `PHOTO_THUMBNAIL_URI` (selection + skip), `Person.photoUri` is now non-null, and
  the initial mosaic is a `shuffled()` random selection (the 2.1 s refresh already
  swaps random cells). The avatar renders the photo cropped to fill; while it decodes
  or if the URI is briefly unreadable it shows a plain colour tint — never initials.
  Degrades to the static glyph when no contact has a photo.

## S24 follow-up — live, location-specific weather (FR-2)

- **Open-Meteo, no API key, no SDK.** Real forecasts come from `OpenMeteoWeatherProvider`
  via `HttpURLConnection` + `org.json` (no Retrofit/OkHttp dependency, keeping the
  module lean). It fetches current temp + WMO `weather_code` + today's max/min +
  precip-probability for the resolved coordinates. Pure parsers
  (`parseOpenMeteoForecast`, `parseOpenMeteoGeocode`, `weatherCodeToCondition`,
  `weatherDetail`) are unit-tested with the real `org.json` (added as a
  `testImplementation` since the android.jar stub throws). `httpGet` is injected so
  the provider's logic is testable without network. New `INTERNET` permission.
- **Location label via Android `Geocoder`.** A coarse fix is reverse-geocoded
  (locality → sub-admin → admin area) on the worker thread to label the tile
  ("Pune"), falling back to "current location"; a typed city is forward-geocoded by
  Open-Meteo (canonical name + coords). The label is shown on both tile faces — the
  prototype shows no place, but the user asked for it. `SampleWeatherProvider` is kept
  only for previews/offline; the worker now uses the network provider and retries on
  failure (keeping the last cached snapshot) rather than showing fake data.
- **Tap opens weather.** Weather has no standard launcher intent, so a blank-package
  weather tile opens a weather web search (`google.com/search?q=weather`) — handled
  in-app by the Google app where present, else the browser — mirroring the calendar
  tap fallback.

## S24 follow-up — drag an app out of a folder + calendar fixes (FR-4 / FR-2)

- **Pull-out is a drag gesture and re-pins onto Start.** First pass used an edit-mode
  × that *deleted* the child; the WP-faithful behaviour (and the user's ask) is to
  drag the app out back onto Start. The folder overlay child now takes a
  `detectDragGesturesAfterLongPress`: long-press lifts the tile (scale + shadow), and
  releasing it more than ~70 % of a tile away from its slot calls `onPullOut`; a quick
  tap still launches. The pulled app is **re-pinned** as a fresh Start tile (appended,
  parallel to `pinApp`) — taking it out of the folder returns it to Start rather than
  deleting it. A one-line hint sits under the folder title.
- **`removeFolderChild` re-pins, then collapses.** `LayoutDao.removeFolderChild` (one
  `@Transaction`) now inserts a new top-level app tile for the removed child
  (`newTileId`/`newTileColorId` computed in the repository, like `pinApp`) before
  collapsing the folder: ≥2 left → renumber & keep; exactly 1 left → dissolve the
  folder tile in place to the survivor's app tile (drop folder meta, leftover child
  cascades); 0 left → delete tile + meta. `folderId` is the folder tile's own id
  (DECISIONS S5). On dissolve/empty the existing self-close effect closes the overlay.
  No schema change — only new queries.
- **Calendar opens the device calendar.** The liveOnly calendar tile was seeding with
  a blank launch target because `APP_CALENDAR` is often undeclared. `roleFor("calendar")`
  now resolves via `ACTION_VIEW content://com.android.calendar/time` (the default
  calendar provider, reliably one handler); the resolver still launches that package's
  main entry. As a belt-and-braces fallback, `onTileClick` fires the same VIEW intent
  for a blank-package tile whose icon key is `calendar`, so tapping always opens a
  calendar when one exists.
- **Calendar tile always shows today's date.** Previously it degraded to a bare glyph
  with no permission / no events. `CalendarTileFace` now renders a date face (lowercase
  weekday, large day number, month) as the always-available base — no permission needed
  — and flips to the next event only when READ_CALENDAR is granted and one exists. Pure,
  unit-tested `calendarToday(dayOfWeek, dayOfMonth, month0)`.

## S17 · Personalize sheet: theme + accent

- **"Proto DataStore" honoured as a typed `Serializer`, not the protobuf
  toolchain.** CLAUDE.md mandates Proto DataStore for settings. Adding the
  protobuf-gradle plugin + `.proto` codegen for a two-field schema is
  disproportionate, so `LauncherSettings` is a flat Kotlin data class persisted
  through a typed `DataStore<LauncherSettings>` with a hand-written
  `SettingsSerializer` over a tiny `key=value` text codec (`SettingsCodec`). This
  keeps the architectural intent — typed schema, transactional `updateData`,
  `Flow`-backed live reads — without protobuf weight, and the codec is pure
  Kotlin so its round-trip/tolerance is JVM-unit-tested (org.json would have
  needed Robolectric). A new/corrupt store reads as defaults.

- **Accent is a *global chrome* accent; it does not recolour Start tiles.** The
  prototype renderer paints every tile with the single `state.accent` (per-tile
  `color` is vestigial). Our port deliberately kept per-tile colours since S2/S11
  ("rather than a wall of identical blue tiles"), so changing the accent here
  recolours only the accent chrome — app-list row tiles, letter headers, the jump
  grid and the segmented toggle — threaded via a new `LocalAccent`. Start tiles
  keep `TileAccents.forId(colorId)`. The live feedback for an accent change is
  therefore the seg highlight + selected-swatch ring in the open sheet and the
  app list, not the Start grid.

- **Theme applies live via `LocalColorTokens`, but the wallpaper is theme-
  independent (matching the prototype's separate `wall` state).** A new
  `staticCompositionLocalOf` carries the active `ColorTokens`, provided at the
  Start root from the persisted `dark` flag; the sheet, edit bar and app list
  read it and re-skin the instant the toggle flips. The Aurora wallpaper and the
  solid-accent Start tiles stay as-is across themes (tiles are white-on-accent,
  theme-agnostic), so light theme is visible on the chrome surfaces rather than
  the grid — faithful to the prototype, where `.light` only retints token-driven
  surfaces over the same wallpaper. The folder overlay keeps its light-on-dark
  scrim colours regardless of theme (it always sits over a dark blurred Start).

- **Sheet lives in `:feature:personalize` (its first real source).** The empty
  module finally gets its purpose: a stateless `PersonalizeSheet(visible, dark,
  accentId, callbacks)` depending only on `:core:design`. `:feature:start` owns
  the open/close state (`StartViewModel.personalizeOpen`, so Back/Home close it
  before the folder/edit/app-list) and feeds persisted values straight back in.
  Only the theme + accent groups are built; transparency/blur/wallpaper/layout
  groups from the prototype `buildSettings` are deferred to later sessions.

## S16 · Folder overlay + rename

- **Children render as medium tiles, per the session prompt, not the prototype's
  1×1 cells.** The prototype's `.ggrid` lays children out as unit (1×1) cells
  with an icon + name; the SESSION-PLAN says "grid of medium child tiles", which
  is authoritative. Children are rendered as `MEDIUM` tiles through the existing
  `DenseTileGrid` + `AppTileContent` (2 per row on the 4-column grid), so they
  match Start tiles exactly. All children take the *folder's* `colorId` (the
  prototype paints them with the single global accent; we have no global accent
  yet, so the folder's colour is the WP-faithful stand-in).

- **Rename is new (the prototype has none).** FR-4 asks for it, so long-pressing
  the title swaps it for an auto-focused inline `BasicTextField` (same thin/30sp
  style); IME **Done** commits via `LayoutDao.updateFolderName`. Blank/whitespace
  names are ignored (the title keeps its prior value). Tapping the scrim or a
  child while renaming cancels (discards the draft) — acceptable with no
  prototype reference.

- **Backdrop blur is applied to the Start surface, not the scrim.** Compose has
  no `backdrop-filter`, so the prototype's `blur(14px)` is reproduced by
  `Modifier.blur(14.dp)` on the Start content behind the overlay (the overlay is
  a sibling above it, so it stays sharp). `Modifier.blur` only takes effect on
  API 31+; below that it is a no-op and the translucent scrim alone dims the
  background — an accepted approximation (cf. the wallpaper radial note).

- **Dismissal: scrim tap, close button, Back and Home.** The scrim uses
  `detectTapGestures`; child tiles and the close button consume their taps so
  they don't also dismiss. Back closes the folder before edit/app-list;
  `goHome` (Home press / `onNewIntent`) closes it too. Opening sets
  `swipeEnabled = false`; a guard effect also fully closes (re-enabling the
  swipe) if the folder is dissolved by an uninstall while open. No pure logic
  here, so no new unit tests.

## S14 fix · Drag-to-merge was unreachable

- **Merge targets are hit-tested against the layout packed *without* the dragged
  tile.** Emulator verification of S16 surfaced a bug: dragging a tile onto
  another's centre never created a folder — it reordered. Cause: merge used the
  live, dragged-included packing (`placementsNow()`), so as the finger crossed a
  target's edge a reorder fired that relocated the dragged tile's own slot under
  the finger; that slot is excluded from the hit-test (`it.id != startId`), so
  the centre/merge zone was never detected and the target physically slid away.
  Fix: merge detection now runs against `othersPacked(dragged)` — the other tiles
  packed with the dragged tile removed — which is **invariant** for the whole
  drag (a drag only ever moves the dragged tile within the order, never reorders
  the others). So targets stay put and the centre zone is reachable. Reorder
  still uses the live packing so the gap keeps following the finger.
- **Entering a merge target settles the others under the finger.** When a merge
  target is hovered, the dragged tile is parked at the end of the order
  (`onMergeMode`), so the other tiles render in their natural slots (a tile at
  the end doesn't perturb the dense packing of those before it) and the
  highlighted target sits exactly under the floating tile. Leaving the merge zone
  re-inserts the dragged tile at the finger and the gap-reflow resumes.

## S15 · Resize, unpin, edit bar

- **Corner controls are handled by the grid gesture, not child buttons.** The
  unpin/resize controls render as visual chrome on the selected tile, but their
  taps are caught by `editDragGesture` via 30 dp corner hot-zones over the
  selected tile's rect (top-left → unpin, bottom-right → resize). This keeps all
  edit-mode interaction in one gesture (as established in S13), and the gesture
  consumes those events so the `emptySpaceExit` never also fires. The trade-off
  is the hot-zones duplicate the controls' corner geometry, but the zones are
  generous enough to cover them despite the selected tile's 1.04 scale.

- **`emptySpaceExit` now ignores consumed taps.** Edit-bar buttons use
  `clickable` (which consumes), and the corner controls are consumed by the grid
  gesture; without an `isConsumed` check a tap on *personalize* would open the
  sheet **and** exit edit. The empty-space exit now skips when the terminating
  change was consumed by a descendant.

- **Room rejects a `TileSize` converter on a `@Query` bind param / scalar
  return.** A `SELECT size … : TileSize?` read and an `UPDATE … :size: TileSize`
  bind both made Room's KSP processor fail with `MissingType`. So the resize read
  goes through the existing `tilesOnce()` and the size is bound as its stored
  `name` string (`updateTileSize(id, size: String)`); the enum↔string conversion
  stays in Kotlin (`TileSize.next().name`).

- **Resize reuses the S13 reflow animation; no separate size tween.** Changing a
  tile's size just persists the new `TileSize`; the grid re-packs and the
  surrounding tiles animate to their new slots via the existing
  `animateIntOffsetAsState` (the resized tile's own footprint snaps). The drag
  gesture is also re-keyed on `byId` so a mid-session resize/unpin refreshes the
  captured tile sizes used for hit-testing (safe: `byId` never changes mid-drag).

- **Personalize is a minimal stub sheet, dismissed by scrim only.** A scrim plus
  a bottom panel naming the future options (accent/background/transparency). No
  `BackHandler` — `:feature:start` doesn't depend on `activity-compose` and a
  stub doesn't warrant adding it; the real sheet arrives with
  `:feature:personalize`. Unpin keeps edit mode active (prototype-faithful); the
  now-removed tile's stale `selectedTileId` is harmless (no placement matches).

## S14 · Merge to folder

- **A merge reuses the target tile's id as the folder id.** The prototype splices
  a brand-new `g-<timestamp>` group into the target's slot. Our schema convention
  (DECISIONS S5) is that a folder tile and its `folders` row share one id, so
  `computeMerge` instead makes the *target tile's own id* the folder id — for an
  app→folder promotion the app tile is rewritten in place as a folder tile, and
  for an existing folder the id is already its folder id. So `MergeResult.folderId`
  is always `target.id`, and no id generator is needed.

- **De-duplication is by component (`packageName/activityName`), not package.**
  The prototype dedups by app id (its ids are packages). Real apps are identified
  by their launcher component, so two activities of the same package stay
  distinct. Union order is target's apps first, then the dragged tile's, matching
  the prototype.

- **The merge persists the surviving reorder, in one transaction.** Dragging onto
  a centre zone first crosses other tiles' edge zones, incurring incidental
  reorders in the working order. To keep the persisted layout matching what the
  user sees (and to mirror the prototype, which mutates one shared array for both
  reorder and merge), `applyMerge` renumbers the surviving tiles to the working
  order *after* writing the folder and dropping the dragged tile — all inside the
  same `@Transaction`. The folder tile reuses the target's id, so it is part of
  that renumber.

- **The 4-icon folder face already existed (S6); only targeting/highlight is new.**
  `FolderTileContent` has rendered a 2×2 mini-grid of the first four child glyphs
  since S6, so S14 added no new face. The merge-target highlight is the prototype
  `.merge-target` 3 px inset outline (`Modifier.border`), and the target is held
  at full opacity (exempt from the .45 edit-mode dim) so it reads as the drop
  destination. The "grouped" toast fires optimistically on drop, like the
  prototype's synchronous `toast('grouped')`.

## S13 · Drag to reorder

- **The dragged tile follows the finger; the prototype only reflows.** The
  prototype's edit drag just splices the array and lets CSS reflow — the tile
  never tracks the cursor and has no scale. FR-3.2 (the session prompt) asks for
  "lift with scale/shadow, follow finger", which is the real WP behaviour, so
  S13 goes beyond the prototype: the lifted tile detaches to a finger-anchored
  offset (1.08 scale + shadow, raised z) while the rest re-flow live.

- **`DenseTileGrid` was inverted from a custom `Layout` to a sized `Box` of
  self-offsetting children.** To finger-follow one tile while animating the
  others, each tile needs its own positionable offset. The grid now computes
  every tile's slot via the shared [GridGeometry] and hands `(slot, sizePx)` to
  the caller, which applies `Modifier.offset { … }` — an `animateIntOffsetAsState`
  slot for resting tiles, the live drag offset for the dragged one. `key(p.id)`
  wraps each tile so per-tile animation state survives a re-flow. Visual spacing
  is unchanged (identical unit/gap/side/top math, now in `GridGeometry`).

- **Hit-testing is synchronous geometry, not `onGloballyPositioned`.** The drag
  gesture is attached to the whole grid, so pointer positions are already
  grid-local; it re-packs the current working order with `GridPacker` and tests
  the finger against `GridGeometry` rects each move. Deterministic and lag-free
  during fast drags (no async layout callbacks), and the same geometry the grid
  renders with, so they can't disagree.

- **Reorder only in the edge zone; centre is reserved.** Hovering the inner
  22–78% of a tile (`inMergeZone`) suppresses reorder, leaving that gesture for
  the S14 folder merge. Outside it, the tile takes over the target's slot, with a
  `lastTarget` guard so crossing one tile reorders once (prototype behaviour).

- **Working order is a separate `SnapshotStateList`, reconciled not reset.** The
  grid renders a local `order` that the drag mutates live; the drop persists it
  via `LayoutDao.applyOrder` (one transaction renumbering `position`). The
  re-sync from the persisted flow *preserves* the existing relative order of
  surviving ids (appending pins, dropping uninstalls) rather than overwriting —
  so the async DB write after a drop lands the same order with no snap-back
  flicker.

- **Auto-scroll is a state-driven frame loop.** The gesture sets a −1/0/+1
  direction from the finger's viewport-Y (mapping content→viewport via the
  status-bar inset + `scrollState.value`); a `LaunchedEffect` scrolls one step
  per frame until it leaves the edge zone or `scrollBy` reports the edge. While
  the finger is stationary at an edge, reorder catches up on the next move
  (acceptable per the SESSION-PLAN's auto-scroll fallback note).

## S12 · Edit mode entry/exit + chrome

- **Tile corner controls and add/personalize are visual chrome only this
  session.** The prototype renders unpin (close, top-left) and resize (bottom-
  right) on the selected tile, and add/personalize/done in the bottom edit bar.
  Their *actions* (unpin removes a tile, resize cycles size, add → app list,
  personalize → sheet) are explicitly SESSION-PLAN S15 work and need repository
  mutators that don't exist yet. S12 therefore renders all of them but wires
  only `done` → `exitEdit` (an FR-3.1 exit path). The non-wired buttons carry no
  `clickable` (rendered, inert) rather than a no-op stub, so there are no dead
  handlers to remove in S15.

- **Edit mode state lives in `StartViewModel`, not local Compose state.** Home
  (`onNewIntent`/`goHome`) and Back (`MainActivity` back callback) both need to
  read and clear it, and entering edit must flip the existing `swipeEnabled`
  flag that gates the pager. Keeping `editMode`/`selectedTileId` as `StateFlow`
  on the VM lets all three call sites share one source of truth; `enterEdit`/
  `exitEdit` also own the swipe toggle.

- **Selection is fixed at entry (prototype-faithful).** The prototype only sets
  the selected tile via the long-press that enters edit; once editing, a plain
  tap on any tile (or empty space) exits rather than re-selecting. S12 mirrors
  this — re-selection/drag is S13. The long-press timer is only armed out of
  edit mode.

- **Jiggle uses one shared phase, composed only while editing.** Rather than a
  per-tile infinite animation, a single `rememberInfiniteTransition` drives a
  ±.5° phase that even/odd tiles apply with opposite sign (approximating the CSS
  `nth-child(2n)` −.45s delay). It is gated behind `if (!editMode) return 0f`,
  so a resting Start screen runs no animation frames. The press-tilt effect
  (S7) is suppressed while editing. This is the "live-animation pause hook":
  real live tiles aren't wired into Start yet, so pausing them is a genuine
  no-op for now.

## S11 · Pin from app list

- **A pinned app's "default colour" is derived deterministically from its
  package.** The prototype pins in each app's authored `col` (data.js), falling
  back to blue. Real Android apps declare no WP tile colour, so rather than pin
  everything blue, `TileColors.defaultIdFor` folds the package name into one of
  the 14 palette ids — the same app always pins to the same colour, giving a
  varied board while staying stable across sessions.
- **"Already on start" is checked against top-level app tiles only.** Matching
  the prototype's `tiles.some(t=>t.app===appId)`, the de-dupe (`appTileCount`)
  looks at pinned/seeded app tiles by package, not folder children — an app that
  only lives inside a folder can still be pinned as its own tile. New tiles
  append at `MAX(position)+1`; the dense packer places them.

## S10 · Search + jump grid

- **The `#` jump cell is a real, tappable section.** The prototype's `buildJump`
  forces the `#` cell `off` (`c!=='#' && have.has(c)`) because its demo apps get
  a digit header (e.g. "9 → 9"), never `#`. TileShell instead buckets every
  non-letter app under a single `#` section (`AppEntry.letter`), so `#` is a
  genuine jump target — its cell lights up as accent and scrolls there whenever
  such apps exist, matching how our headers actually group. Letters absent from
  the (filtered) list stay dimmed and dismiss the grid on tap, as in the
  prototype.
- **Jump grid reflects the filtered list.** `availableLetters`/scroll targets are
  computed from the currently displayed (post-search) apps rather than the full
  catalogue, so a jump always lands on a visible header even while filtering.

## S9 · Alphabetical app list

- **App-list rows show the real app icon, not a monoline glyph.** The
  prototype renders each app-list row as an accent square with the app's
  monoline `ic` glyph, but that only works for its curated demo set — arbitrary
  installed apps have no TileShell glyph. Rows therefore show the real launcher
  icon (loaded via `PackageManager.getActivityIcon` off the main thread,
  `produceState`) on top of the accent square, which is kept as the backing so
  transparent icons still read as a tile. The generic "app" glyph is the
  fallback when an icon fails to load. Start-screen tiles are unaffected (they
  keep monoline glyphs).

## S5 · Room schema + seeder

- **`TileSize` canonical home is `:core:data`.** S3 defined `TileSize` in
  `:feature:start` and S4 duplicated a preview-only copy in `:core:design`.
  Persisted layout models need it, so the canonical enum now lives in
  `:core:data` (`com.tileshell.core.data.TileSize`); `:feature:start` depends on
  `:core:data` and imports it. The `:core:design` preview enum stays private
  (preview-only; keeps the design module free of a data-layer dependency).

- **Schema shape (spec §4.3, not re-read — WP-faithful reconstruction).**
  Four entities: `tiles` (ordered grid items, `type` = app|folder, app columns
  nullable, `folderId` links folder tiles to their meta), `folders` (id + name),
  `folder_children` (folderId FK + position + component, `onDelete=CASCADE`),
  `app_cache` (component → label/letter/lastSeen for offline tile rendering and
  uninstall detection). A folder tile and its `folders` row share the same id
  (e.g. `g-social`); `tiles.folderId == tiles.id` for folder tiles. No FK on
  `tiles.folderId` (avoids insert-ordering constraints; Room `@Relation` does
  not require one).

- **Seeder role mapping.** Prototype app ids are generic roles. Each maps to a
  standard intent/category resolved against installed apps; the resolved
  package's *launcher* activity is stored so tapping a tile opens the app's
  entry point. Roles with no Android equivalent (weather, notes, bank, auth,
  …) have no mapping and their tiles are skipped. Folders keep only resolvable,
  de-duplicated children and are dropped entirely if none resolve. Positions
  are re-numbered contiguously after skips so dense packing is unaffected.

- **Migration scaffolding.** Database is version 1 with `exportSchema=true`
  (schema JSON under `core/data/schemas/`). `TileShellDatabase.MIGRATIONS` is an
  empty array wired into the builder, ready for future versioned migrations.

## S18 — Glass, blur, wallpapers (FR-7)

- **Custom wallpaper picker: `ACTION_OPEN_DOCUMENT`, not the photo picker.**
  The spec calls for a *persistable* custom-wallpaper URI so the photo survives a
  reboot. `ActivityResultContracts.PickVisualMedia` (the system photo picker)
  returns URIs whose read grant is session-scoped and **cannot** be persisted via
  `takePersistableUriPermission`. `OpenDocument(arrayOf("image/*"))` is still a
  system picker UI but yields a SAF URI that we persist (best-effort `runCatching`
  around the grant). The URI string is stored in `LauncherSettings.customWallpaperUri`.

- **Blur-wallpaper effect.** Prototype `#screen.blur #wall { filter: blur(18px)
  saturate(1.1); transform: scale(1.12) }`. We apply `Modifier.blur(18.dp)` +
  `graphicsLayer` scale 1.12 to the wallpaper layer when blur is on. `blur()` is
  a RenderEffect → no-op below API 31 (same caveat as the folder overlay). The
  `saturate(1.1)` is applied as a `ColorMatrix` colour filter only to the **custom
  photo** (where it's perceptible); the bundled mesh gradients are left unfiltered
  (saturating a flat-ish gradient reads identically, and `drawBehind` gradients
  have no cheap colour-filter hook).

- **Glass tiles keep their per-tile colour identity.** In glass mode the Start
  tile background becomes `Glass.fill(dark, transparency)` with an inset
  `glassLine` hairline (prototype `#screen.glass .tile`). Per S11, Start tiles keep
  their own `colorId` rather than the global accent, so the small-tile accent dot
  (`#screen.glass .tile.small .accentdot`) uses `TileAccents.forId(tile.colorId)`,
  not the chrome accent. Glass is applied to the main Start grid only; the folder
  overlay tiles stay solid-accent (separate surface, contained scope).

- **Reset layout** re-seeds the WP default via a new `LayoutRepository.resetLayout()`
  that always calls `replaceLayout` (vs `seedIfEmpty` which no-ops on a non-empty
  grid); both share a private `writeDefaultLayout`. The toast fires immediately
  (prototype behaviour) even though the DB write is async.

- **Settings codec growth.** `LauncherSettings` gained `glass`, `transparency`,
  `blur`, `wallpaperId`, `customWallpaperUri`. The flat `key=value` codec takes the
  value to end-of-line, so a content URI containing `=` round-trips. Tolerance:
  transparency is clamped to 0..1 (bad floats keep the default); an empty
  `customWallpaper=` decodes to null; an empty `wallpaper=` keeps the default.

## S19 — Persistence hardening + first run

- **Serialized layout writes.** All Start-layout mutations (reorder, resize,
  unpin, merge, rename, reset, uninstall-prune) now run on
  `Dispatchers.IO.limitedParallelism(1)` in `StartViewModel`, so committed edits
  apply in call order and never interleave one another's `@Transaction`. Settings
  writes stay on plain `Dispatchers.IO` — Proto/DataStore already serializes them.

- **Debounced reorder.** Reorder commits route through a `MutableSharedFlow`
  (`DROP_OLDEST`) `.debounce(120 ms)` so a flurry of drops coalesces into a single
  transactional write of the freshest order. 120 ms is small enough to be
  invisible; other edits (resize/unpin/merge) write immediately.

- **Corruption → default-layout fallback.** `TileShellDatabase.build()` adds
  `fallbackToDestructiveMigration()` (schema-version mismatch / downgrade recreates
  rather than crashes) and force-opens `openHelper.readableDatabase` at startup so
  on-disk corruption surfaces immediately; a `SQLiteException` the framework's
  handler can't recover from triggers an explicit `deleteDatabase` + rebuild. The
  DB always comes up — empty if wiped — and `seedIfEmpty()` re-seeds the WP
  default. Settings live in a separate DataStore file, unaffected.

- **First-run hint overlay.** New `FirstRunHint` composable in `:feature:start`
  shows the prototype's `.hint` text verbatim (same bolded spans) as a one-time
  bottom card over Start, dismissed by tap. A `first_run_hint_shown` flag in the
  existing `tileshell.prefs` SharedPreferences keeps it from returning. Layered
  above all Start content so it reads on a fresh install.

- **Default-launcher prompt polish.** `MainActivity` now early-returns when
  TileShell already holds the HOME role (never prompts even if we never recorded
  asking — e.g. set default from system settings), records the ask *before*
  launching (a process death mid-dialog can't cause a re-prompt), and wraps the
  `launcher.launch` in `runCatching`. Decline is still respected — never an
  automatic re-prompt.

- **Restore checklist.** `docs/RESTORE-CHECKLIST.md` captures the manual
  kill/reboot/corruption verification steps (executed on device, not in CI).

## S20 — flip engine + clock tile

- **Real 3D flip over the prototype's slide.** The HTML prototype fakes the live
  flip with a vertical `translateY(-100%)` (its CSS comment notes 3D backface was
  unreliable in the browser). Compose handles real 3D, so `FlipTile` does an
  X-axis `rotationX` 0°→180° with a shallow `cameraDistance`, swapping faces at
  the 90° midpoint (back counter-rotated to read upright). This is closer to the
  actual Windows Phone tile flip while keeping the prototype's 500 ms /
  `cubic-bezier(.5,.05,.2,1)` timing.

- **Live faces keyed off the icon key.** There is no `live` column on the tile
  model; `LiveFace.forIconKey(iconKey, size)` maps a tile's monoline icon key to
  its live face (the prototype's `app.live`), returning null for small tiles and
  unmapped keys so they stay static. S20 implements `CLOCK` only; weather/calendar
  (S21) and the notification faces (S22) extend the same enum.

- **Flip scheduler = gated coroutine.** `rememberFlipState(liveIds, active)` runs
  the prototype's `setInterval(flipOne, 2600)` as a `LaunchedEffect` loop that
  toggles one random flippable tile every 2.6 s. It only runs while `active`;
  `rememberLiveTilesActive(suspended)` ANDs the caller's suspend flag (edit mode,
  app-list shown >50%, open folder/personalize) with three live system signals —
  lifecycle resumed, battery saver off, animator duration scale ≠ 0. Pausing
  freezes the shown faces; they resume turning on return. Ids scrolled out of
  `liveIds` are pruned so flip state doesn't leak back.

- **Clock tick aligned to the minute.** `ClockTileFace` recomputes its `ClockFace`
  on each minute boundary (`delay(60_000 - now % 60_000)`) while active, so a
  paused launcher does no per-minute work and refreshes on resume. Formatting is a
  pure `clockFace(...)` fn (24-hour, unpadded hours, lowercase full weekday/month)
  ported from the prototype `clockNow()`, unit-tested; `alarm` is a static
  placeholder until an alarm provider lands.

## S23 — people + photos tiles (FR-2)

- **People mosaic = contacts opt-in, single-cell cross-fade.** `PeopleTileFace`
  asks for `READ_CONTACTS` once (`rememberOptInPermission`, like calendar), then
  `queryContacts` reads up to 12 distinct contacts (display name + thumbnail) from
  `ContactsContract.Contacts`. The grid is 2×2 at medium / 4×2 at wide+large
  (prototype `cols = big?4:2, rows = 2`). While the live gate is active, a gated
  loop swaps **one random cell to a random contact every 2.1 s** (prototype
  `peopleStep`), rendered as a per-cell `Crossfade(tween 300)` (the prototype's
  `.av` opacity transition; the scale-bounce is dropped as a cosmetic detail). The
  back face is one large avatar + "<first> posted". Denied / no contacts → static
  glyph. `mosaicCells` (cycles contacts to fill every cell) and `colorFor`
  (deterministic initials tint) are pure + unit-tested.

- **Photos tile = picked selection, cross-fade, never flips.** `LiveFace.PHOTOS`
  is the only `flips = false` face, so it is excluded from the flip scheduler
  (`liveIds`) and ignores `flipped` — it is the prototype `data-noflip` face.
  `PhotosTileFace` reads `PhotosStore` (own DataStore `photos_tile.pb`, newline
  URI codec mirroring WeatherCache) and cross-fades through the photos every 3.0 s
  (`Crossfade(tween 800)`, prototype `slideshowStep` / `.photoslab` .8 s opacity)
  while active. Bottom-left shadowed "photos" label. No photos picked → static
  glyph.

- **Photos picked via OpenMultipleDocuments, persistable grant.** Consistent with
  the S18 wallpaper decision: the personalize sheet gains a "live photos · choose
  photos" row launching `OpenMultipleDocuments` (not the photo picker) so each URI
  takes a persistable read grant and the slideshow survives a reboot; the URIs are
  written to `PhotosStore`. An individual revoked/deleted URI just shows the tile's
  accent fill for that step.

- **Tile-sized down-sampled decode.** `rememberTileBitmap` decodes a content URI
  off-thread, down-sampled (`sampleSizeFor`, unit-tested power-of-two) to ~400 px
  (photos) / 120–300 px (avatars) so full-res images don't blow the bitmap budget
  in a small tile. Mirrors the wallpaper decode but bounded.

## S22 — notification listener: badges + mail/messages (FR-1.2 / FR-2)

- **One `NotificationListenerService`, snapshot rebuilt from scratch.**
  `TileNotificationListenerService` (declared in the `:feature:livetiles` library
  manifest so it merges into `:app` automatically) recomputes the whole picture
  from `getActiveNotifications()` on every connect/post/removal rather than
  diffing — cheap, and self-correcting if a callback is missed. It maps each
  `StatusBarNotification` to a framework-free `NotificationItem` and calls the
  pure `summarizeNotifications`, which is unit-tested.

- **Aggregation rules.** Ongoing (`!isClearable`) and group-summary
  (`FLAG_GROUP_SUMMARY`) rows are dropped, so a 3-message thread counts as 3 (not
  4) and music/navigation never badge. The badge count is the number of remaining
  notifications per package (FR-1.2); the mail/messages preview is the newest of
  them (title = sender, text = snippet, count = unread).

- **Live state is an in-memory singleton, not a repository.**
  `NotificationCenter` is a process-wide `StateFlow<NotificationSnapshot>` the
  service publishes to and the Start grid (badges) + conversation tiles (previews)
  collect. Notification state is ephemeral — rebuilt whenever the listener
  (re)binds — so there is nothing to persist (unlike weather's DataStore cache).

- **Faces bind to the tile's own package, not a resolved default app.** The
  mail/messages tiles read `NotificationCenter.conversationFor(tile.packageName)`
  rather than resolving the system default mail/SMS app — the pinned tile already
  *is* that app, so this is both simpler and correct. `LiveFace` gains `MAIL`
  (icon key `mail`) and `MESSAGES` (`messages`), both flippable; the back face
  shows the count with "unread" / "new" wording per the prototype.

- **Opt-in = settings deep-link, re-checked on resume.** Listener access is not a
  runtime permission, so the personalize sheet gains a "notifications" row
  ("badges & live mail") that deep-links to
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. `rememberNotificationAccess`
  re-checks `getEnabledListenerPackages` on every `ON_RESUME` so the toggle label
  flips the moment the user returns. Until granted the snapshot stays empty —
  every tile is un-badged and the mail/messages faces fall back to the static
  glyph, which is exactly the graceful opt-out.

- **Reconnect.** `onListenerDisconnected` clears the snapshot (immediate degrade)
  and best-effort `requestRebind`s; `onListenerConnected` republishes. Revoking
  access disconnects permanently — the opt-out path.

- **Badges only on app tiles.** Folder tiles don't aggregate child badges this
  session (the WP-faithful default shows badges on app tiles); the per-package
  count is keyed off `TileModel.App.packageName`. The badge pill follows the
  prototype `.badge` (22dp / 18dp on small, white-on-dark, inverted on light,
  ">99" caps to "99+").

## S21 — weather + calendar tiles (FR-2)

- **Live data lives in `:feature:livetiles`, not `:core:data`.** Weather and
  calendar sources sit beside the clock in the live-tiles feature module rather
  than behind a `:core:data` repository. They are tile-specific, Compose-driven,
  and need Android providers (CalendarContract, LocationManager, WorkManager); a
  thin core repository would add indirection without reuse. Pure formatters and
  codecs are still extracted and unit-tested.

- **Permission-agnostic face mapping; degrade in the composable.**
  `LiveFace.forIconKey` keeps mapping by icon key only (`weather`→WEATHER,
  `calendar`→CALENDAR, both flippable). The opt-in check happens in the tile
  composable: `WeatherTileFace`/`CalendarTileFace` take a `fallback` slot and
  render the static glyph (passed down from `AppTileContent`) when the permission
  is denied or no data is available. This keeps the `TileIcons` dependency in
  `:feature:start` and the mapping pure/testable.

- **One-shot opt-in, the WP way.** `rememberOptInPermission` requests the tile's
  permission once on first composition (coarse location for weather, READ_CALENDAR
  for calendar) — the tile asks for exactly what it shows. The ask is remembered
  (`rememberSaveable`) so it is not re-raised; a denial leaves the tile static
  until a later process re-asks. A dedicated re-prompt / settings entry is a later
  pass.

- **Pluggable weather provider + offline sample.** `WeatherProvider` is a
  `fun interface`; a real build swaps in a network implementation without touching
  the worker/cache/tile. Until then `SampleWeatherProvider` returns the prototype
  forecast (23°, partly cloudy, 26/17, "rain by 6pm · 40%") so the tile is
  demonstrable on-device — but only once a `WeatherQuery` resolves, so opt-in is
  still enforced.

- **WorkManager refresh, lazily scheduled.** `WeatherRefreshWorker` is a
  `CoroutineWorker` enqueued as a unique 30-min periodic job (KEEP) plus an
  immediate one-off, scheduled from `WeatherTileFace` only when a weather tile
  appears — no weather tile, no background work. It resolves a query via
  `resolveWeatherQuery` (granted coarse location → manual-city fallback → null =
  skip, tile stays static), fetches, and writes `WeatherCache`. Location is a
  best-effort `LocationManager.getLastKnownLocation` over enabled providers (no
  Play Services); fetch failures `Result.retry()`.

- **Weather cache = own DataStore + flat codec.** `WeatherCache` is a typed
  DataStore (`weather_cache.pb`) using a tolerant `key=value` `WeatherCacheCodec`,
  mirroring `SettingsCodec` (S17). It holds the last snapshot (null = no data yet,
  tile static) and the `manualCity` fallback. The city is kept here (not in
  `LauncherSettings`) so the feature is self-contained; a city-entry UI is
  deferred — without location grant or a set city the tile stays static, which is
  the faithful opt-in behaviour.

- **Calendar via CalendarContract.Instances, polled while active.**
  `queryUpcomingEvents` reads the next two events (title/begin/end) in a 36-hour
  window; `CalendarTileFace` re-queries every 5 min while the live gate is active
  (rolls finished meetings off) and stops when paused. Front = next event, back =
  the following one. `eventTimeLine`/`calendarEvent` are pure (24-hour start +
  compact `30m`/`1h`/`1h 30m` duration; all-day/open-ended drop the duration),
  unit-tested.

## Post-S24 follow-up — resize cycle, edit selection, clock fidelity

- **Resize cycle is medium → small → wide → medium** (`TileSize.next`), per a user
  directive — medium is the default landing size, so the cycle starts and returns
  there. This intentionally departs from the prototype's small→medium→wide order;
  the prototype set never had a "default = medium" anchor. Unit test updated.

- **Edit-mode tap: another tile switches selection; open space exits.** In
  `editDragGesture`, a tap (no lift/move) that lands on a tile other than the
  selected one now re-selects that tile (its corner controls move to it) via
  `enterEdit`; a tap on open space — or on empty area inside the grid — exits edit.
  Tapping the already-selected tile keeps it selected (only open space leaves
  edit). The `pointerInput` is now re-keyed on the selected id too, so the
  corner-control hit-test refreshes when selection switches mid-edit. Matches WP
  Start edit behaviour.

- **Clock time reverted to the normative 64/42 px + non-clipping line box.** The
  S24 "bigger clock" bump (84/54) made the time vanish on device: the prototype's
  `.lc .xl { line-height:.9 }` lets the tall weight-200 glyphs overflow the line
  box harmlessly in CSS, but Compose crops them, and the larger size pushed the
  crop past the glyphs. Restored the prototype sizes (`styles.css`: wide 64 /
  medium 42) and added `LineHeightStyle(trim = None)` so the full glyph is always
  painted regardless of the tight line height.

## Post-S24 follow-up — single tile colour, sticky merge, calendar date-only

- **One tile colour across Start (the global accent, default blue).** Start tiles
  no longer render their per-tile `colorId`; `TileView`/folder children now fill
  with `settings.accentId` (default `blue`), so the whole Start screen is one
  uniform colour, recolourable from the personalize accent swatch. The 14-colour
  palette and each tile's stored `colorId` are retained (data unchanged) — only
  the Start render ignores them. Departs from the prototype's multicolour default
  by user request.

- **Folder-merge target is sticky once entered.** Dragging a tile onto another to
  group them was unreliable: the normative merge zone is the inner 22–78% of the
  target, and a small finger wobble out of that band dropped the merge into a
  reorder. New pure `heldAsMergeTarget(rect, point, alreadyTarget)` keeps the
  22–78% *entry* rule but, once a tile is the target, holds it as long as the
  finger stays anywhere on that tile — so a near-centre wobble no longer breaks a
  folder-merge mid-drag. Unit-tested (`MergeZoneTest`).

- **Calendar tile shows the date only (time removed).** Per user request the base
  face dropped the `· h:mm AM/PM` suffix; `CalendarToday.time`, the `hour24/minute`
  params on `calendarToday(...)`, and `formatClock12` were removed (with their
  tests). The per-minute tick is kept so the date rolls over after midnight
  (re-assigning an equal `CalendarToday` is a no-op for recomposition).

## S26 — performance: baseline profile, macrobenchmark, recomposition audit

- **New `:macrobenchmark` module (`com.android.test` + `androidx.baselineprofile`).**
  `targetProjectPath = :app`, self-instrumenting. Three journeys: `StartupBenchmark`
  (cold `StartupTimingMetric`, None vs Partial compilation), `ScrollBenchmark`
  (`FrameTimingMetric` over deliberate grid drags), `BaselineProfileGenerator`
  (`includeInStartupProfile = true`). `:app` applies the baseline-profile plugin +
  `profileinstaller`, declares `<profileable android:shell="true"/>`, and consumes
  `baselineProfile(project(":macrobenchmark"))`. The plugin's managed
  `benchmarkRelease`/`nonMinifiedRelease` variants are the measurement/generation
  targets — no hand-rolled benchmark build type (an earlier attempt with one
  matched the unsigned `release` and failed to install).

- **Results (Pixel 6 emulator, API 34 — directional, not authoritative).** Cold
  start `timeToInitialDisplay` median ≈ 260 ms with the baseline profile / ≈ 264 ms
  without — well under the spec §3 800 ms budget. A real generated baseline profile
  ships in `app/src/release/generated/baselineProfiles/` (≈18.9k rules, ≈1.3k
  TileShell-specific). Scroll benchmark runs and captures frames (~314/run); the
  emulator's incomplete GPU frame timing yields no `frameDurationCpuMs` percentiles,
  so authoritative jank numbers need a physical device. Macrobenchmark's `EMULATOR`
  error is suppressed via the `androidx.benchmark.suppressErrors` arg at run time
  (not baked in), keeping the harness honest for device runs.

- **Recomposition audit → Compose stability config.** `compose_stability.conf`
  (wired into every Compose module from the root `subprojects` block via
  `composeCompiler.stabilityConfigurationFile`) marks the read-only `:core:data`
  models (`TileModel`/`FolderChild`/`TileSize`/`LauncherSettings`) and the standard
  `List`/`Map`/`Set` interfaces as stable. The compiler report confirms the effect:
  `TileView`/`AppTileContent`/`StartPage` are now `restartable skippable`,
  `TileModel` resolves as a `stable` parameter, and `NotificationSnapshot` /
  `NowPlaying` / `ConversationPreview` (Map/collection-bearing) are now `stable`, so
  tiles no longer over-recompose per scroll/flip frame.

- **Bitmap downsampling audit (no change needed).** All decode sites already run
  off the main thread (`produceState` + `Dispatchers.IO`) and downsample: photos
  via the unit-tested power-of-two `sampleSizeFor` (≤400 px shorter side), the
  people mosaic size-aware (300/120 px), app icons rasterised at 96 px. Memory
  budget is respected; a wide photos tile is slightly soft at 400 px (quality, not
  perf) — left as-is.

## S27 — accessibility + compatibility (release candidate, tag v0.9)

- **TalkBack: tiles are single labelled buttons with action menus.** Each tile uses
  `clearAndSetSemantics` (collapsing the inert icon/label/live-face descendants)
  to expose `contentDescription` = app/folder name + unread count, `Role.Button`,
  and `onClick` = launch/open. In edit mode the label gains the current size +
  selection ("Phone, medium tile, selected") and the drag-only operations become
  `CustomAccessibilityAction`s: resize, unpin, move back/forward (gated on
  position), done editing; activating a tile selects it. A non-edit "customize"
  action enters edit. The sighted drag/corner-control flow is untouched — these are
  the parallel screen-reader path. Verified via the on-device a11y node dump.

- **App list launch + pin via semantics.** `AppRow` (a raw `tapOrLongPress`) now
  also carries `clearAndSetSemantics`: launch on activate, "pin to start" as a
  custom action (the long-press-to-pin gesture is otherwise unreachable).

- **48dp touch targets.** App-list chevron 40→48, folder close 34→48 (and switched
  from a raw `pointerInput` to a real `clickable`+`Role.Button` so TalkBack can
  focus/activate it), edit-bar buttons get `defaultMinSize(48,48)`. The 26dp
  in-tile corner controls stay (sighted micro-affordance) — their accessible
  equivalent is the custom-action menu above.

- **Animations-off.** Compose's `animate*AsState`/`tween` already honour the system
  animator scale via `MotionDurationScale`, and flips are gated by
  `rememberLiveTilesActive` (which observes it). The one continuous animation —
  the edit-mode jiggle — is now explicitly gated: `rememberJigglePhase` returns 0
  when `ANIMATOR_DURATION_SCALE == 0`, so the grid is still for motion-sensitive
  users / battery saver. Verified the app launches and runs with animations off.

- **Display cutouts.** `displayCutoutPadding()` added to the Start scroll column and
  the app-list column so tiles/content clear a landscape notch. (3-button nav is
  already handled by the existing `navigationBarsPadding`; edge-to-edge via
  `enableEdgeToEdge`.) Font scale verified to 1.3× — fixed-dp tiles hold, `sp`
  labels scale, `maxLines = 1` prevents reflow.

- **RTL.** Standard layouts (app rows, edit bar, personalize, folder, tile labels)
  mirror automatically via Compose `LayoutDirection`, and directional padding uses
  `start`/`end`. The dense 4-column Start grid keeps a fixed left-to-right packing
  (it positions tiles by absolute pixel offset, and the drag hit-testing assumes
  it) — a deliberate constraint, matching the WP Start screen's anchored grid;
  full column mirroring is intentionally out of scope.

## Post-S27 fix — clock tile always seeds (live clock)

- **`t-clock` is now `liveOnly`.** The clock face is self-contained (it shows the
  system time with no app), so — like weather/calendar — the clock tile now seeds
  on first run *regardless of whether its role resolves*. Previously it depended on
  `roleFor("clock")` (SHOW_ALARMS) resolving; on devices whose clock app doesn't
  export that action the tile was silently dropped from the default layout, and the
  same unresolved role left the clock package out of `roleIconKeyMap` so pinning the
  clock app got `iconKey = null` (static glyph, no live clock). Marking it liveOnly
  fixes the missing tile and renders the live clock with a blank, inert launch
  target when no clock app resolves (tap opens the clock app when one does).
- **Clock role resolution hardened with `RoleQuery.AnyOf`.** Clock now resolves via
  SHOW_ALARMS → SET_ALARM → SHOW_TIMERS (first match wins), widening device coverage
  so tap-to-open and the pinned-clock live glyph work on more devices. The resolver
  recurses into `AnyOf`; tests updated.
- **Note:** `seedIfEmpty()` does not re-seed a populated grid, so existing installs
  must reset the layout (personalize → reset) or clear data to gain the clock tile.

## Post-S27 fix — app icon on the music now-playing face

- **The music tile now draws the playing app's launcher icon top-left**, matching
  the notification/conversation tiles. `MusicFront`/`MusicBack` are wrapped in a Box
  with `AppIconCorner` at `TopStart`. The icon's package is the tile's bound package
  for a music-app tile (Apple Music / YT Music), or the package of the active
  playing session for the generic music tile — so the source app is always
  identified while now-playing/paused is shown. (Calendar confirmed correct as-is —
  it keeps the date front + next-schedule back, no icon change requested.)

## Post-S27 — app list cleanup + recents, Start settings button

- **App-list rows drop the accent square.** `AppRow` renders the app's real
  launcher icon (40dp) directly on the list background — icon + name only, no
  backing block; apps with no resolvable icon fall back to the monoline "app"
  glyph. The `accent` param was removed from `AppRow`.
- **"recent" section at the top of the app list.** Above the alphabetical list (and
  only when the search box is empty) a "recent" header lists the up-to-5
  most-recently-launched apps followed by up-to-5 newly-installed apps (first
  install within 7 days), de-duplicated, recents-first. Pure `AppListFilter.topApps`
  (unit-tested) builds it. Recents are tracked without a usage-access permission by
  a process-wide `RecentApps` DataStore (`recent_apps.pb`, capped at 12) recorded at
  the single `AppLauncher.launch` choke point, so Start tiles, folder children and
  app-list taps all count. `AppEntry` gained `firstInstallTime` (from
  `LauncherActivityInfo`). The jump-grid scroll offsets past the recent rows.
- **Settings button on Start.** A settings (gear) icon sits just below the app-list
  chevron at the bottom-right (a 48dp target, hidden in edit mode like the chevron);
  tapping it opens the personalize sheet (`openPersonalize`) directly — previously
  only reachable via edit mode → edit bar.

## Post-S27 feature — transport controls on the music now-playing tile

- **The music live tile now has prev / play-pause / next buttons** under the
  track/artist while now-playing. `MediaCenter` keeps the live `MediaController`
  map alongside the published `NowPlaying` map (refreshed by `MediaSessionsEffect`)
  and exposes guarded `togglePlayPause` / `skipToNext` / `skipToPrevious` keyed by
  package (the bound app for a music-app tile, else the playing session for the
  generic music tile). New monoline `play`/`pause`/`prev`/`next` glyphs in
  `TileIcons` (`:feature:livetiles` now depends on `:core:design`).
- **Tap routing:** the buttons are `clickable(enabled = active)` so they're inert
  in edit mode (drag/select still works) and consume the tap when active;
  `tileGesture` now bails when a child consumed the pointer, so pressing a control
  doesn't also launch the app.
- **Known limitation:** the tile is a single `clearAndSetSemantics` node (S27), so
  the control buttons aren't individually exposed to TalkBack — a follow-up could
  add them as tile custom actions.

## Post-S27 feature — app-list context menu + tap-to-open notification tiles

- **App-list long-press now opens a context menu** (`DropdownMenu`) with "pin to
  start" and "uninstall", replacing the direct long-press-to-pin. Uninstall fires
  the system `ACTION_DELETE` dialog (`package:` uri, no special permission); the
  catalog updates live on removal via the existing package observer. The TalkBack
  custom actions on `AppRow` gain a matching "uninstall" alongside "pin to start".
- **Tapping a tile that's showing a notification opens that notification and clears
  the app's notifications.** The listener service publishes a parallel per-package
  `TileNotificationAction` map (newest dismissable notification's `contentIntent` +
  every dismissable key for the package) to `NotificationCenter` alongside the pure
  snapshot, and registers itself so `cancelNotifications(keys)` works. `onTileClick`
  calls `NotificationCenter.openAndClear(pkg)`: it sends the content intent (jumping
  into the relevant in-app screen) and cancels the package's notifications; returns
  true (caller skips its normal launch) only when an intent was sent, so a tile with
  no pending notifications — or only intent-less ones (now cleared) — still falls
  through to a plain launch. Group-summary keys are cleared too so the whole group
  empties; ongoing (music/nav) notifications are excluded, so they never clear and
  the tile launches normally. Pure `tileNotificationActions` unit-tested.

## Post-S27 feature — "wallpaper behind tiles" (show-through) mode

- **New personalize toggle "wallpaper behind tiles"** (in the transparent/blur
  group). The prototype has no such mode, so this is a WP-faithful addition: the
  classic Windows Phone photo-background look where the wallpaper is visible only
  *through* the tiles and everything else stays dark.
- **Setting** `LauncherSettings.tiledWallpaper` (codec key `tiledWallpaper`, default
  false; round-trip unit-tested), `SettingsRepository.setTiledWallpaper` /
  `StartViewModel.setTiledWallpaper`.
- **Rendering.** When on, the full-screen `WallpaperBackground` is replaced by a flat
  dark fill (`#0A0A0D`) so all gaps/borders stay dark. Each tile then draws the
  wallpaper as a *window* onto a screen-anchored canvas: `wallpaperWindow`
  (`:core:design`, gradient — radial centres shifted by −tileOrigin) or `photoWindow`
  (`:feature:start`, custom photo — cover-scaled then translated/clipped), both keyed
  off the tile's grid `slot` origin against `widthPx × viewportHeightPx`. Adjacent
  tiles continue the same image, so the grid reads as windows onto one photo. A 1 px
  `#66000000` hairline separates the windows. The custom photo bitmap is decoded once
  at the Start level (`rememberWallpaperBitmap` made public) and shared.
- **Precedence/decisions:** tiled-wallpaper wins over glass for the tile fill (they're
  alternative looks); the glass small-tile accent dot is suppressed in tiled mode. The
  window is anchored to **grid** coordinates (not absolute screen), so it doesn't
  parallax on scroll — simpler and still continuous; tiles scrolled well past one
  screenful fall back to the dark base. Gradient anchoring ignores the status-bar
  offset (invisible on a soft gradient).

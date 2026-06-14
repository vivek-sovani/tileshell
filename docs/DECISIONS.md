# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

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

# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

## Play Console "deprecated edge-to-edge APIs" — fixed in themes.xml, not code

Play Console's pre-launch report flagged deprecated `Window.setStatusBarColor`/
`setNavigationBarColor`/`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` usage,
attributed to `FeedStoreKt.<clinit>`, `WidgetStoreKt.<clinit>`, and an
obfuscated `A0.y.m`. Investigated by decoding the release build's own R8
mapping file (`app/build/outputs/mapping/release/mapping.txt`): `A0.y.m` is
Compose's own internal text-layout API-level shim
(`StaticLayoutFactory28$$ExternalSyntheticApiModelOutline0`), and the two
`<clinit>` hits are just `androidx.datastore.dataStore(...)` property
delegate initializers with no Window code at all — a misattributed stack
trace, not a real hit in either file. A repo-wide grep for the literal
deprecated method/constant names across every module's Kotlin/Java source
found zero hits. The actual (if minor) source was
`app/src/main/res/values/themes.xml`'s theme attributes —
`android:statusBarColor`/`navigationBarColor` (deprecated as of API 35, a
no-op once `enableEdgeToEdge()` — already called correctly in
`MainActivity.kt` — enforces edge-to-edge) and
`android:windowLayoutInDisplayCutoutMode="shortEdges"` (Android's own
edge-to-edge guidance recommends `"always"` over `"shortEdges"` for a fully
edge-to-edge app). Removed the two color attributes outright and switched
the cutout mode to `"always"`. The separate "edge-to-edge may not display
for all users" advisory needed no change at all — `enableEdgeToEdge()` was
already in place with extensive `statusBarsPadding()`/`navigationBarsPadding()`
handling throughout Start, the app list, and every personalize sheet.
Build + tests green.

## Quick panel restyled as a mini Start screen (WP tile style)

User feedback: the quick panel's generic grey-chip-and-slider look "does
not look interesting." Sketched and showed two visual directions before
implementing (per the user's own established "show visuals first"
preference): (A) recolour every toggle as a small Start-tile-style square —
accent fill when on, neutral dark tile when off, monoline icon + lowercase
corner label, matching the real Start grid's small-tile layout; volume/
brightness as wide accent tiles with a dark scrim covering the unfilled
portion (a live-tile-style progress fill) instead of a Material slider. (B)
a grouped glassmorphism/iOS-Control-Center look reusing the existing "glass
tiles" transparency mode. User picked (A) — it reuses the app's own tile
visual language instead of introducing a second UI system alongside it.
`QuickPanelChip`/`PillSlider` (Material `Slider`-based) replaced by
`QuickPanelTile` (plain colour-filled `Box`, chunked 3-per-row instead of
`LazyVerticalGrid` since 7 items never need lazy layout) and
`LiveTileSlider` (a raw `pointerInput` drag reads touch-x as a fraction of
tile width, mirroring the drag-gesture style already used elsewhere in this
codebase — e.g. `StartScreen.kt`'s pager/tile-drag gestures — rather than a
Compose `Slider`). Trade-off: the standalone mute-tap icon on volume tiles
is gone (a full-width drag-to-set tile can't also host a small competing
tap target without touch-region conflicts) — dragging to the left edge
already reads as "muted," and the icon still swaps to its muted glyph at
zero. See `docs/QUICK-PANEL-SPEC.md` §2a. Build + tests green.

## Quick panel: rotation lock, brightness, screen timeout via WRITE_SETTINGS

Direct follow-up to "what more settings could be added" — researched whether
`WRITE_SETTINGS` would trigger a new Play Console declaration before
implementing (user explicitly asked to check first). Confirmed via Google's
own Play Console Help docs (fetched live, not from training-data memory) that
the restricted-permissions list requiring the Permissions Declaration Form —
SMS/Call Log, location, broad photo/video, `MANAGE_EXTERNAL_STORAGE`,
`QUERY_ALL_PACKAGES`, body sensors, `SYSTEM_ALERT_WINDOW`, exact alarms,
full-screen intent, AccessibilityService, VpnService, Health Connect — never
mentions `WRITE_SETTINGS` anywhere. It's architecturally identical to the
already-shipped DND/notification-listener special-access pattern (one-time
Settings deep link, `Settings.System.canWrite()`/`ACTION_MANAGE_WRITE_
SETTINGS`, no manifest dangerous permission), so it's safe under the same
no-new-declaration constraint the whole quick panel feature is scoped to.
Added a rotation-lock chip (inline fallback: tap deep-links to the grant
screen until access is granted) plus brightness and screen-timeout rows
below the volume sliders — both replaced by a single "allow modify system
settings" row while ungranted, rather than rendering dead/disabled controls.
Screen timeout is tap-to-cycle through a small preset list (15s…30m) instead
of a picker dialog — simpler for 7 discrete values. See
`docs/QUICK-PANEL-SPEC.md` §6. Build + tests green (new `ScreenTimeoutTest`).

## Quick panel follow-up fixes: thicker pills, general DND settings, mute buttons, reachable feed toggle

Four user-reported issues after the first on-device pass of the quick panel
(see the entry below). **(1)** Chip pills were visually thin — bumped to a
52dp min height with more generous padding. **(2)** The DND chip, when access
isn't yet granted, was deep-linking to `ACTION_NOTIFICATION_POLICY_ACCESS_
SETTINGS` — technically correct (that's the screen that actually grants the
permission) but it renders as a per-app access list, not the general DND
settings a user tapping a "dnd" chip expects. Switched to the literal action
string `"android.settings.ZEN_MODE_SETTINGS"` (there's no public `Settings`
SDK constant for it, but it's a stable AOSP intent-filter present since
Marshmallow — verified live on the test device via `adb shell am start`),
falling back to the access-grant screen if a device's Settings app doesn't
expose it. **(3)** Media/ring volume rows gained a mute/unmute icon button
(remembers the pre-mute level to restore); alarm deliberately gets none —
already called out in `docs/QUICK-PANEL-SPEC.md` §3a as a footgun to avoid.
**(4)** Real bug, not a polish item: the user turned "show feed page" off
from inside the feed page's own gear-icon settings sheet — and then had no
way to turn it back on, since that toggle only ever existed inside the feed
page itself, which stops being composed (and thus reachable) the moment it's
off. `PersonalizeSheet` had actually been receiving a `feedEnabled: Boolean`
parameter all along with no setter and no UI row rendering it — a dead
param, presumably a gap from whenever the toggle was moved into the feed's
own sheet. Added `onFeedEnabledChange` + a new "feed & glance" `SettingGroup`
in `PersonalizeSheet` (reachable regardless of the feed's on/off state, since
Personalize is opened from the settings gear, not the feed page) with both
"show feed page" and "show device status card" toggles — the latter was also
only reachable from inside the same now-provably-unreliable feed settings
sheet. Both toggles are left in place in the feed's own sheet too (harmless
duplication) since they're convenient there when the feed is already on.
Build + tests green.

## Quick panel: two-finger swipe-up, Bluetooth has no live state

New feature, not in the WP prototype/spec — user-requested after a discussion
of which Android settings a launcher can control without declaring new Play
Console permissions (see `docs/NO-EXTRA-PERMISSION-FEATURES.md` and
`docs/QUICK-PANEL-SPEC.md` for the full design). Two decisions worth
recording: **(1)** the open gesture is two-finger swipe-**up**, sliding a
sheet up from the bottom edge — deliberately the mirror of quick search's
existing two-finger swipe-**down** (`QuickSearchGesture.kt`), so the two can
never both fire for the same swipe and neither collides with Android's own
status-bar-anchored pull-down. **(2)** the Bluetooth chip shows no live
on/off state at all, tap-only to `ACTION_BLUETOOTH_SETTINGS` — reading
`BluetoothAdapter.isEnabled()` requires the dangerous `BLUETOOTH_CONNECT`
permission on API 31+, which would need a new Play Console "Nearby devices"
declaration; every other chip (Wi-Fi, airplane mode, location, battery saver,
flashlight, DND, volume) needed either an already-declared permission, a
normal-protection permission (`ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` —
auto-granted, no Data Safety entry), or a special-access settings deep-link
identical in shape to the already-shipped notification-listener flow. Build
+ tests green (`QuickPanelGestureTest`).

## Closed folder's mini-grid shows a per-app badge, not just the folder's total

User-requested follow-up: a closed folder tile already showed one aggregate
notification count (`TileView`'s `badgeCount`, summed across all children by
package — see the "notification listener" work). That tells you *how many*
pending notifications the folder holds but not *which* app they belong to,
so a folder with mail+chat apps looked the same whether it was one app with
many unread or several apps each with one. Real WP folders don't show
per-child badges at all (a WP group is just a section of always-visible
tiles, each already showing its own badge in place) — this launcher's closed
folder collapses its children into a small icon mini-grid instead, so the
per-child badge has nowhere to live unless the mini-grid itself draws one.

Added a small `FolderChildBadge` (same white/dark-inverted pill as the
existing `NotificationBadge`, shrunk to fit an icon-sized mini-grid cell) in
`FolderTileContent`, positioned top-end of each non-"+N" cell whenever
`NotificationCenter`'s badge count for that child's package is > 0. The
folder tile's own aggregate badge (`TileView`) is unchanged — this is
additive, not a replacement. Threaded `NotificationSnapshot` one level
further down (`TileView` → `FolderTileContent`) since only the aggregate sum
was previously computed at the `StartPage` level. Does not extend to the
widget-stack carousel (`StackTileContent`) or the inline-expanded folder
view — both already show each member/child as its own full tile via
`AppTileContent`, which the top-level per-tile badge logic already covers.

## Folders: inline expand-in-place replaces the modal FolderOverlay

User-requested follow-up to the sticky-mode session (deliberately deferred
earlier, see "Tile arrangement" below): tapping a folder no longer opens a
full-screen overlay. It expands in place on the Start grid — the folder tile
becomes an up-arrow placeholder at its existing cell, and its children appear
as extra rows directly below it, pushing everything further down out of the
way. Tapping the placeholder again collapses it. Real WP doesn't literally
have this (WP's Start groups are always-visible sections, not collapsible
tiles), but it's what the user asked for and it fits this launcher's grid
model better than a modal ever did.

**Mechanism — render-time only, nothing persisted.** `StartViewModel.
expandedFolderId` (accordion: expanding one collapses whatever else was open)
is the only new state. Children are given synthetic ids
(`folderChildTileId(folderId, rowId)`, format `folderchild:<folderId>:
<rowId>`, parsed back with `parseFolderChildId`) and rendered via a real
[`FolderChild.asTileModel`] stand-in `TileModel.App` — this is what lets a
folder child flow through the *exact* same `TileView`/`AppTileContent`
rendering, corner-control zones, and accessibility semantics as any pinned
app, with no parallel code path to maintain. New pure `GridPacker.
expandFolderInline(placements, expandedId, children, columns)` runs *after*
the normal `pack`/`packSticky` computation (as a `postProcess` hook added to
both `DenseTileGrid` and `editDragGesture`): the expanded folder's own
placement is left untouched, its children are packed as their own local dense
block starting at its bottom row, and everything at or below that row shifts
down by the block's height. Because this only transforms the *output*
placements, it works identically regardless of whether dense or sticky mode
produced them, and reverses for free on collapse (no gridSlot/position is
ever written for the expansion itself) — verified with `GridPackerTest` cases
for "nothing above moves," "only what's strictly below shifts," and "children
land right after."

**Editing scoped to what's cheap and safe, not full parity.** Resize, the
colour picker, and pull-back-to-Start (unpin) all route through
`folderChildRef(id)` (parses a synthetic id back to its real `FolderChild`) at
the exact three points that already existed for top-level tiles —
`editDragGesture`'s corner-zone taps, its TalkBack-accessibility twin, and the
colour-picker's `onPick`. All three are pure "act on this one id" operations,
so they're safe to enable immediately. **Deliberately deferred**: drag-to-
reorder within an expanded section, rename, and the "make stack" chip. The
existing `order: List<String>` (top-level ids only, used for `reorderTiles`
splicing) never contains synthetic child ids by design, so a drag lift on a
child harmlessly no-ops (visually follows the finger, then snaps back on
release since nothing in `order` changed) rather than corrupting anything —
correct default behaviour, not a bug, but not full parity with the old
overlay's in-place reorder either. Revisit if this is reported as a gap.
Merging is disabled outright (`allowMerge = expandedFolderId == null`) while
any folder is expanded, since a folder child is never a valid merge
participant and without the guard a drag hovering near one would show a
confusing "merge target" highlight for a merge that would silently no-op.

**Verified on an emulator** via both a visual screenshot and cross-checked
`uiautomator dump` accessibility-tree snapshots (bounds before/after): tapping
a 3-child "social" folder correctly renders the up-arrow placeholder at the
folder's unchanged cell, with two children appearing in the row immediately
below (third off-screen) and unrelated neighbor tiles undisturbed; tapping the
placeholder again correctly removes the children and returns to the
collapsed layout, confirmed by both the screenshot and the accessibility
dump matching the pre-expansion state exactly. `FolderOverlay`,
`StackModeChip`, and `FolderTitleEditor` (the entire modal + its exclusive
helpers) are deleted outright, not left dead. Build + tests green (304 total,
`GridPackerTest` extended for `expandFolderInline`).

## Sticky mode wasn't actually active until the setting was toggled off and on

User-reported, right after making sticky the fresh-install default: the very
first time, the grid still behaved like auto-arrange — switching to
auto-arrange and back to windows phone style is what made it start working.

Root cause: gap preservation only works for tiles that have an anchored
`gridSlot`; a tile is anchored either by being dragged, or by
`StartViewModel.seedStickySlots` (previously inlined in `setTilePackMode`),
which stamps a `gridSlot` onto every currently-*unslotted* tile at its present
cell. That seeding only ever ran as a side effect of the user flipping the
setting *off, then back on* — never merely because sticky was already the
active mode. Since the fresh-install default layout's tiles all start
`gridSlot = null` and nothing else anchors them, every tile stayed
unanchored — and `GridPacker.packSticky`'s fallback for a fully-unanchored set
degenerates to the exact same append-only scan `pack` uses, so gap
preservation had nothing to hold anyone's position and the grid behaved like
plain auto-arrange until an explicit toggle round-trip happened to seed
everything at once.

Fix: `seedStickySlots` extracted out of `setTilePackMode` into its own
suspend function, called both there (explicit toggle) and once more at
`StartViewModel` init — right after `repository.seedIfEmpty()` completes, if
the persisted settings' `tilePackMode` is already `STICKY` (reads
`repository.tiles.first()` directly rather than the `tiles` StateFlow, which
may not have an active collector yet during init). Idempotent and cheap to
re-run every launch (a no-op once nothing is left unslotted), so no "only seed
once ever" flag was needed. Verified with `pm clear` on an emulator: unpinning
a tile on a truly fresh install now leaves the gap open on the very first try,
no toggle round-trip required.

## Windows-phone-style tile arrangement is now the default on a fresh install

User-requested: after verifying sticky mode against a real device, the
gap-preserving arrangement should be what a new install gets, not an opt-in
buried in Personalize. `LauncherSettings.tilePackMode` default changed from
`DENSE` to `STICKY`.

No seeding change was needed: the default layout's tiles all start
`gridSlot = null` (never anchored) regardless of which mode is active, and
`GridPacker.packSticky` renders an all-unanchored set identically to
`GridPacker.pack` (both just scan top-left-first with nothing already
placed) — so a fresh install looks pixel-identical to the old dense default
until the user actually unpins, resizes, or drags a tile, at which point gaps
start being preserved instead of repacked. Verified with `pm clear` on an
emulator: fresh install renders the same layout as before, and Personalize →
tile arrangement shows "windows phone style" already selected.

## Sticky-mode resize: shift the growing tile's own column instead of bailing out

User-reported: in windows-phone-style mode, resizing "finds first available
space on top or bottom" instead of expanding in place — but only for a tile
on the right with another tile to its left; never for a tile already on the
left. Also: the full-row-gap collapse wasn't kicking in either.

Root cause: `stickyPushDown` (the collision-resolution helper from the
earlier push-down fix) bailed out entirely — `if (col + w > columns) return
emptyMap()` — whenever the tile's *own* anchored column didn't leave enough
room to the right for the new, wider size. This is the common case for any
tile not already at column 0: growing to WIDE (which always needs the full
grid width) overflows from *any* other column. With the bail-out, nothing
about the resize was computed — no push, no collapse — so the DB just grew
the tile's size while its column stayed put; `GridPacker.packSticky` then
found that stored cell literally didn't fit the new footprint and silently
re-flowed it through its own unanchored-tile fallback (first free cell after
the bottom row) — which is exactly "finds first available space on top or
bottom." A tile already at column 0 never overflows this way, so it never hit
the bug — matching the "only on the right, with a tile on the left" report
precisely (a tile with nothing to its left is normally the one already at
column 0).

Fix: replaced the bail-out with an *effective column* — `col.coerceAtMost
(columns - w)` — that shifts the tile's own left edge just enough to keep the
new footprint inside the grid, closest to its original position. This
effective cell (not the stale stored one) now feeds both the push-down
collision search (so a former left-neighbor now inside the shifted footprint
gets pushed down like any other collision) and the full-row collapse check,
and is unconditionally written back as the resized tile's own new cell — not
just for tiles whose neighbors moved, but also when only its own column
shifted with no neighbor to push. Old `stickyPushDown(model, nextSize)` (which
derived col/row from the tile's stored slot) is now `stickyResizeSlots`
(computes the effective column and orchestrates push-down + collapse
together) calling a lower-level `stickyPushDown(excludeId, col, row, w, h,
columns)` that just does collision resolution against an already-decided box.

Verified on an emulator with a clean sticky-mode layout (phone at column 0,
camera at column 1): resizing camera from small straight to WIDE now shifts
it to column 0 and pushes phone down to the next row — staying in place and
displacing its neighbor, instead of jumping to the bottom of the grid.

## Corner-control zones weren't bounded to the selected tile's own rect

User-reported: tapping a *different* tile while editing unreliably fired
unpin, resize, or the colour picker instead of switching the selection — "many
times it opens colour palette or resizes or removes the tile." This is
separate from (and in addition to) the tap-to-switch `change.consume()` fix
below.

Root cause, in `editDragGesture`'s corner-control hit-test
(`StartScreen.kt`): each check was a one-sided threshold against the
*selected* tile's rect edges — e.g. `inUnpin = x <= r.left + zone && y <= r.top
+ zone` — with no matching lower/upper bound tying it to actually being
*inside* that tile. `x <= r.left + zone` is satisfied by any `x` all the way
to the left edge of the screen, and `y <= r.top + zone` by any `y` up to the
top — so a tap anywhere in the quadrant up-and-left of the selected tile's
top-left corner (however far away, including squarely inside a *different*
tile) counted as "unpin." Same for resize (down-right quadrant) and colour
(down-left quadrant). Depending on which tile was selected and where the next
tap landed relative to it, this could misfire any of the three actions on the
*previously* selected tile instead of switching to the tapped one.

Fix: each check now first requires `r.contains(down.position)` (the tap must
actually be inside the selected tile's own rect), so a zone only ever applies
within its own corner of its own tile, never spilling into a neighbor.
Verified on an emulator: selected phone, tapped camera right at the shared
border (a position that used to satisfy phone's resize-zone threshold) — now
correctly switches the selection to camera instead of resizing phone.

## Sticky mode: a full empty row is never allowed; edit-mode tap-to-exit fix

Two follow-ups from on-device testing of the sticky (gap-preserving) tile
arrangement (two entries below).

**Full-row-gap collapse.** User-stated invariant: a gap *within* a row (some
columns empty, others occupied) is the whole point of sticky mode, but a
**fully empty row** — one no tile's vertical span touches in any column — must
never persist; anything below it shifts up to close it. New pure
`GridPacker.collapseEmptyRows(placements)` (unit-tested: no-op when nothing's
fully empty, leaves a partially-occupied row alone, closes a single or several
consecutive fully-empty rows, and correctly treats a multi-row tile as
touching every row it spans) computes, for a given projected set of absolute
cells, which tiles' rows must decrease and by how much. Wired into every
sticky-mode mutation that can vacate a row:
- **Drag-drop** (`StartViewModel.collapseEmptyRowsAfterMove`): the tile's old
  cell is dropped from the projected set (replaced by its new one) before
  collapsing, so a row it alone occupied closes immediately.
- **Resize** (`collapseEmptyRowsAfterResize`): runs after `stickyPushDown`
  fully converges, over the complete projected layout (resized tile at its new
  footprint + pushed tiles at their settled cells + everyone else unchanged) —
  catches a row a pushed tile vacated, and, in principle, could even pull the
  resized tile itself up if a row above it were empty (shouldn't normally
  happen if the invariant already held, but the general computation covers it
  for free rather than special-casing).
- **Unpin** (`collapseEmptyRowsAfterRemoval`): the removed tile is dropped from
  the projected set first.
All three compute the projection *synchronously* from the current `tiles.value`
before launching any write — avoids a read-after-write race against Room's Flow
re-emission (which isn't guaranteed to land before the next statement in the
same coroutine). Merge isn't wired up (dragged tile removal there follows a
different code path); revisit if it's reported as a gap too.

**Edit-mode tap-to-switch (real fix — see below for the wrong first attempt).**
User confirmed on-device that tapping a different tile while editing still
exited edit mode entirely instead of switching the selection to it — the
`if (startId != selectedId()) onSelect(startId) else onTapExit()` fix (below)
was necessary but not sufficient. Root cause, found by adding temporary
`Log.d` calls at the `editDragGesture` release site and in
`enterEdit`/`exitEdit` and reproducing on-device via `adb shell input tap`:
`onSelect(startId)` *did* fire correctly, but `exitEdit()` fired immediately
after it, in the same instant — both for every tile tap, not just the
already-selected one. Cause: `editDragGesture`'s tap-handling branches never
called `change.consume()` (only the drag/lift path and the corner-controls
path did), so the plain-tap release event stayed unconsumed and was *also*
independently seen by the sibling `emptySpaceExit` gesture (attached higher up
on the whole screen, which exits edit mode whenever it sees an unconsumed,
un-moved release — this is exactly how it already knows to stay out of the
way of the edit-bar and corner controls, which do consume). Fix: consume the
change whenever `startId != null` (a genuine tile tap, whichever of the two
outcomes), so `emptySpaceExit` never gets a look at it. Verified end-to-end on
an emulator via `adb shell input swipe`/`tap` + screenshots: tapping a
different tile now switches the selection and *stays* in edit mode; tapping
the same tile (or true empty space) exits, as intended.

**Edit-mode tap-to-exit (first, incomplete attempt).** `editDragGesture`'s tap
handling already switched the edit selection to another tapped tile, and
exited edit mode on an open-space tap — but tapping the *already-selected*
tile did neither (silently no-op, stayed in edit mode). Added
`else onTapExit()` to also exit on that case — necessary, but this alone
didn't fix the actually-reported bug (see above), since the real defect was
the missing `change.consume()`, not the branch structure.

## Tile arrangement: user-selectable dense repack vs. WP-style gap-preserving grid

User-reported after checking a real Windows Phone device: the Start grid's
dense packing (`GridPacker.pack`, mirroring the HTML prototype's CSS
`grid-auto-flow: dense`) always repacks every tile toward the top-left the
instant anything changes — removing a tile, resizing one, changing column
count — so a gap left behind never stays open. Real WP doesn't do this: each
tile sits at a fixed cell, and a gap stays empty until the user drags
something into it. This is a genuine behavioural difference from the prototype
(which is otherwise the authoritative visual/behavioural reference per
CLAUDE.md) — the prototype relies on the browser's native dense-grid engine
for a simplification the real OS doesn't share, so this deliberately deviates
from it in favour of the verified real-device behaviour.

Rather than replace dense packing outright, added a **user-selectable**
"tile arrangement" setting (`LauncherSettings.tilePackMode`: `DENSE` default /
`STICKY`) in Personalize, next to "grid columns" — existing installs see no
change until they opt in. Scoped to the top-level Start grid only this
session; folder overlays keep dense packing unconditionally (a much larger,
separate change — replacing the current modal `FolderOverlay` with real WP's
inline-expand-in-place folder model — deferred to its own session).

**Data model**: rather than a schema overhaul, added one nullable
`TileEntity.gridSlot: Int?` (schema v5→v6 migration) encoding an absolute grid
cell (`row * 1000 + col`, `GridPacker.encodeSlot`/`decodeSlotCol`/
`decodeSlotRow`) — deliberately independent of the 4/5/6 column-count setting,
so changing columns can't corrupt a stored cell. `null` means "never
anchored." The existing `position: Int` (sequential rank, `ORDER BY
position`) is completely untouched and still drives dense mode and the
append-order tie-break among never-anchored tiles in sticky mode — no
migration risk to the existing behaviour.

**`GridPacker.packSticky`**: anchored tiles render exactly at their stored
cell; unanchored tiles (new pins, or an anchored tile whose cell no longer
fits after a column-count change) auto-place starting *after* every anchored
tile's bottom row — never backfilling an earlier gap. This also matches how
the user confirmed real WP places new tiles: always appended at the bottom,
never inserted into an existing gap, in *either* mode.

**Ambiguous mechanics resolved WP-faithfully**:
- **Resize collision (revised twice)**: growing an anchored tile in sticky
  mode can collide with a neighbor that dense mode would've silently repacked
  around. Attempt 1 blocked the resize outright on any overlap; user-reported
  this made growing a tile fail almost everywhere ("only medium to small
  working"), because a freshly-toggled sticky layout starts fully packed with
  no gaps. Attempt 2 un-anchored the colliding tile entirely so it floated to
  the bottom of the grid; user-reported this was also wrong — two adjacent
  tiles should stay adjacent, not have one flung away. Landed on **push-down**
  (`StartViewModel.stickyPushDown`): every tile the new, larger footprint would
  overlap is shifted straight down — same column, to just below whichever
  fixed tile(s) it now overlaps — cascading to whatever it in turn newly
  overlaps below (a small fixed-point relaxation loop, bounded by the tile
  count so it can't spin). Two side-by-side smalls, one resized to medium: the
  other tile moves one row down, staying directly adjacent below, instead of
  teleporting to the end of the grid. Only tiles in the affected column band
  move; everything else on the grid is untouched. Still not real WP's exact
  reading-order cascade, but keeps neighbors visually adjacent, which is what
  both reports were actually asking for.
- **Drag-and-drop**: in sticky mode, `editDragGesture`'s reorder-by-splice
  mechanic (`onReorderTo`/`onMoveToEnd`) is replaced by "drop the tile at
  whatever free cell the finger is over" (`onStickyDrop`, computed via the new
  `GridGeometry.cellAt`); dragging onto another tile's merge zone still merges
  exactly as before. A drop over an occupied, non-merge-zone cell is invalid
  and the tile stays where it was (no snap-back animation added — the next
  recomposition just re-renders it at its unchanged anchored cell).
- **Merge preserves the anchor**: `LayoutRepository.mergeTiles` now carries
  `target.gridSlot` into the newly-formed folder tile — otherwise the folder
  would silently "float" back to an unanchored position after every merge.
- **Re-enabling sticky mode doesn't discard a prior arrangement**:
  `StartViewModel.setTilePackMode` only seeds a `gridSlot` for tiles that have
  *never* been anchored (via `packSticky` around whatever's already anchored),
  not a blanket re-seed from the current dense layout — so toggling
  dense→sticky→dense→sticky again preserves whatever the user built in the
  first sticky session.
- **Accessibility**: sticky mode hides the "move back/forward" TalkBack custom
  actions (`canMoveBack`/`canMoveForward` forced false) — they reorder the
  list-backed sequence, which has no meaning once a tile sits at an
  independent anchored cell; drag-to-any-free-cell is sticky mode's equivalent
  gesture, but has no TalkBack-accessible substitute yet (revisit later).

Build + tests green (`GridPackerTest`/`SettingsCodecTest` extended). Verified
on an emulator: fresh install migrates cleanly (v5→v6, existing layout
renders unchanged with the setting defaulting to DENSE); app launches with no
crash. Interactive drag/resize verification in sticky mode is the user's own
on-device pass before deciding whether to commit.

## In-app "how to personalize" guide

Not a WP prototype/spec feature — new, ad-hoc, user-reported: several users
said they didn't know how to use the less-discoverable personalization
interactions (per-tile colour override, merging tiles into folders/widget
stacks, wallpaper reframing, tile background modes) even though every control
lives in `PersonalizeSheet`. `AboutSheet`'s existing "personalization"
`FeatureGroup` is a feature inventory, not a how-to, and `FirstRunHint` is a
one-shot generic welcome card that never resurfaces once dismissed — neither
addresses "how do I actually do this."

Added a new static how-to sheet, `PersonalizeGuideSheet.kt`
(`:feature:personalize`), reusing `AboutSheet`'s sheet chrome and its
`FeatureGroup`/`SectionHeader` composables (widened from `private` to
`internal`, same module, to avoid duplicating the bullet-list widget) — but
phrased as instructions ("in edit mode, tap the colour dot on a selected tile
to give just that tile its own colour") instead of feature statements.
Considered interactive coach-marks/tooltips pointing at the live controls
instead, but rejected for this pass: no spotlight/overlay system exists yet in
the codebase, and the cost didn't match a "users want a guide" ask — a static
sheet reusing existing patterns ships the same information for a fraction of
the effort. Wired with the same open/close `StateFlow` + one-sheet-at-a-time
visibility-gating convention as `aboutOpen`/`backupOpen`/`foldersOpen`
(`StartViewModel.personalizeGuideOpen`, `StartScreen.personalizeVisible` now
also excludes it). Two entry points: a permanent "how to personalize · guide
›" row, placed as the very first `SettingGroup` in `PersonalizeSheet` (above
even "theme") for maximum discoverability; and an auto-open-once the very
first time Personalize is ever opened, tracked by a `PersonalizeGuidePrefs`
flag (`tileshell.prefs`, key `personalize_guide_shown`) modeled exactly on
`FirstRunHintPrefs`. No schema change, no new permission.

## Glass tint follows tile accent (v1.9.0)

- **Problem (user-reported, same pre-release polish pass as the wallpaper fix
  below):** transparent ("glass") tiles never reflected the tile's own colour,
  in either theme. `Glass.fill(dark, transparency)` always returned one of two
  fixed neutral colours (dark charcoal / near-white) at an alpha derived from
  the transparency slider — a blue tile and a red tile rendered the identical
  grey/white glass square. This is a real bug, not a WP-prototype-fidelity
  choice: the prototype's `applyTransparency()` is genuinely accent-blind, but
  the user explicitly asked for the tile's own colour to carry through the
  glass effect, so this is a deliberate deviation from the ported prototype
  behaviour, not a restoration of it.
- **Fix:** `Glass.fill` gained an `accent: Color` param and blends it 65% into
  the neutral frost colour before applying the transparency alpha, so glass
  tiles are recognisably tinted per-tile instead of a single shared shade.
- **Per-tile, not per-screen.** The bug's root cause was architectural as much
  as the missing blend: `glassFill` was computed once in the top-level Start
  composable (`Glass.fill(dark, transparency)`, no accent available there) and
  handed down as a single `Color?` to every tile. Fixed by threading
  `glass: Boolean` + `transparency: Float` instead all the way down to
  `TileView`, `StackTileContent`, and `FolderTileContent`, each of which
  computes its own tint from whichever accent it already has locally resolved
  (`tileAccent`, `memberAccent` for the currently-visible stack member,
  `cellBg` for a folder mini-grid cell) — so nested cases (a stack's rotating
  members, a folder's child icons) each tint independently too, not just
  top-level tiles.
- **Wallpaper blend retuned in the same pass.** On-device testing of the
  light-theme wallpaper fix (below) found the original 82%/30% base/layer
  blend toward the light bg washed the gradients out almost to a flat light
  colour — retuned to 45% (base) / 12% (layers), which keeps a legible
  mid-tone version of the gradient's own hue instead of near-black (the
  original bug) or near-white (the overcorrection).

## Wallpaper theming: light-theme adaptation + gradient banding fix

- **Problem (user-reported, pre-release polish pass):** all 6 bundled gradient
  wallpapers (`Wallpapers.kt`) are designed dark-base-first (a near-black base
  with colourful radial glows). In light theme the base showed through
  unchanged wherever a glow hadn't reached — most of the screen — reading as a
  plain black backdrop behind/between tiles, clashing with the light theme's
  `#ece9e4` bg. Separately, "wallpaper behind tiles" mode's screen-anchored
  base (`TiledScreenDark`, a hardcoded `#0A0A0D`) never respected theme at all.
- **Fix, not a redesign.** Rather than hand-author 6 new light palettes,
  `themedBase`/`themedLayer` (`Wallpapers.kt`) algorithmically blend the base
  and each glow layer toward `LightColorTokens.bg`/white when `dark == false`
  (originally 82%/30%, retuned to 45%/12% after on-device testing showed the
  first pass washed the gradients out too far — see "Glass tint follows tile
  accent (v1.9.0)" above), so every gradient keeps its identity
  (hue/composition) but reads as a light backdrop instead of black.
  `wallpaperBackground`/
  `wallpaperWindow` both take a `dark: Boolean = true` param (default keeps
  every existing preview/picker-swatch caller, which intentionally always
  shows the dark identity look, unchanged). `TiledScreenDark` was removed
  entirely — the tiled-mode root fill and both `photoWindow` `darkBase` sites
  now use `colorTokens(darkTheme).bg`, matching the non-tiled path.
- **Banding fix, same session.** Each radial layer's `Brush.radialGradient`
  gained a third colour stop (a half-alpha version of the layer colour at 55%
  of the fade distance) instead of a hard 2-stop colour→transparent falloff —
  visibly smoother on the large, mostly-flat areas these gradients fall off
  into, on 8-bit-panel devices.

## Post-S29 — re-enable the 4×4 LARGE tile, gated to music/news on 5/6-column grids

- **The 4×4 `LARGE` size (removed post-S24) is back, but conditionally.** It is
  only reachable in the resize cycle for **music** and **news** app tiles, and only
  on a **5- or 6-column** grid. Every other tile keeps the
  medium → small → wide → medium cycle and never sees large. Rationale: a 4×4 tile
  fills the whole width of a 4-column grid (too dominant), and large faces only earn
  their space for content-rich live surfaces — the now-playing music tile and a news
  app tile. The user asked specifically for these two categories.
- **`TileSize.next()` gained a `largeAllowed: Boolean = false` parameter** rather
  than a second enum or a per-tile flag. Default `false` keeps the cycle (and every
  existing caller/test) unchanged; `StartViewModel.resize` computes `largeAllowed`
  per tile via the pure `AppCategories.allowsLargeTile(iconKey, app, columns)`.
- **Category match:** media = the designed `"music"` icon key OR
  `classify(app) == "entertainment"` (the music/video bucket: `ROLE_MUSIC`,
  `CATEGORY_AUDIO`/`CATEGORY_VIDEO`, or audio/video/stream tokens); news =
  `classify(app) == "news"` (`CATEGORY_NEWS` or news tokens). The check needs the
  catalogue `AppEntry` (the `TileModel.App` carries only `packageName`/`iconKey`),
  looked up by package in the ViewModel where `apps`/`settings` are available.
  **Initially the music match used `ROLE_MUSIC` alone, but that only catches the
  app declaring the `CATEGORY_APP_MUSIC` launcher role** — the seeded default music
  tile (e.g. YT Music) qualified, but pinned media apps like Apple Music / YouTube /
  Spotify did not. Broadening to the `"entertainment"` category fixed it: those apps
  already carry the now-playing live face, so letting them go large is consistent.
- **Large news notification face fills the tile.** A news app at 4×4 with no media
  session renders `NotificationTileFace`; the compact single-row layout left most of
  the 4×4 empty, so `NotificationFaceContentLarge` gives it a hero layout — the
  shared picture becomes a large image taking the available height, with the source
  + headline below in bigger type (no picture → the headline itself is the hero).
  Threaded via a `large` flag (`tile.size == LARGE`) into `NotificationTileFace`.
- **Auto-shrink on 4 columns (chosen over leaving large tiles as-is).** Switching
  the grid back to 4 columns demotes every large tile to MEDIUM
  (`setColumns` → `demoteLargeTiles` bulk `UPDATE`), so the invariant "no LARGE below
  5 columns" always holds and a 4×4 tile never dominates a 4-column grid. The
  alternative (keep existing large tiles) was rejected as it would leave an
  unreachable, over-sized tile the user couldn't have created on that grid.
- **Folders never carry LARGE.** A large tile dragged into a folder demotes its
  child to MEDIUM (like WIDE); a large merge *target* makes a WIDE folder tile (the
  widest the mini-grid face renders) — `TileMerge.clampForFolder` / `clampFolderTile`.
- **Legacy `LARGE` rows:** re-adding the enum value means `TileSize.valueOf("LARGE")`
  now succeeds, but post-S24 builds already decoded those rows to MEDIUM and
  re-persisted them, so nothing resurrects in practice. `GridPacker`/`GridGeometry`
  are size-agnostic, so 4×4 packing needed no layout change.

## Post-S29 — gallery photo picker + copy-to-internal-storage (supersedes S18/S23)

- **The wallpaper and live-photos pickers now use the Android Photo Picker**
  (`PickVisualMedia` / `PickMultipleVisualMedia`) instead of the SAF document
  browser (`ACTION_OPEN_DOCUMENT`). The photo picker opens the phone's gallery /
  system media picker, which is the "open my gallery" experience the user expects,
  and needs no storage permission. The earlier S18/S23 decision used SAF
  specifically so a *persistable* read grant (`takePersistableUriPermission`) would
  keep the wallpaper / slideshow alive across reboots — and the photo picker's grant
  is **not** persistable, so a naive swap would lose the image on reboot.
- **So the picked image bytes are copied into private storage** (`MediaImport`,
  `filesDir/wallpaper/` and `filesDir/livephotos/`) and a `file://` URI to our own
  copy is stored. Reading our own file via `contentResolver.openInputStream` needs
  no grant, so the choice now survives reboot *and* process death unconditionally —
  strictly more robust than holding a persistable grant on a foreign URI (which a
  revoked/deleted source could still break). Filenames are timestamped so the URI
  changes on each pick, busting the URI-keyed bitmap cache; the target dir is
  cleared before each new selection and on "clear selected photos", so copies don't
  accumulate. This supersedes the persistable-grant rationale in S18/S23.

## S28 — Beta hardening: OEM battery guidance + notification bitmap cap

- **OEM battery guidance is a two-layer problem.** On stock Android / most
  Samsung devices, requesting `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the
  standard Doze-whitelist dialog) is enough for the `TileNotificationListenerService`
  to survive. On Xiaomi/MIUI, Huawei/EMUI, OPPO/ColorOS, Vivo, and OnePlus,
  OEMs run a second independent kill switch — "AutoStart", "App Launch", or
  "Startup Manager" — that terminates the listener even after Doze exemption.
  `OemBatteryGuard.requestExemption` therefore tries the standard dialog first, then
  navigates to the OEM-specific battery management activity when one is resolvable
  on this device. The user still has to toggle AutoStart manually (no API). An
  empty `guidanceNote()` on stock Android means the extra row is text-free.
- **Warning row only when needed.** The PersonalizeSheet "notifications" group
  gains a second "background activity · fix ›" row that appears only when
  `notificationsEnabled && !batteryOptimizationExempt`. Once the user grants Doze
  exemption (and the compositor resumes, re-checking via `ON_RESUME`) the row
  disappears — so it is not a permanent fixture but a contextual guide.
  `rememberBatteryOptimizationExempt` mirrors the lifecycle pattern of
  `rememberNotificationAccess`.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission.** Added to the manifest.
  Per Android policy this is acceptable for a launcher (a system-replacement app
  that must remain resident), and it is sideloaded / in-house. No Play Store
  policy concern for the v0.9 release candidate.
- **Notification bitmap cap at 600 px (S28 OOM fix).** `EXTRA_PICTURE` bitmaps
  from messaging-app notifications can be full-resolution photos (several MB each).
  The previous `extractImage` returned them raw and stored all in a `StateFlow<Map>`
  keyed by package, creating an unbounded memory accumulation across all apps with
  notifications. Now downscaled to max 600 px on the longer axis — well above the
  largest tile render size at 3× density — before being held in the map. `Bitmap.
  createScaledBitmap` is wrapped in `runCatching` so a failed scale returns the
  original rather than crashing (e.g. a recycled bitmap edge case).

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

## Post-S27 follow-up fixes — notification open / uninstall / wallpaper parallax

- **Notification tile tap now reliably opens the app.** `openAndClear` was sending
  the notification's `contentIntent` with a bare `send()`, which can silently no-op
  on Android 12+ (notification trampolines / background-activity-launch). It now
  takes the foreground launcher `Context` and, on API 34+, sends with
  `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_…_ALLOWED)` so the
  target activity actually comes forward. When the content intent is null or fails,
  the caller still falls back to `AppLauncher.launch` — so a tap always opens the app
  *and* clears that app's notifications.
- **App-list "uninstall" made robust.** The single `ACTION_DELETE` intent (silently
  swallowed on failure) is replaced by a try-list: `ACTION_UNINSTALL_PACKAGE` (via
  `Uri.fromParts("package", …)`) then `ACTION_DELETE`, with a failure toast if neither
  resolves.
- **"Wallpaper behind tiles" now parallaxes correctly.** The window origin was the
  tile's static grid slot, so the wallpaper scrolled *with* the tiles. The window
  modifiers (`wallpaperWindow`/`photoWindow`) now take an `origin: () -> Offset` lambda
  read in the draw phase; the Start grid feeds each tile its live on-screen position
  (`statusBarTop + slot.y − scrollState.value`). The wallpaper is now fixed to the
  screen and the tiles move over it, revealing different slices as the grid scrolls.

## Post-S27 feature — notification image + album art on live faces

- **Notification images on mail/messages + generic notification tiles.** The listener
  service now extracts the newest notification's image per package — the big-picture
  style photo (`EXTRA_PICTURE`) if present, else the large icon (contact photo) via
  `getLargeIcon().loadDrawable().toBitmap()` — into a parallel `NotificationCenter.images`
  `StateFlow<Map<String, Bitmap>>` (kept out of the pure, unit-tested
  `NotificationSnapshot`). `ConversationTileFace` and `NotificationTileFace` render it
  behind the sender/snippet via a shared `TileImageBackground` (cropped image + a
  top-light/bottom-heavy vertical scrim so the white text stays legible). No image →
  unchanged accent face.
- **Album art on the music tile.** `buildMediaState` also pulls the session's album art
  (`METADATA_KEY_ALBUM_ART` → `_ART` → `_DISPLAY_ICON`) into a new `MediaCenter.artwork`
  `StateFlow`; `MusicTileFace` shows it behind both the now-playing and paused faces via
  the same `TileImageBackground`, so EQ bars / title / artist / transport controls sit
  over the cover.
- **Why parallel flows, not the data classes:** `NotificationSnapshot` and `NowPlaying`
  stay framework-free/unit-testable; the `Bitmap`s ride separate volatile/StateFlow
  channels, mirroring the existing `TileNotificationAction` / `MediaController` split.
- **Known limits:** image extraction (incl. `loadDrawable`) runs on the listener
  callback thread on each notification change — fine for infrequent posts, not cached
  across refreshes. Big-picture bitmaps are held at full size (bounded by the notifier);
  no downsampling. A contact-photo large icon shown full-bleed behind text reads as a
  zoomed background under the scrim (acceptable; matches the WP photo-tile look).

## Left feed page — Session A (real-data cards only)

A third pager page to the **left** of Start (swipe right), an independent info screen
inspired by the standalone prototype's `Feed` module. Reached by swiping right; Start is
still the HOME page. This session shows **only cards backed by real data** — news/sport/
stock and anything needing a network source are deferred to the RSS/market engine (S29).

- **Pager model.** Reused the existing finger-following pager rather than a new
  component: `progress` now ranges `-1 (feed) … 0 (start) … +1 (apps)`. Commit uses the
  prototype's **0.28** net-travel threshold via a pure, unit-tested `pagerCommitTarget`
  (replacing the old absolute `>= 0.5` test); the gesture's lower bound is clamped to 0
  when the feed is disabled. The app-list side is byte-for-byte unchanged.
- **Independent opaque screen.** The feed is an opaque page drawn *on top* of Start (its
  own `bg` background), sliding in from the left (`w·(-1 - progress)`) — mirroring the
  app-list page — so Start never shows through it. (An earlier behind-Start version let
  Start's faded tiles bleed over the feed and read as a translucent "glance" overlay that
  was hard to read.) Start still parallaxes **±22%** symmetrically and fades by
  `abs(progress)` underneath, visible only at the uncovered trailing edge mid-swipe.
- **No new module.** The feed UI lives in `:feature:start` (`feed/` package), not a new
  `:feature:feed` module — staying within the fixed module list (CLAUDE.md). The feed is
  a Start surface (a pager page), like the app-list page is hosted here. If the RSS
  engine (S29) grows, extract then.
- **Real Google Discover is intentionally NOT used.** Third-party launchers are not on
  Google's overlay allowlist; the only way to host the real `-1` feed is a sideloaded
  patched Google app. We render our own feed from data we already hold instead.
- **Live cards reuse existing sources, zero new plumbing:** weather card ← `WeatherCache`;
  today's agenda ← `queryUpcomingEvents` (READ_CALENDAR, already requested); now-playing ←
  `MediaCenter` (card hidden when nothing is playing).
- **Glance row = date + live clock.** The right side shows a live 12-hour `h:mm am/pm`
  clock (pure, unit-tested `feedClock12`), not the weather temp — the temp already lives
  on the weather card. The row re-reads on the minute boundary while composed.
- **Weather card opens fuller detail.** Tapping it runs a `weather <place>` Google search
  (same path as the weather tile's tap fallback) via the shared `launchWebSearch`.
- **Add a schedule from the feed.** The "today" section header carries a `+ add` action
  that opens the calendar app's add-event screen (`ACTION_INSERT` on
  `CalendarContract.Events.CONTENT_URI`); toasts when no calendar app handles it.
- **Weather hourly strip adapted.** The provider has no hourly series, so the card shows
  a **now / high / low** stat strip + the precip detail line instead of fabricated hourly
  temps. Hourly deferred until the provider exposes it.
- **No sample content.** Discover articles, the sport score card, and the stock watchlist
  were dropped from this session — showing fabricated headlines/scores/index values
  contradicts "real data only." They return in S29 wired to live sources (RSS for news;
  Moneycontrol / ET markets for the watchlist, defaulting to Indian indices).
- **Search pill → Google.** Typed query fires `ACTION_WEB_SEARCH` (Quick Search Box /
  Google app), falling back to a browser `google.com/search?q=` view; both guarded. Pure
  `googleSearchUrl` unit-tested.
- **Opt-out.** `feedEnabled` (default on) in the settings DataStore + a "left feed page"
  toggle in personalize; turning it off clamps the pager to Start⇄apps and slides back to
  Start if it was showing.

## Follow device dark-mode setting

The launcher now follows the **system dark-mode** setting by default via a new
`followSystemTheme` flag (default **true**) in `LauncherSettings`.

- **Effective theme** is computed once in `StartScreen`:
  `val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.dark`,
  then threaded everywhere the chrome is skinned (`colorTokens`, `Glass.fill`, the
  `darkTheme`/`dark` pass-downs to `StartPage` and `PersonalizeSheet`). Because it reads
  the Compose `isSystemInDarkTheme()`, the whole tree re-composes when the device toggles
  light/dark.
- **Manual choice retained.** The old `dark` boolean still persists the user's manual
  light/dark pick and is used only while `followSystemTheme` is false — so toggling
  "follow system" off restores their previous explicit choice rather than a default.
- **Personalize UI.** The theme group gains a "follow system" toggle; the manual
  dark/light segmented control is hidden while it is on.
- Codec round-trips `followSystemTheme` (tolerant: bad value → default); unit-tested.

## Now-playing transport controls on the feed

The feed's now-playing card gained **previous / play-pause / next** controls.

- **Reused, not duplicated.** Extracted the music tile's private control row into a public
  `MediaTransportControls(playing, packageName, tint, enabled)` in `:feature:livetiles`
  (the tile now delegates to it); the feed card renders the same row tinted `tokens.fg`.
  `ControlButton` gained a `tint` param so the icons match the host surface.
- **Right session.** The card now keeps the `MediaCenter.nowPlaying` map *entry* (package
  key + value), so the controls drive that package's session via the existing
  `MediaCenter.togglePlayPause/skipToNext/skipToPrevious`. Play-pause icon reflects
  `playing`.
- **Works on the feed even though live tiles are gated there.** `MediaSessionsEffect`'s
  `DisposableEffect` keeps the session listener registered and `MediaCenter` (incl.
  controllers) published regardless of the `active` flag — only the 2 s poll is gated — and
  it lives on the always-composed `StartPage`. So the feed's controls function; the only
  cost is that a mid-track change not signalled by the session-changed listener won't
  refresh the title until Start is foreground again (acceptable, matches existing gating).

## Live RSS news engine — Session B

The feed's "discover" section is now backed by **live RSS/Atom news**, replacing the
removed sample cards. Built in `:feature:livetiles` (alongside weather: provider/worker/
cache/pure-parser precedent), consumed by the feed page in `:feature:start`.

- **Pure parser.** `parseFeed(xml, sourceName)` handles RSS 2.0 and Atom via namespace-
  unaware `javax.xml` DOM (so `media:content`/`media:thumbnail` match by literal prefixed
  tag), extracting title, link (RSS text / Atom `href`), source (channel title), category
  tag, image (media/enclosure/inline `<img>`), and published time. Helpers `parseFeedDate`
  (RFC-822 + RFC-3339), `stripHtml`, `feedAgo` (now/Xm/Xh/Xd) are pure + unit-tested. A
  broken feed yields an empty list.
- **Store + defaults.** `FeedStore` (own `news_feed.pb` DataStore) holds the subscribed
  `FeedSource`s and the cached articles via a tolerant tab-delimited `FeedCodec`. Seeded
  with the chosen India feeds (`DEFAULT_FEED_SOURCES`: The Hindu, NDTV, Indian Express,
  Gadgets 360, TOI Tech, ESPNcricinfo, NDTV Sports, Moneycontrol, ET Markets, NDTV Food).
- **Worker.** `FeedRefreshWorker` (30-min periodic + immediate one-off, `ensureScheduled`/
  `refreshNow`, scheduled from the feed page) fetches each enabled feed over
  `HttpURLConnection`, parses, and `mergeFeedArticles` (dedupe by link, newest-first, cap
  40). A dead feed is skipped; retry only when *every* fetch failed (keeps last good cache).
- **UI.** Live `ArticleCard`s (thumbnail, source, title, tag, time-ago); tap opens the link
  in the browser (`ACTION_VIEW`). Remote thumbnails load via a tiny `rememberRemoteImage`
  (HttpURLConnection + BitmapFactory, downsampled, process-wide `LruCache`) — no image
  library. Empty cache → "no articles yet" card.
- **Management.** Personalize gains a "news feeds" group: per-feed enable toggle + remove,
  and an add-URL field. Wired through `StartViewModel` (`feedSources` StateFlow +
  add/remove/enable) to `FeedStore`; editing triggers an immediate refresh.
- **Stock watchlist intentionally NOT built.** Moneycontrol/ET RSS are *news* feeds, not
  quote feeds — real index values (Sensex/Nifty) need a quotes API with its own ToS/key.
  Per "no fabricated data," those feeds appear as market *news* in discover and the numeric
  watchlist is deferred until a real quotes source is chosen.
- **Known limits.** Article images are remote (network) and uncached across process death;
  no per-article read state; the 30-min cadence + immediate refresh on open/edit; feeds with
  TLS/redirect quirks may fail silently (skipped). No OPML import.

## Now-playing live updates, album art, wider news images

Follow-up fixes after on-device testing of the feed.

- **Event-driven media updates (fixes stale play icon + track name on the feed).** The
  play/pause icon didn't flip and prev/next kept the old title because the 2 s poll is
  gated off on the feed and the session-changed listener only fires on session add/remove
  — not on playback-state or metadata changes. `MediaSessionsEffect` now registers a
  `MediaController.Callback` per active controller (`onPlaybackStateChanged` /
  `onMetadataChanged` → republish; `onSessionDestroyed` → rebind), re-bound whenever the
  session set changes. Updates are now event-driven everywhere (feed and tile); the poll
  stays as a gated fallback.
- **Album art on the feed now-playing card.** The leading 44 dp box shows the session's
  cover from `MediaCenter.artwork` (already populated by `buildMediaState`), falling back
  to the accent + play glyph when a session carries no artwork.
- **More news thumbnails resolve.** Two gaps fixed: (1) `imageOf` now also reads
  `itunes:image`, scans `content:encoded` (not just description), accepts lazy `data-src`,
  skips non-image `media:content`, and normalises protocol-relative `//host` URLs;
  (2) the remote loader follows http↔https redirects manually (HttpURLConnection refuses
  cross-protocol auto-redirects, which many image CDNs use) and sends a browser-like
  User-Agent + Accept. Items genuinely without any image still render as text-only cards.

## Cricinfo images, manual refresh, news categories

Follow-ups after testing.

- **ESPNcricinfo (and other cleartext) images now load.** The cricinfo feed gives the
  image as `media:content medium="image" url="http://p.imgci.com/…"` — Android blocks
  cleartext `http://`, so it failed. `normalizeImageUrl` now upgrades `http://` → `https://`
  (those hosts serve https; verified `p.imgci.com` returns 200) and `imageOf` also reads
  the non-standard `<coverImages>` element cricinfo provides. No global cleartext opt-in.
- **Manual refresh.** The discover section header has a "refresh" action →
  `FeedRefreshWorker.refreshNow` (`StartViewModel.refreshFeeds`), toasting "refreshing
  news". `SectionHeader` generalised to a text action with an optional leading plus (today
  = "+ add", discover = "refresh").
- **Category selection.** `FeedSource` gained a `category`; `DEFAULT_FEED_SOURCES` is now a
  verified, category-tagged India set across `FEED_CATEGORIES` (nation, state,
  entertainment, cricket, sports, tech, business, food) with a sensible subset enabled by
  default. Personalize's "news categories" group shows a toggle per category (enables/
  disables all its feeds via `FeedStore.setCategoryEnabled`) plus the custom-URL add and a
  custom-feeds remove list. The codec persists `category` and backfills it by url-match for
  pre-category stored feeds. Custom feeds use `CUSTOM_CATEGORY`.

## Feed category fixes, live-tile restore, system settings

Three follow-ups.

- **State/entertainment toggles "not working" → missing feeds, now reconciled.** The
  cause wasn't the toggle: DataStore keeps the first-seen source list, so feeds added in a
  later version (the `state` Hindu-States feed, the newer `entertainment` feeds) never
  appeared in existing installs — toggling a category with no stored feeds did nothing.
  `FeedStore.reconcileDefaults()` (run on ViewModel init) adds any `DEFAULT_FEED_SOURCES`
  missing by url, leaving the user's enable/disable choices and custom feeds intact.
- **Per-feed selection + reliable refresh.** The personalize "news feeds" group now lists
  feeds **grouped under each category**: the category header toggles all its feeds, and each
  feed has its own toggle to pick individual sources. Every toggle (feed or category, on or
  off) now triggers `refreshNow`, and the worker **clears the cache when no feed is enabled**,
  so the discover list reflects changes promptly instead of keeping stale cached articles.
- **Re-add deleted live tiles (clock/weather/calendar).** Pinning the clock app only gets the
  live face when the alarm-action role resolves on the device; when it doesn't (or the tile
  was deleted), there was no recovery. `LayoutRepository.addDefaultTile(appId)` re-seeds a
  single default liveOnly tile (designed size/colour/icon key, seeder-resolved or blank
  target) appended to the grid; personalize's new "live tiles" group has + clock / + weather
  / + calendar buttons. Deterministic — independent of role resolution.
- **Android settings from personalize.** A "system" group with an "android settings" row
  opens `Settings.ACTION_SETTINGS`.

## Feed: reliable now-playing, accent cards, Google News, chips

Five feed follow-ups.

- **Now-playing reliable on the feed.** Per-app `MediaController.Callback`s proved
  unreliable on some players (artwork/play-state stale), and the Start media poll is gated
  off on the feed. Added a public `refreshMediaSessions(context)`; `FeedPage` polls it every
  1.5 s **while the feed is the foreground page** (`active`), so play/pause icon + album art
  + track stay current. Callbacks remain as a secondary signal for the tile.
- **Accent live-data cards.** The "your data" blocks — weather, today's schedule, now-playing
  — are now accent-filled with white text (WP live-tile look), grouping them apart from the
  neutral search pill and news cards. Agenda event bars and the now-playing art placeholder
  switch to white/translucent-white so they read on the accent. Discover/news cards stay on
  the neutral sheet.
- **Google News consolidator.** Added Google News India (`news.google.com/rss?...IN:en`) as
  an enabled national feed — an aggregator across outlets. (Its items are text-only; Google
  News RSS carries no images. MSN has no clean public RSS, so Google News is the practical
  consolidator.)
- **News section only when the feed page is on.** The personalize "news feeds" group renders
  only while `feedEnabled` — no point managing feeds with the page off.
- **Fewer toggles → chips.** Per-feed selection under an expanded category is now a `FlowRow`
  of tappable chips (filled = selected) instead of a toggle pill per row, which was getting
  noisy (local alone has 7 feeds). Category headers stay toggle rows.

## Feed tabs + Android widget host

The feed page is now tabbed (**glance | news**) and the glance tab hosts a real
Android app widget.

- **Tabs.** Search pill + glance row (date/clock) stay persistent at the top; a two-
  segment selector switches between the **glance** tab (weather, today, now-playing,
  widget) and the **news** tab (the discover feed). Each tab scrolls independently; the
  selected tab is `rememberSaveable`.
- **Widget host — self-contained, no MainActivity plumbing.** `WidgetSlot` owns an
  `AppWidgetHost` (started/stopped via `DisposableEffect` while the glance tab is composed),
  runs the system widget picker (`ACTION_APPWIDGET_PICK`) and the optional configure
  activity via `rememberLauncherForActivityResult` (the composition is already activity-
  hosted, so `:app` needs no changes), persists the bound widget id in a new `WidgetStore`
  DataStore, and renders the live `AppWidgetHostView` through `AndroidView`. Empty → an
  "add a widget" prompt; a "change"/"remove" affordance manages it. Everything is
  `runCatching`-guarded, and a widget whose provider was uninstalled (null info) clears
  itself — so a device that blocks third-party widget hosting just shows the prompt.
- Chose the `ACTION_APPWIDGET_PICK` path (system picker handles the bind for the host)
  over manual `bindAppWidgetIdIfAllowed` + `ACTION_APPWIDGET_BIND`, since `BIND_APPWIDGET`
  is signature-level and the launcher is the host. Added the `androidx.datastore` dep to
  `:feature:start` for `WidgetStore`.

## Widgets: multiple, proper sizing, resize, preview picker

Reworked the single-widget slot into a full multi-widget host.

- **Multiple widgets.** `WidgetStore` now holds a list of `HostedWidget(widgetId, heightDp)`;
  the glance tab renders each with its own **resize (± ) / edit / remove** controls, plus an
  "add a widget" button. Codec is one `id,heightDp` per line (unit-tested).
- **Proper height (fixes horizontal-widget compression).** Each `AppWidgetHostView` is given
  an explicit `Modifier.height(heightDp)` *and* `updateAppWidgetSize(...)` with that height,
  so the RemoteViews lays out for its real size instead of collapsing. Default height is the
  provider's `minHeight` (px→dp) clamped to 96–320 dp.
- **Vertical resize.** `−` / `+` step the height by 24 dp (clamped 72–520) and persist it;
  the view re-measures and `updateAppWidgetSize` re-applies.
- **Custom preview picker.** Replaced the system `ACTION_APPWIDGET_PICK` with an in-app
  `Dialog` listing `installedProviders` with each widget's **preview image** (`loadPreviewImage`
  → `loadIcon`, drawn to a bitmap) + label. Selecting one runs the bind flow:
  `bindAppWidgetIdIfAllowed`, falling back to `ACTION_APPWIDGET_BIND` (user-confirm) when not
  allowed, then the optional configure activity, then commit. "edit" re-runs the configure
  activity for an existing widget. All guarded; uninstalled providers self-remove.

## Widgets: long-press edit, drag-resize, taller defaults

Follow-up on the widget host.

- **Long-press to edit (like tiles); remove inside edit.** Each `AppWidgetHostView` gets a
  `setOnLongClickListener` (forwards the long-press while normal taps still reach the
  widget) that opens an edit overlay: a dim scrim (tap to exit), top-right **edit**
  (reconfigure) + **remove** pills, and a bottom **drag handle**. No always-visible
  −/+ buttons anymore.
- **Drag to resize.** Dragging the handle changes the height live (`detectDragGestures`,
  consumed so the feed scroll doesn't steal it) and persists on release; range 72–720 dp.
- **Taller defaults for calendar/collection widgets.** Initial height now uses
  `targetCellHeight × 60` (API 31+) or the provider `minHeight`, clamped up to 480 dp (was
  320), so agenda/calendar list widgets render fuller out of the box instead of clipped.
  (Very long lists still rely on the widget's own internal scroll; the larger ceiling +
  drag-resize cover the common case.)

## Landscape: two-panel layout (feed + Start) instead of stretched tiles

Grid sizing is purely responsive (`GridGeometry.of(constraints.maxWidth, columns)`), so in
landscape the doubled width was divided across the same 4 columns and tiles ballooned. Fix:
in landscape, drop the feed↔Start swipe and show both as side-by-side panels.

- **`isLandscape`** = `LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE`.
- **Feed on (default):** a `Row` with feed (left, `weight(1f)`, always `active=true`) and
  Start (right, `weight(1f)`). Start renders at half width (`widthPx/2f`) — the grid
  self-measures via `fillMaxWidth` and `widthPx` is passed to `StartPage` so the
  edit-drag hit-testing geometry matches, keeping tiles portrait-sized. **50/50 split**
  (user choice); no divider Box so the halves stay exactly equal.
- **App list in landscape covers the Start panel only** (user choice): its slide Box lives
  inside the right-panel Box, translating by `panelWidthPx`. The feed panel stays put.
- **Feed off:** no left panel; Start is centred at a capped portrait-like width
  (`min(widthPx, 460dp)`) so tiles still never balloon, and the app list covers the full
  width. (The `feedEnabled` setting does exist, default on; this is the defensive fallback.)
- **Pager refactor:** the `pager` val became `fun pagerModifier(pageWidthPx, lower)` so each
  layout drives the gesture with its own page width and lower bound (portrait −1 to reach the
  feed; landscape 0 — feed is a panel, not a swipe position). A `LaunchedEffect(isLandscape)`
  clamps `progress` to ≥0 on rotation so the pager never rests on the now-absent feed page.
- Page bodies are hoisted into `renderStartPage(pageWidthPx)`, `renderAppList()`,
  `renderFeed(active)` composable lambdas, shared by both layouts (no duplicated arg lists).
- Caveat: tiled-wallpaper "window" mapping uses the panel width as the full screen, so in
  that mode the show-through wallpaper shows its left portion in the right panel — cosmetic,
  only affects tiled-wallpaper users in landscape.

### Landscape follow-up: personalize sheet docks right-half

`PersonalizeSheet` gains a `rightHalf: Boolean = false` param. When true (passed
`isLandscape` from `StartScreen`) the sheet `Column` aligns `BottomEnd` at
`fillMaxWidth(0.5f)` instead of `BottomCenter`/full width, so it docks over the
Start (right) panel rather than spanning both panels. The scrim still covers the
full screen (tap anywhere dismisses).

### Landscape follow-up: shared SheetStage for all Start-spawned sheets

Confining one sheet to the right half by narrowing only its panel left the scrim
full-screen (dimming the feed). Extracted `core/design/SheetStage.kt`: a
`SheetStage(rightHalf, modifier) { … }` wrapper that hosts scrim + panel inside a
stage box sized to the right half (`fillMaxWidth(0.5f).fillMaxHeight()`, aligned
`BottomEnd`) in landscape, full screen otherwise. The scrim's `fillMaxSize()` and
the panel's `align(BottomCenter)` resolve against the stage, so both shrink to the
half automatically. Applied to `PersonalizeSheet`, `AboutSheet`,
`CategoryFolderSheet`, `BingHistorySheet` — each gains `rightHalf: Boolean = false`
wired to `isLandscape` at the `StartScreen` call site. Feed-spawned sheets
(`FeedSettingsSheet`) and the wallpaper crop overlay are left full-width for now
(the feed is the *left* panel, so its sheets don't belong on the right).

### Landscape follow-up: crop overlay right-half + back-gesture dismiss

- **Wallpaper crop/position overlay** (the photo-positioning step, both the post-pick
  crop and the "reframe" path) now routes through `SheetStage(rightHalf)` too, so it
  docks to the right half in landscape like the other personalize sub-sections. Its
  internal `BoxWithConstraints` measures the half region; the chosen focal point still
  maps onto the live wallpaper. (The OS photo *picker* — `PickVisualMedia` /
  `PickMultipleVisualMedia` — is a system activity we can't resize.)
- **Back-gesture dismiss.** The sheets relied on a scrim tap to close; in landscape the
  half-scrim made that worse and `AboutSheet` had no on-screen close at all. Added
  `BackHandler(enabled = visible) { onDismiss() }` to `PersonalizeSheet`, `AboutSheet`,
  `BingHistorySheet`, and `BackHandler(enabled = true) { onCancel() }` to
  `WallpaperCropOverlay` (`CategoryFolderSheet` already had one). A sub-sheet opened over
  personalize registers its handler later, so back peels them off one level at a time
  (sub-sheet → personalize → home).

### Landscape follow-up: clip right panel + fit the jump grid

- **Right-panel overflow.** As the Start panel parallaxes left (−22%) and the app list
  slides, the Start tiles drew past the panel's left edge onto the feed panel. Added
  `clipToBounds()` to the right-panel container Box so both layers stay inside the half.
- **Jump grid (A–Z board) collapsed to dots.** The board used `aspectRatio(1f)` square
  cells in a non-scrolling Column; in the short, half-width landscape panel 7 rows of
  square cells overflowed the height and the middle rows rendered as unreadable
  slivers/dots. Rewrote `JumpGrid` with `BoxWithConstraints`: cell = `min(fitWidth,
  fitHeight)`, font size + padding scale with the cell, grid centred. Portrait is
  unchanged (width is the limiting axis there, same ~83dp cells / 26sp).

### Large tile resized 3×3 (was 4×4)

`TileSize.LARGE` changed from `(4, 4)` to `(3, 3)` per request. Dimensions live only
in the `TileSize` enum; the packer and all rendering read `.cols`/`.rows`
generically, and faces measure from the packed pixel size, so the tile simply
renders smaller — no packer/migration/test changes. Gating is unchanged: large is
still reserved for media/news tiles on 5/6-column grids (`allowsLargeTile`,
`columns < 5` → false) and auto-shrinks to MEDIUM on a 4-column grid. Persisted as
the enum name `"LARGE"`, so existing layouts are unaffected. Comment references to
"4×4 large" updated to "3×3" across the data/start/livetiles sources.

## Widget stack: merge two large tiles into a swipeable carousel

Dropping a LARGE (3×3) tile onto another LARGE tile forms a **widget stack** instead of a
folder: a 3×3 footprint holding several full-size large tiles, each keeping its own live
face, auto-rotating with page dots.

- **Large for any app.** `AppCategories.allowsLargeTile` dropped the media/news category
  check — now just `columns >= 5` (large stays gated to the roomier grids). Any app tile can
  be resized to LARGE, so stacks aren't limited to music/news.
- **Stack is derived, not stored.** `TileModel.Folder.isStack = children.isNotEmpty() &&
  children.all { it.size == TileSize.LARGE }`. No `isStack` column, **no DB migration** — a
  folder renders as a stack exactly while every member is LARGE. The instant a member is
  resized down or a smaller tile is merged in, it reverts to a normal folder.
- **Merge.** `computeMerge` keeps members LARGE + the tile LARGE (`name = "stack"`) only when
  *both* sides are stackable (a LARGE app, or a folder that is already a stack); otherwise the
  existing folder path runs (members clamped to MEDIUM, tile to WIDE) — which is also the
  reversion path when a non-large tile is dropped onto a stack.
- **Render.** `StackTileContent` draws the current member by building a `TileModel.App` from
  the `FolderChild` at the stack tile's size and reusing `AppTileContent` (so music
  now-playing, the news hero, notifications, etc. all work for free). Auto-rotate is a gated
  `LaunchedEffect` (3 s, paused when `!liveActive`/edit/one member) mirroring the flip
  scheduler; page dots are tappable. Tap launches the current member; long-press opens the
  manage overlay; in edit mode the outer `tileGesture` is suppressed (`isStackTile`) so the
  grid drag owns move/select/unpin.
- **Reversion / dissolve.** Resizing a member (`resizeFolderChild` → `dao.collapseStackToFolder`)
  sets all members MEDIUM + tile WIDE. Pull-out and dissolve already preserve sizes
  (`removeFolderChild` re-pins `removed.size`; `convertFolderTileToApp` keeps the tile size),
  so pulling members out of a stack yields LARGE app tiles. `StartViewModel.resize` early-
  returns for a stack so the 3×3 footprint is fixed.
- **Edge:** dropping to 4 columns runs `demoteLargeTiles`, shrinking the stack tile to MEDIUM;
  it then renders as a smaller (2×2) stack and doesn't auto-restore to 3×3 (one-way).

Management reuses the existing `FolderOverlay`. Chosen auto-rotate + dots over swipe because
the global horizontal pager and the vertical grid scroll both contend with an in-tile swipe.

### Widget stack follow-up: vertical swipe instead of page dots

The stack's manual navigation changed from tappable page dots to a **vertical swipe**
inside the tile (large-only stacking is unchanged — 2×2 merges stay folders, no DB
migration). A single combined `pointerInput` on `StackTileContent` (keyed only on the
member count) distinguishes: quick tap → launch current member; press held past the
long-press timeout with no movement → manage overlay; vertical drag → ±1 member per
~44 dp, **consumed** as soon as it goes vertical so it wins over the Start grid's
vertical scroll (a clearly-horizontal drag is left alone). Callbacks are read via
`rememberUpdatedState`, so the recompositions a page change triggers don't restart the
gesture mid-swipe. The bottom page dots were replaced by a thin vertical scroll
indicator (track + thumb) on the right edge; members cross-fade on change, and the 3 s
auto-rotate stays. Swipe up → next, down → previous.

### Widget stack follow-up: slide animation + in-place delete-only edit

- **Slide animation.** Members now slide vertically (`AnimatedContent`, in/out offset =
  travel direction) instead of cross-fading, so each member reads as a distinct tile
  scrolling past — applied to both the swipe and the 3 s auto-rotate (`lastDir` tracks
  the direction).
- **No folder overlay for stacks.** Long-press now enters edit mode (not the overlay).
  A selected stack shows only an in-place **×** (top-left) that deletes the *current*
  member — no resize/colour (`TileControls` is gated off for stacks via `isStackTile`).
  Delete uses a new `deleteStackMember` (DAO/repo/VM): like `removeFolderChild` but it
  drops the member instead of re-pinning it to Start, dissolving the stack to a single
  tile when one remains. Pick the member to delete by swiping to it before/while editing
  (auto-rotate is paused in edit, so the shown member is the one removed).

## Quick search: two-finger swipe-down overlay (apps, contacts, web)

Not in the WP prototype or spec — a new request (search apps/contacts/web from Start via a
gesture). No dedicated "search" tile or button exists in this launcher, so a gesture was
the only entry point available; several choices below are therefore new, not ported.

- **Naming.** Called **"quick search"**, not "Spotlight" (that's iOS branding) — chosen to
  match the doc comment already on `launchWebSearch` ("the Quick Search Box / Google app
  picks it up"), so the name ties into an existing in-repo concept rather than inventing one.
- **Gesture: two-finger swipe-down, not a button/tile.** A `pointerInput` on the outer
  `BoxWithConstraints` (`StartScreen.kt`) tracks two concurrent pointers' *average* vertical
  travel since both went down; `isQuickSearchSwipe` (pure, unit-tested,
  `QuickSearchGesture.kt`) fires once the average downward travel clears 40dp and is more
  vertical than horizontal. Runs in `PointerEventPass.Initial` like the pager, but keyed off
  pointer *count* rather than direction, so it never competes with the single-finger
  pager/tile-drag gestures underneath — those simply never see a second pointer. Gated off
  during edit mode, an open folder, any personalize sub-sheet, or while already open
  (`quickSearchEnabled` in `StartScreen.kt`), and while it's open it disables the pager swipe
  the same way edit mode and the folder overlay do.
- **Slides from the top, not the bottom.** Every other overlay (`AboutSheet`,
  `BackupRestoreSheet`, …) slides up from the bottom sheet-style; `QuickSearchOverlay` slides
  down from the top edge instead, since that matches the gesture that opens it (reuses the
  same `SheetStage` + 300ms progress-driven `graphicsLayer` translation, just negated).
- **Three sections, capped at 5 rows each.** Apps via the existing `AppListFilter.filter`
  (already unit-tested, so no new app-matching logic); contacts via a new
  `ContactsSource.searchContacts` using `ContactsContract.Contacts.CONTENT_FILTER_URI` (the
  same filter URI the Dialer/People app use — matches name/phone/email, not just name); web
  always shown as a "search the web for '<query>'" row reusing the existing
  `launchWebSearch` (widened from `private` to `internal` so this new file can call it).
  Hidden apps (personalize → hidden apps) are excluded from the apps section, matching the
  app list.
- **Contacts degrade, don't block.** No new permission — reuses `READ_CONTACTS` (already
  requested for the people tile). Without the grant, the contacts section is replaced by a
  single "allow contacts access…" row wired to the same request launcher the personalize
  sheet already uses; the apps and web sections still work.
- **Tapping a contact opens the contact card**, not a call/message shortcut
  (`ContactsContract.Contacts.getLookupUri` + `ACTION_VIEW`) — the safer, permission-free
  action for a launcher-level search (calling/texting are the *contacts app's* job).

## Quick search follow-up: contact quick actions, pin-to-start, photos, recent/suggested

Four follow-up additions, all scoped to the quick search overlay from the previous session.

- **Call/message reintroduced, but as a long-press menu, not the default tap.** The prior
  session deliberately made tap-a-contact open the card, not call/text, reasoning that's the
  contacts app's job. Revisited: a long-press menu (450ms, same threshold as the app list's
  pin gesture — a private `tapOrLongPress` duplicated into `QuickSearchOverlay.kt`, a different
  module from `AppListScreen`'s) keeps the *tap* behaviour unchanged while adding "call"/
  "message" as an explicit, deliberate action alongside "pin to start". Numbers are looked up
  lazily (`ContactsSource.primaryPhoneNumber`, only queried once the menu opens) rather than
  for all 5 rows on every keystroke. `ACTION_DIAL`/`ACTION_SENDTO`, not a direct `CALL_PHONE`
  intent — opens the dialer/messaging app pre-filled, no new dangerous permission.
- **Pinning a contact reuses the App tile shape instead of a new tile kind.** A `TileModel`
  sealed-interface addition would touch merge (`TileMerge.kt`), stack/resize, accessibility
  labels, and every `when (tile)` in `StartScreen.kt`/`StartViewModel.kt`/`AppListViewModel.kt` —
  real surface area for what's fundamentally the weather/calendar tiles' own trick: a `TileModel.App`
  with no resolvable launch component. `ContactTile.encode`/`decode` (`:core:data`, pure,
  unit-tested) packs the contact's id + lookup key into `activityName` (`packageName` stays
  blank, exactly like `DefaultTile.liveOnly`); `iconKey = "contact"` marks it for rendering.
  Zero schema change, and the tile gets merge/resize/drag/per-tile-colour for free by riding the
  existing App tile machinery — a bonus of the representation, not something coded specially.
  The tradeoff: every `TileModel.App` consumer must remember to check `ContactTile.decode` before
  assuming a blank `packageName` means weather/calendar (`onTileClick`, `launchFolderChild`,
  `AppTileContent` all do).
- **Merge-dedup bug this surfaced, fixed alongside it.** `TileMerge.mergeKey()` keyed a blank-
  package tile on `iconKey` alone (`"live:${iconKey}"`) — correct while there was at most one
  weather, one calendar, one clock tile ever, but every pinned contact shares the same
  `"contact"` iconKey, so merging two contacts collided onto one dedup slot and silently
  dropped one. Fixed by also keying on `activityName` (blank for weather/calendar/clock, so
  no behaviour change there; unique per contact). Would have been latent forever without
  contact tiles existing to exercise it.
- **Contact tile face: full-bleed photo, or the tile's normal fill + glyph — never a
  separate flat colour.** With a photo, it fills the tile (`ContentScale.Crop`) with the name
  legible over a bottom gradient scrim — the WP people-tile look. Without one, *nothing* is
  drawn as a background by `ContactTileFace` itself; the "people" glyph + name sit directly over
  whatever the tile's normal accent/gradient/wallpaper-window fill already painted (same
  convention as `StaticTileGlyph`), so the per-tile colour picker still does something useful for
  a photo-less contact instead of being silently overridden by a separate initials-colour palette.
- **Photos section shipped, then removed — Play Console declaration, not a technical
  problem.** It worked (verified on-device: filename match, thumbnail, opens the photo) —
  images-only was the right technical scope (a true downloads/documents search needs
  `MANAGE_EXTERNAL_STORAGE`, much heavier). But `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` for
  photo *search* (not just the picker this app already uses elsewhere for wallpaper/live-photos)
  falls under Google Play's **Photos and Videos Permissions** policy: publishing to Play would
  require a declaration form justifying the access. Decided that obligation isn't worth it for
  a personal-launcher feature, so `MediaSearch.kt` and both permissions were deleted outright
  (`git log` has the working version if this is ever revisited with Play distribution in mind, or
  swapped for something that doesn't need the declaration — e.g. only ever showing photos the
  user already granted via the personal-photos/wallpaper picker, which are already private-storage
  copies with no extra permission needed, just a smaller corpus to search).
- **Recent searches record on action, not on every keystroke or on cancel.** `RecentSearches`
  (`:core:data`, mirrors `RecentApps`'s DataStore/codec exactly) is written only from the
  overlay's `act()` wrapper — used by every result tap and the keyboard "search" action — never
  from a scrim-tap or back-press cancel, so abandoned typing never pollutes the suggestion list.
  "Suggested apps" reuses `AppListFilter.topApps` (already unit-tested for the app list's own
  "recent" section) rather than inventing new ranking logic — one function, two call sites.

## Notification package alias for OEM companion-service splits

Found on a physical Samsung device: a pinned Gallery app's live tile never showed a pending
"story"/highlights notification, even though notification access was granted and the listener
was confirmed connected (`dumpsys notification` showed a live bound proxy). The notification was
real — `dumpsys notification --noredact` showed it posted by `com.samsung.storyservice`, a
distinct package from the Gallery app itself (`com.sec.android.gallery3d`). Every notification-
to-tile match in this app (badges, previews, images, tap-to-clear) is a plain package-name
lookup, so a notification posted by a *different-but-related* package is invisible to any tile,
by design — this isn't a bug in the matching logic, it's a gap the logic can't close on its own.

- **A small, explicit alias table, not a general heuristic.** `NOTIFICATION_PACKAGE_ALIASES`
  (`TileNotificationListenerService.kt`) maps `com.samsung.storyservice` →
  `com.sec.android.gallery3d`. Considered and rejected: fuzzy-matching by shared signing
  certificate/UID, or by app label similarity — both are the kind of clever-but-fragile logic
  that breaks in ways that are hard to debug later (a false match would misattribute a real
  notification to the wrong tile). A hardcoded table is honest about its scope: it fixes the one
  confirmed split, and future ones get added the same way once actually seen, not guessed at.
- **Remapped at the boundary, before anything pure sees it.** `StatusBarNotification
  .tilePackageName()` applies the alias once, right where `packageName` is first read
  (`toItem()`/`toActionRow()`/`notificationImages()` grouping) — `summarizeNotifications` and
  `tileNotificationActions` (both pure, unit-tested) stay unaware that aliasing exists at all.
- **The alias only affects grouping, not cancellation.** `NotificationActionRow.key` is left as
  the real `StatusBarNotification.key` — tapping the Gallery tile still cancels the actual
  `com.samsung.storyservice` notification via its real key; only the *lookup* (which tile does
  this belong to) is aliased, not the object being acted on.

## Play Store update prompt on Start

New ask: check Play Store for a newer version and prompt the user to update, from Start.

- **Flexible in-app update, never immediate.** Google Play Core's In-App Updates API offers two
  flows: IMMEDIATE (a full-screen, blocking takeover the OS draws until the update installs) and
  FLEXIBLE (silent background download, app stays usable, a small prompt to restart once ready).
  TileShell is the user's Home app — an IMMEDIATE takeover on top of the launcher would strand
  anyone who happens to unlock their phone mid-rollout. Only FLEXIBLE is wired up
  (`AppUpdateType.FLEXIBLE` in `rememberAppUpdateState`, `:feature:system`).
- **Module split: Play Core wrapper in `:feature:system`, banner UI in `:feature:start`.**
  `:feature:system` already owns the launcher's other OS-integration surfaces (default-launcher
  prompt, screen lock) and had no Compose dependency yet — added it (mirrors `:feature:livetiles`
  hosting `rememberNotificationAccess`/`rememberBatteryOptimizationExempt`, i.e. permission/
  system-state gates live next to *what* they gate, not next to the UI that reads them).
  `rememberAppUpdateState()` returns `(AppUpdateState, () -> Unit)` — no Play Core types leak into
  `:feature:start`, which gets a new one-directional `implementation(project(":feature:system"))`
  dependency (same pattern as `:feature:applist` → `:feature:livetiles`).
- **Banner, not a scrim dialog.** `FirstRunHint` is a one-time full-screen scrim because it only
  ever fires once, on a fresh install. An update prompt can recur every session until the user
  acts, so a `FirstRunHint`-style takeover would become naggy fast — `UpdateAvailableBanner` is a
  thin dismissible strip pinned to the top of Start instead, closer to the transient prompts
  elsewhere in the app (`PermissionRow`, wallpaper-crop toasts). Dismissing only hides it for the
  current state value; it resurfaces if the state changes (e.g. `AVAILABLE` → `READY_TO_INSTALL`
  once the background download finishes) since that's materially new information.
- **Re-check on `ON_RESUME`, same as `rememberNotificationAccess`.** Play can flag an update at
  any point in the session, not just at cold start — this keeps the check consistent with the
  other opt-in/state gates in the app rather than inventing a separate polling scheme.
- **Gated off editing/overlay surfaces.** The banner only renders when none of edit mode, the app
  list, an open folder, personalize, or quick search is showing (`showUpdateBanner` in
  `StartScreen.kt`) — it would otherwise float on top of a full-screen sheet that itself expects
  to own the top of the screen.

## Wallpaper crop zoom + wallpaper slideshow

New ask: the wallpaper crop overlay could only pan (horizontal/vertical), not zoom; and a wallpaper
could only ever be one fixed photo, not a rotating set.

- **Zoom is a pinch gesture on the existing crop overlay, not a separate slider screen.**
  `WallpaperCropOverlay` swapped `detectDragGestures` for `detectTransformGestures`, which reports
  pan and zoom together — reuses the exact same interaction (drag to reposition) users already
  know, adding pinch on top rather than a second control surface. `zoom` (1..3,
  `LauncherSettings.MIN/MAX_WALLPAPER_ZOOM`) is applied as a `graphicsLayer` scale on top of the
  already cover-cropped/aligned image, pivoted at the screen centre. Pan deltas are divided by the
  live zoom level before being converted to alignment change, since at higher zoom the same finger
  travel is a smaller fraction of the (visually magnified) image.
- **Tiled "wallpaper behind tiles" mode mirrors the same centre-pivot zoom.** `photoWindow()` (the
  per-tile screen-anchored window painter) zooms around the *screen's* centre expressed in each
  tile's own local draw coordinates, not each tile's own centre — otherwise every tile would zoom
  toward a different point and the "single photo behind all tiles" illusion would break. This keeps
  the crop-overlay preview and both live-render paths (normal + tiled) visually consistent (WYSIWYG).
- **Slideshow reuses the single-photo render path — no new UI plumbing.** `wallpaperSlideshowEnabled`
  rotates through `WallpaperSlideshowStore`'s (`:feature:livetiles`, mirrors `PhotosStore`) picked
  photos by periodically writing the next URI into the *same* `customWallpaperUri` field a single
  custom photo uses (`SettingsRepository.setWallpaperSlide`), via `WallpaperSlideshowWorker`
  (mirrors `BingWallpaperWorker`'s periodic-job shape). Every existing renderer (`WallpaperBackground`,
  tiled `photoWindow`, the crop/reframe overlay) already reads `customWallpaperUri` — none of them
  needed to learn about "slideshow" as a concept.
- **Mutually exclusive with Bing daily wallpaper, not with a single custom photo.** Bing and the
  slideshow both drive `customWallpaperUri` on a timer from different sources, so turning one on
  clears the other's flag (`SettingsRepository.setBingWallpaper`/`setWallpaperSlideshowEnabled`).
  Picking a single custom photo or a bundled gradient also turns the slideshow off. Toggling the
  slideshow off does *not* explicitly cancel Bing's `WorkManager` job (and vice versa) — matches the
  existing convention where `setWallpaper`/`clearWallpaper` never call `BingWallpaperWorker.cancel()`
  either; each worker's `doWork()` guards on its own still-enabled flag and no-ops otherwise, so a
  stale periodic tick is a harmless skip rather than a real bug.
- **Alignment/zoom reset to centred/1x on every slide change.** A crop chosen for one photo rarely
  suits a different one, so each rotation (and each freshly picked slideshow photo) resets
  `wallpaperAlignX/Y` to 0.5 and `wallpaperZoom` to 1 — same reset `setWallpaper` already does when
  switching to a bundled gradient.
- **Interval floor is 15 minutes.** `WorkManager`'s `PeriodicWorkRequest` cannot run more often than
  15 minutes; the UI only offers 15m/30m/1h/3h so every choice is actually honoured, and
  `ExistingPeriodicWorkPolicy.UPDATE` re-enqueues in place on an interval change (no cancel/re-enqueue
  race, mirrors how auto-backup's frequency pills reschedule).
- **Known limitation:** the "adjust position" reframe row is gated on `customWallpaperUri != null`,
  which is also true *during* an active slideshow (it writes the same field) — reframing a slideshow
  photo works, but the crop is discarded at the next scheduled rotation by design (see the reset
  bullet above). Not fixed further since a rotating wallpaper's per-photo crop is inherently
  transient.

## Wallpaper type selector (personalize reorganization)

Follow-up ask: the wallpaper group had grown into a flat stack of toggles (Bing, slideshow, custom
photo, bundled gradients all interleaved) with no way to tell at a glance which one was active —
reorganize into "pick one of five wallpaper kinds, then configure that kind."

- **No new persisted field.** `WallpaperType` (`NONE`/`PHOTO`/`SLIDESHOW`/`BING`/`STOCK`,
  `PersonalizeSheet.kt`) is derived, not stored — `currentWallpaperType(wallpaperId, customWallpaper,
  bingWallpaper, wallpaperSlideshowEnabled)` reads the same flags the data layer already treats as
  mutually exclusive, in the same priority order (Bing > slideshow > photo > stock > none). This is
  the same "no separate stored mode" approach the slideshow feature itself took reusing
  `customWallpaperUri` — one more derived-from-existing-state layer, not a second source of truth.
- **Selecting a type applies a sensible default immediately**, reusing the exact setters the old
  flat toggles already called (`onClearWallpaper`, `onPickCustomWallpaper`,
  `onWallpaperSlideshowChange(true)`, `onBingWallpaperChange(true)`,
  `onWallpaperChange(Wallpapers.all.first().id)`) — no new callback plumbing needed. Each setter
  already clears the other types' flags (mutual exclusion lives in `SettingsRepository`, not the
  UI), so switching types is correct by construction rather than by the sheet re-deriving what to
  clear. Tapping the already-active pill is a no-op (`if (type == currentWallpaper) return`).
- **The five-way selector reuses `SegCell`**, the existing dark/light segmented-toggle cell, rather
  than inventing a new pill component — one visual language for "choose exactly one of N" in this
  sheet. Labels are kept short ("slides" not "slideshow") since `SegCell` divides the row width
  evenly with `Modifier.weight(1f)` and has no built-in text truncation.
- **`PhotoButton` and `NoneWallpaperCell` deleted.** Both were only ever used inline in the old
  6-cell wallpaper grid (photo-picker button + "no wallpaper" cell mixed in with the 6 bundled
  gradients); now that photo and none are their own top-level types, the STOCK section is a plain
  3×2 grid of just the bundled gradients and neither composable has another caller.

## Wallpaper effects moved out of tile style + tile style sub-grouping

Follow-up ask: "blur wallpaper" and "wallpaper behind tiles" lived in the "tile style" group even
though both are wallpaper-rendering effects (`WallpaperBackground`/`photoWindow`), not tile
properties; "tile style" itself had also grown into an undifferentiated stack of eight controls.

- **Blur/tiled-wallpaper moved into the wallpaper `SettingGroup`**, as a small "effects" subsection
  below the type-specific content, shown for every type *except* `NONE` — `NONE` renders a flat
  `tokens.bg` fill directly in `StartScreen.kt` (`noWallpaper` branch) and never reaches
  `WallpaperBackground`, so both toggles would be inert there. No behavioural change to the toggles
  themselves (`onBlurChange`/`onTiledWallpaperChange` unchanged) — purely a placement fix.
- **"tile style" split into three labelled subgroups** (`glass`, `colour & fill`, `shape & spacing`)
  separated by `HorizontalDivider`s, mirroring the wallpaper section's new clarity. Reset stays a
  fourth, unlabelled block at the end (it already reads as a distinct action). No control moved
  between subgroups relative to before, other than the two that left for wallpaper — this pass is
  visual grouping only, not a re-think of which knobs belong together.

## Clock tile date clipped at 5/6 grid columns

Bug: the clock tile's date line (below the time) was partially clipped at 5 columns and fully
invisible at 6, on both the WIDE (top Start tile) and MEDIUM sizes.

- **Root cause: fixed-sp text sized for 4 columns, against a tile height that isn't fixed.**
  `GridGeometry.unit` is `(width - sides - gaps) / columns` — raising `columns` shrinks every
  tile's *pixel* size at a constant screen width, even though a tile's footprint in *units* (WIDE =
  4×2, MEDIUM = 2×2) doesn't change. `ClockFront`'s three stacked lines (time/weekday/date) were
  sized in fixed `sp` for the 4-column case and simply didn't fit in the shorter 5/6-column tile;
  Compose clips overflowing content at the tile bounds rather than reflowing it.
- **Fix: scale text/spacing by measured tile height, not by columns.** `ClockTile.kt`'s `ClockFront`/
  `ClockBack` wrap their content in `BoxWithConstraints` and compute
  `clockFaceScale(maxHeight) = (maxHeight / 165.dp).coerceIn(0.6f, 1f)`, multiplying every font size
  and spacing value by it. Measuring the actual rendered height (rather than threading `columns`
  down through `ClockTileFace`'s call sites) means the fix works regardless of *why* the tile got
  shorter — column count, a future tile-spacing change, anything — with no new parameter. 165.dp
  was picked so ordinary 4-column phones (WIDE ≈ 170dp+ tall in practice) clamp to scale 1 and stay
  pixel-identical to before; only the shorter 5/6-column case actually shrinks. WIDE and MEDIUM
  share this fix since both occupy the same 2-row footprint and shrink identically.

## AI assistants in quick search

New ask: quick search's "web" fallback should also offer asking an AI assistant (ChatGPT, Gemini,
Claude, Perplexity) — not in the WP prototype/spec.

- **Plain-text share (`ACTION_SEND`), not a guessed deep link or web URL.** Each assistant app is a
  registered share target that opens a new, pre-filled conversation from shared text — the same
  mechanism as sharing text from any other app — so `launchAiAssistant` (`StartScreen.kt`) uses
  `Intent.ACTION_SEND` + `setPackage(pkg)` + `EXTRA_TEXT` rather than a per-service web URL query
  parameter (which isn't consistently documented/stable across these services and would silently
  rot). Package names (`com.openai.chatgpt`, `com.google.android.apps.bard`, `com.anthropic.claude`,
  `ai.perplexity.app.android`) were verified against each app's live Play Store listing, not
  recalled from memory. Falls back to that app's Play Store listing when the share intent fails to
  resolve (not installed), matching `launchWebSearch`'s existing two-tier degrade pattern, so the
  row is still useful on a device without the app rather than a silent no-op.
- **New "ask ai" section in `QuickSearchOverlay`**, below "web", one row per assistant
  (`AiSearchRow` — reuses the search glyph rather than each brand's logo, keeping the launcher's
  original-monoline-icon convention with no third-party assets). Only shown once the user has typed
  something (same gate as the "web" section) — asking an assistant needs a query.

## Personalize bug fixes + further reorganization; search pills with real icons

Follow-up bug/polish pass on the wallpaper and tile-style work above, plus a redesign of the
quick-search AI/web rows.

- **Bing history pin no longer reclassifies as "photo".** Picking an image from "recent bing
  wallpapers" only reaches the picker from within Bing mode, but `BingWallpaperWorker`'s pin path
  called `setCustomWallpaper` — which (correctly, for the *general* "set a photo" case) clears
  `bingWallpaper`. From the wallpaper-type selector's point of view this looked like the pin
  silently switching you to "photo". New `SettingsRepository.setPinnedBingImage` keeps
  `bingWallpaper = true` instead, so the type stays "bing"; the daily worker still refreshes over
  the pinned image on its next scheduled run, same as any other day.
- **Glass and "wallpaper behind tiles" are now mutually exclusive at the data layer**
  (`SettingsRepository.setGlass`/`setTiledWallpaper`, each clearing the other on enable) rather than
  just being mutually exclusive at render time (`TileView`'s fill-priority `when` already picked
  tiled over glass) — previously both toggles could show "on" while only one was visibly doing
  anything.
- **Blur wallpaper is hidden (not merely disabled) while "wallpaper behind tiles" is on**, instead
  of the originally-planned "make blur actually work in tiled mode." That fix was implemented once
  — threading a `wallpaperBlur` flag down to `TileView`/`StackTileContent` and applying
  `Modifier.blur(18.dp)` to each tile's own wallpaper-window — and caused a real ANR on-device:
  every visible tile got its own RenderEffect layer, and compositing a dozen-plus simultaneous blur
  layers is far more expensive than one full-screen blur. Reverted rather than chasing a safer
  version (e.g. pre-blurring the shared bitmap once) given the effort/benefit here — tiled mode is
  a decorative extra, not worth the risk of a repeat performance bug.
- **"reset tile style" now confirms via `AlertDialog`** (cancel/reset) before calling
  `onResetTileStyle`, mirroring `LayoutHistorySheet`'s existing restore-confirmation pattern —
  the action is destructive-ish (loses corner radius/spacing/columns/fill/colour/font choices) and
  had no undo.
- **Sheet order reshuffled**: theme → grid columns → accent colour → typography → colour & fill →
  wallpaper → tile style (now just glass + shape & spacing) → live tiles → … `columns` and
  `fontStyle` controls didn't move logically, just physically (same params, same callbacks) — this
  is a pure ordering/grouping pass, not a re-think of what belongs together beyond pulling colour &
  fill out of tile style to sit with the other "pick a look" groups near the top.
- **Quick search's AI-assistant and web-search rows became icon pills**, replacing the vertical
  "ask X about Y" / "search the web for Y" list rows. Each `ServicePill` shows the target app's own
  real launcher icon via `rememberAppIconBitmap` (already used for the app-list icons — no bundled
  brand assets, no trademark concerns, and it only ever shows an icon for an app the user actually
  has installed) falling back to an accent-tinted initial when not installed. Added Microsoft
  Copilot as a fifth assistant and a "search" pill row (Google/Bing/DuckDuckGo/Yahoo/Yandex) above
  "ask ai" — every non-Google engine opens its own search URL directly (verified against each
  engine's real query-parameter docs, not guessed) since `ACTION_WEB_SEARCH` has no way to target a
  specific non-default engine; "google" keeps reusing `launchWebSearch`'s existing default-handler
  behaviour.

## Tile background as a third type selector; typography after colour & fill

Follow-up: the "effects" subsection under wallpaper (blur + wallpaper-behind-tiles) and the "glass"
subgroup under tile style (transparent tiles + transparency) were really the same underlying choice
— glass vs. tiled vs. neither — split across two different groups with plain toggles. Reworked into
a third type selector (`TileBackgroundStyle`), mirroring `WallpaperType`'s pattern exactly:

- **`SettingGroup(label = "tile background")`**, new, positioned right after wallpaper: a
  `none`/`transparent`/`behind tiles` segmented row (`SegCell`, same as the wallpaper selector).
  Selecting an option calls the existing `onGlassChange`/`onTiledWallpaperChange` callbacks — the
  mutual exclusion those already enforce (`SettingsRepository`, added for the earlier glass/tiled
  bug fix) means the selector is correct by construction, same as the wallpaper type picker.
- **Tile transparency slider + "blur wallpaper" now live under "transparent" only**, appearing the
  moment that option is selected — matches the wallpaper selector's "pick a type, see that type's
  options below" shape instead of the flat toggle list this replaced. "tile style" now opens
  directly on "shape & spacing" (colour & fill and glass have both moved out of it).
- **"typography" moved below "colour & fill"** (was above it) — both are now adjacent "how tiles
  look" groups ahead of "wallpaper"/"tile background", with no other reordering.

## Search-pill logos for services the user hasn't installed

Follow-up: `ServicePill`'s real-installed-app-icon tier only shows anything for services the user
actually has — in practice, usually just Google, since Bing/Yahoo/Yandex/DuckDuckGo/the AI
assistant apps are rarely all installed, so those pills fell back to a plain accent-tinted initial.

- **Second tier: Google's `s2/favicons` endpoint** (`faviconUrl(domain)`,
  `https://www.google.com/s2/favicons?domain=…&sz=128`) fetched via the existing
  `rememberRemoteImage` (already used for feed-article thumbnails — same `HttpURLConnection` +
  manual-redirect + `LruCache` machinery, no new networking code). This is a widely-used, stable
  but undocumented Google endpoint, not an official API — acceptable here because the fallback
  chain degrades gracefully (accent-tinted initial) if it ever goes away, and because it returns
  each service's own real favicon rather than a bundled/recreated copy of their logo. Verified
  directly (`curl`) for every current pill before shipping: all resolve, most at a full 128×128
  (Bing/Yahoo returned smaller native favicons — 32×32/48×48 — still legible at pill size).
  `SearchEngine`/`AiAssistant` both gained a `domain` field to drive this.
- **Backdrop differs by tier**: a real app icon already has its own opaque, full-bleed art — no
  backdrop. A favicon is often small and colour-keyed for a *light* background specifically (many
  favicons are ~32-48px and were never designed for a dark UI), so it gets a white circle behind it
  regardless of app theme, plus inset padding since favicons are usually square, not pre-cropped to
  a circle like an app icon. Only the final "neither loaded" tier uses the accent-tinted dot.

## Blur wallpaper available for "none" tile background too

Bug: "blur wallpaper" only showed under the "transparent" tile-background option, not "none" — but
both render through the same non-tiled `WallpaperBackground` (only "behind tiles" doesn't support
blur, per the ANR fix above). Split the two controls: "tile transparency" stays "transparent"-only
(nothing to tint otherwise), "blur wallpaper" now shows whenever the background isn't "behind
tiles" — i.e. for both "none" and "transparent".

## Widget picker grouped by app

Improvement: the feed/glance page's "+ add a widget" dialog (`WidgetPicker`, `WidgetSlot.kt`) listed
every installed `AppWidgetProviderInfo` as one flat, alphabetically-sorted list — hard to scan once
a phone has 20+ widgets spread across a handful of apps.

- **Grouped by owning app** (`AppWidgetProviderInfo.provider.packageName`), each group headed by
  that app's real label (`PackageManager.getApplicationLabel`, falling back to the raw package name
  if the lookup fails). Groups are sorted by app label, and each group's own widgets stay sorted by
  widget label — same ordering as before, just partitioned.
- **Implementation is a plain `LazyListScope` `forEach`** (`groups.forEach { item {…}; items(…) {…} }`)
  — the same "loop emitting header + items per group" shape already used by quick search's app/
  contact sections, not a new pattern.
- **Follow-up: groups are collapsible, collapsed by default.** A `Set<String>` of expanded package
  names (`remember { mutableStateOf(setOf()) }`, reset each time the dialog reopens) drives whether
  a group's `items(...)` are emitted at all; the header shows a `(count)` and a `▸`/`▾` indicator and
  toggles that package in/out of the set on tap. Collapsed-by-default rather than expanded-by-default
  because the whole point of grouping was taming a long list — leaving every group open by default
  would have looked identical to the old flat list until the user manually collapsed something.
- **Follow-up: group headers show the app's real icon at app-list size.** `rememberAppIconBitmap`
  (already used by `AppRow`/`ServicePill`) — no new icon-loading code. Name bumped from a 13sp dim
  caption to 16sp/`fg`/medium-weight, matching `AppRow`'s own label exactly, since with an icon now
  present the header reads as a mini app row rather than a section label.

## Feed search pill: removed the "g" avatar, whole pill opens quick search

Bug: the feed/glance page's search pill had an inline `BasicTextField` (typing + IME-search fired
`launchWebSearch` directly) plus a separate "g" avatar circle intended to open the same
apps/contacts/web/ask-ai overlay as the two-finger quick-search swipe. The "g" circle had no
`.clickable` at all, so taps there fell through to whatever was underneath — and the underlying
cause is structural: `StartScreen.kt`'s pager only parallaxes the Start page by 22%
(`translationX = -0.22f * widthPx * progress.value`) when Feed is foregrounded, so Start (including
its clock tile) is never actually off-screen and stays hit-testable under Feed's non-interactive
areas. Adding `.clickable` to just the "g" circle was tried first but didn't read as an obviously
correct fix given how easy it is to mis-hit a small 32dp circle inside a larger tap surface that
itself does nothing.

Fixed per explicit request ("remove this g button, wire search or ai chat through this text box"):
`SearchPill` (`FeedPage.kt`) is no longer an editable text field — it's a plain clickable `Row`
(icon + "search or ask ai" placeholder) whose entire surface calls `onOpenQuickSearch`
(`StartViewModel::openSearch`), opening the exact same `QuickSearchOverlay` the two-finger swipe
does. This both removes the redundant "g" button and eliminates the fall-through risk: the whole
pill is now one unambiguous tap target, and since `QuickSearchOverlay` renders as a top-level
sibling (not nested inside the Start-only page `Box`), it always intercepts the tap regardless of
the underlying pager translation math. `FeedPage`'s `onSearch` param (and the inline
`launchWebSearch` wiring in `StartScreen.kt`) was removed — quick search's own "search the web for
'<query>'" row already covers that path. Verified on-device: tapping the pill opens quick search
with the keyboard focused, no more Alarm/Clock fall-through. Build + tests green.

## Widget host: retry before deleting a widget with transient null provider info

Bug (reported: "samsung widgets are not running/showing properly"), diagnosed live on the physical
Samsung device already connected this session via `adb shell dumpsys appwidget`: TileShell holds
the widget bind grant fine (it's the default HOME, confirmed via `dumpsys package`/`resolve-activity`
— not a permission issue), but the alarm history showed Samsung's "spage" news widget
(`com.samsung.android.app.spage/...NewsWidgetProvider2x2`) being **added, then auto-cancelled 8
seconds later** — i.e. TileShell bound it and then immediately deleted it itself. `adb logcat`
around that package confirmed why it's slow: Samsung's newer system widgets (spage news, S Notes,
Reminder, S Health) are built on **Jetpack Glance**, whose provider registration goes through an
async background rendering session (`GWT:GlanceSession`/`GlanceStateDefinition`/`CoroutineSession`
log tags, plus Samsung's own "Kumiho" One UI Home widget-hosting layer) rather than being available
synchronously the instant `bindAppWidgetIdIfAllowed`/`ACTION_APPWIDGET_BIND` returns.

`WidgetView` (`WidgetSlot.kt`) called `manager.getAppWidgetInfo(widget.widgetId)` once per
composition and deleted the widget immediately if it came back null, on the assumption that null
only ever means "the provider app was uninstalled." That assumption doesn't hold for a
just-bound Glance-backed widget — the info lookup can transiently miss before Samsung's async
registration finishes, and TileShell was deleting the widget it had just added out from under
itself, which is exactly the add→cancel pattern seen in `dumpsys`. Fixed by giving a bound-but-
not-yet-visible widget a grace period: a null read now retries up to 4× at 500ms (2s total) before
concluding the provider is actually gone and calling `onRemove()` — a real uninstall still gets
cleaned up, just not instantly. This is a real, reproducible bug independent of any Samsung-only
platform limitation, so it's fixed for every OEM, not special-cased.

Caveat noted but not fixed (OS-level, not ours to fix): even once bound, some Samsung system
widgets may still render sparser or slower than in Samsung's own One UI Home, since part of their
layout/sizing logic is tied to Samsung's proprietary "Kumiho" hosting extensions that no
third-party `AppWidgetHost` (including this one) has access to.

## Widget host: don't trust an OEM configure activity's result code

Follow-up bug in the same area (reported: Samsung Health's "Daily activity" widget "not shown in
gadgets even after adding"). Diagnosed live via `adb logcat` while reproducing on the physical
Samsung device: right as Samsung Health's `DailyActivityWidgetReceiverGlance` logged
`update widget - id = AppWidgetId(appWidgetId=4228)` (i.e. it was actively initializing after the
user finished its own "Widget settings" configure screen, `DaHomeWidgetSettingActivityOneUI7`),
TileShell's own `AppWidgetHost.deleteAppWidgetId(4228)` fired and the system immediately logged
`cannot find widget for appWidgetId=4228`. TileShell deleted the widget it had just walked the user
through configuring.

Root cause: `WidgetSection`'s `configureLauncher` callback (`WidgetSlot.kt`) deleted the widget
whenever the configure `Activity` didn't return `Activity.RESULT_OK`, per the standard
`ACTION_APPWIDGET_CONFIGURE` contract. Samsung's `DaHomeWidgetSettingActivityOneUI7` doesn't
reliably call `setResult(RESULT_OK)` on save — it evidently `finish()`es with the default
`RESULT_CANCELED` even when the user picked options and the widget went on to initialize normally
on Samsung's side. Trusting that result code meant a correctly-configured Samsung Health widget
was silently thrown away every time.

Fixed by no longer trusting the configure activity's result code at all: the widget was already
bound (allocated + `bindAppWidgetIdIfAllowed`/`ACTION_APPWIDGET_BIND`) *before* configure ever
launched, so `manager.getAppWidgetInfo(id)` still resolving after configure returns is a more
reliable "did this actually work" signal than an OEM's self-reported result code — `commit()` now
runs whenever the id is still validly bound, regardless of `resultCode`, and only deletes when the
provider info is genuinely gone. Trade-off accepted: a user who backs out of a configure screen
without saving now gets the widget added in its default/unconfigured state rather than nothing —
preferred over the previous failure mode (silently losing a correctly-configured widget), and it's
still one tap to remove via the existing edit/remove control. `bindLauncher` (the earlier,
system-owned `ACTION_APPWIDGET_BIND` permission dialog, not an OEM activity) keeps its strict
`RESULT_OK` check — that result code comes from the OS itself, not a third-party app, so it's
trustworthy.

## Widget default height: scale to the provider's own aspect ratio, not raw minHeight

Improvement (reported after the fixes above got Samsung Health's widget showing at all: "it is
showing but in small size... can we display the widget as per the recommended widget size by
provider... I mean proportion"). `commit()` (`WidgetSlot.kt`) previously set a newly-added widget's
height directly from `provider.minHeight` (or `targetCellHeight * 60dp` on API 31+), ignoring width
entirely. Every widget slot in the feed renders at the full device width, so a widget authored for
a narrow cell (say a 2-column ~110dp-wide layout) got its designer-intended *height* applied
verbatim to a much wider slot — squashing its recommended proportions into something visibly
squat/undersized.

Fixed by deriving an aspect ratio from the provider's own recommended footprint and scaling it to
the slot's actual width, instead of using minHeight as an absolute value: API 31+ providers publish
an explicit recommended cell size (`targetCellWidth`/`targetCellHeight` — literally "recommended
size" in the platform's own terms) and its ratio is applied to `widthDp`; older providers fall back
to the `minWidth:minHeight` ratio as the next-best proxy. Same final `coerceIn(96, 480)` sanity
clamp as before. Only affects *newly added* widgets — an already-hosted widget's height is
persisted in `WidgetStore` and isn't retroactively recomputed, so an existing undersized widget
needs a remove-and-re-add (or a manual drag-resize) to pick up the new proportional default.

## Widget host: `Bundle.EMPTY` silently broke size reporting to every provider

Follow-up (reported after the aspect-ratio fix above: "rendered big (square) but characters are
still small"). Diagnosed via `adb shell dumpsys appwidget` on the physical device: the hosted
Daily Activity widget's `options` bundle was `Bundle[{appWidgetCategory=1}]` — no
`appWidgetMinWidth`/`MaxWidth`/`MinHeight`/`MaxHeight` keys at all, on *every* widget TileShell
hosts, not just Samsung's. The widget box itself was correctly big, but the provider had never
been told its real size, so it kept rendering whatever default/smallest layout it falls back to
when it thinks it has no room — hence a big empty box around small, unscaled content.

Root cause: `WidgetView`'s `AndroidView.update` block called
`view.updateAppWidgetSize(Bundle.EMPTY, widthDp, liveHeight, widthDp, liveHeight)` on every
recomposition. `Bundle.EMPTY` is Android's immutable singleton; `updateAppWidgetSize` calls
`.putInt(...)` on the options bundle it's given to stash the computed min/max width/height before
pushing it to `AppWidgetManager.updateAppWidgetOptions` — calling `.putInt()` on `Bundle.EMPTY`
throws `UnsupportedOperationException`, which the surrounding `runCatching` silently swallowed on
literally every call, so the size update never once reached any provider. Fixed by passing a fresh
`Bundle()` instead. Verified via `dumpsys appwidget`: every hosted widget now reports real
`appWidgetMinWidth`/`MaxWidth`/`MinHeight`/`MaxHeight` values (e.g. the Daily Activity widget now
shows a correct 316×316dp square) instead of an empty bundle — this was starving *every* hosted
widget of size info, not just Samsung's, so Gmail/ChatGPT/Apple Music/etc. should all render more
appropriately now too, not only the widget that happened to surface the bug.

## Square widgets render centered at half width, not stretched full-width

Improvement, once the previous two fixes got a real, correctly-sized square widget on screen:
"it is spanning across width. can 2x2 be shown half size centrally. and 2x4 and 1x4 full width."
Every hosted widget was rendered `fillMaxWidth()` regardless of its actual shape — fine for a
widget designed to span wide (a 4-column-style layout), but a small squarish one (2x2-style
icon/toggle widget) just looks stretched thin edge-to-edge.

New `isSquareWidget(info, density)` (`WidgetSlot.kt`) classifies a provider's shape from its own
reported footprint — API 31+ `targetCellWidth`/`targetCellHeight` ("recommended size" in the
platform's own terms) when available, falling back to `minWidth`/`minHeight` on older providers —
and treats a width:height ratio of roughly 0.7–1.4 as "square." `WidgetView` now renders square
widgets in a `contentWidthDp = widthDp / 2` box centered in the feed-width slot (everything else
keeps the full slot, unchanged); the edit-mode scrim, drag-resize handle, and reorder/edit/remove
`Popup` were all switched from the old fixed `widthDp` to this same `contentWidthDp` so they stay
aligned with whichever bounds the widget is actually rendered at. `commit()`'s height calculation
was updated to scale a widget's aspect ratio against this same `contentWidthDp` (not the full slot
width) when first adding it — otherwise a square widget's height would be sized for double its
actual display width and come out as a tall rectangle instead of a square.

Shape classification (`isSquareWidget`) is computed live from the provider's info on every
composition, so it applies immediately to already-hosted widgets with no re-add needed. The stored
*height*, however, is only computed once at add time (`commit()`) and persists in `WidgetStore` —
a square widget added before this fix has a height sized for the old full-width rendering, so it'll
now render at half width but keep its old (too-tall) height until removed and re-added.

## Square widgets resize diagonally, others only in height

Follow-up ("square widgets should expand diagonally and other in height"). The bottom drag handle
only ever changed `liveHeight`, keeping width fixed at whatever the slot computed — fine for wide
widgets (drag = taller, same width), but dragging a square widget bigger just stretched it into a
non-square rectangle instead of growing as a square.

`HostedWidget` gained an optional `widthDp: Int = 0` (`WidgetStore.kt`, tolerant codec — a 3rd
`,widthDp` column, 0/missing means "no custom width, use the default"; `WidgetCodecTest` covers the
round-trip), and `WidgetStore.setHeight` became `setSize(widgetId, heightDp, widthDp)`. Only square
widgets (per the existing `isSquareWidget` check) ever get a non-zero stored width — `commit()`
persists the initial half-slot width for a newly-added square widget, everything else keeps 0 and
derives its width live from the slot as before. In `WidgetView`, the drag handle now branches on
`isSquare`: for a square widget, dragging moves `liveHeight` **and** `liveWidth` together (clamped
to `min(WIDGET_MAX_H, widthDp)` so it can't outgrow the available slot), growing/shrinking outward
from the centered box — a literal diagonal resize; for everything else, only `liveHeight` changes,
exactly as before. Same known caveat as the last two entries: a square widget added before this
fix has no persisted width (defaults live to the half-slot default until first resized), so nothing
breaks, but its very first drag will jump from the old default rather than a previously-saved size.

## Square widgets never sized below the provider's own declared minimum

Regression from the half-width change above (reported: Samsung Device Care and the Gallery/photo
widget — both worked fine before the size-related changes — now show their own "Can't show
content" fallback, and it persists even after manually dragging bigger). Diagnosed on-device via
`dumpsys appwidget` + `logcat`: no crash, no exception, no permission denial anywhere in TileShell
— `androidx.glance.session.SessionWorker` (confirmed both are Jetpack Glance-based) reports
"SUCCESS" repeatedly, so the widget's own session runs fine; "Can't show content" is the *widget's
own* fallback string, not a host-side error screen. The width we were computing for a square
widget — half the feed slot, ~150–190dp on this device — is likely below what these specific
providers consider usable room, and rather than clip their layout they show this defensive
placeholder instead. Some providers (Samsung's `pictureframe`) declare no minimum at all
(`min=(0x0)`, happy at any size) which is why the earlier square-widget change looked fine when it
was tested against those; others (`SMWidgetOneButton`/Device Care) apparently need more than half
the slot and silently refuse below it.

Fixed with a floor, not a special case: new `squareContentWidthDp(info, widthDp, density)` computes
`max(widthDp / 2, provider's own declared minWidth in dp)`, capped at the full slot width — a
square widget still gets half-width when that's enough room, but never less than what its own
manifest says it needs. Applied everywhere a square widget's width is decided: `commit()`'s initial
size, `WidgetView`'s live default, and the diagonal drag handle's lower resize bound (previously
only floored at the generic `WIDGET_MIN_H` constant, letting a user drag a widget below its own
provider's minimum). Same caveat as before applies to *already-added* widgets with a small
persisted width from before this fix — they need either a fresh drag (the new floor applies from
the first pixel of movement) or a remove-and-re-add to pick up the corrected default immediately.

## Widened the null-info retry grace period from 2s to ~15s

Turned out the min-width floor above wasn't the actual bug: re-checked on-device (`dumpsys
appwidget`) after resizing per that fix, and Device Care / Digital Wellbeing weren't rendering
undersized — they were **gone from the host entirely**. The original retry-before-delete logic
(added earlier this session for the "spage" news widget, `WidgetView`) gives a widget with null
`getAppWidgetInfo` a 2s grace period (4×500ms) before concluding its provider was uninstalled and
deleting it. 2s was enough for spage but not for Device Care/Digital Wellbeing — both are
pre-installed **system** apps that can never actually be uninstalled, yet kept getting auto-deleted
by this exact logic. The likely trigger: every one of this session's many install-and-relaunch
cycles cold-starts the whole widget host at once, so a dozen-plus widgets all register
simultaneously and the slower ones (these two, both Jetpack Glance-based per
`androidx.glance.session.SessionWorker` in logcat) don't make it inside 2s under that contention —
a realistic scenario for any real phone reboot too, not just this session's repeated test installs.

Widened the grace period to ~15s (15×1s) before concluding a widget is actually gone. This doesn't
restore widgets already deleted by the old 2s window — those need to be re-added once — but should
stop it from recurring on future cold starts.

## Widget resize: independent width/height/diagonal handles, not shape-guessed

The Device Care/Digital Wellbeing investigation above didn't turn up a fixable root cause (looks
like a genuine Samsung OEM restriction on system-privileged widgets — dropped, not pursued
further). Separately, asked whether resize could work via pinch-zoom or per-direction handles
instead of the single bottom-center handle whose behavior (height-only vs. diagonal) was decided by
the `isSquareWidget` shape guess. Pinch-zoom was considered and rejected — it fights the feed's own
scroll gesture and can't set width/height independently; per-edge/corner handles are the standard
Android widget-resize pattern (matches Pixel Launcher) and let the user override the shape guess
entirely instead of being stuck with whatever the host inferred.

`WidgetView`'s single bottom-center handle is now three independent ones (new `ResizeHandle` helper,
`WidgetSlot.kt`): bottom-center (height only, horizontal pill), right-center (width only, vertical
pill), bottom-right corner (both at once — a literal diagonal drag, small square dot). Any widget
can now be resized in any direction the user wants, not just square-classified ones — the
`isSquareWidget` check still decides the sensible *initial* default width when a widget is first
added (half-slot-or-provider-minimum for square shapes, full slot otherwise), but no longer gates
which resize directions are available afterward. The width handles share the same
provider-minimum-width floor as before (`providerMinWidthDp`); the corner handle moves width and
height independently based on the drag's x/y components, not locked to a shared square value.

## LARGE tile allowed on 4-column grids too (drops the 5/6-column gate)

User-requested: the 3×3 LARGE size was gated to 5/6-column grids (`AppCategories.allowsLargeTile`
== `columns >= 5`, see "Post-S29 — re-enable the 4×4 LARGE tile" and the widget-stack decision
above), with the grid auto-demoting every LARGE tile to MEDIUM whenever it dropped back to 4
columns (`StartViewModel.setColumns` → `LayoutRepository.demoteLargeTiles`). No structural reason
for the gate remains — a 3-wide-by-3-tall footprint still fits inside the minimum 4-column grid
(it just takes 3 of the 4 columns for those rows, the same way WIDE already takes all 4), so
`allowsLargeTile` now unconditionally returns `true` (`iconKey`/`app`/`columns` all unused, kept
for call-site compatibility — same pattern as when the media/news restriction was dropped
earlier). `demoteLargeTiles` had exactly one caller (`setColumns`); removed it, its `LayoutRepository`
wrapper, and its DAO `@Query`, rather than leave dead code now that no column transition ever needs
to shrink a LARGE tile. Folder-child resize (`StartViewModel.resizeFolderChild`, previously hardcoded
`columns >= 5`) and the folder overlay's resize-indicator check (`StartScreen.kt`, same hardcoded
check) were both switched to call `AppCategories.allowsLargeTile` too, so a folder member can now
also reach LARGE on a 4-column grid — keeping the two code paths on one source of truth rather than
duplicating the same boolean in three places. No schema change (`TileSize.LARGE` already existed);
no migration. Widget stacks are unaffected structurally (still "every member uniformly WIDE or
LARGE"), but merging two LARGE tiles into a stack — and the stack keeping its 3×3 footprint — now
works the same way regardless of the current column count, since nothing ever demotes it back down.

## Sticky-mode drag-drop onto an occupied cell pushes it down, instead of rejecting the drop

User-requested, checked against real Windows Phone behaviour: dropping a tile onto a cell that
already holds another tile used to be a no-op — `editDragGesture` only ever set `pendingSlot` when
the target cell was entirely free (`blockers.none { ... overlap ... }`), so landing on an occupied
tile just snapped the drag back to its start, and the only way to actually place a tile there was
to first find a genuinely empty cell. Real WP instead makes room: dropping onto an occupied spot
pushes the occupant down, exactly like growing a tile via resize already displaces a neighbor
(`StartViewModel.stickyResizeSlots`/`stickyPushDown`, see "Tile arrangement: user-selectable dense
repack vs. WP-style gap-preserving grid" above) — it must not, however, turn into a full
`GridPacker.pack`-style auto-arrange repack of the whole grid, which is the behaviour sticky mode
exists to avoid in the first place.

Fixed by reusing the resize push-down machinery for a plain move instead of inventing a second
mechanism. `editDragGesture` (`StartScreen.kt`) no longer computes a `free` check at all — the
sticky-mode branch always sets `pendingSlot` to whatever cell the finger is over, occupied or not.
`StartViewModel.stickyResizeSlots`'s push-down + empty-row-collapse body was extracted into a new
shared `stickySlotsForPlacement(movedId, size, targetCol, targetRow)`: the tile's own cell (column
clamped to stay in-grid), every anchored tile the resulting footprint displaces (`stickyPushDown`,
unchanged — straight down, same column, cascading until nothing overlaps), and
`GridPacker.collapseEmptyRows` over the result so a push can never leave a fully-empty row behind.
`stickyResizeSlots` now just calls it with the tile's *own* current cell as the target (a resize
never changes position, only size); `setTileGridSlot` (the drag-drop write path) calls it with the
cell the drag released over as the target — the only difference between the two call sites is where
the target cell comes from, so the actual displacement logic is identical and no longer duplicated
in a resize-only place and a would-be drop-only place. `setTileGridSlot`'s old
`collapseEmptyRowsAfterMove` helper (which only ever repositioned the *dragged* tile, with no
push-down — silently overlapping two tiles if the target was occupied) is deleted outright, replaced
by this shared helper.

Only the tiles a placement genuinely displaces ever move — a resize/drop that lands somewhere with
no neighbors in the way still touches nothing else, and unrelated tiles (folders, tiles in the other
column, tiles above the target) are provably untouched since `stickyPushDown` only walks tiles whose
box overlaps the moved footprint. Verified on an emulator (`adb shell input swipe` to drag one
medium tile onto another's cell, plus `uiautomator dump` bounds checks): the dropped tile lands
exactly where released, the tile that was there cascades down just far enough to clear it (and, when
a further tile was already sitting in the way, that one shifts the minimum needed too), no two tiles
end up overlapping, and no fully-empty row is left standing. A separate drop onto a genuinely empty
cell (the pre-existing case) is unaffected — `stickyPushDown` finds nothing to displace and the tile
just lands there.

## Second Accessibility API rejection: the disclosure text was fine, the demo video wasn't

`v2.2.0` (versionCode 220) fixed a Play Console "Accessibility API policy: Insufficient data use
declaration in the prominent disclosure" rejection by itemizing all data TileShell collects —
location, calendar, contacts, notification content, installed apps, recent-apps taps — in
`AccessibilityDisclosureDialog` (`MainActivity.kt`). Google rejected the resubmission again under the
same policy, but this time flagged only two of the six items as still missing: Calendar events and
Contacts. The dialog already listed both, in items 2 and 3 of the six-item list.

Root cause, confirmed with the developer: reviewers grade this policy from the demo video required
in the Play Console submission (per the rejection email: "include... a link to an updated video
showcasing the core functionality feature that uses the AccessibilityService API"), not by installing
and scrolling the app themselves. The recorded video scrolled through the disclosure dialog's
scrollable `Column` too quickly, past the calendar/contacts bullets, without pausing long enough for
a reviewer to read them — while the items before and after (location, notification content, installed
apps, taps) happened to be on-screen long enough to register. The app itself was never wrong; the
video evidence just didn't show what the app does.

Fixed on both sides. Code (`v2.2.2`, versionCode 222): reordered the six-item list so Contacts and
Calendar are first (previously buried at positions 2-3), on the theory that whatever a reviewer/video
covers first is least likely to get scrolled past; tightened the wording so the whole dialog needs
less scrolling; split the one giant concatenated string into three separate `Text()` calls, matching
its actual visual sections (accessibility-service explanation / itemized data list / privacy-policy
+ CTA line) — no functional change, easier to audit which sentence covers which data type next time.
Process: re-record the disclosure-dialog walkthrough video for this resubmission, scrolling slowly
and pausing on every bullet — especially Contacts and Calendar — before uploading it to Play Console
alongside the new build. This is the change that actually fixes the rejection; the code changes are
a defensive improvement against the same failure mode recurring with a future rushed recording.

## Feed region: Google News-templated country presets, not hand-curated lists

The feed's default RSS sources started India-only (10 hand-picked sources). A first pass added a
locale-detected binary choice — India vs. a small hand-curated "international" set (BBC + NYT Food)
— seeded once per install from the device's `Locale` country. The user then asked for a proper
per-country picker ("default country + select other countries"), not just a binary switch.

Hand-curating a source list for each of ~20 requested countries was rejected as the implementation
approach: it's real per-country research effort, and independent news sites' RSS feeds go dead far
more often than a single reliable domain would. Instead, `RssFeed.kt`'s `countryFeedSources
(countryCode)` generates five feeds per country purely from its ISO code, all on `news.google.com`:
the plain top-stories edition plus `BUSINESS`/`TECHNOLOGY`/`ENTERTAINMENT`/`SPORTS` topic-section
editions (Google's own well-known topic slugs). `hl` is pinned to `en-US` for every country rather
than varying by language — the app's UI and RSS parsing assume English content throughout, and
`gl`/`ceid` alone are enough to scope an edition to a given country. This trades "the single best
local sources per country" for "guaranteed-reachable, zero-curation, works for effectively any
country Google supports" — the right trade for a picker that needs to cover ~20 markets in one
session without an ongoing dead-feed maintenance burden.

India keeps its original hand-curated 10-feed list untouched (it's the one country where curation
already happened and reads noticeably richer than a Google-News-only set would), and the earlier
"international" bucket is kept too — not as the auto-seed fallback's only option anymore, but as an
explicit choice for whoever prefers the generic BBC-based set over their own country's Google News
edition, and as the fallback for any locale that resolves to a country outside the curated 19.

## Curated top-stories override for the highest-value country presets

Google News RSS's tradeoff (see above) turned out to have a real cost the "no curation, zero dead-URL
risk" framing didn't account for: fetching a live Google News feed directly and inspecting its raw
XML confirmed it carries **no per-article image whatsoever** — no `media:content`, no `enclosure`,
and the `<description>` is just an HTML list of links to the same story at different outlets, no
`<img>`. `ArticleCard` (`FeedPage.kt`) only renders its hero-image block when `article.imageUrl !=
null` — and the category tag chip is nested inside that same block — so every article from a
Google-News-only country degrades to a bare text row with no tag, no thumbnail.

Rather than solving this for all ~19 generated countries (real per-country curation effort — the
exact cost the Google News approach was chosen to avoid), the user asked for it specifically for the
5 requested markets (US/UK/Australia/Canada/UAE — not coincidentally the highest-eCPM markets plus
UAE). `countryFeedSources`' "nation" slot now overrides to a `CURATED_TOP_STORIES` entry for just
those 5 codes, leaving the other 14 (and every other category slot, even for these 5) on the
zero-curation Google News template. Each override was live-verified this session, not guessed:

- **US** → NYT Home Page (`rss.nytimes.com/services/xml/rss/nyt/HomePage.xml`) — CNN
  (`rss.cnn.com/rss/cnn_topstories.rss`) was tried first and rejected: its feed only serves over
  plain `http://` (the `https://` handshake fails outright — `SSL_ERROR_SYSCALL`), which is a
  non-starter given the cleartext policy below.
- **UK** → BBC UK (`feeds.bbci.co.uk/news/uk/rss.xml`, already a trusted domain elsewhere in
  `INTERNATIONAL_FEED_SOURCES`).
- **Australia** → ABC News "Just In" (`abc.net.au/news/feed/51120/rss.xml`).
- **Canada** → CBC Top Stories (`cbc.ca/webfeed/rss/rss-topstories`) — its images arrive as a plain
  inline `<img>` inside the CDATA `<description>` rather than a `media:`/`enclosure` tag, which the
  existing parser's content-encoded/description `<img>` fallback (added for a different feed
  originally) already extracts with no code changes needed.
- **UAE** → Gulf Today (`gulftoday.ae/rssFeed/0/`) — Gulf News, Khaleej Times, The National, and a
  handful of other obvious UAE outlets were tried first and all 404'd or blocked automated fetches;
  Gulf Today was the one that actually worked, confirmed carrying both `enclosure` and `media:content`
  image tags.

## Feed sources must be https and dead-link verified

Verifying the CNN candidate above (previous decision) surfaced a real, pre-existing bug: four BBC
feeds in `INTERNATIONAL_FEED_SOURCES` (added the previous session) were on plain `http://`. Android
blocks cleartext traffic by default once `targetSdk` is 28+, and neither the manifest nor
`FeedRefreshWorker`'s fetch (a plain `HttpURLConnection`) declares any cleartext exception — so on a
real device, an `http://` feed source would throw a cleartext exception on connect, get swallowed by
the surrounding `runCatching` (the project's standard "a broken feed source degrades to `null`, never
crashes" pattern), and just silently never populate. No test had ever caught this because JVM unit
tests never make a real network call — the previous session's "build + tests green" was true and
still meant a partially non-functional default.

The same live-curl pass also caught a second, unrelated bug in the same list: the BBC entertainment
URL's path segment was wrong (`entertainment_arts`, missing "`_and`") — it 302-redirected to the
correctly-named `https://` URL, which then 404'd, meaning that feed was dead over *either* protocol.

Fixed both: all four BBC URLs switched `http://` → `https://` (each individually re-curled to confirm
the https version actually serves 200 with image tags intact, not just protocol-swapped blindly), and
the entertainment path corrected to `entertainment_and_arts`. Added a permanent guard rather than
relying on manual verification catching it next time: `RssFeedTest`'s `all built-in feed source urls
are https` asserts every `FeedSource` across `DEFAULT_FEED_SOURCES`, `INTERNATIONAL_FEED_SOURCES`, and
every generated `SELECTABLE_COUNTRIES` preset starts with `https://` — a plain JVM test, so it can't
catch a feed being reachable-but-wrong (only `curl` during development catches that), but it makes
the cleartext class of bug specifically impossible to reintroduce silently.

## News regions are multi-select, additive/subtractive by url

The region picker started as a single choice — selecting a country replaced the entire subscribed
feed list with that country's preset. The user asked for multiple countries to be selectable at once
(e.g. India + UK together), which rules out "replace wholesale" as the toggle semantics.

`FeedData.regions` is a `Set<String>` rather than one `String`. Toggling a region **on** merges its
preset's feeds into the existing `sources` list, skipping any url already present — so it can never
duplicate a feed another active region already contributes, and never touches manually-added custom
feeds or another region's enable/disable choices. Toggling a region **off** is the trickier direction:
it must not blindly remove every url in that region's preset, because a url could be shared with
another still-active region (unlikely given each preset is generated from a distinct country code, but
not impossible, e.g. two presets could coincidentally reference the same underlying source) — so the
"off" path recomputes the union of every *other* currently-active region's preset urls first, and only
drops urls unique to the region being turned off. `reconcileDefaults` (run on every launch to backfill
newly-added default feeds) was updated the same way: it now unions all active regions' presets
(`distinctBy { it.url }` to dedupe) instead of reconciling against a single region.

## Per-source article cap, so one high-volume region can't crowd out the others

Landing multi-select regions surfaced a real bug, not a perception issue: the user reported the feed
"only loads one country at a time" even with several selected. Pulling the actual on-device
`news_feed.pb` (via `adb shell run-as com.tileshell cat files/datastore/news_feed.pb`) with India + UK
+ US all active proved the subscriptions themselves were correct — every region's feeds were enabled —
but the *cached articles* were 39/40 Indian, 1 American, 0 British.

The cause was in `mergeFeedArticles`, not in region selection: it merged every enabled feed's articles,
sorted the combined list purely by `publishedAtMillis` descending, and took the top
`FEED_ARTICLE_CAP` (40) — no per-source or per-region floor. India's 10 default feeds (The Hindu, NDTV,
TOI, etc.) post frequently enough that their own newest articles alone exceed 40, so nothing from a
less prolific region's feed could ever rank high enough to survive the cut, however many other regions
were also subscribed. Live-curling BBC UK's feed directly (outside the merge logic) confirmed it was
never a fetch failure — the feed had recent, valid articles that simply lost every recency comparison
against India's higher-frequency output.

Fixed with a `FEED_PER_SOURCE_CAP` (8): each individual feed's article list is now sorted and truncated
to its own top 8 *before* the global merge/sort/final-cap runs. This guarantees every enabled source
gets a chance to place in the final cache regardless of how prolific its neighbors are, at the cost of
capping how many of any one (very active) source's articles can appear even when it's the only region
selected — an acceptable trade given the alternative was silently excluding entire regions. A per-region
quota (rather than per-source) was considered but rejected as unnecessary complexity: since each
generated country preset already contributes a small, roughly-even number of feeds (~5), capping at the
feed level achieves fair regional representation without needing to track which region a `FeedSource`
originated from.

## Merge-to-folder silently broken in sticky mode: a live-preview feedback loop

Unrelated to the feed work above — a user report that dragging one Start-screen tile onto another to
create a folder ("merge") no longer worked, specifically in sticky (WP-style gap-preserving) tile
arrangement mode. Dense mode was unaffected.

Root cause traced in `editDragGesture` (`StartScreen.kt`): merge requires a 250ms dwell
(`mergeDwellMs`) with the drag centre held inside a target tile's inner merge zone before it commits
(`mergeNow`). Every pointer-move tick was structured as `if (mergeNow) { …merge bookkeeping… } else {
…sticky push-down preview / reorder… }`. During the dwell window itself — after entering the merge
zone but before 250ms has elapsed — `mergeNow` is still false, so the tick falls into the `else`
branch. In sticky mode that branch computes and applies a **live push-down preview**
(`onStickyPreview`) reflecting "if you dropped right here, this is who gets displaced" — and the tile
currently being hovered for a potential merge is exactly the tile that preview displaces. The next
tick's hit-test (`othersPacked`, which packs using the same `slotOf` closure the preview just wrote
into) sees that target at its new, pushed-down position — the drag centre no longer falls inside it,
`inCentre` flips false, and the dwell timer resets to zero. This repeats every single tick for as long
as the finger holds still, so the 250ms window could never elapse: not a rare race, a guaranteed
100%-repro loop the instant a drag entered any tile's merge zone in sticky mode. The merge-zone
detection, `TileMerge.computeMerge`, and the release-time write path (`onDrop(mergeId)`) were all
completely intact — the bug was purely in this one live-tick branch, which is why the underlying
merge *machinery* worked fine once the loop was fixed and never needed to change.

Dense mode's equivalent `else` branch (`onReorderTo`) never had this problem: it only fires once per
newly-hovered target and doesn't mutate any shared state `othersPacked` depends on, so the dense-packed
position of a hovered tile never moves out from under the drag.

Fixed by re-gating the merge-tracking block on `inCentre` (the "are we currently inside a merge zone at
all," true throughout the dwell) rather than `mergeNow` (true only once the dwell finishes) — so the
sticky preview is cleared exactly once, at the moment dwelling begins, and the whole tick is then
"claimed" by the merge-tracking branch (doing nothing further while still dwelling, recording the
target once `mergeNow` does flip true) instead of ever reaching the preview-recomputing branch again
until the finger genuinely leaves the zone. The target tile now stays visually and positionally
stationary for the entire dwell, so the 250ms window can actually complete.

### Second round: the merge hitbox itself was reading the live preview layout

The `inCentre`-gating fix above was correct but insufficient — the user tested it and reported merge
still didn't work ("it pushes the destination tile, not allowing to stable"). On-device diagnostic
logging (temporary `Log.d` in `editDragGesture`, read via `adb logcat`) pinned the actual blocker: the
merge-target hit-test computes `hovered` from `othersPacked(startId)`, whose doc comment asserts the
packed layout is "invariant for the whole gesture … so a merge target never slips out from under the
finger." In **dense** mode that's true. In **sticky** mode it is not: `othersPacked` packs via the
shared `slotOf` closure, and `slotOf` is `{ id -> stickyPreview[id] ?: byId[id]?.gridSlot }` — it reads
`stickyPreview`, the live push-down preview this same gesture rewrites every tick. So a tile that got
displaced into the preview earlier in the drag keeps being hit-tested at its *displaced* rect; when the
finger later lines up over that tile's true on-screen cell, the merge-zone check is still comparing
against the moved hitbox and never registers a hit. (This is a different, deeper instance of the same
"preview feeds the hit-test" coupling — the first fix stopped the preview from being *written* during a
dwell, but any displacement already present from before the dwell started still poisoned the hitbox.)

Fixed by introducing `othersPackedStable(exclude)`: identical to `othersPacked`, except in sticky mode
it packs from a slot function that reads each tile's real persisted `gridSlot` only (`{ id ->
byId[id]?.gridSlot }`), never `stickyPreview`. Merge-target detection uses `othersPackedStable`; the
push-down preview computation (its legitimate separate job) still uses the live `slotOf`. This
guarantees every candidate merge target's hitbox sits exactly where the tile visually and persistently
belongs, regardless of what the in-progress preview is doing to other tiles. Verified working on the
user's physical device. The lesson worth keeping: *hit-testing for one interaction must never read a
layout that a concurrent interaction is actively mutating* — merge detection and push-down preview are
two such interactions sharing `editDragGesture`, and they need independent, non-interfering views of
the grid.

## People tile mosaic: circular avatars, not the prototype's square crops

User-requested follow-up: the people live tile's photo mosaic should show each contact's profile
photo as a circle, matching the familiar round contact-photo convention. The HTML prototype's `.av`
avatar cells (`styles.css`) are plain squares with no `border-radius` — WP's own People tile is
square-cropped — so this is a deliberate deviation from the prototype, not a bug fix.

`PeopleTile.kt`'s `Avatar` composable now clips the mosaic (front-face, `big = false`) cells to
`CircleShape` with a small 3dp inset, so the tile's own fill (accent/gradient/glass) shows through
each cell's corners instead of the crop touching the cell edges — reads as a grid of round avatar
chips rather than square photo tiles. The back face (`big = true`, a single full-bleed photo behind
the "‹name› posted" caption) is unchanged — that's a photo-post treatment, not an avatar grid, so it
stays a full-bleed rectangle. The colour-tint fallback (while a photo decodes, or for an unreadable
URI) is clipped to the same shape as whichever face it's standing in for.

## Clock tile: 12-hour am/pm, matching the glance screen

User-requested: the clock live tile's time should read 12-hour with an am/pm suffix, the same format
already used by the feed/glance screen's clock (`feedClock12` in `feature/start/feed/FeedFormat.kt`).
The prototype's own `clockNow()` (`launcher/tiles.js`) is 24-hour (`d.getHours()` with no 12-hour
conversion), which is what `ClockTile.kt`'s `clockFace` faithfully matched through S20 — so this is a
deliberate deviation from the prototype, not a bug fix, made for consistency with the glance screen
that was added later and already reads 12-hour.

`clockFace` now builds `hm` as `"$hour12:${minute} $suffix"` (unpadded hour, zero-padded minute,
lowercase am/pm) instead of raw 24-hour `hour24:minute` — same shape as `feedClock12`, computed
independently rather than shared, since the two live in different Gradle modules
(`:feature:livetiles` has no dependency on `:feature:start`). Only the front face's time string
changes; the back face's date and `nextAlarmString` were already 12-hour am/pm and are untouched.
`ClockSmallFace` (the 1×1 tile) reads the same `ClockFace.hm`, so it picks up the format for free.

## Live tile text: black when the wallpaper behind it is light

Known caveat called out since S21/S22 ("live face text is Color.White regardless of glass+light
theme … revisit when glass + light + live overlap looks off"), addressed on direct user request in
two passes. Confirmed the prototype's own `.tile { color:#fff }` (`styles.css`) is unconditional — no
`#screen.light .tile` override exists — because a *solid* tile's fill is always the user's saturated
accent colour, never actually light, regardless of screen theme; that case is correctly left as white.

**First pass** shipped `Glass.faceTextColor(dark, glass)`: white unless `glass && !dark` (transparent
tiles on, theme light) — confirmed the scope with the user before implementing, since a blanket
"black whenever theme is light" would have broken contrast on solid accent tiles instead. **Second
pass**, prompted by direct user follow-up ("behind the tiles should also be addressed … talking about
the text colour if tile background is light because of chosen wallpaper"): the theme flag was the
wrong signal. A glass tile is translucent, so its *effective* appearance is the computed glass tint
alpha-composited over whatever the real wallpaper layer draws underneath — if the user picks a bright
custom photo (or a light Bing daily image), the glass tile reads as light regardless of the dark/light
theme setting; conversely a dark bundled gradient wallpaper stays fairly dark even with "theme light"
(the gradients are dark-base-first and `Wallpapers.themedBase` only lifts them ~45% toward the light
theme's own bg — confirmed via `LuminanceTest`'s Aurora-lifted-45%-still-not-light case). "Wallpaper
behind tiles" mode has the identical problem for the same reason (each tile is a literal window onto
the wallpaper/photo).

Replaced the theme-based check with an actual-brightness one. `core/design/Luminance.kt` adds a pure,
unit-tested `perceivedLuminance(Color)` (simple 0.299/0.587/0.114 weighting — a UI heuristic, not
WCAG-exact) and `isLightBackground(Color)` (>0.6 threshold; verified against both screen tokens).
`StartScreen.kt`'s `rememberChosenWallpaperIsLight` resolves what the user's actual background reads
as: a custom/Bing photo's sampled average brightness (`averageLuminance`, a coarse ~48×48-sample scan
— fast enough for a one-off `remember`, no need to scan every pixel of a multi-megapixel photo) when
one is set, else the plain screen bg (no wallpaper), else a bundled gradient's own `themedBase`.
`Glass.faceTextColor` now just takes the resolved `useDarkText: Boolean` instead of `(dark, glass)` —
the caller (`StartScreen.kt`) combines `(glass || tiledWallpaper) && chosenWallpaperIsLight`, since
solid, non-tiled tiles never show the wallpaper at all. The custom-photo bitmap is now decoded
unconditionally when a custom wallpaper is set (previously only in tiled mode, for the tile-window
use) so it's available for the brightness sample in the more common untiled-glass case too; this adds
one redundant decode alongside `WallpaperBackground`'s own internal one in that specific case, judged
an acceptable one-time (per wallpaper-change) IO cost rather than a bigger refactor to share a single
decode across composables.

The Start screen's own chevron ("open app list") and settings gear sit directly on the general screen
area, not a tile's fill — a related but distinct condition, `screenBackgroundIsLight`, since in
"wallpaper behind tiles" mode the general screen area is always flat `tokens.bg` (the real
photo/gradient is windowed *only* into each tile there), diverging from what a tile itself shows. The
open-folder's `FolderActionTile` ("make stack"/"keep as folder" chip) draws directly over that same
general screen area too (not a tile fill) — user-requested follow-up — so it takes the same
`screenBackgroundIsLight` signal for its text, neutral fill, and border, rather than the tile-fill
condition.

`LocalTileFaceColor` (`core/design/ThemeLocals.kt`) still carries the resolved colour down to every
tile-face composable exactly as before (each live-tile file's module-level `FaceText` reads it via a
`@Composable get()`; `StaticTileGlyph`'s monoline icon, `TileLabel`, `TileControl`'s edit-mode corner
glyphs, the folder inline-expand chevron + rename field, the contact-tile people-glyph fallback, and a
closed folder's per-cell icon/"+N" overflow text all read the same local) — only *how* the provided
value is computed changed.

Deliberately left alone: text drawn over an actual photo with its own dark scrim (the photos-tile
"photos" caption, a pinned contact's name over their photo) — that's contrast-safe against arbitrary
photo content already, unrelated to this condition; the tile-colour-picker sheet, which paints its own
fixed overlay background; and the colour-swatch selection ring, deliberately always white as a
fixed-contrast ring against the swatch's own (arbitrary) colour.

## Closed folder's mini-grid: an empty slot gets no backdrop, not a dark square

Bug fix, user-reported: a folder with fewer apps than its mini-grid's capacity (e.g. 2 apps in a 2×2
grid) rendered every unused cell with the same neutral `rgba(0,0,0,.18)` tint used for a real app
cell — `FolderTileContent`'s `cellBg` fallback chain (`child?.accentOverride ?: child?.let {
dominantColor } ?: Color(0x2E000000)`) always resolved to that default `0x2E000000` neutral even when
`child` was null, since a null-safe `?.` chain on a null receiver just short-circuits straight to the
final `?:` fallback — there was no separate "nothing to show here" branch. The result: 1-2 unused
cells per folder rendered as ugly dark/black squares with no icon in them. Confirmed against the
prototype's own markup (`tiles.js`'s group-tile renderer, `kids.map(...)` over the *actual* children
only) — it never generates a `.gm` div for a non-existent child at all, so an empty slot is simply
absent, not a tinted placeholder. Fixed by adding an `isEmptySlot = !isPlus && child == null` check
in `FolderTileContent` that skips the `cellFill` background modifier entirely for such a slot —
it now just shows the folder tile's own fill (accent/gradient/glass/wallpaper-window) showing through,
matching a slot that was never drawn. The "+N" overflow cell and any real app cell are unaffected.
Build + tests green.

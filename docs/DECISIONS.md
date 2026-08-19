# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

## People tile: flip removed, replaced with an animated bubble cluster

User-requested, two complaints in one: the flip's back face showed the
contact photo as a full square crop (`Avatar(big = true)` used
`RectangleShape` + no clip, unlike the front mosaic's circular cells — an
inconsistency, not a deliberate design choice), and more fundamentally the
user didn't want a flip on this tile at all — instead, circles of varied
size with their own animations and alternating photos. Rather than just
fixing the back face's shape, removed the flip entirely: `LiveFace.PEOPLE`
now has `flips = false` (excluded from the shared 2.6s flip scheduler, the
same opt-out `PHOTOS` already used), and `PeopleTile.kt`'s uniform
`MosaicGrid` (a `Column`/`Row` of equal-`weight(1f)` cells) is replaced with
a scattered cluster of circular "bubbles" at fixed relative positions/sizes
(`CircleSlot(cx, cy, d)` — 5 bubbles for the wide tile, 4 for medium/large,
each a different diameter, positioned via `BoxWithConstraints` so the
fractional layout scales to the tile's actual rendered size). Each bubble
runs its own independent timer (still ~2.1s, staggered per bubble by
`300ms + seed*260ms` so they don't all swap in visual lockstep like the old
grid did), cross-fading to a different contact and popping with a bouncy
`Animatable` scale animation (0.82 → 1.0, `Spring.DampingRatioMediumBouncy`)
on every swap — both "various sizes" and "animations" from the request, plus
the alternating photos the old mosaic already did. `mosaicCells` is reused
unchanged for each bubble's *initial* photo assignment (same distinct-
coverage cycling as before); only the ongoing per-bubble swap logic is new.
Build + tests green (one pre-existing test, `PeoplePhotosFaceMappingTest`,
updated to assert `PEOPLE.flips == false`).

## AGP 9 upgrade (S31): on-device regression sweep — clean, branch ready to merge

Full manual sweep on the `agp9-upgrade` branch's signed release build
(emulator, since no physical device was connected this pass), per the S31
scope defined when the upgrade was split into two sessions:

- **Notification listener**: granted access via `adb shell cmd notification
  allow_listener`; `TileNotificationListenerService` connects cleanly
  (confirmed in logcat), no crash. A real per-app badge test needs a genuine
  Gmail/Messages install, not available on a bare AVD — the service
  lifecycle itself is what AGP 9/R8 could plausibly break, and that's clean.
- **Quick panel / DND**: opened via the settings-gear-adjacent tap
  affordance (a genuine two-finger swipe can't be scripted through `adb
  shell input`), renders correctly with live Wi-Fi/location state, chip
  taps don't crash. The DND deep-link's exact external-Settings-app
  behavior is unchanged application logic already verified on physical
  hardware in an earlier session — out of scope for what this upgrade could
  break.
- **Accessibility-service screen lock**: enabled via `adb shell settings
  put secure enabled_accessibility_services`; long-press on the settings
  gear correctly triggered `GLOBAL_ACTION_LOCK_SCREEN` — confirmed via
  `dumpsys window` showing the screen actually went to sleep.
- **WorkManager jobs**: force-ran every scheduled job (`adb shell cmd
  jobscheduler run -f`) — `FeedRefreshWorker` explicitly logged `Worker
  result SUCCESS`; the other two (weather refresh, layout auto-backup)
  rescheduled with new job IDs and logged zero errors. The `InputMerger`
  fix from S30 is systemic (shared `WorkerWrapper` code, not per-worker), so
  one explicit `SUCCESS` plus zero errors across the others is sufficient.
- **Widget hosting**: full real round-trip — opened the picker, it listed
  every installed app's widgets (Calendar, Chrome, Clock, Gmail, Maps,
  etc.), selected the Clock app's Digital widget, went through its own
  configure-activity picker, and the bound widget rendered live on the
  glance tab. Zero crashes through the whole flow.
- **Personalize / backup UI**: the full sheet renders correctly end to end,
  including the newly-added "quick panel" guide section and "show device
  status card" toggle from this session's earlier work. A full SAF
  export/import round-trip wasn't exercised (fiddly to script blindly via
  adb), but Room 2.8.4 — which backs layout history/backup — is proven
  sound by the app booting, loading tiles, and persisting state correctly
  throughout the entire session with zero crashes.
- **Cold-start timing**: not measurable via `adb shell am start -W` for
  this app specifically — TileShell is a registered HOME app, so Android
  auto-relaunches it the instant it's force-stopped, meaning there's no way
  to force a genuine cold start through simple adb commands. A real
  comparison against the S26 baseline-profile numbers needs the
  `:macrobenchmark` module or a physical device reboot, not a quick spot
  check — no number is fabricated here.

No further regressions found. Merged into `main` on the user's explicit
go-ahead.

## AGP 9 upgrade (S30): version bumps + a real WorkManager R8 regression, found and fixed

Per the `SESSION-PLAN.md` S30/S31 split: this pass is the version-bump +
build/test session, done on an isolated `agp9-upgrade` branch (not merged to
`main` without an explicit decision — the revert provision the user asked
for) so a bad upgrade never touches the working tree. Bumped AGP 8.9.1 →
9.0.1 (the minimum satisfying Play Console's "9.0+" ask, not the newest
9.3.0, to keep the version jump smaller), which drags Kotlin 2.0.21 → 2.2.10
(AGP 9's hard KGP floor) and a matching KSP 2.2.10-2.0.2; Gradle wrapper
8.11.1 → 9.1.0 (AGP 9.0.1's minimum); Compose BOM → 2026.06.00. Deliberately
opted **out** of AGP 9's new build DSL and built-in-Kotlin defaults
(`android.newDsl=false`, `android.builtInKotlin=false` in `gradle.properties`
— both documented as safe until AGP 10 removes them) since a repo-wide grep
found zero usage of the legacy APIs that migration actually replaces
(`applicationVariants`, `variantFilter`, direct task access) — no reason to
take on that migration's surface area in the same pass as everything else.
Enabled `android.r8.optimizedResourceShrinking=true` (confirmed active: the
release build's `optimizeReleaseResources` task ran) — the actual fix for
Play Console's resource-shrinking recommendation.

Room 2.6.1 → 2.8.4 was an unplanned but required addition: the initial
build hit `[ksp] java.lang.IllegalStateException: unexpected jvm signature
V` in `:core:data:kspDebugKotlin` — a documented KSP2 bug when processing
Room DAOs under newer Kotlin, fixed upstream in Room 2.7.0+.

**On-device verification caught a real regression a green build/test run
never would have**: installing the signed release build on an emulator and
watching logcat showed every WorkManager worker logging
`NoSuchMethodException: androidx.work.OverwritingInputMerger.<init> []` on
first run — R8 had stripped the no-arg constructor of WorkManager's default
`InputMerger` (used by *every* work request, not just chained ones), since
nothing in our code references it directly; only WorkManager's own internal
`Class.forName(...).getDeclaredConstructor()` reaches it, invisible to R8's
static analysis. This is exactly the "passes a green build, breaks silently
at runtime" risk category called out when S30/S31 were split. Fixed with an
explicit `-keep class * extends androidx.work.InputMerger { public <init>();
}` in `proguard-rules.pro`; re-verified via `adb shell cmd jobscheduler run
-f` to force a worker immediately rather than waiting out its real schedule
— `FeedRefreshWorker` now logs `Worker result SUCCESS` with zero
`InputMerger` errors. Debug build + full unit test suite green throughout.
S31 (the fuller on-device regression sweep — notification badges,
accessibility lock, DND, widget hosting, backup/restore, baseline-profile
cold-start check) is still outstanding before this is genuinely
release-ready; the branch stays unmerged until then.

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

## Wallpaper reframe: zoom now actually opens up pan room on the tight axis

Bug fix, user-reported: "when photo is reframed for wallpaper zooming centrally is only a
possibility if i want to show only upper portion of the photo or lower portion of the photo that is
not feasible." Root cause found in three places (`WallpaperCropOverlay`'s pinch/drag handler,
`WallpaperBackground`'s custom-photo render, and `photoWindow()`'s tiled-wallpaper variant): the
cover-fit "overflow"/pan-slack on each axis was computed once from the zoom-1 cover scale only, and
`zoom` was then applied as a wholly separate transform on top. A cover-fit photo has zero slack on
whichever axis exactly matches the box at zoom 1 (the "tight" axis) — since that slack was never
recomputed as zoom increased, panning the tight axis stayed a no-op no matter how far the user
pinched in, which is exactly the "can't reveal just the top or bottom" complaint.

Fixed with one shared pure function, `wallpaperCropGeometry(imageWidth, imageHeight, boxWidth,
boxHeight, alignX, alignY, zoom)` (`feature/start/WallpaperGeometry.kt`, unit-tested), that folds
zoom into the *same* scale used to compute slack (`scale = coverScale * zoom`) before deriving the
draw offset — so zooming in genuinely creates proportional pan room on both axes, and the existing
alignX/alignY (0..1) semantics are unchanged. Applied consistently at all three call sites, replacing
`Image(contentScale = Crop, alignment = BiasAlignment(...))` (which computes its own slack once at
layout time and can't be corrected by an outer zoom transform) with manual `drawWithCache`/
`drawBehind` + `translate`/`scale` drawing. `WallpaperCropOverlay`'s pinch-drag gesture also had a
related bug fixed in the same pass: its `pointerInput` was keyed on the old (now zoom-independent)
overflow values, which would have restarted the gesture detector mid-pinch once zoom started
affecting slack — the key is now the stable `(image, screenW, screenH)`, and the pan-to-alignment
math recomputes slack fresh from the live `zoomLevel` on every gesture callback. Build + tests green
(`WallpaperGeometryTest`, 5 cases covering the tight-axis-has-zero-slack property, zoom opening up
slack on the previously-tight axis, alignment coercion, and degenerate-dimension fallback).

## Backup/restore completeness audit — scope of what's covered, and what deliberately isn't

Bug fix, user-reported: "restore is not exactly the same as backup." Root cause #1, and the direct
match for the report: `TileEntity.gridSlot` — the absolute grid cell that anchors a tile's position
in the default STICKY (WP-style gap-preserving) tile-arrangement mode — was never read or written by
`BackupManager`'s JSON codec at all, so every restore silently dropped it and let tiles re-flow to
different positions than what was actually exported. Fixed additively (no version bump — old backups
without the field still decode it as null, same as before).

A fuller audit (agent-driven, read-only) then surfaced several entire domains added in later sessions
that were never wired into backup/restore either: feed subscriptions/custom URLs/regions
(`FeedStore`), hidden apps (`HiddenApps`), feed widget layout (`WidgetStore`), the photos-tile
selection (`PhotosStore`), and the wallpaper slideshow's photo list (`WallpaperSlideshowStore`).
Extended `BackupData`/`BackupManager.buildBackupJson`/`parseBackup` (`:core:data`) with new,
additive/optional fields for all of these — plain local mirror types (`BackupFeedSource`,
`BackupWidget`) rather than importing the feature-owned `FeedSource`/`HostedWidget` types directly,
since `:core:data` must not depend on feature modules; `StartViewModel` (which already depends on
both) maps between them at the export/import call sites. Added a bulk-replace function to each store
that only had incremental mutators (`HiddenApps.replaceAll`, `FeedStore.replaceSourcesAndRegions`,
`WidgetStore.replaceAll`) — `PhotosStore`/`WallpaperSlideshowStore` already had a whole-list
`setUris`, reused as-is.

Deliberately scoped out:
- **Recent apps / recent searches** — excluded on purpose. These are MRU history, not user
  configuration; restoring them would overwrite the *current* device's actual usage history with
  whatever was captured at export time, which is the wrong direction for a "restore my personalization"
  feature. Their own stores also expose no bulk-replace, reinforcing that they were never meant to be
  bulk-written.
- **Feed article cache** — not backed up (refetchable, and `FeedStore.replaceSourcesAndRegions`
  explicitly clears it on restore so stale articles don't linger against a newly-restored source
  list; the next scheduled/one-off `FeedRefreshWorker` run repopulates it).
- **Weather cache** — unchanged, still excluded; refetchable, not user state.
- **The automatic rolling layout-history snapshots** (`saveLayoutSnapshot`/`restoreFromSnapshot`)
  deliberately still only cover tiles/folders/settings, at their existing defaults for the new
  `BackupData` fields (empty) — feed subscriptions/hidden apps/etc. aren't really part of "the
  layout," and history entries are frequent/automatic, not the deliberate act export/import is.
  Only the manual SAF export/import path (`StartViewModel.exportBackup`/`importBackup`) captures and
  restores the full extended set.
- **Feed widget ids are restored selectively, not wholesale**: a `HostedWidget.widgetId` is bound to
  this specific `AppWidgetHost` instance and isn't portable like the rest of a backup (a cross-device
  restore, or a reinstall, invalidates every existing id). `importBackup` filters the restored list
  to ids that still resolve via `AppWidgetManager.getAppWidgetInfo` before writing, so a foreign/stale
  id is dropped rather than kept as a broken slot — same-device history-style recovery works fully,
  cross-device restore gracefully loses just the widgets (sizes/order for everything else is intact).
- **Photo URIs (photos tile + wallpaper slideshow) are backed up as plain content URI strings with
  the same known caveat as the existing custom-wallpaper URI**: persistable grants are best-effort
  and may not resolve after a reinstall or on a new device; a broken URI degrades the same way an
  already-revoked custom wallpaper URI does elsewhere in the app, not a crash.

Build + tests green (`BackupManagerTest` extended: gridSlot round-trip + hash sensitivity, and a new
round-trip test for hidden apps/feed sources+regions/widgets/photo URIs, plus a "missing keys decode
as empty" test for old-backup compatibility).

## Feed/glance redesign, Personalize restyle, and feed-widget sizing/reorder (feed-glance-redesign branch)

Three-part plan, user-approved via plan mode up front, executed one part at a time on a
dedicated branch (`feed-glance-redesign`) specifically so it could be reverted wholesale if the
new look wasn't wanted. Landed as a single squashed-in-spirit history of small, individually
verified rounds (build + tests green, then installed and visually checked on an emulator and the
user's physical device after nearly every round).

**Part A — feed/glance page.** Replaced the old glance/news tab switcher in `feed/FeedPage.kt`
with one continuous scroll matching an external mockup: a personalized "good morning, `<name>`"
greeting (new `LauncherSettings.userName`, best-effort auto-seeded once from the device's own
contact profile via `ContactsSource.queryProfileName` if `READ_CONTACTS` is already granted, freely
editable in Personalize afterward — never re-seeds once set, same guard shape as the existing feed
region seeding), a condensed weather+today row side by side (`Row`/`weight(1f)` around trimmed
`WeatherCard`/`AgendaCard`), then widgets, device status, and news inline with the settings gear
moved into the news section's own header. `greetingFor(hour: Int)` (time-of-day bucket boundaries)
is a pure, unit-tested function. Two on-device-reported bugs fixed in the same part: the feed
panel's blurred wallpaper bled onto Start because Compose's `graphicsLayer` doesn't clip its
children by default — fixed with an explicit `clipToBounds()`; and text sitting directly on the
background (greeting, date/clock) was unreadable in some themes because it used the fixed white
`tokens.fg` regardless of what was actually behind it — fixed by deriving `feedFg`/`feedFgDim` from
the *actual* rendered background's measured brightness (reusing the same brightness classification
Start already applies to glass/tiled tile faces), not an assumption. A short-lived news
quick-filter chip row was added then explicitly removed again per user feedback — a separate,
already-existing per-region feed picker covers that need, and duplicating it inline on the glance
screen was redundant.

**Part B — Personalize sheet.** Restyled `PersonalizeSheet.kt` to match a second mockup: theme
collapsed from a toggle + conditional pair into one flat `dark | light | auto` segmented row;
tile-color-source and tile-arrangement (pack mode) each collapsed from full descriptive cards to
compact inline segmented pills; the "tile background" and "tile style" groups merged into one.
New `liveTilesEnabled` master on/off switch, folded into the existing `rememberLiveTilesActive`
gate as one more `suspended` input — no new gating mechanism needed. The "+ clock/+ weather/+
calendar" quick-add buttons moved out of the live-tiles group and into `CategoryFolderSheet`,
alongside the rest of that sheet's app-adding affordances. New `NewsRegionSheet` (a shared region
chip-grid, used both from Personalize and the feed's own `FeedSettingsSheet`) and (initially)
`NotificationsPermissionsSheet` — the latter was revised again later in the same branch (see
below) once its "badges & live mail" content turned out to belong closer to the live-tiles toggle
that actually depends on it, and once jumping straight to the notification-access settings screen
on enabling live tiles turned out to be too abrupt without an explanation first.

**Part C — widgets on the Start grid: researched, then explicitly dropped.** The original
"proposed" idea was long-press an empty Start cell → pick a widget → it lands on the tile grid →
drag to resize its footprint. Traced the whole stack (`GridPacker`/`TileModel`/Room schema/
`editDragGesture`) and found the packer's own cell-collision math is already fully generic over
arbitrary footprints — the only real blocker was `TileSpec`/`TilePlacement` being typed to the
closed 4-value `TileSize` enum. A full plan was drafted (new `TileModel.Widget` tile kind, schema
migration, a shared `AppWidgetHost` hoisted above both Start and the feed, continuous drag-resize
instead of tap-to-cycle, merge/stack exclusions) — but discussing it surfaced two problems with no
good answer, so the user dropped it rather than build it: (1) letting a widget take any col×row
footprint clashes with the tile grid's whole visual identity, a rhythmic mosaic of exactly 4 fixed
shapes — mixing in arbitrary-sized widgets risks exactly the "aesthetic imbalance" flagged during
the discussion, with no clean mitigation short of constraining widgets back to tile-like sizes
(which would defeat the point); (2) tile-stacking's carousel model depends on every member sharing
one fixed footprint and being simple, low-interactivity glance content — neither holds for a real
Android widget (each wants its own natural size; already interactive/scrollable on its own), so
"stack widgets like tiles" isn't a small extension of the existing mechanism, it's a different,
harder feature. **What survived**: the one piece of Part C's thinking that generalizes cleanly —
size something to its own preferred footprint instead of one fixed size — was redirected to the
feed page's *existing* widget hosting, landing as the half/full-width classification + side-by-side
pairing described in the CLAUDE.md status entry (not the Start grid at all).

**Wallpaper on the glance screen — went through three iterations before landing.** First shipped
as "always show Start's wallpaper, but always blurred, regardless of Start's own blur toggle" (the
original Part A plan). User feedback moved the target twice more: first to "the glance screen
should never show the literal photo at all — always an abstract colour gradient synthesized from
its prominent colours, not a blurred version of the real photo" (landed via `androidx.palette`,
surfacing and fixing a real Palette bug — see the CLAUDE.md entry — where a near-flat photo can
make every named swatch, including `dominantSwatch`, come back null even though `generate()`
itself reports success); then to "even when Start *has* a wallpaper set, the glance screen should
still have its own independent option to show no background at all" — landed as
`LauncherSettings.feedNoBackground`, decoupled entirely from Start's own wallpaper setting rather
than reusing the existing `noWallpaper` (Start-has-nothing-set) flag, since the two are genuinely
different conditions with different owners.

**Feed widget reordering: buttons → drag-and-drop, after a state-loss bug the buttons caused.**
The half/full sizing pass (see CLAUDE.md) initially kept the existing ↑/↓ move buttons, generalized
from full-width-only to work for the new paired layout too. On-device testing found tapping either
button *also* silently exited the widget's edit mode — traced to `packWidgetRows` legitimately
reshuffling which row (and which Compose parent) a moved widget ends up under, which reparents its
composable; `WidgetView`'s `editing` was local `remember` state at the time, and local `remember`
doesn't survive a reparent. Rather than patch around that one call site, replaced the reorder
mechanism entirely with drag-and-drop per direct user request, and fixed the underlying state
problem properly: `editing` moved to a per-widget-id map hoisted in the stable `WidgetSection`
parent, which a reparent can't affect. The drag itself only *commits* a reorder on release, not
continuously while dragging — an earlier design that reordered live (mirroring how Start's own
tile drag commits continuously) was rejected specifically because it could reparent the dragged
widget's *own* composable — including the very gesture detector tracking the finger — mid-touch,
which risks silently orphaning the gesture rather than just cosmetically resetting a flag. The
edit-mode overlay's controls were separately found (same on-device round) to detach from a widget's
true position when scrolled, and to exit edit mode on scroll or reorder — both traced to using a
window-level `Popup` (which positions relative to a captured anchor that doesn't reliably track
content inside a scrolling container) instead of a plain in-place Compose overlay; switching to the
latter fixed both for free, since in-place content scrolls and reorders exactly like the rest of
the widget already does.

## Data Safety form rejection: approximate location was collected but not declared

versionCode 228 was rejected under "Invalid Data safety form": Play detected user data transmitted
off-device (approximate location) that wasn't declared in the app's Data Safety section. Confirmed
this is a real, longstanding gap, not a false positive — `WeatherRefreshWorker`
(`feature/livetiles/WeatherWork.kt`) reads a last-known coarse fix (gated on `ACCESS_COARSE_LOCATION`)
and `OpenMeteoWeatherProvider` (`OpenMeteoWeather.kt`) sends that lat/lon to Open-Meteo over HTTPS to
fetch the weather-tile forecast — exactly what `docs/PRIVACY_POLICY.md` §1/§2 already discloses in
prose, but the Play Console Data Safety *form* itself was never updated to match. An earlier session's
notes (S21-era, see the "known issues" trail in `CLAUDE.md`) had already flagged the Data Safety form
as suspect — "Precise location" was checked even though the app only ever requests coarse — but that
was never corrected.

This is a **console-only fix, no app rebuild required**: the Data Safety section (Play Console → App
content → Data safety) needs updating to match what the app actually does, then resubmitted for review
via Publishing overview — no new versionCode needed unless the declaration change is bundled with an
unrelated code change anyway. Fix isn't tracked in this repo since Play Console has no exportable
config file; the values to set are recorded here so the next rejection (if any) can be diffed against
what was actually declared:

- Location → **Approximate location**: collected = yes; purpose = App functionality; optional (the
  permission is opt-in — the weather tile just stays static if denied); encrypted in transit = yes
  (HTTPS to Open-Meteo); not shared for advertising/analytics — Open-Meteo only processes the
  coordinates to return a forecast on the app's behalf, so this is a service-provider pass-through
  under "App functionality," not a third-party data sale/share.
- Location → **Precise location**: should be **unchecked** — the app never requests
  `ACCESS_FINE_LOCATION`, only `ACCESS_COARSE_LOCATION`. If it's currently checked (per the earlier
  session's suspicion), that's a separate stale/incorrect declaration to remove in the same pass.
- No other data type declarations needed changing for this rejection — contacts/calendar/notification
  content all stay on-device (never transmitted off the device), per the privacy policy's existing
  accurate table.

## Feed greeting name: retry the contact-profile seed on permission grant, not just at init

User report: "user name not collected from profile" — the feed's "good morning, `<name>`" greeting
(`StartViewModel`'s `userName` seeding, added in the feed/glance redesign) never picked up the
device's own contact profile name even with contacts access granted. Root cause was a race, not a
logic bug: the one-shot seed ran inside `StartViewModel.init`, which executes during the very first
Compose composition — before `MainActivity`'s `LaunchedEffect`-driven runtime permission request has
even shown its dialog, let alone been granted. `ContextCompat.checkSelfPermission` at that moment is
always `PERMISSION_DENIED`, so the seed silently no-ops, and nothing ever re-triggers it afterward.
Because TileShell is the default launcher, its Activity/ViewModel is extremely long-lived (survives
normal home/back navigation), so a one-shot init-time check effectively never gets a second chance in
a real session.

Fixed by extracting the seed logic into `StartViewModel.seedUserNameFromProfileIfBlank()` (still
gated on `userName.isBlank()`, so it never clobbers a name the user has since typed in or cleared),
and re-invoking it from `StartScreen.kt` via `LaunchedEffect(contactsGranted)` — `contactsGranted` is
the existing `rememberPermissionGranted(READ_CONTACTS)` state, which already re-checks on `ON_RESUME`.
This covers both the in-app grant flow (permission dialog → resume → effect fires) and granting via
system Settings and returning to the app, without touching the existing `contactsLauncher` callbacks.
Build + tests green.

## Now-playing tile: don't flip to "paused" while actually playing

User report: "now playing tile shows pause even when the music is playing." The music tile's data
pipeline (`MediaSessionsEffect`/`MediaCenter`/`nowPlayingFrom`) was correct throughout — the bug was
that `LiveFace.MUSIC` sits in the same generic decorative `liveIds` flip pool as clock/weather/mail
(`flips = true`), and the shared scheduler (`rememberFlipState`) flips a random live tile every 2.6s
purely on a timer, with no awareness of tile content. For those other tiles a stray flip while
"wrong" is harmless (calendar/weather's fallback face ignores `flipped`), but `MusicTile`'s back face
(`MusicBack`) unconditionally renders hardcoded "paused / tap to resume" text regardless of the real
`NowPlaying.playing` state — so any random flip landing on the music tile mid-song showed a false
"paused" claim, invisible to the user until the next 2.6s tick happened to flip it back.

Fixed at the consumer, not the shared scheduler (matching the existing calendar/weather convention of
guarding on the consumer side): `MusicTileFace`'s `FlipTile` now uses `flipped = flipped && !np.playing`
— the back face can only show while genuinely paused/stopped, when "paused / tap to resume" is both
true and a useful affordance. `rememberFlipState`/`liveIds`/`FlipTile` are unchanged and still shared
identically by every other live face. Build + tests green.

## "badges & live mail" is a separate row again, not folded into the live-tiles toggle

User report: "live tiles setting is on by default even when notification access not asked/given. it
should be corrected." `LauncherSettings.liveTilesEnabled` defaults to `true` (correctly — it only
gates the flip/animation loop, and most live faces — clock/weather/calendar/photos/people — need no
permission at all and are meant to work out of the box). The real gap: a prior session's redesign
had folded the notification-access ask into the master toggle's `onChange` (only firing the "allow
live tile updates?" explainer dialog when the user *interactively* flipped the toggle on) — but since
the toggle is born `true` on a fresh install, it's never flipped, so the dialog never fires and the
user is never asked. Badge counts render unconditionally whenever notification access happens to be
granted regardless of this toggle (`StartScreen.kt`'s `notifications.badgeFor(...)`), so the toggle
showing "on" gave a false impression that mail/badges were already live.

Asked the user for a preference: keep `liveTilesEnabled` defaulting on (don't regress permission-free
tiles) vs. default the whole system off until the user opts in. They chose a third option: restore
"badges & live mail" as its own row, independent of the master toggle, directly below it — matching
how it worked before this area was consolidated (a nav row reading "on ›" / "allow access ›" off the
raw `NotificationAccess.isEnabled()` system state, not a new persisted boolean). `PersonalizeSheet.kt`'s
"live tiles" group: the master `ToggleRow`'s `onChange` no longer triggers the permission dialog at
all (now just `onLiveTilesEnabledChange` directly); a new row right below it, tapping either opens
`onNotificationAccess()` directly (already granted) or the existing explainer `AlertDialog` (not yet
granted, same wording as before — just triggered from here instead of the toggle). This isn't a new
persisted setting — `notificationsEnabled` is still the same raw system permission-grant boolean
(`rememberNotificationAccess()`) that already existed; the change is purely which control triggers the
ask, decoupling it from the master live-tiles switch. Build + tests green.

## Play Console pre-launch recommendations for v2.3.0/code 230: traced, one real fix

User surfaced 3 "actions recommended" from Play Console's Production track for the 230 (2.3.0)
release: (1) "edge-to-edge may not display for all users," (2) "your app uses deprecated APIs or
parameters for edge-to-edge" — flagging `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` at obfuscated
location `a4.b.t` — and (3) "improve your app's performance with bitmap downsampling" at `b6.o.u`.
User's concern: these look like things already addressed (v2.2.7's build.gradle.kts comment claims
"dropped the deprecated statusBarColor/navigationBarColor... switched the cutout mode to 'always'").

Traced both edge-to-edge items using the actual R8 mapping file from this build
(`app/build/outputs/mapping/release/mapping.txt`) rather than guessing: `a4.b.t` deobfuscates to
`androidx.emoji2.text.ConcurrencyHelpers$Handler28Impl$$ExternalSyntheticApiModelOutline0.m
(android.view.WindowManager$LayoutParams)` — a D8-synthesized API-compat bridge method inside the
**androidx.emoji2 library** (`androidx.emoji2:emoji2:1.4.0`, already the latest resolved version per
`./gradlew :app:dependencies`), not our own source. Our own code was independently confirmed correct:
`app/src/main/res/values/themes.xml:15` sets `android:windowLayoutInDisplayCutoutMode` to `always`
(never `shortEdges`), and `MainActivity.kt` calls `enableEdgeToEdge()`; there is exactly one Activity
in the manifest. So this flag and the companion "may not display for all users" advisory are Play's
static analysis surfacing a legacy compatibility shim bundled inside a transitive AndroidX dependency
— not a regression, and not something fixable from app code short of excluding emoji2 entirely (not
worth doing: it provides legitimate emoji-rendering compat and the flagged bridge method is very
likely unreachable dead code in this app's actual usage, just present because R8 keeps synthetic
API-bridge classes for safety).

`b6.o.u` was different: it deobfuscated the same way conceptually, but rather than chase the mapping
further, a direct grep for `BitmapFactory` across the codebase found the real gap immediately —
`LayoutHistorySheet.kt`'s `SnapshotRow` decoded a layout-history snapshot's full-screen `PixelCopy`
screenshot via a bare `BitmapFactory.decodeFile(path)`, no `Options`/`inSampleSize` at all, to render
a 64×104dp row thumbnail. Every other `BitmapFactory` call site in the app (`TileBitmap.kt`,
`WallpaperBackground.kt`, `RemoteImage.kt`) already follows the two-pass `inJustDecodeBounds` →
computed `inSampleSize` pattern; this one call site was a genuine, missed exception — a real,
previously-unaddressed instance of exactly what Play flagged. Fixed with a module-local
`decodeSampledScreenshot`/`thumbnailSampleSize` pair (mirrors the existing per-module pattern — each
module keeps its own private sample-size helper rather than a shared cross-module one), targeting a
300px longer side. Build + tests green.

## Edge swipe-down for notifications/quick settings

User request, not in the WP prototype/spec: a single-finger swipe down starting from the left screen
edge opens the system notification shade; from the right screen edge opens system quick settings.
Real Android's shade-pull normally only responds within the actual status bar strip — a normal app
(without a signature-level permission) can't call the hidden `expandNotificationsPanel`/
`expandSettingsPanel` APIs directly. Since TileShell already ships an `AccessibilityService`
(`LockAccessibilityService`, used for the gear long-press screen-lock and the edge-strip's "recents"
button) that calls `performGlobalAction`, extended it with two more one-liners:
`expandNotifications()` → `GLOBAL_ACTION_NOTIFICATIONS` and `expandQuickSettings()` →
`GLOBAL_ACTION_QUICK_SETTINGS`. Both actions have existed since API 16/17 — well under this app's
minSdk 26 — so unlike `GLOBAL_ACTION_LOCK_SCREEN` (API 28+, needs the device-admin fallback in
`ScreenLock.kt`) neither needs any `@RequiresApi`/version-fallback path.

Gesture recognition (`EdgeSwipeGesture.kt`, `:feature:start`) is a single-finger variant of the
existing two-finger quick-search/quick-panel swipe gestures in `StartScreen.kt`: same
`awaitFirstDown`/`awaitPointerEvent(PointerEventPass.Initial)` shape, same "never consume until the
gesture's own trigger condition fires" rule (so an ordinary tap, tile long-press-drag, or vertical
grid scroll starting away from either edge passes through completely untouched) — but keyed off
*which screen edge the touch started in* (`edgeZoneFor`, a pure classifier checking only the touch's
starting X coordinate against a `EDGE_SWIPE_ZONE_DP` = 32dp strip on each side) rather than pointer
count. Deliberately **not** gated to the top of the screen — the touch can start at any height along
the left/right edge, matching the user's explicit correction ("it is not swipe down from top edge ..
it is mid screen") over an initial assumption that this should mirror the real status-bar pull-down.
Enable-gating mirrors the two-finger gestures exactly (`swipeEnabled && restingAtStart && !searchOpen
&& !quickPanelOpen && !anySheetOpen`), so it's live only while resting on Start with nothing else
already capturing touch — edit mode already disables `swipeEnabled`, so mid-edit-mode tile drags
starting near an edge column are never at risk of misfiring this gesture.

Wiring follows the existing `onLockScreen`/`onRecents` pattern exactly: `MainActivity` attempts the
action first (`LockAccessibilityService.expandNotifications()`/`expandQuickSettings()`, both false if
the service isn't connected yet) and only falls back to the existing `AccessibilityDisclosureDialog`
→ Accessibility Settings flow when that attempt fails — no new dialog copy needed beyond extending
the existing itemized disclosure text to also mention this gesture. No manifest/XML changes needed;
the accessibility service declaration is shared as-is. Build + tests green (`EdgeSwipeGestureTest`
new: edge-zone classification + vertical-dominance threshold).

## Occasional "enjoying tileshell?" rating prompt

User request: randomly ask the user to rate the app if they haven't already responded, gated behind
an "are you enjoying it?" question rather than jumping straight to a store review. First implementation
attempt gated on an "app open count" (mirroring how a normal app counts launches) — user corrected this
immediately: "it can not be linked to open as it is launcher app," since TileShell *is* the launcher, so
its Activity has no discrete per-launch lifecycle the way a normal app does (`onNewIntent` fires on
Home-press, not a fresh launch; `singleTask` + the manifest's `configChanges` keep the same Activity
instance resident indefinitely). Reworked to be purely day-interval based instead
(`isRatingPromptCheckWindowOpen` in `:core:data`'s new `RatingPromptPrefs.kt`): no ask before
`RATING_PROMPT_MIN_AGE_MS` (3 days) since first launch, then at most one "check window" every
`RATING_PROMPT_INTERVAL_MS` (5 days), each with only a `RATING_PROMPT_CHANCE` (30%) chance of actually
showing — evaluated on every `ON_RESUME` (mirrors `rememberAppUpdateState`'s re-check pattern) rather
than once per process, since a launcher resumes many times a day. The "last asked" clock advances the
moment a check window opens, *regardless* of the roll's outcome — otherwise a resume storm within one
day would re-roll on every resume until one hit, collapsing the multi-day interval down to "the first
resume after the window opens." Answering either way (`RatingPromptPrefs.markResponded`) stops it being
asked again, ever; dismissing the initial ask without answering does not, so it can resurface at the
next window.

"Enjoying it" originally called Play Core's `ReviewManager.launchReviewFlow` directly. Live on-device
testing (physical Samsung device, debug build installed via plain `adb install`) showed tapping "yes!"
did nothing visible at all — confirmed via `adb shell run-as com.tileshell cat shared_prefs/
tileshell.prefs.xml` that the tap really did fire (`rating_responded` flipped to `true`), so the dialog
itself was working; Play's review API was the dead end. This is expected Play behaviour, not a bug: the
native review overlay only ever renders for a build installed through a Play-associated channel
(internal/closed/open testing or production) — a plain adb-sideloaded debug build (this project's whole
local test loop) makes `requestReviewFlow()` "succeed" with a no-op `ReviewInfo` that shows nothing when
launched, and Google additionally applies a silent, undocumented per-app quota on top even for real
installs; neither condition is something the app can detect. First fix attempt replaced the native API
outright with a direct `market://details?id=...` deep link to the store listing page (verified working
live on-device via screenshot — Play Store opened straight to the TileShell listing with a visible
"Your review" section). User then asked for the *actual* review screen, not the listing page
("should directly open the review screen not app listing page") — reinstated the native
`ReviewManager` flow as the primary path (`InAppReview.launch`, `:feature:system`, new
`play-inapp-review-ktx` dependency), falling back to the store-listing deep link only when the request
task genuinely fails (no Play Store on the device, an exception) — deliberately *not* falling back
just because nothing visibly happened, since that's indistinguishable from Play's normal silent-quota
behaviour and always-falling-back would defeat the point of using the nicer native overlay whenever it
does work in production. Documented clearly in code and to the user: this project's adb-sideload test
loop can never visually confirm the native overlay renders — that's only verifiable once shipped to a
real Play testing track.

"Not really" opens a second dialog offering an email feedback channel (`mailto:` `ACTION_SENDTO` to the
existing support address from `docs/PRIVACY_POLICY.md`) instead of a "no thanks" dead end — verified
live on-device (screenshot) that the second dialog renders correctly; tapping "send feedback" itself
best-effort `runCatching`s the intent, since not every device has a mail app configured to handle
`ACTION_SENDTO`, and there's nothing more useful to do if it doesn't (a Play Store review page would be
the wrong response to "not really enjoying it"). Build + tests green (`RatingPromptTest` new: 7 cases
covering the day-interval/quota-window math and the roll threshold).

## Quick Panel redesigned as true square tiles (real WP Action Center), settings tile, screen lock relocated

User attached a real Windows Phone/Lumia Action Center photo: a dense grid of **perfect square**
tiles (5 across), where brightness shows its current level as bold tile text ("25%") and steps on
tap, not drag. The Quick Panel (`QuickPanelOverlay.kt`) had drifted from its own design spec
(`docs/QUICK-PANEL-SPEC.md` explicitly called for squares) into fixed-height wide chips
(`weight(1f) × 44dp`, icon-left/label-right) plus separate wide `LiveTileSlider` drag rows for
brightness/volume and a plain row for screen timeout — justified in-code at the time as "one
proportional grid, not mixed sizes." Rewrote the whole panel into **one unified grid of true squares**
(`Modifier.weight(1f).aspectRatio(1f)`, `chunked(5)` rows — 5 columns confirmed by the user over 4
(Start's own default), since "few settings are added" made 5 the better fit) — icon top-center, short
state label bottom-center, same on/off contract as before (`accent`+white when on, `tokens.chip`+
`tokens.fgDim` when off). Binary toggles (wifi/bluetooth/flashlight/dnd/airplane/location/rotation
lock) keep that on/off coloring; brightness/volume/timeout/settings/android-settings/lock-screen are
all "value" tiles that render neutral always (matching the real photo, where non-toggle tiles like the
brightness "25%" tile are gray, not blue) — same shared tile composable throughout, just `active =
false` for the value tiles.

**Brightness and both volume streams (media + ring) became tap-to-step**, replacing `LiveTileSlider`'s
continuous drag entirely — the real device has no slider there at all. New `nextPercentLevel`
(`:feature:livetiles/SystemToggles.kt`) cycles `[0, 10, 20, 40, 60, 80, 100]`, deliberately using a
strict `>` (unlike the existing `nextScreenTimeoutPreset`'s intentional `>=`-based "skip past the next
preset" behavior) so an arbitrary starting value that doesn't land exactly on a step (e.g. system
brightness at 45%) still always advances to the very next step up. Screen timeout became a square tile
too (it was already tap-to-cycle, just reshaped).

**Settings gear → a real Start tile.** The floating corner gear icon (bottom-right, long-press = lock
screen) is gone entirely; Personalize is now opened by a normal, draggable/resizable/unpinnable Start
tile — `DefaultTile("t-personalize", ..., app = "personalize", liveOnly = true)` in `DefaultLayout.kt`,
following the exact same blank-package/`selfContainedComponent` pattern the weather/calendar/clock
liveOnly tiles already use (no schema change). `iconFor("personalize")` maps to the existing gear glyph.
Tapping it is special-cased at the two call sites that already dispatch tile taps (`StartScreen.kt`'s
`onTile`/`onLaunchFolderChild` lambdas, which already have `viewModel` in scope) rather than threading a
new callback through the `onTileClick`/`launchFolderChild` top-level functions — simpler, and the
existing `packageName.isBlank() && iconKey == "settings"` check is enough to identify it uniquely (no
other liveOnly tile uses the settings glyph).

This retires the **existing default "Settings" app tile** (opened Android's real Settings app,
`DefaultTile("t-settings", ..., app = "settings")`) as a separate Start pin — per explicit user
instruction ("provide android settings tile in quick settings ... hide android settings from app list
so no question of creating tile out of it"), the real Settings app is now reachable only via the Quick
Panel's own "android settings" tile, and is hidden from the App List (`HiddenApps`, reused as-is) so it
can't be re-pinned as a duplicate from there. A plain "currently hidden" check isn't the right gate for
the hide, though — the user might deliberately un-hide it later from Personalize's hidden-apps sheet,
and re-hiding it on every subsequent launch would silently undo that choice — so a dedicated one-shot
flag (`SettingsAppMigration`, mirroring `PersonalizeGuidePrefs`'s shape) guards it instead, checked
once at `StartViewModel` init alongside the personalize-tile backfill for existing installs (mirrors
the already-documented `seedStickySlots`-once-at-init pattern: fresh installs get both outcomes for
free from `DEFAULT_TILES`/`seedIfEmpty`, existing installs get backfilled via the existing
`LayoutRepository.addDefaultTile("personalize")`, unchanged, plus a new small `resolvedPackageFor`
lookup added to resolve the real Settings package for the hide).

**Screen lock relocated into the Quick Panel** as its own "lock screen" square tile (per the user:
"shift screen lock functionality to quick settings as one of the settings tile"), reusing the exact
same `onLockScreen` callback/disclosure-dialog flow the removed gear's long-press used — verified
live on-device that it still shows the same "Before you enable accessibility" prompt when the service
isn't connected. A new "lock" monoline glyph was added to `TileIcons.kt` (padlock body + shackle arc).
The corner gear's dead `onLockScreen` plumbing inside `StartPage` (now unused after the gear's removal)
was deleted rather than left dangling.

All of it verified live on an emulator via adb screenshots end-to-end: the panel's true 5-column square
grid; toggle on/off coloring; brightness tapped 10%→20%→40%→60% (confirms the step math); the
"settings" tile closes the panel and opens Personalize; "android settings" opens the real Settings app
(confirmed by opening it live); "lock screen" shows the disclosure dialog; the Start-grid "settings"
tile (a different code path from the Quick Panel's own tile) also opens Personalize correctly; an
**existing** install (not a fresh `pm clear`) picked up the migrated settings tile at the bottom of its
layout without disturbing anything else, and a search for "settings" in the App List correctly returned
"no apps found". Build + tests green (`PercentLevelTest` new; `LayoutSeederTest` updated for the
`t-settings`→`t-personalize` swap).

## Quick Panel follow-up: volume-step bug, real Settings icon, "personalize" rename, app-list entry, theme tile

Same-session follow-up after live on-device testing (physical Samsung device, not just the emulator)
surfaced one real bug and several explicit refinements.

**Real bug: volume tap-to-step "not working properly."** Root-caused by reproducing on-device:
`nextPercentLevel` was being fed a *fresh readback* of the hardware fraction every tap
(`(mediaVolume * 100).roundToInt()`), but media/ring streams have a tiny native range (`AudioManager`
maxes out around 15/7 steps on most devices) — writing a target like 10% rounds to the *nearest
achievable raw step*, and reading that back rounds to a different percent than the one just requested
(e.g. targeting 10% on a 15-step stream actually sets raw step 1 ≈ 6.7%, which reads back as 7%). The
next tap then sees "7%, still below the 10% target" and re-requests 10% again — forever stuck. Brightness
never showed this because its native range (0–255) is fine enough that the rounding error stays within
the same 10%-wide bucket. Fixed with `rememberSteppedPercent`: seed once from the real hardware value
(snapped to the nearest `BRIGHTNESS_VOLUME_LEVELS` step), then only ever advance that local state on tap
— never re-derived from a hardware readback again — so cycling is deterministic regardless of how coarse
the underlying stream's native step count is. Applied uniformly to brightness too, even though it wasn't
actually broken, since the same fragility (depending on incidentally-fine hardware granularity) existed
latently.

**"Android settings" tile now shows the real, device-specific Settings app icon** (`rememberAndroidSettingsIcon`,
resolves `Intent(Settings.ACTION_SETTINGS)` at runtime via `PackageManager`, mirrors `StartScreen.kt`'s
`rememberTileAppIcon` decode-with-fallback shape since that one is file-private) — per user request,
since the generic gear glyph didn't visually distinguish it from the "personalize" tile next to it.
Confirmed on-device that it renders the actual OEM icon (AOSP's blue circular gear on the emulator,
this Samsung's own plain gear glyph on the physical device — correctly device-specific, not hardcoded).

**Renamed the Personalize-opening tile's label from "settings" to "personalize"** (both the Quick Panel
tile and — where it doesn't matter, since it's a SMALL Start tile with no visible label — the underlying
identity), reverting to the naming this doc's *first* pass on this topic had already recommended, which
the user initially overrode and then asked to restore once "android settings" existed as a separate,
real distinction. Uncovered a genuine cross-device text-layout issue in the process: "personalize" (11
characters, one unbroken word) fit fine on the emulator's font metrics but hard-wrapped at an arbitrary
character on the physical Samsung device ("personali"/"ze", no hyphen) since Compose's line breaker has
no space to wrap at within a single word. A soft hyphen (`"personal­ize"`) was tried first as a
targeted per-label fix and **did not work** — the break point didn't move to the hyphen's position at
all on-device, suggesting Android's text stack didn't honor it as a break opportunity here. Reverted
that and instead reduced every tile's label from 10sp/12sp line-height to 9sp/11sp — a general fix
(smaller font buys enough margin for an 11-character word to fit on one line) rather than a fragile
per-label special case that had already proven unreliable once.

**The Personalize-opening Quick Panel tile got a companion in the App List** per explicit request
("show dummy settings(personalisation) app in app list"): a synthetic, non-installed `AppEntry`
(`PERSONALIZE_APP_ENTRY`, blank package/activity, label "personalize" — same blank-identity pattern the
weather/calendar liveOnly Start tiles already use) is appended to `AppListViewModel`'s app list so it's
discoverable/searchable there too. `AppListScreen`'s row tap special-cases the blank package to call a
new `onOpenPersonalize` callback instead of `AppLauncher.launch`; the long-press pin/hide/uninstall menu
is skipped entirely for it (`isPseudo` check) — none of those map cleanly onto a non-installed entry:
`LayoutRepository.pinApp`'s dedup counts *every* blank-package tile together (would misfire against the
already-pinned weather/calendar/personalize tiles), and uninstall has no real package to act on. Its row
icon renders the gear glyph directly rather than attempting (and failing) a `PackageManager` icon lookup
for a blank component.

**Theme (dark/light/auto) became tiles, in two places, per two separate explicit requests.** First, in
Personalize (`PersonalizeSheet.kt`, "make tile of dark light auto theme"): the existing 3-way segmented
row (`SegCell`, still used elsewhere on that sheet — wallpaper type, tile background style, pack mode —
deliberately left untouched, out of scope) became three square `ThemeTile`s (moon/dark, sun/light,
circle-bisected-by-a-line/auto — the last two new monoline glyphs added to `TileIcons.kt`), accent-filled
for whichever is currently selected, matching the Quick Panel's own on/off tile contract. Second, in the
Quick Panel itself (a separate later request: "i wanted theme tile in quick serttings" — initially built
as three tiles mirroring Personalize, then corrected: "it should be a single tiie for theme with
cghanging icons") — one tap-to-cycle tile (`ThemeChoice` enum + `themeChoiceFor`/`nextThemeChoice`,
unit-tested), matching the brightness/timeout tiles' "one tile, changing icon+label, cycles on tap"
convention instead of three separate tiles. Initially shipped neutral (`active = false`, like the other
value tiles); corrected once more ("actually it should be managed by accent color (theme)") to
`active = true` — it always represents a real current selection (unlike brightness/volume's raw
numeric readout), so it accent-highlights permanently, matching how Personalize's own three theme tiles
highlight whichever one is currently selected.

**Single-finger swipe-up from either screen edge now also opens the Quick Panel**, alongside the
existing two-finger swipe-up gesture — explicit request ("also add gesture single finger up from edges
to call quick settings, this is in addition to double swipe"). `isEdgeSwipeUp` (`EdgeSwipeGesture.kt`)
mirrors the existing `isEdgeSwipeDown`'s shape (`dy < -thresholdPx && abs(dy) > abs(dx)`); both directions
share the same `edgeZoneFor` zone check and the same single `pointerInput` block in `StartScreen.kt` —
down-left/down-right still route to system notifications/quick-settings as before, up from *either* edge
(zone doesn't matter for this direction) opens the in-app Quick Panel. During live testing, Samsung's own
One UI system panel appeared on screen a couple of times right after a test swipe; on user clarification
this was the user's own direct interaction with the physical device happening concurrently with the
automated adb testing, not a gesture collision — corrected here after an earlier draft of this entry
mischaracterized it as a device/OS-level priority conflict. No such collision is confirmed; the gesture
works as implemented.

Build + tests green throughout (`PercentLevelTest`, `ThemeChoiceTest`, `EdgeSwipeGestureTest` all
extended); verified live on both an emulator and a physical Samsung device via adb screenshots at each
step.

## Quick Panel follow-up 2: distinct icons, personalize pinnable from the App List, android settings row repositioned

Third round of same-session refinement, all from explicit user requests after seeing the previous
round's screenshots.

**"settings tile should have android icon. and personalise tile should have personalise icon"** — the
personalize Start tile and the Quick Panel's "personalize" tile had been sharing the plain gear glyph
with the *real* Android Settings tile/row, undermining the whole point of distinguishing them.
`DefaultLayout.iconFor("personalize")` now maps to `"palette"` (an existing glyph, already used for the
per-tile colour picker — fits "customize" thematically, no new icon needed) instead of `"settings"`.
The Personalize sheet's own "android settings" nav row (see below) and the Quick Panel's "android
settings" tile both already used a *real*, device-resolved Settings-app icon bitmap
(`rememberAndroidSettingsIcon`) from the previous round, so only the personalize side needed a change.

This exposed a real gap: **Room doesn't retroactively apply a new `iconFor` mapping to an
already-persisted tile** — only freshly-*seeded* tiles pick up a mapping change; the emulator's
personalize tile (seeded earlier in this same session, before the mapping changed) kept rendering the
old gear glyph despite the code change. Confirmed live, then fixed with a new
`LayoutDao.updateTileIconKey`/`LayoutRepository.updateTileIconKey`, wired into
`StartViewModel.migrateSettingsTile()`: if a personalize tile already exists but its `iconKey` isn't
`"palette"` yet, it's patched in place. Verified on-device afterward that the existing tile picked up
the new palette icon without needing a fresh install, and that tapping it still correctly opens
Personalize (the identity check was **also** decoupled from `iconKey` in the same pass — it now checks
`packageName.isBlank() && label == "personalize"` instead of `iconKey == "settings"`, in both
`StartScreen.kt`'s tap handlers and the migration check, via a new shared `List<TileModel>
.hasPersonalizeTile()` extension in `:core:data` — so icon choice and tile identity stay fully
decoupled going forward, not just for this one rename).

**"pin option should be available for personalise app shown on app screen... in case user accidently
deletes the personalisation tile"** — the synthetic App List entry's long-press menu previously skipped
pin/hide/uninstall entirely for any blank-package entry. Split that apart: "pin to start" is now enabled
specifically for it (`AppListViewModel.pinPersonalize()` — checks `hasPersonalizeTile()` first and only
calls `LayoutRepository.addDefaultTile("personalize")` if it's actually missing, rather than reusing the
generic `pinApp(AppEntry)` flow, which dedups by `appTileCount(packageName)` and would misfire since
every liveOnly tile shares the same blank package); hide/uninstall remain disabled (still don't map onto
a non-installed entry).

**"the devices real setting[s] app - should be renamed as android settings"** + moved to the top of the
Personalize sheet with its own icon, per an earlier request in the same round — the new nav row (added
just before this round, initially with a bare `"the device's real settings app"` subtitle and the
generic gear glyph) was restructured to match the sheet's other nav-row convention (bold title + dim
subtitle + "open ›"): title "android settings", subtitle "the device's own settings app", and the real
device-resolved Settings icon (`rememberAndroidSettingsIcon`, duplicated locally in
`PersonalizeSheet.kt` since the Quick Panel's original is file-private in a different module).

All verified live on an emulator via adb screenshots: the Start tile shows the palette icon (confirmed
on an *already-existing* tile, proving the backfill migration works, not just fresh seeds); tapping it
still opens Personalize; the new top-of-sheet row renders with the real device icon, bold title, and
subtitle; the App List's "personalize" entry is searchable and shows the palette icon. Build + tests
green throughout.

## Quick Panel follow-up 3: personalize reverts to the gear icon, real Settings tile brought back with its own icon, App List unhide

Fourth round of the same-session Quick Panel work, triggered by explicit user correction after seeing
the "distinct icons" round's screenshots on their physical device.

**"personalisation tile on start screen should have gear icon and settings tile on start screen should
have android settings icon."** The previous round's `"palette"` icon for the personalize tile is
reverted — `DefaultLayout.iconFor("personalize")` is back to `"settings"` (the gear), by explicit
request, "to keep consistency" with the real Settings tile's own identity as "settings-shaped." The
already-persisted tile from the previous round (backfilled to `"palette"`) needed a *second* backfill —
`StartViewModel.migrateSettingsTile()` now patches any personalize tile whose `iconKey` isn't
`"settings"` back to it, the same one-way-backfill pattern used for the palette change itself.

The user separately clarified: **"the caveat you mentioned is not true, those actions were performed by
me, it is not any one ui issue. please delete that if recorded."** An earlier draft of the
edge-swipe-up entry in this file (and a matching line in `CLAUDE.md`) claimed a Samsung One UI system
panel appeared during testing due to a device/OS-level gesture-priority conflict. The user was directly
interacting with their own phone at the same time as the automated adb test — there was no real
collision. Both docs were corrected in place to remove the incorrect claim.

**Distinguishing the two tiles without two icons.** Rather than reintroduce a second glyph, the real
Android Settings tile is given its own distinct look by resolving the *actual* device Settings app
icon at render time (`StartViewModel.migrateSettingsTile()` clears `iconKey` back to `null` on any tile
whose resolved package matches `settings` and whose `iconKey` is still `"settings"`, so
`StaticTileGlyph`'s existing real-app-icon fallback — `useAppIcon = !TileIcons.hasIcon(tile.iconKey)` —
takes over) — so personalize keeps the generic gear glyph, and the real Settings tile shows its actual
device icon, and the two are visually distinct without adding a new monoline icon key.

**"settings tile is already present... let it remain on start screen (bring back) and also unhide
android settings app on app screen."** The user clarified the real Settings Start tile was never
actually removed on their device (it survived from an earlier layout structure, before this session's
`DefaultLayout` change dropped it from fresh-install seeding) — so no `addDefaultTile("settings")` call
was needed, only leaving any existing tile alone. What *did* need reverting: the App List's earlier
one-shot hide of the real Settings app (added when the App List gained a synthetic non-installed
"personalize" entry, to avoid a confusing duplicate). `SettingsAppMigration` is rewritten from a
"hide, once" flag (`hasRun`/`markRun`) to an "unhide, once" flag (`hasUnhideRun`/`markUnhideRun`) —
a plain "is it currently hidden" check isn't the right gate for the reversal, since the user might
deliberately re-hide it themselves later from the App List, and unhiding it on every launch would
silently undo that later choice. The flag guarantees the one-time unhide happens exactly once, ever.

Verified on an emulator and the user's physical device via adb: the personalize Start tile shows the
gear glyph again; the real Settings tile (already present on the physical device from before) now shows
the actual device Settings icon instead of the shared gear; the real Settings app reappears in the App
List, searchable and pinnable again. Build + tests green throughout.

## Quick Panel landscape fix: dock to the right half, above Start, like every other sheet

User report: "quick settings not fine tuned for landscape mode. it spans full screen and tiles
overlap each other. it can be right side only (above start panel, not on feed panel)." Every other
Start-launched sheet (`LayoutHistorySheet`, `BackupRestoreSheet`, `QuickSearchOverlay`,
`BingHistorySheet`, `WallpaperCropOverlay`, …) already docks to the right half in landscape via
`SheetStage(rightHalf = ...)`, and `QuickPanelOverlay` itself already plumbed a `rightHalf` param
through to its own `SheetStage` call — but the Quick Panel's call site in `StartScreen.kt` was the
one sheet that never actually passed `rightHalf = isLandscape` (a plain oversight from when it was
built earlier in the same session as the two-panel landscape layout). Full-screen width meant its
5-column `aspectRatio(1f)` tile grid stretched across the full 2-panel width instead of just Start's
half, squeezing/misaligning the squares — read as "overlapping." One-line fix: pass `rightHalf =
isLandscape` at the call site, same as every sibling sheet. Verified live on a physical device: in
landscape, the panel now docks to the bottom-right half, sitting above the Start panel with the feed
panel on the left fully visible and undimmed; all 14 tiles render as clean, non-overlapping squares.
Build + tests green.

## Quick Panel bluetooth accent bug fix + tile sequence reorganization

User report: "blue tooth is on but setting tile is not showing accent coloiur as it is displaying
for wifi and location. organise the sequence of settings tile well." Two parts.

**Bluetooth accent bug.** The bluetooth tile's `active` was hardcoded `false` — a deliberate scoping
choice from the original redesign (`BluetoothAdapter.isEnabled()` needs the dangerous
`BLUETOOTH_CONNECT` permission on API 31+, a new Play Console "Nearby devices" declaration this
launcher didn't want to take on, so the tile shipped tap-to-settings only, with no live state).
That scoping missed a simpler option: the bluetooth radio's persisted on/off state is also mirrored
in `Settings.Global.BLUETOOTH_ON`, a public, permission-free key — the exact same pattern already
used for airplane mode (`rememberAirplaneModeOn`). New `rememberBluetoothOn()`
(`:feature:livetiles`, `SystemToggles.kt`) reads it and listens for
`android.bluetooth.adapter.action.STATE_CHANGED` (a normal, unprotected broadcast — receiving it
needs no permission, only calling `BluetoothAdapter` methods directly does) to stay live. The tile
still deep-links to Bluetooth settings on tap rather than toggling directly, but now correctly
accent-fills when bluetooth is actually on, matching wifi/location/every other real toggle.

**Tile sequence reorganization.** `quickPanelTiles()` (`QuickPanelOverlay.kt`) is regrouped by kind
instead of the reference WP photo's literal order: connectivity toggles (wifi, bluetooth, location,
airplane) → device-mode toggles (flashlight, rotation lock) → adjustable-level tiles (brightness,
screen timeout, media volume, ring volume, or the "allow access" fallback) → dnd → theme → app
shortcuts (personalize, android settings, lock screen). Two explicit placements per this request:
**location moved to third** in the top row (ahead of airplane, which was previously third), and
**dnd moved well down the list** (out of the device-mode toggle block entirely, to sit right before
the theme tile in row three) — both deliberate deviations from the original WP-photo-literal
ordering, per this explicit user preference. Build + tests green; installed on the physical device.

## Quick Panel: rotation lock/brightness and volume/screen-timeout swapped

Direct follow-up user request: "interchange position of volume and alarm. same for rotation and
brightness" ("alarm" refers to the screen-timeout tile — clock icon). `quickPanelTiles()` row two
was `rotation lock, brightness, screen timeout, media volume, ring volume`; two adjacent-pair swaps
were requested. Implemented by restructuring the adjustable-level block: brightness (or the "allow
access" fallback) is added first, then rotation lock, then media volume, then screen timeout (only
when `WRITE_SETTINGS` is granted — the ungranted case still collapses brightness+timeout into one
fallback tile, unchanged), then ring volume. New row two: `brightness, rotation lock, media volume,
screen timeout, ring volume`. Build + tests green; installed on the physical device.

## Quick Panel: media volume moved to extreme right of row two

Direct follow-up user request: "volume should be on extreme right in middle row." `quickPanelTiles()`
row two was `brightness, rotation lock, media volume, screen timeout, ring volume` — media volume
is now added last, after ring volume, so it sits at the row's extreme right. New row two:
`brightness, rotation lock, screen timeout, ring volume, media volume`. Build + tests green;
installed on the physical device.

## Hide status bar toggle

New ask, not in the WP prototype/spec (real WP has no OS status bar to hide) — user asked for the
same "hide status bar" option several other Android launchers offer. New `hideStatusBar: Boolean`
in `LauncherSettings`/`SettingsCodec` (default off), a `SettingsRepository.setHideStatusBar` /
`StartViewModel.setHideStatusBar`, and a "hide status bar" toggle in Personalize's `"system"` group
(that group previously only ever rendered the "default launcher" row, and only while TileShell
wasn't already the default launcher — it's unconditional now so the new toggle always has a home).
Actual hide/show is applied in `MainActivity`'s new `StatusBarVisibilityEffect`, since it needs the
Activity `Window` that Compose-only `:feature:*` modules don't have: it collects
`startViewModel.settings` and drives `WindowInsetsControllerCompat.hide/show(Type.statusBars())`
with `systemBarsBehavior = BEHAVIOR_SHOW_BARS_BY_SWIPE`, so the bar stays reachable with a swipe
down from the top edge instead of being fully locked away. Build + tests green
(`SettingsCodecTest` extended).

## Quick Panel docks to the top instead of the bottom

Direct follow-up user request: make the Quick Panel look like a real device's quick settings
panel, which slides down from the top rather than up from the bottom (every other sheet in this
app — Personalize, About, folders, etc. — intentionally still slides up from the bottom; this is a
one-off deviation scoped to the Quick Panel only). The opening gesture itself is unchanged (still a
two-finger swipe-**up** on Start, so it still can't collide with quick search's two-finger
swipe-**down**) — only where the panel visually docks and slides from changed.

`SheetStage` (`:core:design`) gained a `dockTop: Boolean = false` param: `false` (every existing
call site, unchanged) aligns the panel `BottomEnd` as before; `true` (Quick Panel only) aligns it
`TopEnd` instead, so in landscape it still docks to Start's right half, just now at the top edge
rather than the bottom. `QuickPanelOverlay.kt` flips the rest of the bottom-sheet mechanics to
match: `Alignment.BottomCenter` → `TopCenter`, the slide `translationY = size.height * (1f -
progress)` → `-size.height * (1f - progress)` (negative, sliding down from above instead of up from
below), rounded top corners → rounded bottom corners, and `navigationBarsPadding()` →
`statusBarsPadding()` (the panel now sits flush against the top of the screen, so it needs to clear
the status bar inset instead of the nav bar). The pull-tab handle moved from the panel's top edge to
its bottom edge — the edge closest to open space, matching every other sheet's handle placement
convention (handle sits at the edge you'd drag to close), just mirrored top<->bottom since this
panel now docks top instead of bottom. Build + tests green; verified on-device in both portrait and
landscape.

## Quick Panel header: clock/date left, personalize/settings/lock icons right

Direct follow-up, from a reference screenshot of a real device's quick settings panel header
(clock/date on the left, small circular edit/power/settings icons on the right). Added
`QuickPanelHeader` above the tile grid in `QuickPanelOverlay.kt`: a live-ticking clock + compact date
on the left (`feedClock12` reused as-is from the feed page; new pure `quickPanelHeaderDate` in
`FeedFormat.kt`, unit-tested, for the short lowercase "fri, 31 jul" form — distinct from the feed
page's own long uppercase `feedGlanceDate`), and three 36dp circular icon buttons on the right for
personalize / android settings (using the same device-resolved icon as before) / lock screen. Those
three were previously square tiles at the end of the grid (`quickPanelTiles()`) — removed from
there per explicit request, since they're app/system shortcuts rather than device controls and now
have a more prominent, always-visible home in the header instead of competing for grid space with
wifi/brightness/etc. `quickPanelTiles()` lost its `androidSettingsIcon`/`onOpenPersonalize`/
`onLockScreen` params now that nothing inside it needs them. Build + tests green; verified on-device
(all three header icons open personalize / trigger the android-settings deep link / trigger the
lock-screen flow correctly).

## Hidden status bar didn't reclaim its inset on every device

User report: after enabling "hide status bar," the freed space at the top wasn't actually being
used by the Start screen — the tile grid still left a blank gap where the bar used to be. Root
cause: the Start grid's scrollable Column (`StartScreen.kt`) always applied `.statusBarsPadding()`,
which pads by the *system-reported* status-bar inset height — and that inset doesn't reliably
collapse to zero just because `WindowInsetsControllerCompat.hide()` was called; behavior here varies
by OEM/API level (confirmed fine on the emulator used for on-device verification, but not on the
user's real device). Rather than depend on the system inset shrinking, `StartPage` now takes a
`hideStatusBar: Boolean` param (from `settings.hideStatusBar`) and skips `.statusBarsPadding()`
outright whenever the setting is on, so the grid unconditionally fills the top of the screen instead
of trusting the inset to already be zero. Scoped to the Start tile grid only (what was reported) —
the app list and other sheets weren't touched. Build + tests green.

**Follow-up — the real remaining cause was the display-cutout inset, not the status bar.** The fix
above wasn't enough: verified live on the user's physical device (a punch-hole-camera phone) that a
visible gap persisted even with the status bar genuinely hidden. Pulled `dumpsys window displays` on
that device and found `DisplayCutout.insets = Rect(0, 128, 0, 0)` — the system reserves a **128px
full-width top inset** for the punch-hole camera, entirely independent of the status bar's own
visibility. `StartPage`'s tile-grid Column was still applying `.displayCutoutPadding()`
unconditionally, so hiding the status bar alone could never reclaim that space on any device with a
notch/punch-hole. Fixed by folding `.displayCutoutPadding()` into the same `hideStatusBar`
conditional as `.statusBarsPadding()` — both are skipped together now. Confirmed fixed on the same
physical device (tile grid now starts at the literal top pixel, cutout and all) — the visual trade
a user opting into this setting accepts is a tile or two rendering partly behind/around the camera
hole, same as most "hide status bar" launcher features. Build + tests green.

## Right-edge swipe-down opens this app's own Quick Panel, not system quick settings

Direct follow-up user request: the existing single-finger edge-swipe-down gesture opened the
*system's* quick settings panel on the right edge (via `LockAccessibilityService
.expandQuickSettings()`/`GLOBAL_ACTION_QUICK_SETTINGS`) — confusing once this app's own Quick Panel
already exists and now visually resembles a real quick settings panel itself. `StartScreen.kt`'s
`edgeSwipeGesture` now calls `viewModel.openQuickPanel()` for `EdgeZone.RIGHT` instead of the removed
`onOpenQuickSettings` callback; the left edge is unchanged (still opens the system notification
shade). `expandQuickSettings()` (`LockAccessibilityService.kt`), the `onOpenQuickSettings` param
(`StartScreen`), and its wiring/disclosure-dialog state (`MainActivity.kt`'s
`showQuickSettingsDisclosure`) are all deleted outright — fully dead once nothing calls the system
action anymore. The accessibility prominent-disclosure dialog's text was updated to drop the "quick
settings" mention (now only locking, recents, and the left-edge notification shade need the
accessibility service). `AboutSheet.kt`/`PersonalizeGuideSheet.kt`'s "system shortcuts" guide entries
and `EdgeSwipeVisual`'s doc comment updated to match. Build + tests green.

## Quick Panel header icons: no circle background; "hide status bar" defaults to on

Two direct follow-ups from the same on-device round: **(1)** the three header icon buttons
(personalize/android settings/lock screen, added earlier this session) had a circular tinted
background per the initial real-device-quick-settings reference — removed per explicit request, so
they're now plain icons with no background, just a slightly larger 22dp glyph in the same 36dp tap
target. **(2)** "hide status bar" now defaults to **on** (`LauncherSettings.hideStatusBar = true`),
per explicit request that there be "no necessity to turn it on via personalization" — a fresh install
now ships with the status bar hidden out of the box; the Personalize toggle remains for anyone who
wants the bar back. Build + tests green.

## Status bar's swipe-reveal stayed shown permanently on a real device

User report: with "hide status bar" on, swiping down from the top edge to peek the bar (the
documented escape hatch) revealed it as expected, but it then never hid itself again — it should
only be a transient reveal. `WindowInsetsControllerCompat`'s `BEHAVIOR_SHOW_BARS_BY_SWIPE` contract
normally auto-times-out the transient reveal on its own, but that isn't consistent across every
OEM/API level, and this app was relying on it entirely rather than managing it directly. Fixed with
an explicit re-hide: `MainActivity`'s `StatusBarVisibilityEffect` now also attaches an
`OnApplyWindowInsetsListener` to `window.decorView` (observing only — it returns the insets
unmodified, so Compose's own insets dispatch downstream, e.g. `statusBarsPadding()` call sites, is
untouched) that watches for `Type.statusBars()` becoming visible while the setting is on, and
schedules `controller.hide()` again after a fixed 2.5s delay whenever it does. Confirmed on an
emulator: swipe reveal → bar floats over content as before → auto-hides again a few seconds later
with no further input needed. Build + tests green.

## Quick Panel: 4-column grid, real sliders for brightness/volume, draggable close handle

Three rounds of on-device feedback on the redesigned Quick Panel, all implemented together:
**(1) four tiles per row instead of five** — `QUICK_PANEL_COLUMNS` 5→4, with the grid's own spacing
bumped (8dp→10dp gaps, 14dp→16dp side padding) now that each tile has more room; fewer, bigger tiles
read better than the tighter WP-photo-literal 5-across grid. **(2) brightness/ring-volume/media-
volume become real drag sliders instead of tap-to-step tiles** — reverting, for just these three,
the earlier square-tile redesign's deliberate choice ("a real WP tile has no slider at all"); a new
`QuickPanelSliders` composable renders three full-width `Slider` rows below the toggle-tile grid
(icon + slider + live percentage), replacing their old `QuickPanelTileSpec` entries entirely.
`rememberSteppedPercent` (tap-to-step) is replaced by `rememberSliderFraction`, the same
"seed-once-never-resync" pattern applied to a continuous `Float` instead of a quantized `Int` step —
still needed, since binding a slider straight to a coarse hardware readback (media/ring streams often
have only 7–15 native steps) would make the thumb visibly snap/jitter mid-drag as each write
round-trips to a slightly different value. Brightness's slider only renders when `WRITE_SETTINGS` is
granted (the existing "allow access" tile still covers the ungranted case in the grid); ring/media
need no special permission and always show. The now fully-dead `nextPercentLevel`/
`BRIGHTNESS_VOLUME_LEVELS` (`SystemToggles.kt`) and their dedicated `PercentLevelTest.kt` are deleted
outright — nothing calls the tap-to-step path anymore. **(3) the pull-tab handle is directly
draggable** — dragging it upward past a 24dp threshold now dismisses the panel (`detectVerticalDrag
Gestures` on the handle's touch target, widened to 56×20dp for an easier grab), on top of the
existing tap-outside/back-press/header-icon dismiss paths; the direction matches the panel's own
slide-down-from-top motion (pull it back up to close it). `PersonalizeGuideSheet.kt`/`AboutSheet.kt`'s
quick-panel guide entries and `QuickPanelVisual`'s mockup illustration (square "60%" tile → a small
slider-bar mockup, undoing that same swap from an earlier session) updated to match. Build + tests
green; verified live on an emulator (4-column grid, all three sliders respond to drag, handle-drag
dismiss works).

## Quick panel / quick search gestures swapped; sheets go full-screen

Direct follow-up user request: the Quick Panel and quick search's two-finger gestures were swapped —
Quick Panel is now two-finger swipe-**down** (was up), quick search is now two-finger swipe-**up**
(was down). Implemented by swapping the *direction check* inside `isQuickSearchSwipe`/
`isQuickPanelSwipe` (`QuickSearchGesture.kt`/`QuickPanelGesture.kt`) while keeping each function
named after the feature it triggers — so `StartScreen.kt`'s gesture blocks needed no changes beyond
updated comments. The single-finger edge-swipe-up gesture (an alternate path to whichever the
two-finger up gesture opened) was flipped too, from Quick Panel to quick search, to stay consistent
with its two-finger sibling — edge-swipe-down is unchanged (left → system notifications, right →
Quick Panel). `QuickSearchOverlay.kt` now slides up from the bottom (`translationY` sign flipped)
with its search box moved to the **bottom** of the screen (closer to the thumb, since that's where
the opening swipe came from) and results filling the space above it, instead of sliding down from
the top with the search box at the top. Tests, doc comments, and the Personalize guide/about sheets'
gesture descriptions updated throughout; `docs/QUICK-PANEL-SPEC.md` gained an amendment note rather
than a rewrite, since it's a historical design doc.

Also, per explicit request, **Personalize, "how to personalize" (guide), and "features & info"
(about) now render full-screen** instead of bottom sheets capped at 86–92% height with a dimmed gap
above them — `fillMaxHeight(0.86f)`/`fillMaxHeight(0.92f)` → `fillMaxSize()`, plus a new
`.statusBarsPadding()` so their grip handle clears the status bar/cutout at the very top. Every
other sub-sheet (backup, folders, news region, hidden apps, edge strip, permissions) was left as a
capped bottom sheet — not mentioned, not changed. Build + tests green.

## Quick Panel header gains a status row; device status card removed from glance entirely

Direct follow-up, in stages: first, battery/wifi/cellular readouts moved from the glance page's
device-status card into a new status row on the right of the Quick Panel header's top line (next to
the personalize/settings/lock icons, which moved to their own row below it) — reusing the existing
`rememberDeviceStatus()`/`Connectivity`/`rememberBluetoothOn()` data with **no new permission**.
Considered showing real per-SIM cellular signal (`SubscriptionManager`/`TelephonyManager`) but
`READ_PHONE_STATE` sits in Android's restricted "Phone" permission group, which Play generally only
approves for default dialer/messaging/call-screening/VOIP apps — a launcher's cosmetic signal readout
isn't a listed qualifying use case, so it was likely to draw the same kind of Play rejection this
project already hit once over Accessibility API disclosure. Went with a single-indicator design
instead: a new hand-drawn monoline `"cellular"` glyph (`TileIcons.kt`, four ascending outline bars,
matching every other icon's stroke-only style) tinted active only when `Connectivity.CELLULAR` is
the current transport — same simple on/off treatment as the wifi icon, no per-SIM breakdown,
zero new permissions. Bluetooth was added to the same row per explicit follow-up request, reusing the
toggle tile's own `rememberBluetoothOn()`.

Then, a final follow-up removed the device-status card from the glance page **entirely** — including
the storage/alarm stats that had been left behind after battery/wifi/cellular moved out — rather than
leave a half-empty card there. `DeviceStatusCard`/`DeviceStatusStat` (`FeedPage.kt`) and the whole
`deviceStatusCardEnabled` setting (`LauncherSettings`/`SettingsCodec`/`SettingsRepository`/
`StartViewModel`/the Personalize toggle row) are deleted outright — dead code once the card that
setting gated no longer exists. `rememberDeviceStatus()`/`Connectivity` themselves stay in
`:feature:livetiles`, still needed by the Quick Panel header. Build + tests green; verified live on
an emulator (status row renders correctly, glance page goes straight from widgets to news).

## Quick Panel header fixes: wifi bug, airplane swap, battery colour, accent tint; wallpaper background; mute toggle; haptics everywhere

A dense round of on-device feedback after the status-row header shipped, all implemented together:

**Wifi bug.** The header's wifi icon read `Connectivity.WIFI` (is wifi the *active data transport*
right now) instead of the wifi radio's own on/off state — a device can have wifi on and associated
but not be routing traffic through it (captive portal, no internet), and the icon would wrongly read
as off even though the toggle tile right below it (which uses `rememberWifiEnabled()`) correctly
read on. Fixed by reusing the exact same `wifiOn` value the toggle tile already computes.

**Airplane replaces cellular.** When airplane mode is on, the cellular signal slot now shows the
airplane glyph instead of dead signal bars — matching a real device's status bar, where a cellular
icon is meaningless mid-flight.

**Battery: proportionate fill, colour-coded.** The battery indicator was a fixed monoline outline +
separate percentage text. Replaced with a hand-drawn `BatteryIndicator` (`Canvas`, since
`TileIcons`' glyphs are stroke-only with no fill support) showing the real level as a proportionate
fill, colour-coded green (>50%) / amber (20–50%) / red (≤20%) — the percentage text stays alongside
it, now a secondary confirmation rather than the only way to read the level.

**Accent tint for on/off clarity.** Wifi/bluetooth icons switched from a plain brighter-grey-when-on
scheme to full accent tint when on — a much clearer on/off signal, matching the toggle tiles' own
accent-fill convention, per explicit feedback that the fg/fgDim contrast alone wasn't obvious enough
(this doubled as a report that bluetooth's on/off state "isn't indicated" — it was, just too subtly).

**Background: same synthesized wallpaper gradient as glance.** Per explicit request ("add background
to quick panel just like glance"), the panel's outer backdrop (previously a flat `tokens.sheet`
rectangle) now paints the same `WallpaperGradient` synthesis the glance page uses — `rememberFeedPalette`
(promoted from `private` to `internal` in `FeedPage.kt` so `QuickPanelOverlay.kt`, same module, can
reuse it directly rather than duplicating the palette-extraction logic) extracts up to 3 prominent
colours from a custom photo via `androidx.palette`, or passes a stock gradient through unchanged;
falls back to a flat surface when Start has no wallpaper at all, exactly mirroring glance's own
`noWallpaper` fallback. The panel's own `accent` (tile fills, slider colours, header status tints)
switches to the wallpaper-derived colour too, matching how glance's cards use `feedAccent` instead of
the plain global accent — full parity, not just a backdrop swap. Contrast: `panelFg`/`panelFgDim`
(via `Glass.faceTextColor` + `rememberChosenWallpaperIsLight`, same pattern as glance's `feedFg`)
apply only to text/icons sitting directly on the gradient (header, sliders, handle bar) — the tile
grid's own opaque chip/accent-filled squares are untouched, exactly matching how glance's own
opaque cards don't adapt either, only the text directly on its backdrop does.

**Ring/media volume icon is now a mute toggle.** Tapping the bell/speaker icon at the start of the
ring or media slider row mutes it to 0% (remembering the pre-mute level) or restores it — like a
real device's volume panel. Brightness has no mute concept, so its row's icon stays non-interactive.

**Haptic feedback added throughout Quick Panel, quick search, and the App List long-press menu.**
`HapticFeedbackType.GestureThresholdActivate` fires the moment a two-finger or edge swipe crosses its
trigger threshold (both the Quick Panel and quick search gestures, all three `edgeSwipeGesture`
branches) and when the panel's drag-to-close handle crosses its dismiss threshold. Every Quick Panel
tile tap and header icon tap fires `VirtualKey`; slider drags fire `GestureEnd` on release; the new
mute-toggle icon fires `ToggleOn`/`ToggleOff`. Quick search's `act()` (the single choke point nearly
every committing action already funnels through — app/contact/search-engine/AI-assistant taps, the
keyboard search action) fires `VirtualKey`, plus the same for the empty-state "suggested app" tap,
recent-search tap/remove, and the clear (×) button; the contact long-press-for-menu gesture fires
`LongPress`. The App List's existing long-press-for-pin/hide/uninstall menu (`AppRow` in
`AppListScreen.kt`) also gained a `LongPress` haptic, per a separate explicit request scoped to just
that gesture.

**Quick search keyboard overlap bug.** Since quick search's redesign pinned its search box to the
bottom of the screen (this session, gesture-swap entry above), opening the keyboard would overlap it
outright — nothing was pushing content up above the IME. Fixed with a single `.imePadding()` on the
overlay's outer Column; the results area (`weight(1f)`) shrinks to make room and the search box
stays visible right above the keyboard, exactly as before the bottom-pinning change.

Build + tests green throughout; every item verified live on an emulator (wifi/bluetooth accent tint,
battery colour-fill, gradient background with matching accent across tiles/sliders, mute/unmute
round-trip, quick search keyboard clearance, App List long-press menu). Airplane-mode substitution
verified by code-path symmetry with the already-verified wifi/bluetooth checks — toggling airplane
mode via `adb shell settings put global airplane_mode_on` doesn't fire the broadcast the app listens
for without a broadcast permission this shell session didn't have, so the live icon swap itself
wasn't re-confirmed pixel-by-pixel this round.

**Quick Panel background now respects the feed's own "no background" opt-out, not just Start's
wallpaper state.** Follow-up: the panel's synthesized-gradient background (previous entry) only
ever flattened to plain surface fill when Start itself had no wallpaper (`noWallpaper`); the glance
page's separate `feedNoBackground` toggle (a deliberate independent opt-out — see the "glance screen
background" entry, since the feed is a denser reading surface where a colourful background behind
text can be unwanted even when the same wallpaper looks fine behind Start's tiles) was never
threaded through, so turning it on for the feed didn't also flatten the Quick Panel, even though the
panel reuses the *exact same* `rememberFeedPalette` mechanism. Fixed by widening
`QuickPanelOverlay`'s existing flatten condition to `noWallpaper || feedNoBackground`, with
`feedNoBackground` passed in from `StartScreen.kt`'s already-collected `settings.feedNoBackground`.

**Wifi/bluetooth header icons reverted from accent tint back to plain fg/fgDim.** The "bluetooth
also indicate on or off state" fix (previous entry) tinted both icons with the global accent colour
when on. On-device the accent tint read as inconsistent with the cellular icon sitting directly next
to it, which has always used plain `fg`/`fgDim` — per explicit user feedback ("show these symbols
same as network symbol color"), both icons now use the same plain fg (on) / fgDim (off) scheme as
cellular, with no accent tint. The two now-stale "lights up in your accent colour when on" bullets in
`AboutSheet.kt` and `PersonalizeGuideSheet.kt` were corrected to match. Build + tests green.

## Feed widget stacks — merge two hosted widgets into one swipeable card

New ask, not in the WP prototype/spec (the whole feed widget-hosting feature is bespoke). Start
already had a "widget stack" concept for its *own* tiles — a folder whose members are all uniformly
WIDE/LARGE renders as a swipeable carousel (`StackTileContent`, `StartScreen.kt`) — and the request
was the equivalent on the glance/feed screen, where each hosted Android app widget previously always
took its own row. Motivation is vertical space: the feed is meant to be a dense at-a-glance surface,
and two or three full-width widgets push the news section well below the fold.

**The pattern is borrowed from Start's stack; the implementation deliberately isn't.** Start's
members are virtual tiles this app draws itself; a feed stack's members are real
`AppWidgetHostView`s owned by other processes. Three consequences shaped the design:

- **Gesture confinement is load-bearing, not polish.** Start can capture a drag anywhere on its tile
  because nothing else wants the touch. Here every member has its own taps, internal scrolling, and
  buttons. So swipe-to-flip is granted *only* to touches starting in a 40dp right-edge strip
  (`WIDGET_STACK_EDGE_ZONE_DP`, mirroring Start's `STACK_EDGE_DRAG_ZONE_DP`, where the position
  indicator also sits); anything starting elsewhere returns from `awaitEachGesture` without consuming
  anything at all, so it reaches the hosted widget exactly as on an un-stacked card. There is
  deliberately no tap-to-launch or long-press-to-edit competing for it — the existing "edit" pill
  covers that.
- **Hidden members need no keep-alive plumbing.** The question was whether a member that isn't
  currently showing would go stale. It doesn't: `AppWidgetHost.startListening()` (already called once,
  host-wide) caches every *bound* widget's latest `RemoteViews` regardless of whether a view is
  inflated for it, so only the visible member is composed and flipping back shows current content.
  This is why `AnimatedContent` can drop hidden members from composition freely.
- **Only same-width widgets may merge.** Half-width and full-width widgets can't share one card, so
  the merge hit-test requires `dragged.halfWidth == target.halfWidth` — mirroring Start's rule that a
  stack's members are uniformly sized. A mismatched hover is treated as an ordinary reorder and is
  never highlighted as mergeable.

**Trigger is drag-onto-centre, matching Start's tile merge** rather than a separate menu action:
`onWidgetDragBy` now also tests the drop point against the target's inner 22–78% band on both axes
(`isInMergeZone`, the same normative merge zone as the Start grid), and `onWidgetDragEnd` routes to
`mergeIntoStack` or `reorderWidgets` accordingly. An accent outline previews the merge before release.

**Grouping is a `stackId: Int?` on `HostedWidget`, contiguity-based, with no schema migration.**
Members share the founding widget's own `widgetId` as their `stackId` and always sit adjacent in the
persisted order, so the row packer finds a group by scanning outward instead of re-grouping the list.
`WidgetCodec` gained a fifth column, written blank when null so an un-stacked widget is
byte-indistinguishable from an older 4-column file — verified on-device, where a real pre-existing
`feed_widget.pb` (`3,127,0,true` / `4,171,0,true`) loaded and rendered unchanged.
`packWidgetRows`'s return type became a `WidgetRow` sealed type (`Solo`/`HalfPair`/`Stack`) instead of
`List<List<HostedWidget>>`, and `reorderWidgets` became **block-aware** (a stack is one block) so an
ordinary reorder can never slice a group apart. A group that somehow drops to one member is packed as
a plain `Solo` and `WidgetStore.remove` clears a stranded survivor's `stackId`, so a "stack of one"
can't exist by either route.

**Two real bugs found during on-device verification**, both in code newly written here:

1. **The position indicator was invisible.** It was copied from Start's version, which sizes itself
   with `fillMaxHeight(0.5f)` — correct inside Start's fixed-size tiles, but the feed is a vertically
   scrolling column, so the incoming max height is unbounded and the fill fraction resolved to
   nothing. Replaced with an explicit `(liveHeight / 2).dp`.
2. **...and would have been invisible anyway on light widgets.** Start's indicator is white because
   its tiles are accent-coloured; a hosted widget can be any colour, and white-on-near-white vanished
   outright on both test widgets. The track is now dark (`Black @ 0.22`), borrowing the "edit" pill's
   already-proven backing, which reads on light and dark content alike.

**Verification.** The pure layer is unit-tested (26 new/updated cases across `WidgetSlotTest`/
`WidgetCodecTest`: merge/join/dissolve, unstack, block-aware reorder, merge-zone geometry, row
packing with stacks, codec round-trips old and new). On an emulator, confirmed end-to-end: the group
renders as one card sized to its tallest member; the carousel auto-rotates (sampled 8 frames over
32s — content alternated and the indicator thumb tracked the index exactly, never out of sync); the
edit overlay acts on the visible member and correctly omits the "edit" pill for a widget with no
configure activity; and "unstack" dissolved the two-member group, clearing both `stackId`s and
re-rendering as two rows.

**Drag-to-merge and swipe-to-flip could not be verified by automation** and need a real finger. Both
`adb shell input swipe` and `input motionevent` deliver only a single 1–2px move to Compose's
`detectDragGestures` before the gesture ends (confirmed with temporary logging: `delta=Offset(1.0,
0.1)` then immediate drag-end, against correct widget bounds), so a multi-event drag can't be
synthesized — the same ADB limitation already recorded for the feed's drag-to-reorder work. The logic
behind both is unit-tested; it's the gesture plumbing that remains hand-verified only. Worth a
specific on-device look: merging two *side-by-side* half-width widgets requires a mostly-horizontal
drag, which is the axis the Start↔feed pager also claims.

## Feed widget stacks — four fixes from on-device testing

Direct follow-up to the entry above, all four user-reported after real-hardware use.
Symptoms were "widget stack position can't be changed" and "another widget can not be placed
next to the stack" — which turned out to be four separate defects, two per symptom.

**1. The drag handle was hidden underneath the action pills.** The overlay laid out the handle
and the actions as two independently-aligned children of the same Box (handle at `TopStart`,
actions at `TopEnd`). Nothing reserved space between them, so as soon as the actions grew wider
than the gap they silently covered the handle. A stack adds a third pill ("unstack") on top of
edit + remove, so on a narrow card the handle disappeared completely and the stack genuinely
could not be dragged — the user's report was literally "no handle to move on stack". Now a
single `Row(SpaceBetween)` holds the handle and a `FlowRow` of actions: the handle's space is
reserved, and the actions wrap onto further lines instead of encroaching. This class of bug has
bitten this file before — the old ↑/↓ reorder buttons collided with the edit/remove pills on
half-width widgets for the same reason. Verified on-device at the exact failing geometry.

**2. Dragging a stack destroyed it instead of moving it.** `reorderWidgets` was made
block-aware, but `mergeIntoStack` never was: it does `out.removeAt(di)`, removing one widget.
So dragging a stack's handle onto another card — landing in the wide default merge zone, which
covers 56% of each axis and is therefore most of the card — tore just the anchor member out of
the group, dissolved the remainder, and merged that lone member into the target. Repositioning
only worked in the narrow outer band, which reads as "it doesn't work". Merging is inherently a
per-widget operation, so rather than make it block-aware, **a drag that starts on a stacked
widget now never merges at all** — it only ever reorders. Combining two stacks stays
unsupported, as originally scoped.

**3. A widget dropped near a stack was absorbed into it rather than placed beside it.** Same
wide zone, opposite direction. Joining an existing stack is the rarer of the two intents, so it
is now the one that has to be aimed at: `isInMergeZone` takes the band as parameters and the
call site tightens it to roughly the centre third (`STACK_MERGE_ZONE_MIN`/`MAX`, 0.34–0.66)
when the target is already a stack. Loose-widget-onto-loose-widget keeps the normative 22–78%
zone shared with the Start grid. Chosen over a dwell-to-merge timer or capping stacks at two
members, both of which were offered — the user picked the tighter zone as it adds no new
gesture vocabulary.

**4. A half-width stack hogged a whole row.** `packWidgetRows` was written to give
`WidgetRow.Stack` its own row unconditionally, so merging two narrow widgets produced a
half-width card sitting alone with dead space beside it, and nothing could ever be placed
alongside. Rows are now packed from **cards** (`WidgetCard.Solo`/`WidgetCard.Stack`, via
`cardsOf`) instead of raw widgets, so a stack takes part in row packing on exactly the same
footing as a lone widget: `WidgetRow` collapsed from three cases to two (`Single`/`Pair`), and
a half-width stack pairs beside a half-width widget — or beside another half-width stack. This
also simplified the packer to a single pass.

**Also fixed, found while verifying the above: the card visibly resized as it rotated.**
`halfContentWidthDp` floors a card's width at the provider's own declared minimum, and that was
being computed from whichever member happened to be showing. Members declare different minimums
(on the test pair, the analog clock's is materially larger than Screen time's), so the card
changed width on every flip. `WidgetStackView` now resolves every member's info up front —
keyed, so the composition slots stay stable as the list changes — and takes the max, which also
removed a duplicate `rememberWidgetInfo` call for the visible member.

Build + tests green (`WidgetSlotTest` now 51 cases, including regressions for the half-width
stack pairing, the tighter stacked-target zone, and card width/hit-id derivation). Fixes 1 and 4
plus the width-stability fix were verified on an emulator at the failing geometry; 2 and 3 are
covered by unit tests but their gesture plumbing still needs a real finger, per the ADB
drag-synthesis limitation recorded in the previous entry.

## App list long-press raised to 700 ms (deviation from the prototype's 450 ms)

User-reported from hardware use: the app list's long-press menu (pin to start / hide /
uninstall) fired too readily, so a press that was meant to launch an app opened the menu
instead. `APP_LIST_LONG_PRESS_MS` in `AppListScreen.kt` is now **700 ms**, up from the
prototype-derived 450 ms recorded as normative in CLAUDE.md.

This is a deliberate, explicitly requested deviation from a normative prototype value, so
CLAUDE.md's "Normative behaviour values" line was annotated rather than left implying 450 ms
is still in force.

Why the app list warrants a longer hold than a Start tile (still 430 ms): a tile only competes
with the grid's own gestures, whereas an app row sits in a long scrolling list that people rest
a finger on while reading. The 7 px move-cancel threshold already handles the *scrolling* case —
what it can't catch is a stationary tap-and-linger, which only a longer timeout fixes.

Scoped to the app list only. Quick search's contact long-press (`QuickSearchOverlay`) still uses
450 ms: it was originally written to mirror this gesture, but nothing was reported about it, and
its result list is short enough that the linger problem doesn't arise the same way. Worth
revisiting together if the same complaint surfaces there.

Magic number replaced with a named constant in passing, since the value now needs an explanation
attached to it.

## Android-style icons home style — a second Start renderer alongside WP tiles

New user ask, not in the WP prototype/spec: let someone who doesn't want the Windows Phone
interface turn TileShell into a normal Android-style launcher — shaped app icons, folders, free
placement — while keeping live tiles and widget stacks available on the same screen. Landed as a
five-stage arc on `android-home-style`, described here as one connected decision since the stages
share a single design: one layout engine, two cell renderers, switched by size alone.

An earlier draft tried to reach this by making tiles progressively more flexible instead — nine
size presets, per-tile corner styles, per-tile spacing insets applied to the *existing* `TileView`.
That was designed, mocked up, and explicitly abandoned: it produced a hybrid that was worse at
being either a WP launcher or an Android one. The architecture that shipped instead treats ICONS
mode as a genuinely different cell renderer for a 1×1 (SMALL) tile, not a variant of the tile grid.

**`LauncherSettings.homeStyle: HomeStyle { TILES, ICONS }`** is the only new stored flag. Layout,
persistence, gestures, folders, the app drawer and backup are all shared unmodified between the two
styles. Icon vs. live tile is derived purely from the tile's own `size`, not a second per-tile
flag: a SMALL app tile renders as a plain shaped icon (`IconCellView`, new in `:feature:start`); a
SMALL folder renders as the same shaped icon holding a 2×2 mini-grid of its children
(`IconFolderCell`); anything at MEDIUM or larger — including live tiles, folders and widget stacks —
still renders through the existing `TileView`/`FolderTileContent`/`StackTileContent` exactly as in
TILES mode. The entire mixed-content mechanism is one condition at the single `TileView` call site
in `StartScreen.kt`: `homeStyle == ICONS && model is TileModel.App && model.size == SMALL` (and the
equivalent for `TileModel.Folder`) routes to the new renderer; everything else falls through
unchanged. Because that's the *only* branch, live tiles, widget stacks and MEDIUM+ folders needed
zero new code — keeping them working was a matter of not suppressing them, not writing anything.

One consequence worth stating plainly: this makes growing/shrinking a tile across the SMALL
boundary the icon↔live-tile conversion gesture. Grow a shaped icon past SMALL and it becomes a live
tile; shrink a live tile down to SMALL and it becomes a shaped icon. Switching `homeStyle` itself
rewrites nothing in the database — a tile's stored size is simply read through a different renderer
— so switching back and forth is lossless and instant.

Verified end-to-end on both an emulator and a physical device (Samsung SM-S938B, after the user
authorized wiping a differently-signed prior install to sideload this build): the home-style and
icon-shape rows render correctly in Personalize, ICONS mode shows real device icons unfilled with
the wallpaper showing through, a clock/weather live tile keeps flipping normally alongside shaped
icons on the same screen, and — the load-bearing behaviour — growing a SMALL icon via the resize
corner control converted it into a normal filled live tile on-screen, and that survived exiting edit
mode. The reverse (icon shape toggling, folder mini-grid masking) was confirmed on the emulator;
finishing the physical-device pass was handed to the user after repeated on-device gesture
mistargeting (see the note on TalkBack below).

Follow-ups intentionally left out of this arc: a dock/hotseat, widgets placed directly on the ICONS
grid (previously researched and dropped for TILES because arbitrary widget footprints clash with
fixed tile sizes — much less true once 1×1 icons are the norm, so this is now viable for the first
time), horizontal paging instead of vertical scroll, per-tile corner styles, and free-form
(arbitrary n×m) tile sizes — deferred because every live tile face is hand-designed per size, and a
free-form range is an untested combinatorial surface; the calendar/weather "big text" clipping bug
recorded elsewhere in this file is exactly the class of bug that produces.

## FREE tile arrangement mode — nothing moves unless you move it

Shared groundwork for the icons-mode arc (see above), but independently useful in TILES mode too:
`TilePackMode` gains a third value, `FREE`, alongside `DENSE` and `STICKY`. Where `STICKY` (real
Windows Phone) preserves a gap a removed tile leaves behind but still collapses a *fully* empty row,
`FREE` is stickier still — no push-down on drop, no empty-row collapse at all. Dropping a tile onto
an already-occupied cell **swaps** the two instead of displacing anything
(`GridPacker.swapPlacement`), falling back to the proven `stickyPlacement` push-down solver only
when a swap can't cleanly fit — more than one occupant in the drop zone, mismatched footprints that
would overlap something else, or no known origin cell for the dragged tile (never anchored yet).

Explicitly **not** a reversal of `STICKY`'s own invariant, which is on record earlier in this file
("Sticky mode: a full empty row is never allowed") as an explicit user-stated rule after a real
on-device report. `FREE` and `STICKY` are both "anchored" placement modes for rendering and
slot-seeding purposes (`TilePackMode.isAnchored`); only `FREE` skips the full-row collapse and the
push-down-on-drop/resize behaviour that `STICKY` still does. `FREE` becomes the default arrangement
in ICONS mode and stays an opt-in third choice in TILES.

`GridPacker` itself needed no changes to support any of this — `pack`/`packSticky`/`stickyPlacement`
already read only `size.cols`/`size.rows` and never branch on the `TileSize` enum, which is also why
Stage 2 below could add five new footprints without touching the packer at all.

## Nine tile size presets + gesture-based drag resize

`TileSize` grows from four footprints to nine — `SMALL`/`MEDIUM`/`WIDE`/`LARGE` plus `WIDE_SMALL`
(2×1), `TALL` (1×2), `WIDE_MEDIUM` (3×2), `TALL_MEDIUM` (2×3), `XLARGE` (4×4) — reachable only via
three new drag handles on a selected tile in edit mode (bottom-centre = height, centre-right =
width, bottom-right corner = both), mirroring the three-handle pattern already shipped for feed
widget resize and chosen there (and here) over pinch-zoom for the same reason: pinch fights the
surrounding scroll and can't set the two axes independently.

The tap-to-cycle resize control deliberately keeps `TileSize.next()` on the original four sizes —
cycling nine sizes by tap would be unusable — so tapping resize while at one of the five new presets
folds back to `MEDIUM`, the cycle's own documented landing size. New pure
`GridGeometry.snapResizeTarget` maps a drag's accumulated pixel delta to the nearest preset by
squared cols/rows distance, re-derived from the *total* delta on every tick rather than
incrementally, so it can't drift from what a single call with the same inputs would produce.

The live preview during a drag actually resizes the tile's own wrapper `Box` in place — the same
hoisted-offset mechanism the existing reorder-drag ghost already uses, extended to override size too
— rather than a separate outline overlay that was in the original design sketch. Chosen because
`TileView` never branches its renderer by size at all (only the ICONS-mode call site does, and it
reads the *persisted* size, never the live preview), so a real live-resize carries no risk of a
mid-drag renderer swap while being much simpler than a manually-positioned sibling overlay.

Verified on both an emulator and a physical device: the three drag handles render at the expected
positions on a selected tile, and — confirmed by an actual tap-based resize commit on the emulator —
growing a tile from SMALL to WIDE via the resize corner correctly repainted it from a shaped icon
(ICONS mode) to a filled live tile, and the change persisted after exiting edit mode.

## Icon shape masking — a real superellipse, not a corner-radius approximation

Personalize gains an "icon shape" row (circle / squircle / rounded / original), shown only while
`homeStyle` is `ICONS`. The substantive part is `core/design/Squircle.kt`: a One UI/iOS "squircle"
is a **superellipse** (Lamé curve) whose curvature eases in continuously rather than snapping from a
straight edge into a circular arc — the whole visual character, and the reason a `RoundedCornerShape`
can't express it. The curve's point-generation math (`superellipsePoints`) is deliberately separated
from the `Shape`/`Path` wrapper (`SquircleShape`) that consumes it, because of a real constraint
discovered empirically during this session: this project's plain-JVM unit tests have no Robolectric
and don't set `returnDefaultValues`, so constructing a bare `androidx.compose.ui.graphics.Path`
throws immediately (confirmed with a throwaway probe test, since removed). `SquircleTest` exercises
the pure math instead — bounds, closure, cardinal points, and the property that actually matters:
a higher exponent bulges the curve further toward the corner than a lower one, proof the exponent
parameter does something rather than being decorative — and leaves the `Shape`/`Path` layer itself
to on-device verification.

Real mid-stage architecture correction, worth recording so it isn't repeated: `IconShape` was first
drafted as a `:core:design` enum. But `:core:design` has no Gradle dependency on `:core:data` (and
vice versa) — every existing persisted-style enum (`TileFill`, `FontStyle`, `HomeStyle`) lives in
`:core:data`, with the actual `Shape`/`Brush` mapping done locally by whichever feature module
renders it. `IconShape` moved into `LauncherSettings.kt` next to `HomeStyle`; the
`IconShape → Shape` mapping lives in `:feature:start` (`IconCellView.kt`, since that's what actually
renders it) with a small local duplicate in `:feature:personalize` for the swatch-row preview,
rather than inventing a shared home for a four-line `when` expression that neither core module could
host without adding a new cross-module dependency for it alone.

`IconCellGlyph`'s masking branches on the loaded drawable's real type, checked *before* it's
flattened to a bitmap: an `AdaptiveIconDrawable` (minSdk 26, the same level `AdaptiveIconDrawable`
itself shipped in, so no version gate needed) gets its already-square flattened bitmap clipped
straight to the shape, since its background layer fills the square by OS convention; a legacy
(pre-adaptive) icon has no such guarantee, so it instead sits smaller and unclipped on a shaped
"plate" tinted from its own dominant colour (reusing `dominantIconColor`, the same helper tile
mode's colour-suggestion picker already uses) rather than being cropped. This is a deliberate
simplification of manually decomposing and recompositing an `AdaptiveIconDrawable`'s
background/foreground layers at the standard 66/108 safe-zone scale — that finer approach couldn't
be verified without a device attached at the time it was written, while clip-vs-plate is simple
enough to trust without on-device verification and matches the same visual split real Android
launchers show between adaptive and legacy icons. Flagged here for revisit if on-device testing ever
shows the plate reads wrong.

One more real discovery, caught by a test rather than assumed: Compose Foundation's `CircleShape` is
itself defined as `RoundedCornerShape(50)`, so a distinctness check comparing `Shape` values by
runtime *class* would have falsely reported `CIRCLE` and `ROUNDED` as the same shape. `IconCellShapeTest`
compares by value instead.

## Icon-style folders — the closed-folder mini-grid at SMALL

Closing stage of the icons-mode arc: a closed folder at SMALL in ICONS mode renders as a shaped icon
holding a 2×2 mini-grid of its first four children (`IconFolderCell`), instead of falling through to
`FolderTileContent`'s tile-scale mini-grid. A folder at MEDIUM+ still renders via `FolderTileContent`
unchanged, and a widget stack never reaches this new code at all — a stack's own `size` is always
`WIDE` or `LARGE` (see `TileModel.Folder.stackSize`), never `SMALL`, so no explicit stack guard was
needed at the branch.

Inline-expanded folder children turned out to need no new code whatsoever: they already flow through
the grid as synthetic `TileModel.App` instances (`FolderChild.asTileModel`, which carries the
child's own persisted `size` through unchanged), so a SMALL expanded child already matched the
Stage 3 `model is TileModel.App && size == SMALL` branch before this stage was even written. This
stage was really only ever about the *closed* folder's own mini-grid preview.

`IconCellView.kt` was refactored along the way — the chrome shared by every ICONS-mode cell
(edit-mode dim/scale/jiggle, tap/long-press gesture, TalkBack semantics, the notification badge, the
selected-tile corner control and resize handles) extracted into `IconCellChrome`, and the
masked-icon-or-glyph rendering into `maskedOrGlyphIcon` — so `IconFolderCell` reuses both instead of
duplicating `IconCellView`'s wrapper code. Each mini-grid cell's icon is masked to the same
`IconShape` as top-level icons and carries its own per-child badge (`FolderChildBadge`, widened from
private to internal to reuse it here — the same visibility-widening pattern already used for
`rememberTileAppIcon`/`tileGesture`/`TileControls`/`NotificationBadge` earlier in this arc). Drag
resize needed no change either: `resizeHandlesEnabled` was already generic over `App` and `Folder`
models, so a SMALL folder grows into `FolderTileContent`'s normal mini-grid via the exact same
handles an icon uses to become a live tile.

## Icons-mode resize: three on-device fixes after real-hardware testing

Direct follow-up after installing the icons-mode arc on a physical device: "cancel double finger
gesture for resizing. corner stetch work well. in collapsed folder show 1x1 tile show as icon. also
support 1x4 4x1 folder." Three separate corrections, landed together.

**Two-finger stretch removed; single-finger corner-drag is now the only resize gesture.** Between
the "Nine tile size presets" entry above and this one, the resize gesture itself went through an
extra round not otherwise recorded here: the original three-drag-handle design (bottom-centre /
centre-right / bottom-right corner) was replaced with `Modifier.tileStretchGesture`, offering both a
two-finger stretch and a single-finger drag from a 40dp corner zone as alternatives, with the live
preview resizing the tile's own wrapper `Box` in place exactly as described above. On-device testing
found the corner-drag alone worked well and the two-finger path added complexity without a real
benefit, so `tileStretchGesture` was simplified back down to corner-drag only — a single
`awaitFirstDown` inside the 40dp corner zone, tracked by pointer id through `awaitPointerEvent()`
until release. `snapResizeTarget` and the size-preset set are unaffected; only the input gesture
narrowed.

**Folder mini-grid children always render `IconShape.ORIGINAL`, never the ambient icon shape.** User
report: "in collapsed folder show 1x1 tile show as icon" — clarified as "just remove square border
around icon in collapsed folder in icon mode." Reproducing on an emulator with the prototype's
monoline WP glyphs showed no border in any shape, because those glyphs never go through
`maskedOrGlyphIcon`'s masking path at all. The most plausible real cause, given the code: a *legacy*
(non-adaptive) installed-app icon gets a tinted "plate" drawn behind it once a non-`ORIGINAL` shape is
selected (see "Icon shape masking" above) — comfortable at a top-level icon's full size, but at an
18dp mini-grid cell that plate reads as a cluttered square outline crammed into a space too small for
it. Rather than trying to shrink or suppress the plate at that scale, `IconFolderChildGlyph` now
hardcodes `shape = IconShape.ORIGINAL` unconditionally instead of taking the ambient `IconShape`
parameter — a folder's closed-preview children always show their unmasked, unplated icon, regardless
of what shape the user picked for top-level icons. Top-level icons and expanded folder children (which
route through the ordinary `IconCellView`/`IconFolderCell` branch, not this mini-grid) are unaffected.

**Two more drag-only presets — `BANNER` (4×1) and `COLUMN` (1×4) — reachable by both app tiles and
folder children.** `TileSize` grows from nine footprints to eleven; `next()`/`nextForFolderChild()`
fold both back to `MEDIUM`/its existing landing sizes exactly like the other five drag-only presets,
so the tap cycles are untouched. The more substantial half of this fix: folder children previously
couldn't reach *any* of the seven drag-only presets, because `resizeHandlesEnabled` was explicitly
gated off for them (`folderChildRef(model.id) == null`) — a Stage 5 decision made when folder children
still only had the tap-based `nextForFolderChild` cycle to fall back on. That gate is now removed
entirely (`resizeHandlesEnabled = true` unconditionally); a folder child gets the exact same
corner-drag gesture a top-level tile does, since inline expansion already renders it in the same
absolute grid a top-level tile uses — nothing folder-specific about the geometry needed solving. The
drag's release now branches on `folderChildRef(model.id)`: a top-level tile still calls
`StartViewModel.resizeTo`, while a folder child calls the new `resizeFolderChildTo` (ViewModel) →
`LayoutRepository.resizeFolderChildTo` (repository), a direct-set sibling of the existing
`resizeFolderChild`/`nextForFolderChild` tap path that shares its stack-collapse/-promote bookkeeping
(resizing a stack member off its uniform WIDE/LARGE size still collapses the stack; landing a child on
WIDE/LARGE still checks whether the folder should promote to a stack) but writes the drag's settled
size directly instead of computing the next step in a fixed cycle. The widget-stack corner-control
guard (`isStackTile`) is untouched — a stack tile itself still resizes only via its own overlay
controls, never this gesture.

## Widget stacks: any stackable size, explicit "show as stack"/"show as folder" toggle

Direct follow-up: user asked to widen widget-stack eligibility from "uniform WIDE or LARGE members"
to "any size other than SMALL/WIDE_SMALL/TALL/COLUMN" — i.e. every size except the four smallest/
thinnest, where a single live tile face reads too cramped to swipe between — and to replace the two
fixed "make stack · wide"/"make stack · large" action tiles with one contextual toggle: "show as
stack" on a plain folder, "show as folder" on a stack. Applies identically in both TILES and ICONS
home style, since a folder at MEDIUM+ (the only sizes a stack can ever be) already renders through
the exact same `FolderTileContent`/`StackTileContent` in both — nothing homeStyle-specific needed
touching.

**Why this needed a real schema migration, not just widening a `when`.** `TileModel.Folder.isStack`
was previously *purely derived*: true whenever every child happened to be uniformly WIDE or LARGE. That
was safe specifically because WIDE/LARGE were rare, deliberate sizes nobody reaches by accident. The
moment `TileSize.stackable` widens to include MEDIUM — the *default* size every pinned app and folder
child starts at — pure derivation breaks: an ordinary, never-customized 2-4-app folder (all children at
the default MEDIUM) would auto-render as a stack carousel the instant this shipped, with no way to
express "uniformly-sized but I want it shown as a folder." A genuine user choice, decoupled from
uniformity, was unavoidable. `FolderEntity` gains `showAsStack: Boolean = false` (schema v6→v7,
`MIGRATION_6_7`); `TileModel.Folder.isStack` becomes `showAsStack && stackSize != null` —
eligibility (`stackSize`, purely derived from uniformity) and the toggle are independent, so a member
resized off the shared size falls back to the plain mini-grid *without* touching `showAsStack`, and
resumes rendering as a stack automatically the moment uniformity returns — no separate "re-enable"
action needed. The migration backfills `showAsStack = true` for any folder that's *currently* a
uniform WIDE/LARGE stack (the only two sizes that could form one pre-migration), so an existing stack
doesn't visually flip to a folder the moment the flag defaults to false on upgrade.

**"Show as folder" no longer needs to resize anything.** The old `collapseStack` demoted every child
one tier (LARGE→MEDIUM, WIDE→SMALL) and forced the folder tile back to WIDE — necessary before,
since demoting to a *still-stackable* size wouldn't actually un-stack it (only SMALL/WIDE were "safe"
demotion targets in the old two-stack-size world). That escape hatch stops working once MEDIUM is
itself stackable — demoting a LARGE stack's members to MEDIUM would leave it *still* uniformly
stackable, not de-stacked. With `showAsStack` doing that job explicitly instead, `collapseStack` is
now a one-column flag flip: children and the folder tile's own footprint are left exactly as they
are. This is possible only because of the earlier BANNER/COLUMN mini-grid fix in this same arc —
`FolderTileContent`'s cols/rows already derive from the folder tile's own size for *any* size, so a
former stack's folder-view renders correctly at whatever footprint it already occupied, with zero
resize/push-down dance. "Show as stack" (`convertFolderToStack`) is symmetric-but-different: it does
need to homogenize children (a plain folder's children are rarely already uniform) to a target size —
the folder tile's *own current size* if that's itself stackable (keeps the same footprint, no
neighbor push-down), else MEDIUM — reusing the existing sticky-mode anchored-slot handling
(`StartViewModel.stickyResizeSlots`) since growing the tile can still displace a neighbor.

**Per-child resize no longer needs stack-collapse/-promote bookkeeping either.** The old
`resizeFolderChild`/`resizeFolderChildTo` called `collapseStack`/`promoteFolderToStackIfUniform` as a
side effect of an individual child landing on or off the shared WIDE/LARGE size. With `isStack` now
derived from `showAsStack && stackSize != null`, this is unnecessary: resizing one child away from
the shared size makes `stackSize` (and so `isStack`) naturally compute false on the next read, with
`showAsStack` left untouched (dormant, not cleared) — and it naturally re-derives true again if the
child is resized back to match. Both repository functions collapsed to a plain `updateFolderChildSize`
call, no bookkeeping.

**Drag-merge (creating a folder by dropping one tile on another) deliberately stays exactly as narrow
as before — LARGE+LARGE only.** Merge is also the *default* folder-creation gesture (drag one app onto
another), so generalizing its auto-stack-formation to every `TileSize.stackable` size would mean the
single most common interaction — merging two ordinarily-sized (MEDIUM) icons — would form a stack
carousel instead of a folder. `TileMerge.isStackable()`'s folder branch checks `stackSize == LARGE`
directly (not `isStack`, which would also require the toggle) so a folder that's currently
uniform-LARGE-but-toggled-to-"show as folder" still correctly re-forms/extends a stack when another
LARGE tile is merged in — unchanged from the pre-toggle behaviour. `MergeResult` gained an `isStack`
field (the merge's own `keepStack` decision) that `LayoutRepository.mergeTiles` writes straight into
the new folder's `showAsStack`.

Verified: build + full unit test suite green (`TileModelStackTest` rewritten for the toggle/
eligibility split — including one case per newly-stackable size and one per still-excluded size;
`TileMergeTest`'s two stack-flag assertions switched from reconstructing a throwaway `TileModel.Folder`
to reading `MergeResult.isStack` directly, since a freshly-constructed test folder now needs an explicit
`showAsStack` the old assertions never set). Installed on the physical device over the existing v6
database from earlier in this same testing session — migration ran cleanly with no crash, confirming
the v6→v7 upgrade path works against a real, non-empty layout, not just a fresh install.

## Widget stacks: three on-device refinements (both-dimensions rule, always-shown resize, moved into the colour sheet)

Direct follow-up after trying the previous entry's toggle on a physical device — three corrections,
asked together and confirmed via `AskUserQuestion` where genuinely ambiguous.

**`TileSize.stackable` tightened to `cols > 1 && rows > 1`.** The prior rule ("every size except
SMALL/WIDE_SMALL/TALL/COLUMN") still allowed `BANNER` (4×1) as a stack size. User feedback: stack
eligibility should exclude *any* size with a dimension of 1, not just those four — a one-cell-thin
strip reads too cramped for a swipeable live-tile face regardless of which axis is thin. The simpler
`cols > 1 && rows > 1` rule subsumes the old four-name exclusion list and additionally excludes
`BANNER`, with no other behavioural change (the "show as stack" button's visibility already read
`expandedFolder.size.stackable`, so tightening the property alone was sufficient — no separate
button-only gate was needed, resolving the one genuine ambiguity in this entry via a clarifying
question: "does this change what a button shows, or what a stack can ever be" — the answer was the
latter).

**A widget stack now always shows its resize/drag corner control, and dragging it resizes the whole
stack.** Previously `StackEditControls` deliberately showed only a folder-icon corner control — no
resize, no colour dot — on the reasoning (recorded in an earlier session) that "stacks are fixed at
3×3." That reasoning is stale now that a stack can be any `TileSize.stackable` size: user asked for
the resize affordance to always be visible, and for dragging it to resize the whole stack. Simplest
correct fix: delete `StackEditControls` outright and let a stack tile take the exact same
`TileControls(isFolder = true)` corner controls a plain folder does (folder icon, resize icon, colour
dot) — `isStackTile` no longer gates anything in that `when` block, since stack and plain-folder
corner chrome are now identical. The corner-drag gesture itself (`tileStretchGesture`) drops its
`!isStackTile` guard the same way. The one real behavioural difference is in the *write path*:
`onResizeDragEnd` now branches on `model is TileModel.Folder && model.isStack` and routes a stack's
drag through a new `onResizeStack` (→ `StartViewModel.convertFolderToStack`, already homogenizing
every member to the new size and setting `showAsStack = true`) instead of the plain `onResizeTo` a
non-stack tile/folder uses. Dragging a stack to a *non*-stackable size (e.g. down to `TALL`) still
works and isn't specially guarded against — it just falls back to the plain mini-grid per
`TileModel.Folder.isStack`'s existing dormant-flag behaviour (from the previous entry), resuming as a
stack automatically if dragged back to a roomy size.

**The "show as stack"/"show as folder" toggle moved into the per-tile colour picker sheet, replacing
the standalone action tile next to the expanded folder's children.** The whole
`FolderAction`/`folderActionTileId`/`parseFolderActionId`/`FolderActionTile`/`expandedFolderActions`
mechanism (an extra synthetic `TileSpec` reserving its own cell in `GridPacker.expandFolderInline`'s
children list) is deleted; `TileColorPicker` gains an optional `stackToggleLabel`/`onToggleStack` — a
row shown above the "use default colour" pill whenever the picked tile is a top-level folder (never a
folder child, which is a synthetic `App`) that's either already a stack, or has ≥2 children at a
`TileSize.stackable` footprint. Tapping it calls `onToggleFolderStack` and dismisses the sheet, same
as picking a colour does. Chosen location per explicit user request ("shift make as folder or stack
action in tile color settings") — reframing the toggle as *another per-tile setting alongside colour*
rather than a grid cell competing for space with the folder's actual children, which also means
expanding a folder no longer reserves an extra slot for it (one less cell to push subsequent rows
down by). Since every selected folder/stack now shows a colour dot (the previous entry's fix already
made the corner controls identical), the sheet is reachable from both a plain folder and a stack.

**Follow-up, same day: toggle repositioned below the colour swatches, with its own icon.** User
feedback on the sheet placement above: "show as folder settings should be below tile color selection.
should be shown separately using some icon usage." Moved from directly under the "tile colour" header
to the very bottom of the sheet, after the swatch grid, set off by a thin divider so it visually reads
as a distinct setting rather than another colour option. `TileIcons` gains a new `"stack"` glyph (two
overlapping rounded squares, hand-drawn in the existing stroke-only monoline style — CLAUDE.md's
"never Microsoft assets" rule means a new icon has to be authored, not borrowed) shown alongside "show
as stack"; the existing `"folder"` glyph is reused for "show as folder". `TileColorPicker` gained a
`stackToggleIconKey` param alongside `stackToggleLabel`.

## Icon shape masking extended to the App List

New user request: the `IconShape` setting (circle/squircle/rounded/original) only masked icons on the
Start screen (ICONS home style); it should apply the same way in the App List.

**Duplicated rather than shared, per explicit choice offered to the user.** `:feature:applist` cannot
depend on `:feature:start` (the dependency graph runs the other way — `:feature:start` already depends
on `:feature:applist` for the app drawer). Sharing the masking logic cleanly would mean giving
`:core:design` a dependency on `:core:data` (where `IconShape` lives) — reversing the earlier deliberate
decision recorded in "Icon shape masking" above to keep those two modules independent. Asked the user
directly which trade-off they preferred; chose duplication. New `AppListIcon.kt` in `:feature:applist`
re-implements the same adaptive-icon-clips / legacy-icon-on-a-tinted-plate split as `IconCellView.kt`'s
`maskedOrGlyphIcon`, gated on `homeStyle == HomeStyle.ICONS` (a plain unmasked icon in TILES mode, same
as before this feature existed) — `AppListViewModel` gained a `settings: StateFlow<LauncherSettings>`
(mirroring `StartViewModel`'s own pattern) so `AppListScreen` can read `homeStyle`/`iconShape` and pass
them into `AppRow`.

**Real performance bug caught before it shipped, not after.** The first pass ported
`maskedOrGlyphIcon`'s plate-colour calculation (`dominantIconColor`, a per-pixel saturation-weighted
scan over a 96×96 bitmap) as-is — safe on Start, where at most a couple dozen icons are ever composed
at once, but the App List is a `LazyColumn` that can hold hundreds of installed apps, and the scan was
running synchronously on the main thread inside the composable body, unmemoized, for every legacy
(non-adaptive) icon row. User caught this ("app icon shape change in app list is costly") before any
device testing. Fixed by moving the colour scan into `rememberMaskableAppIcon`'s existing background
icon-load coroutine (`Dispatchers.IO`) and caching the result on `MaskableAppIcon.plateColor` — computed
once per icon load, never touching the UI thread, never recomputed on recomposition/scroll. The squircle
shape's own `Outline` computation (64 trig-heavy points per `createOutline` call) was checked too and
is not a comparable concern: Compose only calls it for on-screen rows and caches it per shape/size, so
its cost is bounded to whatever's actually visible, unlike the unbounded per-pixel scan.

## Weather/calendar/clock icons stay live at 1×1 in ICONS mode — rendered exactly like a tile-mode SMALL tile

User request: a real Android launcher's dynamic calendar/weather icons were the explicit precedent —
weather, calendar, and clock icons should keep showing live info even at 1×1 in ICONS home style,
rather than falling back to the generic masked/glyph icon every other app gets at that size. Went
through two rounds of on-device correction after the first pass shipped:

**First pass (superseded):** new, smaller purpose-built composables per face (`WeatherIconFace` with
a condition glyph, `CalendarIconFace` at 22sp, `ClockIconFace` at 13sp) sized to fit inside the
existing 40dp icon glyph slot alongside the usual app-name label, plus three new `TileIcons` condition
glyphs (`"sun"`/`"rain"`/`"snow"`) and a pure `weatherConditionIconKey` mapper. User feedback after
trying it: "should be shown just like tile mode" (i.e. reuse tile mode's own SMALL-tile content and
sizing verbatim, not a shrunk-down reinterpretation), and separately "current contents is very small
in size.. also show in accent color background." Both pointed at the same fix, so the first pass's
new composables/glyphs/mapper were deleted entirely rather than kept alongside the real fix.

**Shipped design:** `IconCellView` now renders these three iconKeys as a genuine mini tile — an
`accent`-filled, `RoundedCornerShape(8.dp)`-clipped `Box` (`LiveIconTile`, new private composable)
filling the *entire* cell, holding the exact same `WeatherSmallFace`/`CalendarSmallFace`/
`ClockSmallFace` composables (`:feature:livetiles`) tile mode's own SMALL tile already uses — same
data plumbing (`WeatherCache`/`currentCalendarToday()`/`currentClockFace()`), same font sizes (34sp
day number, 20sp time, weather's temperature text), and deliberately **no label underneath** (tile
mode's own SMALL tile doesn't show one either — the mini tile *is* the whole cell, exactly mirroring
`AppTileContent`'s `tile.size == TileSize.SMALL` branch in `StartScreen.kt`). Every other app keeps
the ordinary icon+label ICONS-mode layout unchanged; only these three iconKeys branch into
`LiveIconTile`. `IconCellView` gained an `accent: Color` param (wired from the same `tileAccent` value
already computed at the call site for `TileView`/`TileControls`, following the existing per-tile
accent-override → app-icon-colour → global-accent priority chain) alongside the already-added
`liveActive: Boolean`. `LocalTileFaceColor` needs no new wiring — it's already provided once, high in
`StartScreen`'s composition, ambient to the whole screen including `IconCellView`, so the reused
`*SmallFace` composables automatically get the same white-on-accent (or black-on-light-glass) text
colour real tiles use.

## First-run home-style (tiles vs icons) choice wizard, with a real live preview

New user ask: on first launch (and once for an existing install upgrading to the version that
introduced ICONS mode), ask the user to choose between the two home styles with a visual sample of
each, rather than silently defaulting to TILES and leaving `HomeStyle` buried in Personalize.
Scoped down via `AskUserQuestion` to keep this a single session's work: just the one choice screen
(no multi-step wizard, no bundled restore-backup step — that stays exactly where it already is,
Personalize → backup & restore), with a **real live preview** (not a drawn mockup) built from the
actual `TileView`/`IconCellView` composables, and a **version-independent one-shot flag** rather than
a specific versionCode check.

**Detection: one flag, not a version comparison.** `HomeStyleWizardPrefs` (new file
`HomeStyleWizard.kt`, `:feature:start`) follows the exact same shape as every other one-shot flag in
this app (`FirstRunHintPrefs`/`SettingsAppMigration`, both in the shared `tileshell.prefs`
`SharedPreferences` file) — `shown()`/`markShown()`, checked once in `StartViewModel`'s `init{}`
alongside `migrateSettingsTile()`. This one flag alone covers both trigger cases without a
versionCode check: a genuinely fresh install has it unset, and so does an *existing* install
upgrading to the first version with `HomeStyle` at all, since the flag itself is new in that same
release. `StartViewModel` gained a `homeStyleWizardOpen: StateFlow<Boolean>` following the identical
sheet-gate shape as `aboutOpen`/`personalizeOpen`/etc., plus `chooseHomeStyle(style)` (sets the style
via the existing `setHomeStyle` — which already seeds a 4dp corner radius on first switch to ICONS —
then marks the flag and closes) and `skipHomeStyleWizard()` (marks the flag and closes without
changing anything, leaving the TILES default in place). Wired into `goHome()`'s existing close-every-
sheet chain, so pressing Home/back while it's open counts as a skip, same "never nags twice" rule
every other one-shot flag in this app follows.

**The preview is the real renderer, not an illustration.** Per explicit user choice ("a real mini live
preview... using the app's real TileView/IconCellView composables"), `HomeStyleWizardScreen` builds
its two option cards from a handful of fabricated `TileModel.App` instances (`SAMPLE_APPS` — never
real installed apps, never touching the user's actual layout) rendered through the *actual*
`internal fun TileView`/`IconCellView` (`TileView` widened from `private` to `internal` for this,
following the same visibility-widening precedent as `rememberTileAppIcon`/`tileGesture`/`TileControls`
earlier in this arc) — so what's shown is pixel-for-pixel what the real renderer produces, not a
close approximation. Sample iconKeys are deliberately restricted to ones with **zero** `LiveFace`
mapping (`"phone"`/`"camera"`/`"store"`/`"settings"`) so a blank/fake `packageName` always takes the
plain static-glyph path on both renderers with no `PackageManager` lookup, no live-data fetch, no
permission prompt — verified by walking every branch of `AppTileContent`/`maskedOrGlyphIcon` for a
blank package before writing the preview. Every wallpaper/glass param `TileView` needs is an inert
placeholder (`tiledWallpaper = false`, `glass = false`, `wallpaperPhoto = null`, `wallpaper =
Wallpapers.Mono`, `fullWidth = 0f`, `fullHeight = 0f`), landing it on a plain `Modifier.background
(accent)` fill with zero risk of needing those params to be meaningful. The ICONS-mode sample fixes
`iconShape = IconShape.CIRCLE` regardless of the app's actual (still-default `ORIGINAL`) setting,
since a masked shape reads as more recognisably "Android-style" for a side-by-side comparison than an
unmasked square icon would.

Drawn last in `StartScreen`'s overlay stack so it fully covers everything else, including the
existing `FirstRunHint` card (explicitly suppressed while the wizard is open, so a genuinely fresh
install never shows both at once — the wizard takes priority as the very first thing seen).

## Closed folder's mini-grid shows the real app icon in ICONS mode too

User-reported, with a screenshot of a real Android launcher's home screen: a folder's default apps
(contacts/mail/messages) showed the generic WP monoline glyph in their closed mini-grid preview
instead of each app's real icon — inconsistent with the rest of ICONS mode, where top-level icons
already prefer the real icon (see "Icon mode shows the real app icon, not the WP category glyph").
Root cause: `FolderChildIcon` (`StartScreen.kt`, feeding `FolderTileContent`'s mini-grid — used by
*any* closed folder at MEDIUM+, in both home styles) had never been touched by that earlier fix; it
still picked `useAppIcon` purely from `!TileIcons.hasIcon(iconKey)`, the original WP-authentic rule.

Fixed by threading a `homeStyle: HomeStyle = HomeStyle.TILES` parameter down through `TileView` →
`FolderTileContent` → `FolderChildIcon`, and branching `FolderChildIcon`'s `useAppIcon` decision on
it: in ICONS mode, prefer the real icon whenever `child.packageName.isNotBlank()` (the same rule
`IconCellView`'s `maskedOrGlyphIcon` already applies); in TILES mode, the original glyph-first rule
is untouched, keeping that mode's WP-authentic look exactly as it was. Deliberately scoped to only
the *closed* mini-grid — inline-expanded folder children were already correct (they route through
`FolderChild.asTileModel` → the ordinary `TileView`/`IconCellView` call site, which already carries
this fix), and a widget stack's members render via `AppTileContent` (tile-mode-only regardless of
home style, unrelated to this bug).

## Closed folder's mini-grid drops its per-cell background plate in ICONS mode

Direct follow-up to the previous entry, same screenshot: with the real icon now showing, each mini-grid
cell still painted a tinted background square behind it (`FolderTileContent`'s `cellBg`/`cellFill` —
a translucent dark tint by default, or the app's dominant colour under "tile colour from app icon",
originally designed for the WP tile aesthetic). User-reported once the real icon was visible underneath
it: "only icon should be shown - dont show inside square." `cellFill` now also branches on the same
`homeStyle` param from the previous fix: ICONS mode skips the background plate entirely (`Modifier`,
no fill), matching a normal Android launcher's folder preview (bare icons, no per-cell backdrop); TILES
mode's tinted-square look is unchanged. `IconFolderCell` (the ICONS-mode SMALL closed-folder renderer,
a separate code path from `FolderTileContent`) already had no such background plate, so it needed no
change.

**Immediate follow-up: the icon itself needed to grow to fill the space the plate used to occupy.**
User-reported right after: "icon size should be bigger... as there is no square around" — removing
the backdrop left the existing 18dp `FolderChildIcon` icon reading as too small/lost in the cell.
`FolderChildIcon` now sizes to 26dp in ICONS mode (vs the original 18dp, kept unchanged for TILES
mode, where the icon still sits on its own tinted-square backdrop and was tuned for that look).

## Picking "icons" in the wizard now actually shrinks the default apps to icons

User-reported, tying back to the reference screenshot from the wizard entry above: picking "icons"
in the first-run wizard still showed a Start screen dominated by big live tiles, not icons — "the
icons mode - default start showing more tiles than icons. user may get confused." Root-caused before
writing any code (via a research pass over `DefaultLayout.kt`/`LayoutSeeder.kt`/`StartViewModel.kt`):
`DefaultLayout.DEFAULT_TILES` seeds ~61% of the default 18 tiles at MEDIUM/WIDE (phone, camera,
contacts, mail, messages, weather, calendar, clock, photos, music, and the whole "social" folder) —
fixed WP-appropriate sizes, written by `seedIfEmpty()` in `StartViewModel.init{}` *before* the wizard
even opens. Choosing ICONS there only flips `LauncherSettings.homeStyle` (`setHomeStyle`'s own doc
comment: "rewrites nothing in the layout itself") — it was never home-style-aware, unlike
`AppListViewModel.pin()`, which already seeds a *newly pinned* app at SMALL in ICONS mode. Since ICONS
mode only renders `SMALL` tiles as icons, everything seeded at MEDIUM+ kept rendering exactly as it
would in TILES mode, regardless of the wizard choice.

Fixed at the one safe hook point: `StartViewModel.chooseHomeStyle(ICONS)` now also calls a new
`shrinkDefaultAppsToIcons()` — walks the just-seeded `tiles.value`, resizing every top-level
`TileModel.App` with a real, non-blank `packageName` down to `SMALL`, and clearing every top-level
tile's `gridSlot` (whether resized or not) so the whole grid re-flows dense/compact around the new
sizes instead of leaving holes where STICKY mode's `init`-time `seedStickySlots` had already anchored
the old, larger footprints. Two things are deliberately left untouched, matching the "known Android
icons + a few live tiles" look: `liveOnly` tiles (blank package — clock/weather/calendar/personalize,
already correct or meant to stay live) and folders (a folder keeps its folder-sized tile, not shrunk
to a compact icon). Scoped to only ever run once, from the one-shot wizard's ICONS pick on a
genuinely fresh layout — never from a later Personalize toggle — so it can never clobber a layout the
user has since customized; that path (`setHomeStyle` called directly, not through the wizard) is
completely unchanged.

A genuinely pleasant emergent result, not separately designed for: on a device where `calendar`'s
role *does* resolve to a real installed app (its package is non-blank), `shrinkDefaultAppsToIcons`
shrinks it to SMALL like any other real app — and since it's still iconKey `"calendar"`, it
automatically gets the earlier "weather/calendar/clock stay live at 1×1" treatment, landing as a
compact live "day-of-month" mini tile for free, with zero code written specifically for that
interaction. Verified on a fresh emulator install (`pm clear` equivalent via uninstall/reinstall):
clock and weather stayed as their original bigger live tiles (unresolved roles, `liveOnly`, blank
package on that emulator), calendar became a compact live "19" tile, every other app (phone/camera/
contacts/gmail/messages/photos/music/maps/chrome) became a small real-icon, and the social folder
kept its bigger folder tile with bare real-icon children — matching the reference screenshot's mixed
look closely. Build + tests green.

## Narrow live tiles (TALL/COLUMN, 1 column wide) show their data stacked vertically

User-reported, on the `android-home-style` branch's drag-resize presets: "vertical with width=1
tiles of clock and weather, not showing full data. need to adjust font size like 1x1 tile," then
clarified further mid-session — the fix should keep showing every field ("may have to show the
matter vertically... same may be applicable for notification display on other live tiles") and use
the tile's full height ("vertical tile full vertical space should be utilised properly"), not just
fall back to the compact single-value SMALL face and drop data.

Root cause: `ClockFront`/`ClockBack`, `WeatherFront`/`WeatherBack`, `CalendarDateColumn`/
`CalendarFaceColumn`, and the shared `ConversationCountFace`/`NotificationFaceContent` (mail/
messages/generic-notification tiles) only ever branched their font sizes and layout on *height*
(`size == WIDE`/`LARGE`, or a MEDIUM/WIDE/LARGE `when`) — never on width. `TileSize.TALL` (1×2) and
`TileSize.COLUMN` (1×4), added for gesture-based drag resize, are exactly as narrow as `SMALL` (1×1)
but reach these full-size faces (only `SMALL` short-circuits to the compact `*SmallFace` composables
in `StartScreen.kt`/`LiveFace.forIconKey`), so their multi-line, wider-tile-oriented text clipped at
1 column width.

Fixed with a new `TileSize.narrowLive` (`cols == 1 && this != SMALL` — true for `TALL`/`COLUMN`,
automatically covers any future 1-column preset) checked inside each face composable, *not* by
routing narrow tiles into the `SMALL` path — the user explicitly wants the full data (weekday+date,
place+condition, sender+snippet), just reflowed to fit. Each narrow branch: centers text
(`Alignment.CenterHorizontally` / `TextAlign.Center` — the non-narrow layouts right- or left-align,
which reads fine at 2+ columns but crowds one edge at 1 column), shrinks/reuses the width-safe font
sizes the existing `*SmallFace` composables already proved fit a 1-column cell (20sp clock time,
34sp weather temp / calendar day), abbreviates weekday/month to 3 letters (a full "wednesday"/
"september" doesn't fit; ellipsis-truncating it mid-word reads worse than "wed"/"sep"), and adds
`TextOverflow.Ellipsis` + a small `maxLines` bump as a safety net on every remaining line (place,
condition, snippet, alarm title) so nothing hard-clips even for a longer string. Per the "utilise
the full vertical space" follow-up, narrow layouts use `Arrangement.SpaceEvenly` instead of the
non-narrow layouts' `Arrangement.Center` + manual `Spacer`s — this spreads the 2–4 lines evenly
across whatever height the tile actually has (`TALL`'s 2 rows vs. `COLUMN`'s 4), rather than bunching
them in the middle with dead space above/below, with no extra branching needed for the two different
row counts. `NotificationFaceContent` gained a fourth `NotificationFaceContentNarrow` branch
alongside its existing MEDIUM/WIDE/LARGE ones (avatar + sender + snippet stacked and centered,
dropping the picture-hero column entirely — no room for it at 1 column); `ConversationCountFace`
(the front face shared by mail/messages/generic-notification tiles) centers and shrinks slightly
rather than needing a wholly separate composable, since it was already a single small `Column`.

Every narrow branch is additive (`if (narrow) ... else <original>`), so `MEDIUM`/`WIDE`/`LARGE`
rendering is byte-for-byte unchanged. Verified on an emulator: drag-resized both a weather tile and
a clock tile to `COLUMN` (1×4) in edit mode — weather shows "mumbai" / "28°" / "overcast" fully
readable and evenly spaced top-to-bottom with no clipping (both in and out of edit mode); clock
shows "4:03pm" / "wed" / "19 august 2026" the same way. Build + full unit test suite green.

## Tile colour source: "wallpaper" option, tiles read the same accent as the feed/Quick Panel

User-requested, drawing an explicit parallel to existing behaviour: "glance and quick settings use
their background and gadget/tile from accent picked up from wallpaper. similarly keep another color
option for tiles to pickup from wallpaper, make provision in personalisation with added color tile."
The feed/glance page and Quick Panel already derive a single accent `Color` from the current
wallpaper via `rememberFeedPalette` (`feature/start/.../feed/FeedPage.kt`, `internal` — androidx.
Palette for a custom photo, falling back to the gradient's own first layer colour for a stock
wallpaper, with an average-colour fallback when Palette yields nothing); `QuickPanelOverlay.kt`
already calls it directly across packages within `:feature:start`, which is the precedent this reuses
verbatim rather than inventing a second implementation.

`TileColorSource` (`core/data/settings/LauncherSettings.kt`) gained a third constant,
`WALLPAPER_ACCENT`, alongside the existing `GLOBAL_ACCENT`/`APP_ICON`. `SettingsCodec` needed no
changes — its enum round-trip is by name (`TileColorSource.entries.find { it.name == value }`), so a
new constant persists for free. `StartScreen.kt` computes `wallpaperAccentColor` once at the top
(unconditionally — cheap, since `rememberFeedPalette` memoizes internally the same way the feed page/
Quick Panel's own always-on calls do) so Personalize can preview the real colour on its swatch before
the user switches to it; a `noWallpaper` guard falls back to the plain global `accent` there, matching
the feed/Quick Panel's own `flatBackground` check — `Wallpapers.forId("none")` has no "none" entry in
its map and falls back to returning the bundled `Aurora` gradient (a deliberate design predating this
change, used elsewhere so swatch previews always have *something* to render), so without the guard a
"no wallpaper set" launcher would misleadingly tint every tile with Aurora's colour as if that were
"the wallpaper," rather than falling back to the accent like the feed/Quick Panel do. A second,
mode-gated `wallpaperAccent: Color?` (null unless `WALLPAPER_ACCENT` is actually selected) threads
into the existing per-tile colour priority chain (`tileOverride → iconColor → wallpaperAccent →
accent`) in three places that needed it: `StartPage`'s own `tileAccent` (top-level tiles, threaded via
a new `TileView`/`StartPage` parameter alongside the existing `appIconColors: Boolean`), and
`FolderTileContent`'s per-child mini-grid `cellBg` (folder children resolve their own colour
independently of the parent tile's already-resolved accent, same as the existing app-icon-colour
branch there). `StackTileContent`'s per-member colour needed **no new parameter** — its fallback chain
already ends at the tile's own `accent` param, which is already wallpaper-aware by the time it reaches
there, so threading a redundant unused parameter through it was reverted after a first pass added it.

Personalize's "tile color source" pill row (`PersonalizeSheet.kt`) gained the requested "added color
tile": a third pill next to "accent"/"app icon", with a small swatch dot sampled from the live
wallpaper accent colour (not just a text label) so the user can see the actual colour before picking
it — the concrete form of "make provision in personalisation with added color tile." When selected,
the swatch's colour also fills the whole pill (matching how the "accent" pill already fills with the
global accent when selected), giving the same "colour tile" affordance both unselected (dot preview)
and selected (full pill). Verified on an emulator via `uiautomator dump`-sourced exact tap coordinates
(manual pixel-eyeballing repeatedly mis-tapped the tightly-packed accent-swatch/pill grid): with no
wallpaper set, the swatch and every tile correctly showed the plain blue global accent (the
`noWallpaper` guard); after picking a distinct "sunset" stock gradient and selecting the "wallpaper"
pill, the swatch, the pill's own selected fill, and every tile on Start (including a folder's mini-grid
children) all switched to the same deep red/brick colour sampled from that gradient — confirming the
whole chain end-to-end, not just the Personalize preview. Build + full unit test suite green.

## Guide and about sheets never mentioned home style, icon shapes, or the drag-resize tile sizes

User asked directly: "have you added selection tiles/icons in guide, and features & info. same for
more tile sizes" — the answer was no. The whole `android-home-style` arc (home-style choice wizard,
icon shapes, gesture-based drag resize to 11 total sizes, "free" arrangement) shipped across several
earlier sessions on this branch without ever touching `PersonalizeGuideSheet.kt` ("how to personalize")
or `AboutSheet.kt` ("features & info") — both still described only the original four tile sizes and
said nothing about icons mode at all. Fixed by adding a new "home style" `FeatureGroup` to the guide
(with a matching visual: a plain tile swatch next to the four selectable `IconShape` outlines —
circle/squircle/rounded/rectangle — reusing `SquircleShape` from `:core:design` rather than duplicating
`PersonalizeSheet`'s own `private` icon-shape preview logic) covering the first-run wizard, the
tiles↔icons switch, icon shapes, the icon↔live-tile size-boundary conversion, and icons mode's "free"
arrangement default; and expanding "organizing tiles" with the drag-corner/11-size detail alongside the
existing tap-cycle description. `AboutSheet.kt`'s "start screen" group got the same content in its
plain (no-visual) bullet-list convention, plus a bonus "sticky/free/dense" arrangement bullet — that
setting predates this branch and had never been documented either, close enough to the new "free" mode
bullet that leaving it out would have read as a gap. Both files also had a second, separate copy of the
guide's one-line subject summary ("colours, wallpaper, tiles, pinning apps, the feed...") —
`PersonalizeSheet.kt`'s own "how to personalize" nav-row subtitle — which needed the same "home style"
addition to stay in sync; missing that copy the first time round is why an early on-device check still
showed the old summary text after editing only the guide sheet's own header. Verified on an emulator:
opened the guide sheet, scrolled to the new "home style" group (visual renders correctly, all six
bullets present) and to the updated "organizing tiles" bullets; opened the about sheet's "start screen"
group and confirmed the same content renders there in its plain-text form. Build + full unit test suite
green.

## Tile colour source row: real bug — "wallpaper" pill wrapped its label vertically, one letter per line

User-reported with a screenshot: the "wallpaper" cell of the tile-colour-source row rendered as a
tall, narrow capsule with "wallpaper" spelled out one letter per line, instead of a normal short pill
next to "accent"/"app icon". Root cause: that row was a one-off layout (`Row(fillMaxWidth,
SpaceBetween) { Text("tile color source"); Row(pills) }`, each pill an ad-hoc `Row` sized to its own
content) — bespoke and different from every other selector on this sheet (home style, arrangement,
wallpaper type), which all use the shared `SettingGroup` + `SegCell` convention (label above, then a
bordered `Row` of equal-`weight(1f)` cells below). Once "wallpaper" grew a leading swatch dot its
pill needed more content width than the same-line label+3-pills arrangement reliably had left over,
and Compose's `Text` inside an unweighted, unbounded-width `Row` responds to too little available
width by wrapping character-by-character rather than clipping or overflowing — reading as a tall
vertical strip. (First attempt: just moving the label above the pills row in isolation, matching the
user's own "label above and pill below" description — a real improvement, but still a bespoke pill
row rather than fixing the underlying inconsistency; the user's immediate follow-up, "pills below as
per other settings," asked for the shared convention instead.)

Fixed by deleting the bespoke row entirely and rebuilding it as `SettingGroup(label = "tile color
source") { Row(fillMaxWidth + border) { SegCell(...) × 3 } }` — byte-for-byte the same shape as
"home style"/"arrangement" immediately below it. `SegCell` (shared by every segmented selector on the
sheet) gained an optional `swatch: Color?` param — a small bordered circle drawn before the label,
used only by the "wallpaper" cell — plus an explicit `maxLines = 1` on its `Text` as a hard backstop
against this exact failure mode recurring for any future segmented cell. Since each `SegCell` now
gets an equal `weight(1f)` share of the row's full width (guaranteed by the shared bordered-`Row`
container, not left to chance the way the old ad-hoc pills were), "wallpaper" always has as much room
as "accent"/"app icon" regardless of label length or swatch presence. Verified on an emulator: the
row now renders three equal-height, equal-width cells; tapping between "accent" and "wallpaper"
selects/deselects cleanly with no wrapping in either state. Build + full unit test suite green.

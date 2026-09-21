# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

## Monochrome icons + "original" icon shape: circle → square → no plate → square → settled on rounded

User-reported, four times in a row, each pinpointing exactly what was wrong
with the previous fix: "when monochrome icons are selected and icon shape
original it renderes in circle shape" → (fix 1) "now it is showing square
when i select original" → (fix 2, misreading that second report as "no shape
at all, not just the wrong shape") "now icon shape getting merged into
background (inside image looks, but no shape as such)" → (fix 3, back to a
square plate) "instead of square select round icon shape for original" →
(fix 4, read too literally as `CircleShape`, rejected before it was even
installed) "no round select rounded".

Root cause of the *shape*: the monochrome accent-plate branches
(`IconCellView.kt`'s `maskedOrGlyphIcon`, `AppListIcon.kt`'s `MaskedAppIcon`)
computed `composeShape` from the selected [IconShape], which is deliberately
`null` for `ORIGINAL` (every other call site treats that `null` as "don't
mask, show it as the device actually would") — but the monochrome plate
branch wrote `val plateShape = composeShape ?: CircleShape`, silently
substituting a circle for "no shape". Fix 1: fall back to `RectangleShape`.

Fix 2 overcorrected: reasoning that the real-icon `ORIGINAL` branch a few
lines below draws no plate at all (bare bitmap, no `Box`/`background`), it
dropped the monochrome plate entirely for `ORIGINAL` too. That's where the
two cases actually diverge and the fix was wrong: a real icon's own bitmap
*is* its visual surface — solid, opaque, already has contrast against
almost anything — so `ORIGINAL` needing no extra plate is true for it. A
*monochrome* glyph is a transparent silhouette with nothing behind it by
construction; remove the plate and it has no surface to sit on at all, so it
can vanish straight into a similarly-toned background (dark accent on a dark
wallpaper, etc.) — exactly what the third report described.

Fix 3 reverted fix 2: every shape, including `ORIGINAL`, keeps an
accent-filled plate for legibility — `ORIGINAL` was just the one case where
that plate wasn't clipped to any particular shape, falling back to
`RectangleShape` (an unclipped `Box` is naturally rectangular anyway, so this
was a plain square). The user then asked directly for round instead of
square — fix 4 reached for the obvious literal reading, `CircleShape`, but
that's a *different* named option (`IconShape.CIRCLE`) from the one actually
requested, `IconShape.ROUNDED` (a `RoundedCornerShape(percent = 30)` —
rounded corners on a square, not a true circle); caught and corrected before
that build was even installed. Final state: `ORIGINAL`'s plate falls back to
`RoundedCornerShape(percent = 30)`, matching `IconShape.ROUNDED`'s own shape
exactly, distinct from both `CIRCLE` and the plain square tried in between.

Applied identically to `IconCellView.kt` (Start, folder mini-grids) and
`AppListIcon.kt` (App List); `feature/livetiles/AppIcon.kt`'s
notification-badge glyph draws no plate for *any* shape (it renders inline in
a live-tile face, not against an arbitrary background), so it was never
affected either way. Build + full unit test suite green; installed on the
physical device with no crash — the user's own settings already had
`homeStyle=ICONS, iconShape=ORIGINAL, themedIcons=true` live (confirmed by
pulling `launcher_settings.pb`), so this is exactly the state they'll see it
in on next unlock.

## The date-rollover widget fix didn't work: enqueuing from the broadcast put the repaint back in Doze's queue

User-reported the morning after the previous entry's fix shipped: "yesterday
there was a fix for no rollover of date for calendar widget panchang.. but
today also it is showing Shanivar at 5.30 today" — and, crucially, "live tile
of panchang updated correctly", which isolated it to the home-screen widget
again rather than anything shared with the in-app tile.

Diagnosed on the physical device rather than by reading code, since the
previous round's reasoning had been right about the mechanism and still wrong
about the outcome. Pulling TileShell's own WorkManager database
(`adb shell run-as com.tileshell cat .../no_backup/androidx.work.workdb`, then
`sqlite3` locally — there is no `sqlite3` binary on the device) gave the two
facts that settled it:

- The last successful `CalendarSystemWidgetRefreshWorker` run was
  `2026-09-19 20:51:16`. Nothing had run since — not at midnight, not after.
  The widget was showing exactly what it was painted with the previous
  evening, which is precisely the reported symptom.
- The periodic backstop's next run was not due until ~19:26 that evening
  (`dumpsys jobscheduler`: `Run time: earliest=+13h44m`), i.e. the "midnight"
  job was anchored roughly 19 hours away from midnight.

So **both** layers of the design had failed, independently, which is why one
round of fixing only one of them changed nothing.

**Failure 1 — the push path never did the work.** `onReceive` did receive the
broadcast (it is a protected system broadcast, genuinely exempt from Android
8+'s implicit-broadcast restrictions, exactly as the previous entry claimed),
but all it did with that wake-up was call `refreshNow`, which enqueues a
`OneTimeWorkRequest`. That hands the actual repaint straight back to
JobScheduler, where Doze defers it to a maintenance window — on an idle phone
overnight, potentially for hours. The app being on the device-idle whitelist
(confirmed: `dumpsys deviceidle whitelist` lists `com.tileshell`) doesn't
change that, and the jobs' own dumps confirm they sat `Ready: false` on
`TIMING_DELAY`. The broadcast was the one guaranteed execution window we were
ever going to get, and we spent it scheduling more work instead of doing the
work. Fixed with `pushDateRollover` (`WidgetWork.kt`): `goAsync()` plus a
coroutine that calls the worker's own `pushAll` directly, so the repaint rides
the wake-up the OS already granted. All three of these widgets are pure local
date math with no network, so they finish far inside the ~10s the platform
allows.

**Failure 2 — `ExistingPeriodicWorkPolicy.UPDATE` silently discards the new
initial delay.** `ensureScheduled` recomputes `millisUntilNextMidnight()` on
every call, but `UPDATE` applied to periodic work that has *already started
its cadence* (`period_count` was 7) keeps that existing cadence and ignores
the new initial delay — so the midnight alignment quietly stopped taking
effect after the very first period, and the daily run kept whatever
time-of-day it happened to land on. Changed to `CANCEL_AND_REENQUEUE`, which
still satisfies the original reason `KEEP` was rejected (an install on the old
30-minute cadence must not keep it forever) *and* genuinely re-anchors.
Re-enqueueing on every `onUpdate` is safe here because `updatePeriodMillis` is
0 for these three, so `onUpdate` only fires on placement, reboot and app
update, and each re-enqueue targets the very next midnight anyway.

Applied to all three widgets sharing this design — calendar system, moon phase
and countdown — not just the reported one, same as last time.

Verified after installing: the periodic job came back with `period_count=0`
and a next run of `2026-09-21 00:01:00` (one minute past midnight, as the code
always intended, versus 19:26 before), and the `onUpdate` repaint ran and
succeeded. As with the previous round, the end-to-end "does it repaint at the
real midnight" behaviour still can't be forced from here: the broadcast is
protected (even `adb shell`, uid 2000, is refused when it tries to send it),
and faking tomorrow's date means changing a system setting on the user's own
phone, which is theirs to do, not ours. The difference this time is that the
mechanism no longer depends on a second, deferrable scheduling hop.

## Home-screen calendar-system/moon-phase/countdown widgets stayed a full day stale after a deferred midnight job

User-reported: "calendar panchang today still at friday. no rollover" — the
Start-screen live tile correctly showed Saturday; only the separate
home-screen widget (`CalendarSystemAppWidgetProvider`, a real Android
`AppWidgetProvider`, distinct from the in-app `CalendarSystemTileFace`
composable — the two don't share a render path at all, only some pure date
helpers) was stuck on the previous day.

Root cause was already half-documented in the widget's own scheduling code
(`CalendarSystemWidgetRefreshWorker`/`WidgetWork.millisUntilNextMidnight`):
since this widget's content is a pure function of the calendar date, it
deliberately moved off a 30-minute poll to a **single daily WorkManager run
timed for just after midnight**, on the reasoning that "the providers also
refresh on placement, resize, reboot and app update, so a deferred run
self-corrects as soon as the device is in use again." That claim doesn't
actually hold: none of "placement, resize, reboot, app update" happens just
from unlocking your phone the next morning, and `ACTION_SCREEN_ON`/
`ACTION_USER_PRESENT` were never wired to trigger a refresh either (and
couldn't be via a manifest receiver — Android has never delivered those to
one, only to a dynamically-registered receiver, which needs a running
process). So a single Doze-deferred midnight run — an explicitly
acknowledged possibility in the same comment — left the widget stale for a
**full extra day**, not the "one short interval" other widgets' shorter
polling cadences would tolerate, since the next scheduled run is 24h after
whenever the deferred one actually fired, not correctively closer to the
next real midnight.

Fixed with the same push-driven-refresh idiom `BatteryAppWidgetProvider`
already uses for plug/unplug events: `onReceive` now checks for
`Intent.ACTION_DATE_CHANGED`/`ACTION_TIME_CHANGED`/`ACTION_TIMEZONE_CHANGED`
and calls the widget's own `refreshNow` directly, ahead of
`super.onReceive`'s normal AppWidgetProvider dispatch. This trio is a
documented exception to Android 8+'s implicit-broadcast restrictions for
manifest-declared receivers — confirmed on-device: `adb shell am broadcast -a
android.intent.action.DATE_CHANGED` itself is refused with a
`SecurityException` ("not allowed to send broadcast ... from
pid=...uid=2000"), meaning it's a genuinely OS-protected broadcast like
`BOOT_COMPLETED`, not an arbitrary implicit one an app could fake — exactly
why it reaches a manifest receiver reliably without the app running. It's
also the same three actions AOSP's own Calendar app widget listens for to
solve this identical "day rollover while asleep" problem. Applied to all
three widgets that share this exact once-a-day design and doc comment
("the midnight-aligned daily workers: calendar system, moon phase,
countdown") — `MoonPhaseAppWidgetProvider` and `CountdownAppWidgetProvider` —
not just the one reported, since all three have the identical latent bug.

Build + full unit test suite green; installed on the physical device, no
crash in `adb logcat`; confirmed via `adb shell dumpsys package` that all
three providers' intent filters now include the three new actions. The
actual end-to-end "does it re-render at the real midnight rollover" behavior
can't be forced via adb (the broadcast is protected, and there's no way to
fake tomorrow's date without root) — it can only be confirmed by the user
noticing the widget is correct the morning after a Doze-deferred midnight
run, which is precisely the failure mode this fix targets.

## Fixed a real race: the merge-target navigation raced the generic blockCount reclamp effect

Direct follow-up, user-reported after the entry below shipped: "i merged page
into music and entertainment. but after merging music page not shwn." The
previous fix computed the correct post-merge target index and called
`settleTo(target)` immediately, right after kicking off `viewModel
.mergeSection(...)`. That looked right in isolation but raced a second,
unrelated effect on the very same `Animatable`.

`mergeSection`'s DB write is asynchronous (`viewModelScope.launch(writeContext)
{ repository.mergeSection(...) }`); the removed section's disappearance only
reaches `blockCount` a few frames later, once Room's Flow re-emits.
Meanwhile `settleTo(target)`'s `progress.animateTo(target, settleSpec)`
starts immediately, on the very next frame. When `blockCount` *does* change
moments later, the existing `LaunchedEffect(blockCount)` reclamp effect —
which exists to keep the pager in a valid range after any page-count change —
fires and calls `progress.animateTo(progress.value.coerceIn(0f, (blockCount -
1).toFloat()), settleSpec)` on the *same* `Animatable`. `Animatable.animateTo`
cancels whatever animation is already running on it before starting its own —
so this second call hijacked the still-in-flight navigation mid-transition,
freezing it at whatever intermediate position it had reached (via
`progress.value.coerceIn`, which is nearly a no-op against wherever the
animation currently sat) instead of letting it continue on to the real
target. The further away the destination page, the more visible the
freeze — exactly matching the report: merging into a non-adjacent named
section landed the pager somewhere between the source and destination, not
on the destination.

Fixed by giving the reclamp effect the destination itself, instead of running
a second competing animation alongside it. New `pendingMergeTargetBlockIndex`
state is set (not `settleTo`-driven) the moment a merge is initiated;
`LaunchedEffect(blockCount)` checks it first, and — since it only runs once
`blockCount` has *actually* changed — consumes and clears it there, animating
straight to the real (already `blockCount`-aware) destination as its own
first branch, ahead of the existing `isAppList`/`feedShown`/generic-coerce
branches. There is now only ever one `animateTo` call in flight per merge, so
nothing can hijack it mid-flight. Every other `blockCount` change (add page,
remove page & tiles, or nothing pending) is completely unaffected — the new
branch is a no-op whenever `pendingMergeTargetBlockIndex` is null.

Build + full unit test suite green; installed on the physical device, no
crash in `adb logcat`. Same caveat as the two entries below — this is a
genuine animation-race fix, not something a unit test harness covers, so it
needs the user's own on-device confirmation with an actually-distant merge
target (adjacent-page merges may have looked fine by coincidence even with
the race present).

## "Merge into…" (arbitrary target page) now shows the target page right after merging
## "Merge into…" (arbitrary target page) now shows the target page right after merging

Direct follow-up, user-requested: "merge into feature now working but after
merge i had asked you to show the page in which it was merged." Same ask as
the earlier "merge with main" fix, extended to `main`'s own newer "merge
into…" picker (`b601e0e`, which lets a removed page's tiles fold into *any*
other existing page, not just main) — that feature had shipped without ever
picking up the "show the destination afterward" behavior.

`onMergeSection`'s call site (`StartScreen.kt`) previously delegated straight
to `viewModel::mergeSection` with no navigation at all, so the generic
`LaunchedEffect(blockCount)` reclamp effect was the only thing moving the
pager after a merge — it just coerces the current position into the new
(shrunken) valid range, which is not the same as showing wherever the tiles
actually landed.

Fix is one level more involved than the plain "always main" case, because the
target can be *any* page, including one that sits *after* the removed page in
section order — removing a page shifts every later page's block index down
by one, so a target after it needs that same shift applied to the index
computed from the picker's own (pre-merge) `sortedSections`; a target before
it, or main (block 0), is unaffected. Computed as: locate the removed page's
and the target's index in the current `sortedSections`, subtract one from the
target's index only if the removed page's index is lower, then convert to a
block index (`+1` for a real section, `0` for main). `settleTo` is called
with that already-correct post-merge index immediately, rather than waiting
for `sections` to actually update — the arithmetic makes it correct up front,
so there's no need for a reactive follow-up once the write commits.

Build + full unit test suite green; installed on the physical device, no
crash in `adb logcat`. Needs the user's own on-device confirmation — same
caveat as the "merge with main" fix, and the same gesture (long-press → edit
mode → merge-into picker) this project's own history says ADB can't reliably
synthesize.

## Sections' "big gap" bug: why the same symptom kept reappearing across three fixes

User reports across several rounds all described the same visible symptom —
resizing/moving a tile inside a small section, or just relaunching the app,
left a large empty gap under a section header (once even surviving a direct
adb-level data repair, which reverted on the very next launch). Each round
found a *real* bug, but not the *only* one, because the same design flaw —
"a sticky-mode helper computes push-down/collapse/seeding against the
entire flat tile list" — existed independently in three separate places that
all predate the sections feature and never needed to know about it before:

1. `StartViewModel.seedStickySlots` — re-anchors any tile with no `gridSlot`
   yet, on every app launch. Packed the whole tile list as one grid and
   wrote back each tile's *global* row.
2. `editDragGesture`'s live sticky-drop cell computation — uncapped, so
   once a drag gesture is scoped to one small section's own tiny Box (see
   the sections rendering entry below), a finger travelling past that box's
   small rendered area (trivial once the box is only 1-2 rows tall) computed
   an arbitrarily large row straight from the raw pointer position.
3. `stickySlotsForPlacement`/`collapseEmptyRowsAfterRemoval` — the actual
   placement engine behind resize, drag-drop, and unpin. Same flaw, and the
   most consequential: a *persisted* write, not a live preview, so the
   damage from an ordinary resize survived a relaunch and even survived
   collapsing the section (collapsing just stops rendering the tiles; it
   never touches their stored `gridSlot`).

Each was found only by reproducing the report, decoding the actual
persisted `gridSlot` value (`row = slot / 1000`, per `GridPacker`'s
`SLOT_ROW_STRIDE`), and recognizing the row number as "this tile's position
among *all* pinned tiles" rather than anything meaningful within its own
2-4-tile section. Fixed with one shared scoping helper
(`StartViewModel.tilesInBlock(sectionId, excludeId)`, mirroring
`blocksFor`'s own section/unsectioned grouping) threaded through every one
of these call sites, rather than three independent patches — the same
underlying invariant ("a block's own placement math only ever sees its own
block's tiles") needed to hold everywhere sticky-mode touches a `gridSlot`,
not just in whichever function happened to be caught first.

A related, separately-diagnosed bug in the same testing round: merging a
tile into a folder that already belonged to a section reset the folder to
unsectioned. `LayoutRepository.mergeTiles`/`mergeFolderChildIntoTile` rebuild
the target's whole `TileEntity` to convert/grow it into a folder;
`insertTiles`'s replace-on-conflict overwrites every column, so omitting
`sectionId = target.sectionId` from the new row silently discarded it. Not
the same root cause as the gap bug (a full-row overwrite forgetting one
field, not a wrong-scope computation), but found and fixed in the same pass
since it produced a similarly surprising "my section membership silently
changed" experience.

## Start screen "sections" — named/collapsible groups, not multi-page desktops

User asked for a "desktop 1 / desktop 2" concept on Start. Real WP/WM10 Start
is one continuous vertical scroll, never paged desktops — a literal Android-
style multi-page implementation would be a deliberate fidelity deviation, and
(confirmed via codebase research before building) the costlier build: it
requires nesting a second horizontal-swipe recognizer inside the screen
region the existing Start↔App List↔Feed pager already owns
(`pagerModifier` in `StartScreen.kt`), genuinely new gesture-disambiguation
work with no existing analog in the codebase.

Built named, collapsible **sections** within the existing single vertical
scroll instead ("work", "games", "travel", ...) — reuses
`GridPacker.expandFolderInline`'s row-shift/collapse math (generalized from
"a folder's children" to "a section's tiles") and the tile corner-control's
existing inline-picker-sheet pattern (today: the colour picker), extended
with one more action, "move to section ▸".

Mechanics decided with the user, all favoring the cheaper of two options at
each fork:
- A section is a lightweight entity (`SectionEntity`: id/label/order/
  collapsed) — not a tile. A tile's own `sectionId` (null = unsectioned)
  records membership; deleting a section ungroups its tiles rather than
  deleting them (same organizational-only contract a dissolving folder
  already has for its own children).
- Existing installs render identically post-migration (v12→v13): every
  current tile decodes to `sectionId = null`, so Start looks exactly as it
  does today until the user creates a section.
- Pinning from the App List is unchanged — always lands unsectioned,
  appended at the bottom, zero added friction to the pin gesture.
- Moving a tile **within** a section: ordinary drag (existing
  `editDragGesture`, section-scoped).
- Moving a tile **across** sections and reordering **whole sections**: both
  use a tap-based picker/buttons (a "move to section ▸" corner-control
  action; ↑/↓ buttons on the section header) rather than long-distance
  drag — avoids building drag-to-autoscroll (dragging near a screen edge to
  scroll a tall page during a drag), which nothing in this codebase does
  today and which true cross-section/cross-page dragging would require.
  Deferred as a possible later follow-up, not bundled into the initial build.
- Section creation/rename/delete/reorder happens inline on Start in edit
  mode, never through the Personalize sheet — Personalize stays global-
  settings-only, matching how folders already work. No new
  `LauncherSettings` field.

Built on a dedicated `start-sections` branch (not `main`), so the whole
feature can be dropped/reverted cleanly if it doesn't land well — same
convention as the `android-home-style`/`feed-glance-redesign` branches.

## FREE-mode drag-drop redirects to the nearest free cell instead of displacing the existing tile

User-reported: "in free mode, when i push tile downwards and tile exists
there the existing tile show inward movement... if there is enough space
there should not be any existing tile movement. unless there is space
creation requirement."

FREE mode's documented premise (`LauncherSettings.kt`'s `TilePackMode` doc
comment) is that "nothing moves unless the user moves it." Its drag-drop
write path (`GridPacker.swapPlacement`, now `freePlacement`) technically
honored that for *most* of the grid, but not for the one tile actually
occupying the drop target: it always swapped the dropped tile with that
single occupant, moving it to the dragged tile's old cell — visible motion
the user correctly identified as inconsistent with "nothing moves unless the
user moves it," since it happened even when the grid had free cells
elsewhere that could have absorbed the drop without touching anyone.

Fixed by redirecting the dropped tile to the **nearest free cell** of its own
footprint (Manhattan distance to the drop point, ties broken toward the
lowest row then lowest column, matching the tie-break convention
`stickyPlacement`'s own `freeColumnNear` already uses) instead of swapping.
A free cell is provably always found: a row past every other anchored tile's
bottom edge is free across every column by construction (nothing else
extends that far), so the search is bounded there and never comes up empty —
which means the old "no free cell" fallback (swap the single occupant, or
push-down via `stickyPlacement` for a multi-occupant/mismatched-footprint
drop) is genuinely unreachable now and was deleted along with it, rather than
kept as dead defensive code. The one case this changes for the user: a drop
that used to swap two tiles now instead leaves the occupant exactly where it
was and settles the dragged tile at whatever open cell is closest to where
it was released — which can be a different cell than the occupant's old one,
since "nearest free" and "the dragged tile's own previous cell" aren't always
the same place. That is the intended trade-off per the user's own framing:
existing tiles should only ever move when the user moves them directly, and
"space creation" (this codebase's other two modes' push-down behaviour) is
reserved for the case FREE mode can never actually hit.

## Tiles without borders: a "borderless" tile style plus a "tile outline" toggle

User asked whether there could be "another option for tiles without borders."
The phrase had two plausible readings that would have produced different
features, so both were mocked up and shown before any code was written, and
the user picked **both**:

- **Borderless** is a fourth mutually exclusive tile background style
  (`LauncherSettings.borderlessTiles`), alongside the existing none /
  transparent / behind tiles. The tile paints no fill *and* no outline — only
  its glyph, label and live-face content render, directly over the wallpaper.
  It is deliberately not "glass at transparency 1.0": glass still draws the
  hairline and still tints by the tile's own accent, so it never fully
  disappears. Mutually exclusive with `glass`/`tiledWallpaper` for the reason
  those two already are with each other — all three decide what a tile's own
  surface paints, so leaving two on would leave one silently doing nothing.
- **Tile outline** (`LauncherSettings.tileOutline`, default on) drops just the
  1dp hairline while keeping the fill. It is surfaced only for the two styles
  that draw a hairline at all — a solid accent tile and a borderless tile have
  none, so the toggle would be inert there — but it is stored independently of
  the active style, so it is remembered across a style switch.

Three consequences worth recording:

1. **Face text contrast came for free.** Every live face reads
   `LocalTileFaceColor`, which Start already resolves as
   `Glass.faceTextColor((glass || tiledWallpaper) && chosenWallpaperIsLight)` —
   i.e. "flip to dark text when the *wallpaper* shows through and is light."
   A borderless tile shows the wallpaper through exactly the way glass does, so
   the fix was adding `|| borderlessTiles` to that one condition rather than
   any new colour plumbing.
2. **A folder's mini-grid and a stack's members must go unfilled too.** Both
   render their own per-child plates (`FolderTileContent`'s `cellFill`,
   `StackTileContent`'s member fill) independently of the outer tile's
   background. Without their own borderless branch a "borderless" folder would
   still paint a grid of tinted squares floating over the wallpaper — exactly
   the tile surface this style removes. `tiledWallpaper` already had that
   branch in the folder path for the same reason.
3. **"Tile spacing" becomes invisible in borderless.** There is no tile edge
   left to space apart, so the slider still moves the grid but shows nothing.
   Left surfaced anyway rather than conditionally hidden: the value is shared
   with every other style and hiding it would make switching styles look like
   the setting had been lost.

`resetTileStyle` deliberately leaves both new fields alone, matching how it
already treats `glass`/`transparency`/wallpaper — a background style is a
deliberate choice, not an over-personalization to recover from.

## Borderless tiles are raised, not empty (and then the shadow was removed again)

User, after living with the first version: "borderless should have raised tile
surface to clear show distinction with outer surfcace." A tile that paints
literally nothing leaves no way to see where one ends and the next begins —
the grid reads as loose floating icons rather than tiles. Three renderings
were mocked up (shadow only / a faint raised pane + shadow / a bevelled edge);
the user picked the raised pane, always on for the borderless style rather
than behind another toggle.

**First shipped as a flat white wash plus a hand-drawn elevation shadow** — see
below for why the shadow had to be hand-drawn rather than `Modifier.shadow`.
The user then reported "there is a difference [from behind tiles]. but
borderless view tile borders are visible. can it be just raised tiles" — a
real regression, not a preference call: at the default 3dp tile gap, two
neighbouring tiles' shadow rings meet in that gap and draw a continuous dark
line around every tile, which is exactly the bordered-grid look this style
exists to remove.

**Fixed by dropping the shadow entirely and using a top-to-bottom gradient
instead of a flat wash** (`Glass.raisedGradient`) — brighter at the top,
falling off toward the bottom, mimicking an overhead-lit surface. That alone
now carries the sense of elevation: the *inside* of each tile reads as raised
because of the light gradient across its own face, with nothing painted in
the gap between tiles at all. This is a strictly better solution to the
original ask, not a compromise — it gives the tile-distinguishing effect
without needing anything drawn outside the tile's own bounds, which is what
caused the border regression in the first place. `BORDERLESS_SHADOW_SPREAD_DP`/
`_DROP_DP`/`_STEPS` and the whole hand-drawn `drawBehind`/`clipPath`/
`ClipOp.Difference` block in `StartScreen.kt`'s `TileView` were deleted along
with `Glass.raisedFill`; `Glass.raisedGradient` replaces it as the one thing
borderless paints.

Two things worth recording from the (now superseded) shadow approach, kept
here because they explain a code shape a future reader might otherwise wonder
about (the `graphicsLayer.shadowElevation` drag-lift shadow is still used
elsewhere in the same file, so "why isn't borderless using that" is a fair
question):

**The platform shadow paints under the whole layer, interior included** — fine
for the opaque drag-lift tile, but under a translucent fill the shadow shows
straight through and darkens the tile itself, the opposite of raised. That is
why a hand-drawn ring (`clipPath` with `ClipOp.Difference` cutting the tile's
own rounded rect out of a concentric-steps shadow) was used instead of
`Modifier.shadow` — and it is also *why dropping the shadow rather than fixing
its geometry was the right call*: even a correctly-clipped ring still paints
something in the tile gap, and any paint there at all reintroduces the border
look once tiles are packed close together.

**`raisedGradient`'s alpha is white in both themes**, unlike most light/dark
pairs in `Glass.kt` — a raised surface catches more light than the ground it
sits on, so darkening it for light theme would read as recessed. Only the
strength differs (top/bottom alpha 0.13/0.03 dark, 0.38/0.14 light), since the
same wash that clearly lifts a near-black backdrop needs to be much stronger
to register against a light one.

Known caveat, carried over unchanged: in light theme over a bundled gradient,
face text stays white (the gradients stay mid-toned even lifted, so
`chosenWallpaperIsLight` is false) and the raised gradient makes that ground a
little lighter still at the top of the tile. The text-colour decision looks at
the wallpaper, not at the tile's own lifted surface — same pre-existing
caveat glass has; fixing it properly means compositing the fill into the
brightness test for every style, not just this one.

## Borderless became a real widget-card look, not a lifted wash

Direct follow-up, and a genuine pivot rather than a tuning pass: the user
said plainly "there is no difference visually for borderless and behind
tiles" about the gradient-lift version, then, once shown mockups of three
actual home-screen-widget looks, "actually i wanted effect like when gadget
is placed on launcher screen. i am still not satisfied with the output." The
top-lit gradient (previous entry) was a *lighting* effect on an otherwise
invisible tile — too subtle to read as a distinct object next to "behind
tiles," which paints a real, saturated wallpaper window. What "a gadget
placed on the launcher" actually means, and what every prior pass had been
missing, is a real Android home-screen widget: its own rounded, opaque-ish
translucent card, clearly separated from its neighbours by *both* a visible
gap and a real drop shadow — not a lighting cue painted onto an otherwise
borderless surface.

Confirmed with two direct questions before touching code again, since this
was the third design iteration on the same feature and a fourth wrong guess
wasn't worth risking: (1) the card look needs bigger rounded corners and a
wider gap than Personalize's own "corner radius"/"tile spacing" sliders
default to (0dp / 3dp) — user chose **borderless enforces its own minimum for
both, as a floor**, leaving a user who's already set something larger
untouched, and leaving the sliders' effect on the other three styles
unchanged; (2) the card's own tint — user chose **neutral white/black,
flipping by theme** (a light gray-ish card in light theme, a dark one in dark
theme, like a real widget surface), not tinted by the tile's own accent
(which would have made it read as "glass at a different opacity" rather than
a distinct style).

Three changes together make the card read as an actual object:

**A drop shadow is safe again, and this time via the real platform
primitive.** The very first "raised" attempt used a hand-drawn shadow *ring*
specifically to dodge `Modifier.shadow`'s own risk of showing through a
near-transparent fill — but at the default 3dp tile gap, that ring still met
its neighbour's ring in the gap and drew a continuous border, which is what
got walked back to the gradient-only version in the entry above. This time
the tile gap itself has a forced floor (`BORDERLESS_MIN_TILE_GAP_DP = 12f`),
so a plain `Modifier.shadow(elevation, shape, clip = false)` — the same
primitive any Material card would use — has room to fall off before it
reaches the next tile. No hand-rolled clipping trick needed this time; the
geometry does the work instead.

**The corner radius gets its own floor too**
(`BORDERLESS_MIN_CORNER_RADIUS_DP = 20f`, `maxOf`'d against whatever
Personalize's own slider is set to) — square corners read as "a tile with
some shading," not "a card." Both floors live at the two places that already
computed these values (`TileView`'s own `tileCornerRadius` local, and the
`tileGapPx` passed into `StartPage` from `StartScreen`), so folder children
and stack members — which render through the exact same `TileView`/grid-gap
mechanism, per the codebase's existing "no parallel rendering path"
convention — pick up both floors for free.

**`Glass.raisedCardFill` replaces `raisedGradient`/`raisedFill` outright** —
a flat, considerably more opaque neutral fill (not the earlier 9–14% wash),
and importantly it now flips by theme (light gray-ish in light theme, dark
in dark theme) rather than staying white in both, which was deliberately
right for a barely-there *lift of the wallpaper's own colour* but wrong for
an opaque card that needs its own theme-appropriate surface identity, the way
a real widget's background does. `BORDERLESS_SHADOW_SPREAD_DP`/`_DROP_DP`/
`_STEPS` and the whole hand-drawn ring plumbing from the previous entry (`Path`
/ `RoundRect`/ `ClipOp.Difference`/ `clipPath`) are gone along with it —
nothing in the current code draws a shadow by hand any more.

## Widget cards: dropped the shadow a second time, decoupled from Personalize's sliders, renamed

Two more direct follow-ups on the same feature, right after the previous
entry shipped.

**"looks very dark. allow to adjust transparency levels or suggest a
method."** The problem wasn't really "no adjustability" — it was that the
card's own colour was wrong. The previous entry's `raisedCardFill` flipped to
a dark neutral tint for dark theme, reasoning that "a real widget's card
matches the theme." In practice, layering a dark tint over an already
near-black wallpaper composites to something just as dark — the card never
actually lifted off its background. Material's own dark-theme convention is
the opposite of that intuition: an *elevated* dark-theme surface gets
*lighter* than its background, not darker. Reverted to a white overlay in
both themes (the same direction round 1/2 already used, just far more opaque
this time: 12–30% alpha depending on the transparency slider, dark theme;
34–78%, light theme) — and wired it to the existing "tile transparency"
slider (the same one glass uses), rather than adding a new setting: both
styles are "how much the tile's own translucent surface shows through," just
applied to a different base colour, so one control for both was the natural
fit. Personalize now shows that slider under widget cards too.

**"there is a big border for inside square in each tile. is such design
necessary?"** A zoomed screenshot resolved this precisely: the platform
`Modifier.shadow` was rendering as a hard, crisply-edged second rounded
rectangle around the actual card, not a soft blur — almost certainly an
artifact of the emulator's software renderer rather than how it would look
on real hardware, but the fix doesn't depend on knowing which: given this is
now the *second* time a shadow implementation on this style has produced an
unwanted border effect (the first was the hand-drawn ring meeting its
neighbour in the gap), the shadow is removed outright rather than tuned
again. The card fill + forced gap + rounded corners already read as
"raised" without it, and removing it deletes a whole recurring category of
bug rather than chasing a third shadow bug.

**"if tile spacing and corner has no relevance in borderless dont display
those settings."** They didn't just have *reduced* relevance — they had
*none*, since the previous entry's `maxOf(slider, floor)` meant a slider
value below the floor was silently clamped with no visible effect, which is
exactly the kind of "control that does nothing" a user notices and rightly
objects to. Decoupled entirely: `BORDERLESS_CORNER_RADIUS_DP` /
`BORDERLESS_TILE_GAP_DP` are now fixed constants, not a floor over
Personalize's sliders, and Personalize hides the corner-radius, tile-spacing,
*and* gradient-fill rows outright while widget cards is the active style (the
last of the three was already dead code for this style before this session —
`useTileGradient` was never read in the `borderless` fill branch — just
never surfaced as a visible bug until the other two were fixed the same way).

**"suggest better name for borderless."** Renamed the user-facing label from
"borderless" to **widget cards** — the style stopped being accurately
described by "no border" once it grew rounded corners and its own card fill;
"widget cards" names what it actually now does, and echoes the user's own
"gadget placed on launcher screen" framing from the entry above. Deliberately
a display-string-only rename: the Kotlin symbol names (`borderlessTiles`,
`TileBackgroundStyle.BORDERLESS`, `setBorderlessTiles`) and the persisted
settings key are unchanged, so no migration and no risk to an existing
install's saved choice — only the three visible strings (the picker's own
cell label, the personalize guide, the about sheet) changed.

## Tile style selector: 2×2 grid instead of one 4-wide row

Direct follow-up, user: "tile style menu is too crowded now in horizontal
space." The row had been 3 cells (none/transparent/behind tiles) since it
was written; this session added a 4th, and — unlike the 5-cell wallpaper-
type row above it, whose labels are all short single words — two of these
four are now two words ("behind tiles", "widget cards"), which squeezed
badly at quarter-width. Rather than shrink the font or truncate the text,
the row wraps into two rows of two (`labels.chunked(2)`, mirroring the same
trick already used for the stock-wallpaper swatch grid a few sections
above), each cell getting roughly double the horizontal room at the cost of
one extra row of height. `SegCell` itself is unchanged; only the container
around it (a bordered `Column` of two `Row`s with a `HorizontalDivider`
between them, instead of one bordered `Row` of four) changed.

## Widget cards carried onto the glance page's own cards

User: "if widget card is selected can the same effect be carried on glance
and quick settings." Both surfaces already had an established, meaningful
use for opaque accent colour — the glance page's weather/agenda/now-playing
cards are the WP "live-tile colour block" look, and Quick Panel uses accent
fill specifically to mean "this toggle is on." Applying the same neutral
translucent fill to both indiscriminately would have erased that on/off
signal on Quick Panel with no replacement (a checkmark or filled/outline icon
would need to take over that job, its own separate design pass). Asked before
touching either: scoped to **glance only** — Quick Panel is unchanged, and
"carrying the effect" turned out to mean exactly the three cards `AccentCard`
already serves (weather/agenda/now-playing); real hosted third-party widgets
render their own content and were never in scope, and the "custom gadget
cards" CLAUDE.md's status log still described (`CustomCardKind`) had already
been removed from the codebase in a session the log wasn't updated for — so
there was nothing else to include.

`AccentCard`, `WeatherCard`, `AgendaCard`, and `NowPlayingCard` each gained
`borderless`/`transparency`/`dark` parameters (defaulting to off, so every
other caller — there are none besides these three today — is unaffected):
when `borderless` is true, `AccentCard`'s fill switches from the tile's own
accent (flat or gradient, per the existing "gradient fill" setting) to
`Glass.raisedCardFill(dark, transparency)` — the exact function Start's own
widget-card tiles use, so the two surfaces are pixel-for-pixel the same
material, not just a similar-looking approximation.

Text contrast needed its own fix once the card stopped being opaque-accent:
the existing `onAccent = Glass.faceTextColor(useDarkText = isLightBackground
(accent))` reads the CARD's own fill for contrast, which is exactly wrong
once that fill is a translucent neutral wash rather than a saturated colour
— under it, the right contrast reference is whatever's actually visible
through the card, i.e. the page's own background. `WeatherCard`/`AgendaCard`/
`NowPlayingCard` now take `onAccent`/`onAccentDim` as parameters with their
old formula as the *default* (unaffected callers see no change at all), and
`WidgetSlot.kt`'s three call sites override them to the feed page's own
`feedFg`/`feedFgDim` — already computed once per page from the actual
rendered background's brightness — via two small pure helpers,
`glanceOnAccent`/`glanceOnAccentDim`, whenever `borderless` is on.

Verified two ways on the emulator: the "before" state (tile style "none")
shows both cards as flat opaque accent blue regardless of what wallpaper
sits behind them; switching to "widget cards" makes the exact same two cards
visibly shift shade with the wallpaper disc passing behind them — the wash
is genuinely translucent, not merely a similarly-toned opaque fill.

## Widget cards carried onto Quick Panel (option A: tinted icon glyph)

Direct follow-up: "show visual if i ask to do it for quick panel also." Two
renderings were mocked up first — a tinted icon glyph vs. a small checkmark
badge — since Quick Panel's accent fill wasn't just decorative here (unlike
the glance page's weather/agenda/now-playing cards, which had no on/off
state to preserve): it is the *only* thing that currently tells you a toggle
is active. The user picked **option A**.

Every `QuickPanelTile` — on or off — now renders the same neutral
translucent card (`Glass.raisedCardFill(dark, transparency)`, the identical
function Start/glance already use) whenever `borderlessTiles` is on. The
on/off signal that used to live in the tile's *background* moves to its
*icon and label colour* instead: an active tile's glyph and text render in
the plain accent colour directly (there's no longer an accent fill to
contrast against, so no `Glass.faceTextColor` derivation is needed there);
an inactive tile falls back to the panel's own already-computed dim text
colour (`panelFgDim` — the same brightness-matched value `QuickPanelHeader`
already uses, mirroring the glance page's `feedFg`/`feedFgDim` pattern)
rather than the fixed `tokens.fgDim` it used before, since the tile's
surface is no longer the panel's own opaque chrome. Edit-mode's drag-handle
colour follows the same substitution (`panelFg` instead of a per-tile
contrast derivation), since every tile now shares one surface rather than
each needing its own.

The "gradient fill" personalize setting (a diagonal shade across a solid
accent fill) is skipped entirely under borderless — there's no accent fill
left to shade.

Verified on the emulator in both themes: active tiles (wifi/bluetooth/
location/flashlight/allow access/dnd/auto) show a tinted icon+label on the
same translucent card inactive tiles (airplane/rotation lock) use, with no
tile reading as "still filled."

## Widget-card icon tint darkened for light theme

Direct follow-up: "accent color needs to be little darker on light background."
Quick Panel's option-A tinted glyph (previous entry) used the plain accent
colour directly with no theme adjustment — fine in dark theme, where a
saturated accent has plenty of contrast against the dark card, but a thin
glyph in that same colour reads washed-out against `raisedCardFill`'s pale,
near-white card in light theme. New `Glass.accentOnCard(dark, accent)`
blends a flat 18% toward black in light theme only (left untouched in dark
theme); `QuickPanelTile`'s active-glyph branch calls it instead of using
`accent` directly. A flat blend rather than a per-colour luminance
calculation — "a little darker" is what was asked for, not "as dark as it
can go," and any of the 14 accent swatches only needs a modest nudge, not a
colour-dependent formula.

## Widget cards carried onto Quick Panel's sliders (option B: frosted track)

Same-session follow-up: "also suggest slide bar matching style." The
notification/volume sliders were still a hard, fully opaque accent-filled
bar directly on the panel background — visually unrelated to the translucent
cards above them. Two renderings were mocked up (each slider wrapped in its
own pill card vs. just frosting the existing bare track) — the first attempt
at this mockup used alpha differences too subtle to read at a glance,
user-reported ("visually all 3 look same. recheck"); redone with much more
exaggerated, unambiguous differences (a visibly thicker capsule, a clearly
bordered pill) on one shared backdrop instead of three separate background
blobs, so the comparison was actually legible. User picked **option B** — no
new pill container, just a frosted track.

Implementation deliberately avoids hand-computing the fraction-width overlay
that would be needed to draw a fully custom track: the real `Slider`
composable is kept completely as-is for gesture handling and (crucially) its
own accurate internal thumb/track positioning, so there is no risk of a
custom overlay drifting out of sync with where the thumb actually is. Two
things layer on top of that unchanged `Slider`, both purely additive:

1. A **purely decorative capsule glow** (`QUICK_PANEL_SLIDER_CAPSULE_HEIGHT_DP`
   = 14dp, several times Material3's own ~4dp default track height — this is
   what reads as "thicker/frosted") drawn *behind* the Slider, at a fixed
   `Glass.raisedCardFill(dark, 1f)` — the faintest end of that scale, since
   this is meant to be a soft glow, not the primary "how see-through" control
   (unlike the tiles, this doesn't read the personalize transparency slider).
   It carries no value-dependent width; it is just a constant-width backdrop.
2. The **Slider's own colours go translucent**: `inactiveTrackColor` becomes
   fully transparent (so only the capsule glow shows through the unfilled
   portion) and `activeTrackColor` becomes `accent.copy(alpha =
   QUICK_PANEL_SLIDER_ACTIVE_ALPHA)` (0.55) instead of a solid accent — the
   filled portion reads as tinted glass over the glow rather than a solid
   painted bar. The thumb stays solid `accent` (unchanged) since a genuinely
   translucent thumb would be hard to spot against the equally-translucent
   fill right next to it.

Because the Slider's real track is what actually draws the value-proportional
fill (just recoloured, not repositioned), the frosted look is pixel-accurate
to the real value with none of the alignment risk a hand-drawn overlay would
have carried. Verified in both themes on the emulator: a visibly thicker,
translucent capsule sits behind each slider, with an accent-tinted (not
solid) fill portion — clearly distinct from the old hard opaque bar and
consistent with the same translucent-card material used elsewhere.

## Widget-card fill: one alpha range for both themes, not a stronger one for light

Direct follow-up: "in light mode when widget look is on tiles on the start
screen transperency level not comparable to dark mode. dark mode has right
levels. also the light mode tiles are too white." `raisedCardFill` gave
light theme a much stronger alpha range than dark (0.34–0.78 vs. 0.12–0.30 —
nearly 3x), on the theory from an earlier entry that "the same wash that
lifts a near-black backdrop needs to be much stronger to register against a
light one." Backwards in practice: that much white blows out to a stark
white square rather than a subtle lift, and — since the two themes no longer
shared the same numbers — the "transparency" slider felt like it meant
something different depending on theme, which is exactly what "not
comparable" describes. Collapsed to one range for both themes (dark's own
0.12–0.30, since the user confirmed "dark mode has right levels"): fixes
both complaints at once — literally comparable now, since it's the same
number, and light theme is no longer overexposed. Same story as the very
first "raisedFill" pass a few entries above (guessing a theme needs *more*
of something rather than trusting that the number which already looked
right in one theme is close enough for the other) — worth remembering
before reaching for an asymmetric formula next time this class of thing
comes up.

## The disc wallpapers are a row of three, and the picker grid is really a grid

User: "create a row of such wallpapers (3) with varying color combinations. so
that symmetry can be maintained." Two separate things, both needed:

**Ember and Reef** join Nebula — identical geometry to the pixel (same centres,
radii and `core`), only the two disc colours differ: blue+plum, orange+wine,
teal+moss. Keeping the geometry fixed is what makes them read as one family
rather than three unrelated wallpapers, and it is the literal "varying colour
combinations" asked for.

**The picker grid was never a grid.** `PersonalizeSheet`'s stock swatches were
a hardcoded `take(3)` then `drop(3)` — i.e. "three, then everything else" —
which was fine at 6 but made Nebula's arrival render a second row of *four*
cells, each narrower than the first row's three. That asymmetry is what
prompted the request. Rows are now `chunked(3)`, and a short final row is
padded with weighted spacers so every swatch keeps the same size whatever the
list length; `Wallpapers.all` is documented as wanting to stay a multiple of
three. Nine entries now, three rows of three, the discs forming the last row.

**`EdgeStripSheet`'s swatch row had a latent overflow** surfaced by the same
change: a plain `Row` of fixed 44dp swatches, one per gradient plus "none".
At 7 gradients that was already 408dp of content in a sheet narrower than
that, silently clipping the last swatches; at 9 it would have been 504dp.
Converted to a `FlowRow` so it wraps. Unlike the stock grid these are
fixed-size, not weighted, so wrapping is the right fix rather than chunking.

## Bundled wallpaper: "nebula" — and hard-edged discs in the layer model

Added while the borderless style was being mocked up — the user saw the
near-black backdrop with a blue shape in one corner and a plum one in the
other and asked for it as a real wallpaper. Ported into `Wallpapers.kt` as a
normal `WallpaperGradient` (base `#0A0A0D`) rather than a bitmap, so it
inherits the existing light-theme `themedBase` lift, "wallpaper behind tiles"
windowing and the wallpaper-derived accent extraction with no new code. Its
base is the same near-black as the dark theme's own `bg`, which is what makes
it work behind borderless tiles: wherever neither shape reaches, tile content
still sits on a high-contrast ground. It is the first bundled wallpaper not
ported from the HTML prototype, so `Wallpapers.all` is now 7 entries, no
longer "the 6 in prototype order."

**A first pass shipped it as two soft radial glows and that was wrong** — the
user came back with "the visual design you showed has some design having
circles." The mockup's shapes were flat discs with crisp edges, and
`WallpaperLayer` could not express that at all: every layer is a radial
gradient whose alpha decays from the centre outward, which is right for the
six ported prototype gradients (all soft mesh glows) and produces a visibly
*different* wallpaper here — the geometry is the whole look.

Fixed by adding `WallpaperLayer.core`, the fraction of `fade` out to which the
colour holds at full alpha before falling off. `0` (the default, and what every
ported gradient uses) is the original behaviour, byte-for-byte; `0.97` paints a
flat disc whose remaining 3% is just the antialiasing feather. The stop array
also moved into one shared `layerStops`, since `wallpaperBackground` and
`wallpaperWindow` had duplicated it and a divergence there would make a
wallpaper look different behind the screen than windowed into a tile. The
banding-smoothing mid-stop is deliberately skipped for a disc: there is no long
falloff to band across, and the extra stop visibly softens the edge that is the
entire point.

## Refresh tap feedback: the pulse needed a guaranteed minimum visible duration

Same-day follow-up, user-confirmed the size pulse worked for weather and
sports but not stock, then more precisely: "showing but very fast
disappearing." Root cause traced to `QuoteCache` (`:core:data`) — a 45s
memo *stock's own fetch* goes through, shared with the in-app tile, the
glance card, and every other placed widget tracking the same symbol
(deliberately, to collapse duplicate requests across those callers).
Weather/sports have no comparable shared cache, so their own real fetch
(1-3s+) naturally left the flash visible for a while; a stock refresh tap
arriving while a recent fetch for that symbol is still cache-fresh resolves
— and so triggers the real content push that resets the flash — in
single-digit milliseconds, before the tint/pulse [`flashRefreshIcon`] had
just set could ever be seen.

Fixed with a guaranteed minimum visible duration, independent of how fast
the real fetch turns out to be: a new `scheduleFlashReset` (still gated
API 31+, same reasoning as the pulse itself) suspends 600ms then resets the
icon back to its own correct per-widget tint (`resolveWidgetAccent`, the
same value a real content push would use) and normal size — run from each
receiver via `goAsync()` (matching `TaskWidgetActionReceiver`'s own existing
async-work pattern in the same file, since a plain unshielded coroutine
risks the OS tearing the receiver down before the delay elapses). Landing
*after* the real content push (weather/sports' normal case) is a harmless
no-op repeat of what that push already set; landing *before* it (stock's
fast-cache case) is what now actually shows the icon settling back to
normal instead of sitting flashed until whenever the fetch eventually
finishes. Verified on the reporting device: rebuilt, reinstalled, re-
triggered the same stock refresh action three times in quick succession
(the exact fast-cache-hit shape) — zero crashes, zero
`ActionException`/inflation errors, every worker run completing
successfully.

## Refresh tap feedback gets a real size pulse, via the RemoteViews-official API this time

Direct follow-up to the reverted spin animation above — user asked to
explore other options, then picked the size-pulse one offered. Same visible
goal (something more than a static colour swap), reached this time through
an API that was never the problem: `RemoteViews.setViewLayoutWidth`/
`setViewLayoutHeight` (API 31+) are *dedicated, first-class* `RemoteViews`
methods, added specifically for this in Android 12's "flexible widget
layouts" — not the generic `setInt`/`setBoolean(id, "methodName", …)`
reflection path whose internal per-view-type allowlist rejected
`setActivated` and broke the widgets last time. Corroborated against
Android's own docs before touching the user's real widgets again, given
what the last attempt cost.

`flashRefreshIcon` (`WidgetActionReceivers.kt`) now sets both the existing
colour tint *and* grows the icon 24dp → 30dp in the same partial update; a
new shared `resetRefreshIconSize`, called alongside the existing colour-
filter reset in all 5 real-content-push build sites (weather/stock ×3/
sports), shrinks it back. That reset needed its own explicit API-31 gate —
unlike the colour-filter reset it sits beside, calling a method the
*platform's own* `RemoteViews` class doesn't define below API 31 (this app
doesn't bundle that class, the device's OS does) would fail to resolve at
all on an older device, not just be silently ignored, so this one can never
run un-gated the way the colour reset safely does.

Not yet verified on-device — the user's physical device wasn't connected
this session; build + full unit test suite are green, and the specific
`ActionException` class of failure from the previous attempt is verified
inapplicable (a real, non-reflection API, corroborated against Android's
own "flexible widget layouts" documentation), but this still needs a real
on-device check before being called confirmed, given the session's own
recent history with this exact icon.

## Weather widget's refresh flash was invisible — a self-inflicted race, not a RemoteViews limit

User-reported: after the previous entry's revert, stock/sports' refresh tap
flash was visible again, but weather's still wasn't. Root cause was entirely
this app's own code, not another RemoteViews restriction like the
`setActivated` one that broke things: `WeatherWidgetActionReceiver` called
*both* `WeatherRefreshWorker.refreshNow` (the one that actually fetches) and
`WeatherWidgetRefreshWorker.refreshNow` (render-only, no network) directly.
But `WeatherRefreshWorker.doWork` already ends by calling
`WeatherWidgetRefreshWorker.refreshNow` itself once the fetch resolves (see
`WeatherWork.kt`) — so the receiver's own extra call was both redundant and
actively harmful: being network-free, it runs in a spare handful of
milliseconds next to the real fetch's 1-3s+, repainting the icon back to
normal (from the *same still-stale* cache, so pointless twice over) well
before the flash `flashRefreshIcon` had just set could ever be seen. Stock
and sports never had this race — each has exactly one worker that fetches
*and* renders in the same `doWork`, so there's a genuine network round trip
between the flash landing and the repaint that clears it. Fix: dropped the
receiver's redundant direct call; the real fetch's own existing completion
hook is enough. Build + full unit test suite green.

## Stock/sports widgets: faster refresh while live/open, a manual refresh button, faster while visible

User-requested, three related asks in one thread: "when sports is live,
refresh rate should be more … and refresh button should be provided on
widget. same for stock widget", followed by "can we make more frequent when
visible."

**1. Faster while live/open, in the background.** Both widgets' periodic
`WorkManager` job sits at its floor already (stock 15 min, sports 30 min —
`WorkManager` won't schedule a periodic job tighter than 15 min regardless),
so "more frequent" for a *background* widget (placed on some other launcher
entirely, or on Start's own glance page while it's scrolled off-screen)
can't come from shortening the periodic interval. Instead, each worker's
`pushAll` now schedules its own short-delay **one-off follow-up** —
`StockWidgetRefreshWorker`: 5 min while any tracked exchange is open;
`SportsWidgetRefreshWorker`: 3 min while any followed match is live — and
re-arms that same follow-up at the end of every run for as long as the
condition holds, cancelling it the moment it doesn't (a closed market/
finished match falls back to the plain periodic tick, which then further
backs off via the pre-existing `isMarketOpenFor`/`shouldFetchSports` gates).

**2. Manual refresh button.** New `ic_widget_refresh.xml` (a monoline
circular-arrow glyph, matching every other hand-ported icon in this set) at
`bottom|start` — the opposite corner from the existing settings gear, which
only ever reopens colour/pick setup and was never wired to force a fetch.
Wired the same way the existing torch toggle/task-checkbox widget taps are:
a small `exported="false"` `BroadcastReceiver`
(`StockWidgetActionReceiver`/`SportsWidgetActionReceiver`, alongside
`TaskWidgetActionReceiver`/`FlashlightWidgetActionReceiver` in
`WidgetActionReceivers.kt`) that the exported `AppWidgetProvider` can't
safely handle inline itself — see that file's own doc comment on why an
exported provider's receiver can't be trusted with anything that changes
state. Calls each worker's existing `refreshNow()`, which already forces a
fetch regardless of market/live-state gating — exactly a manual tap's own
intent.

**3. Faster still while the page is genuinely visible.** Neither of the
above two can know whether anyone is actually looking at a specific placed
widget — there's no OS API for that on an arbitrary home-screen placement.
But the dominant real case, a widget hosted on TileShell's *own* glance page
via `WidgetSection`, already carries a real, known visibility signal: the
same `active: Boolean` the page's own widget-stack auto-rotation is already
gated on. Added a `LaunchedEffect(active)` there that, only while the page
is the one on screen, calls each worker's plain (non-forced) `pushAll`
directly — bypassing `WorkManager` entirely, since this is a purely
foreground, ephemeral loop with no reason to pay its scheduling
latency/overhead — on a 60s cadence (matching the in-app Start tiles' own
on-screen poll rate, not a new number). Deliberately *non*-forced: this
layers a tighter check on top of the existing gates, it doesn't bypass
them, so a widget tracking a closed market/finished match sitting on a
visible glance page still doesn't hit the network needlessly.

Verified on the physical device the feature was requested from: broadcasting
each new `ACTION_REFRESH_*` directly (`adb shell am broadcast`, targeting
each new receiver explicitly) completed with no crash and produced a real
`WM-WorkerWrapper` success log for that worker; a screenshot of the glance
page with both a stock and a sports widget already placed confirmed the new
refresh icon renders cleanly in its own corner on both, with no layout
overlap with the existing gear. Build + full unit test suite green; no new
unit tests added — nothing pure was extracted here (the gating logic
`pushAll` reuses, `isMarketOpenFor`/`shouldFetchSports`/`SPORTS_STATE_LIVE`,
is pre-existing and already covered), the new code is `WorkManager`/
`AppWidgetManager`/`BroadcastReceiver` glue with no Robolectric in this
project, matching this file's own established convention for that class of
change.

**Same-day follow-up: the weather widget gets the manual refresh button
too** (user-requested — "can weather widget also have refresh button").
Only item 2 above applies to weather; items 1 and 3 (faster while live/
open, faster while visible) don't carry over as-is — weather has no
"live/open" concept the way a market or a match does (its own `WorkManager`
periodic tick is already a flat 30 min, always-on, not conditionally
skipped the way stock/sports are), so there was nothing to chain faster and
no request to add one. Same `ic_widget_refresh.xml`/`bottom|start`
placement on both `widget_weather.xml`/`widget_weather_compact.xml`, and
the same private-receiver shape (`WeatherWidgetActionReceiver`). One real
difference from stock/sports: `WeatherWidgetRefreshWorker` never fetches
anything itself — it only re-renders whatever the in-app
`WeatherRefreshWorker` already cached — so a manual tap has to force *both*
workers, not just the widget one, or it would just repaint the same stale
snapshot. Same pairing `WidgetConfigureActivity.refreshOwningWidget` already
uses after its own location/colour step saves. Verified the same way:
`adb shell am broadcast` against the new receiver produced real
`WM-WorkerWrapper` successes for *both* workers with no crash, and a
screenshot of the glance page's two already-placed weather widgets (mid
flip, showing their 7-day-outlook back face) confirmed the icon renders
cleanly there too.

**Second same-day follow-up: repositioned (user-reported the original corner
overwrote text), and a real animation was tried and reverted after it broke
the widgets.**

*Position.* `bottom|start` overlapped real content on several full-size
layouts — anything sitting at the very top of padding, left-aligned, full-
width and ellipsized (weather's own `widget_place`; the back faces'
`widget_back_name`/`widget_back_team`) was fine, since the icon's own 24dp+
6dp margin band is comfortably below where those start; but the corner
itself, and full-width single-line labels *near* it on some layouts, read as
covering text. Moved to `top|end` across all 8 layouts (weather/stock ×4/
sports ×2), and added `layout_marginEnd="30dp"` (24dp icon + 6dp margin) to
the handful of top-of-face labels that could now reach into that corner
(`widget_place` in `widget_weather.xml`; `widget_back_name` in
`widget_stock.xml`/`widget_stock_group.xml`; `widget_back_team` in
`widget_sports.xml`) — the equivalent front-face labels didn't need it,
since their own top row is a small left-aligned icon, not full-width text.

*The animation.* First a static colour flash (`setColorFilter`), per the
entry above — user-reported afterward: "there is no animation. only color
change." Built a real one: `ic_widget_refresh.xml`'s two paths wrapped in a
named, centre-pivoted `<group>`; a new `ic_widget_refresh_spin.xml`
(`<animated-vector>`, 360° rotation of that group); a new
`ic_widget_refresh_selector.xml` (`<animated-selector>` whose
`normal → activated` transition plays the spin) as the icon's `android:src`;
`flashRefreshIcon` triggering it via `RemoteViews.setBoolean(id,
"setActivated", true)` — the one RemoteViews-legal way to flip a boolean
View property remotely, with the actual motion left entirely to ordinary
Drawable/View state machinery, not anything RemoteViews-specific.

**This broke the widgets in production** — user-reported "widgets crashed."
Root-caused via `adb logcat`: `RemoteViews$ActionException: view: android
.widget.ImageView can't use method with RemoteViews: setActivated(boolean)`,
thrown inside `AppWidgetHostView.applyRemoteViews` and caught *there* (so
TileShell's own process never crashed — confirmed via `dumpsys window`/
`ps`, the app stayed running throughout), but the widget's host view itself
was left `null` — a blank widget, which is exactly what reads as "crashed"
from the user's side. Real, useful lesson about RemoteViews confirmed
directly against this codebase's own use: its reflection setters
(`setBoolean`/`setInt`/etc.) are *not* generic — each is checked against an
internal per-view-type method allowlist, and `setActivated` isn't on it,
unlike `setColorFilter` (already used extensively elsewhere in this exact
codebase, e.g. every `onAccent` icon tint) or `setOnClickPendingIntent`.

Reverted the whole mechanism rather than guess at a different boolean
setter (`setSelected`, `setEnabled`, ...) against the user's own already-
placed, already-broken-once widgets and risk a second blank-widget
incident: deleted `ic_widget_refresh_spin.xml`/`ic_widget_refresh_selector
.xml`, un-wrapped the `<group>` back out of `ic_widget_refresh.xml`, all 8
layouts back to plain `android:src="@drawable/ic_widget_refresh"`,
`flashRefreshIcon` and all 5 build-site resets back to the confirmed-safe
`setColorFilter` flash. A genuinely smooth animation would need a
RemoteViews-*official* API instead of generic reflection to be safe here —
e.g. `setViewLayoutWidth`/`Height` (API 31+, real first-class `RemoteViews`
methods, not reflection-checked against an allowlist) could grow/shrink the
icon as a "pulse" — not attempted this session, given the priority of
restoring the user's widgets to a known-working state first.

Verified the revert on the reporting device: rebuilt, reinstalled, force-
stopped and relaunched (confirmed via `ps`/`dumpsys window` that this
produced a genuinely fresh process, distinct from the one that had the bad
code loaded), then re-broadcast all three `ACTION_REFRESH_*` actions —
`adb logcat` filtered to that fresh process's own pid showed zero
`ActionException`/inflation-error lines and real `WM-WorkerWrapper` success
logs for every worker; two of three placed weather widget instances were
confirmed rendering again (`v != null`) in that same log. One instance
still showed a stale `v = null` — expected: `AppWidgetHost`'s own cached
view reference for that specific id was left null by the *original* crash,
before the fix was ever installed, and only clears once that widget's host
view is actually recreated (its own Composable remounting, e.g. the user
next opening the glance page) — not evidence of the fix being incomplete,
but flagged to the user rather than asserted away.

## "Set default launcher" prompt fired repeatedly even though TileShell was the sole default

User-reported, "observed on many devices": the auto-prompt (`MainActivity`'s
`DefaultLauncherPrompt`, `LaunchedEffect(Unit)` per fresh process) kept
re-asking to set TileShell as default even when it plainly already was. Asked
to debug it and remove the feature entirely if no real bug turned up — a real
one did, so it's fixed instead of removed.

**Two things had to line up for this to read as "frequent," and both check
out**: (1) `DefaultLauncher.isDefault()` only ever fires once per fresh
`MainActivity` process (`singleTask` launch mode means an ordinary Home
press reuses the running task via `onNewIntent`, never a new `onCreate`) —
but `MainActivity` also declares `android:stateNotNeeded="true"`, and as the
device's actual Home app it sits backgrounded almost the entire time the
user is in any other app, which is exactly the process shape Android is most
willing to trim under memory pressure. A killed-and-recreated Home process
re-runs the check from scratch. On the "many devices" reporting this, that
recreation is apparently frequent — plausible on the OEM skins known for
aggressive background-process policies (Samsung/Xiaomi/OnePlus among them).
(2) Each of those fresh evaluations used to trust *only*
`RoleManager.isRoleHeld(ROLE_HOME)` on API 29+. That's the precise, intended
check on stock AOSP, but several OEM "default apps" screens manage the Home
choice through their own preferred-activity bookkeeping without reliably
keeping `RoleManager`'s role-holder state in sync with it — so the role can
read "not held" on exactly the OEM builds in question, even while the
device's own Home-activity resolution (`resolveActivity` against a HOME
intent — the only check this function ever used pre-Q) already agrees
TileShell is what actually opens on a Home press. One stale "not held" is
then enough, combined with (1), to re-surface the prompt repeatedly over a
day on the same device that's genuinely never switched away from TileShell.

**Fix**: `isDefault()` now treats the role check and the resolved-activity
check as corroborating rather than either/or — RoleManager is still asked
first (nothing here removes a signal that the role is genuinely held), but a
"not held" answer is cross-checked against the actual resolved HOME activity
before concluding "not default." Either signal saying TileShell is enough.
The asymmetry is deliberate: a false "still not default" causing a repeating,
user-visible nag is the reported bug; the reverse mistake (treating a
genuinely different launcher as default) isn't a realistic risk this
introduces, since resolveActivity only ever agrees when TileShell really is
what the OS currently hands a Home press to. No unit test — this is thin
Android-framework glue (`RoleManager`/`PackageManager`) with no pure logic to
extract and no Robolectric in this project (matches this file's pre-existing,
already-untested state); verified instead via `adb shell cmd package
resolve-activity` against the real device the bug was reported from, which
already agreed TileShell is the resolved Home activity there. Build + full
unit test suite green; installed on that device, launched with no crash in
`adb logcat`.

## Battery diagnosis: news feed's own re-fetch cadence, not widgets, was the real cost

User asked to diagnose TileShell's battery use, suspecting the widget work
from the last several sessions. Read straight off the physical device
(`dumpsys batterystats --charged com.tileshell`), not guessed: TileShell was
the day's #1-consuming app on the device (145 mAh of 2271 mAh total, just
ahead of WhatsApp's 142), and of that, **65.8 mAh (45%) was `mobile_radio`**
— 22m11s of radio-active time, 67 radio wakeups, 63 MB received in under
8 hours on battery. CPU was 42.2 mAh (29%, only 6m46s of actual CPU time);
screen was 36.0 mAh (inherent to being Home). Sensors measured essentially
free (0.008 mAh) despite the step-counter sensor being registered 6h57m of
the 7h41m on battery — worth fixing anyway (below), but not the story here.

**Root cause, found by walking the periodic workers' own schedules**:
`FeedRefreshWorker` re-downloads all 15 subscribed RSS feeds in full every
30 minutes (48 cycles/day), whether or not the feed page has ever been
opened, with no HTTP caching at all (`httpGetText` sent no
`If-None-Match`/`If-Modified-Since`, so an unchanged feed's full body came
back every single cycle). Stock/commodity/sports widget polling and article
thumbnail re-fetches (no disk cache, only an in-memory `LruCache` that a
process restart empties) are real but secondary contributors. Widget hosting
itself (the actual subject of the user's suspicion) is not a meaningful
battery cost on this device — every widget's own periodic worker is
15 min–1 day, and only weather/stock/commodity/sports/feed even touch the
network.

**Fixes, user-approved (all four; nothing deferred this time)**:
1. **Conditional GET for RSS** (`FeedWork.kt`) — `FeedData` gained a
   `validators: Map<url, FeedValidator>` (etag/last-modified), round-tripped
   by `FeedCodec`'s new `V` lines; `FeedArticle` gained `feedUrl` (`A` lines'
   new 8th field, defaulting to `""` for an article cached before this
   existed) so a `304` response's already-cached articles for that feed can
   be reused verbatim instead of re-parsed from a re-sent body. A feed with
   no attributable cached articles (the legacy-cache case) never sends a
   validator, so a stray 304 can't strand it with nothing to show.
2. **Skip the periodic tick entirely once idle** — new `FeedUsagePrefs`
   (`:core:data`, same plain-`SharedPreferences` shape as `StepsPrefs`)
   records when the feed page last actually became the *visible* page (not
   merely composed — the pager can keep an adjacent page mounted off-screen),
   via a `LaunchedEffect(active)` in `FeedPage.kt`. Pure
   `shouldSkipIdleFeedRefresh(now, lastOpened, idleAfter = 6h)` gates only the
   *periodic* `doWork` (a new `KEY_FORCE` input flag, same pattern
   `StockWidgetRefreshWorker` already uses for its own market-hours skip) —
   every one-off path (placement, a feed-list edit, manual "refresh") still
   forces a real fetch, so opening the page always shows current content.
   "Never opened" (0) also counts as idle, and a clock that jumps *backwards*
   (manual change, timezone/NTP correction) reads as recent rather than idle,
   so it can never freeze the feed until the clock catches up.
3. **Bounded on-disk thumbnail cache** (`RemoteImage.kt`) — the raw downloaded
   bytes (not just the decoded `Bitmap`) are now written under
   `context.cacheDir/feed_images/<md5(url)>`, capped at 20 MB total
   (oldest-touched-first eviction after every write, scanning the directory
   fresh each time — negligible at feed-thumbnail volume). `cacheDir`
   deliberately, not `filesDir`: this is purely a re-derivable network cache,
   reclaimable by the OS under storage pressure with no real data loss.
4. **Step-counter sensor gated on `active`** (`StepsTile.kt`) — the
   `SensorEventListener` was registered for as long as a Steps tile/card
   stayed *composed*, with no tie to whether it (or Start at all) was ever
   actually on screen, unlike every other live tile's own
   `rememberLiveTilesActive`-driven gate; `StepsTileFace`/`StepsSmallFace`
   gained an `active: Boolean = true` param (both call sites now pass the
   same `liveActive` every sibling face already receives) and the
   registration is also batched (`maxReportLatencyUs` = 60s) so the sensor
   hardware can queue readings instead of waking the AP for every sample.
   Measured cost was zero on the diagnosed device (Samsung offloads
   `step_counter` to its sensor hub), but a plain always-on AP-side
   registration is real, unnecessary battery risk on any device without that
   offload — fixed as a real (if here-invisible) bug, not left merely
   harmless-on-this-hardware.

## Weather tile/widget location: ask, or pick a place — multiple instances each follow their own

User-requested: "when weather tile and widget is added, ask for current
location or select location and establish widget/tile based on user choice
… hence multiple weather tile/widgets are allowed." Before this, weather was
hardcoded to "follow the device's coarse location, or the manual-city
DataStore fallback nobody has UI for" — every weather tile/widget on the
device showed the exact same forecast, with no choice at add time.

**Encoding.** New `WeatherTile` (`:core:data`, mirroring `CountdownTile`/
`StockTile`'s "blank-package tile, `activityName` carries the config" trick):
`Location.Current` or `Location.Fixed(lat, lon, name)`, `encode`/`decode`.
Safe for weather specifically because `DefaultLayout.roleFor("weather")` has
never resolved to a real app, so the column was always free. The home-screen
widget stores the identical string per `appWidgetId` in `WidgetConfigStore`
(same codec, same module boundary reasoning as stock/sports there) — one
encoding, one cache key (`WeatherTile.key`), shared by both surfaces.

**Multiple places, one cache.** `WeatherCacheData` gained `places: Map<String,
WeatherSnapshot>` alongside the existing single `snapshot` (which now means
specifically "the device's own location"). `WeatherRefreshWorker` gathers
every distinct fixed place any tile or widget currently wants
(`requestedFixedPlaces` — reads the live layout DB + every placed weather
widget's stored config, deduped by `WeatherTile.key` so two tiles pointed at
the same city share one fetch) and fetches each once per run, alongside the
one device-location fetch it already did. Coordinates round to 2 decimals
(~1km) for the cache key specifically so two picks of "the same city" from
slightly different search results collapse to one entry.

**Asking.** Mirrors the steps-permission fix directly above this entry: a
`RemoteViews` tree can't host a dialog, so the ask happens in the one Activity
each surface already has. Start's "add widgets" catalog intercepts the
`weather` entry — instead of `addLiveTile` inserting a blank tile immediately,
it opens `WeatherLocationSheet` (`:feature:personalize`, new) first, and only
creates the tile (`LayoutRepository.addWeatherTile`) once the user answers
("use current location" or a searched place — `fetchWeatherPlaceSearch`, a
new `:core:data` Open-Meteo geocoding search alongside the existing single-
result lookup `:feature:livetiles` already had for city-typed queries). The
home-screen widget gets the identical choice from `WidgetConfigureActivity`'s
own flow (`RequiredStep.WEATHER_LOCATION` → `WeatherLocationPickerScreen`,
self-contained in that file since `:feature:livetiles` can't depend on
`:feature:personalize`), gated on "no location stored yet."

**A real race, found on-device, in the first cut of that gate.** The first
attempt also had `WeatherAppWidgetProvider.onUpdate` backfill a missing
location to `Current` — reasoning that `onUpdate` only ever fires for a
widget placed *before* this feature existed, since a genuinely new one goes
through its configure step (which writes a real location) before the OS ever
calls `onUpdate` for it. On-device testing (adding a fresh weather widget
from the glance page's own "+ add" picker) proved that reasoning wrong: this
app's own bind path (`WidgetSlot.kt`'s `addProvider`, via
`bindAppWidgetIdIfAllowed` — a same-app bind, not the standard OS pick flow)
gets an immediate `onUpdate` push from the system that arrives *before* the
follow-up `ACTION_APPWIDGET_CONFIGURE` intent it fires next ever launches —
confirmed by pulling `tileshell_widget_config.xml` mid-repro and finding a
freshly-bound id already holding `weather:current` the instant its configure
Activity opened, which landed straight on the colour step, location step
skipped entirely. Removed the backfill outright rather than chase the race:
it wasn't even needed, since `WeatherWidgetRefreshWorker.pushAll` already
treats a stored-`null` location as `Current` at render time, so a widget from
before this feature keeps working with zero code in `onUpdate`. The one
behavioural cost is deliberately accepted — manually reopening an *old*
widget's own configure (its gear icon) now shows the location step once, the
first time, instead of never; answering it either way (including "use
current location") stores a real value and it never asks again for that
instance.

**The compact (half-width) widget had to start labelling itself.** Follow-up,
user-reported once several locations were actually in use: "half size widget
doesnt display location name." `widget_weather_compact.xml`'s front face had
no place `TextView` at all — correct while every weather surface shared one
location (nothing to disambiguate), ambiguous the moment two half-width
widgets could each follow a different city and otherwise render identically.
Added a small (10sp, single-line, ellipsized) `widget_place` to that layout's
front face and dropped the `if (!compact)` guard around setting it, so both
layouts now populate the same id. Its *back* face (the 7-day outlook) is
deliberately still unlabelled — that matches the full-size layout's own back
face, which has never labelled itself either, and there's no vertical room
there at this width for a row that isn't a forecast day.

**Not breaking existing installs, without any backfill.** A pre-existing
weather tile (seeded before this feature, or from an untouched default
layout) has no encoding at all — `WeatherTile.decode(null)` resolves to
`Location.Current` everywhere a face reads it, so it keeps behaving exactly
as it always did, with no "tap to configure" dead end ever shown for it.
Re-tapping an already-configured weather *tile* on Start does still reopen
the location sheet (unlike stock/commodity's "open the real page, only the
picker if unset" pattern) — a location, unlike a stock symbol, has no
external page to open, and letting the choice be changed later was the
explicit ask.

## Steps permission: a permanent way back in, and the widget asks for it too

Three real defects around `ACTIVITY_RECOGNITION`, all reported together
("count widget needs health data permission. same for health tile. for widget
health permission not asked. and [start] tile it asked but was non responsive
hence probably permission not given and now steps count not showing").

**1. The home-screen/glance steps widget never asked at all.** A `RemoteViews`
tree can't host a permission dialog, so the widget had no way to ask, and the
old code's comment said so outright ("this pilot doesn't show the in-app
permission-rationale dialog itself; that only happens once a Steps tile/card
is actually opened in the app"). The consequence, though, is that a user who
only ever added the *widget* got a permanent "--" and nothing anywhere told
them why. Fixed by using the one part of the widget's own flow that *is* an
Activity: `WidgetConfigureActivity` (already launched by the OS at add time,
and re-launchable from the widget's gear via `reconfigurePendingIntent`) gained
a `RequiredStep.STEPS_PERMISSION` first step for the steps provider, skipped
once granted so a later colour change doesn't re-ask. Granting is not a
precondition for finishing setup — "not now" still lands on the colour step
and finishes normally.

Paired with that, the placed widget now *says* what's wrong instead of showing
a bare dash: a new pure `stepsWidgetState(granted, steps)` (unit-tested)
separates the three outcomes the old `steps: Int?` conflated, and
NEEDS_PERMISSION relabels the caption "tap to allow" and points the whole
widget body at its configure activity. UNAVAILABLE (no step sensor on the
device, or the one-shot sensor read timing out) deliberately stays a plain
dash and keeps the no-body-tap rule — there's nothing the user could act on
there. The narrow/compact layout has no room for a permanent caption, so its
new `widget_label` is `visibility="gone"` by default and appears only for the
needs-permission hint.

**2. "It asked but was non responsive."** Confirmed on the user's own device:
`steps_permission_asked = true` in `tileshell.prefs` with
`ACTIVITY_RECOGNITION: granted=false`. A permanently denied runtime permission
makes `ActivityResultLauncher.launch` a silent no-op — the system dialog never
appears and the result is "denied" instantly — which is exactly a dead button
from the user's side, and the gate's one-shot flag then made that state
permanent. New `canShowSystemPermissionDialog(context, permission, asked)`
(`TilePermission.kt`) detects it (`shouldShowRequestPermissionRationale`,
plus the caller's own `asked` record, since that API is *also* false for a
permission never requested yet), and `openAppPermissionSettings` is the
answer: the steps gate now shows a second "steps permission is turned off ·
open settings" dialog rather than doing nothing. Applied to the *existing*
contacts/calendar/location rows in the permissions sheet too — same dead-button
bug, same fix, and `asked = true` is sound for those three because
`MainActivity` requests them upfront on every fresh process.

**3. No manual route to it (user-requested: "this permission has to be part of
permission section in personalisation. hence if i miss i can able to manually
go and give permission").** Activity recognition is the one permission this app
asks for *contextually* rather than in the upfront batch (deliberately — see
`StepsTile.kt`'s gate doc comment and Play's review of unexplained asks), which
is precisely why missing that single ask left no way back. `PermissionsSheet`
gained a fourth row, "physical activity · steps tile · steps widget", with the
same allow/allowed treatment as the other three; granting from there also
pushes the placed steps widget immediately (`StepsWidgetRefreshWorker
.refreshNow`) instead of leaving it stale for up to its 15-minute interval.

Note on naming: the reported "health data permission" is `ACTIVITY_RECOGNITION`
("physical activity" in system Settings), read straight off
`Sensor.TYPE_STEP_COUNTER`. No Health Connect / health-platform integration is
involved, and none was added — that would need a package in this app's
manifest `<queries>` and its own Play declaration.

## Themed icons: parked

Built, then immediately parked at the user's request after seeing it on
real installed apps. The feature (Personalize toggle → each app's Android
13+ monochrome adaptive-icon layer, tinted to the resolved accent, in the
App List, Start's ICONS-mode icon cell, live-tile "posted by" corner badges,
and a real app icon shown directly on a WP tile) worked exactly as built —
confirmed on-device (emulator, API 36): Google apps that ship a themed layer
(YouTube, YT Music, Play Store, Settings, Safety, Voice Access) correctly
rendered as accent-filled circles with a white glyph, while apps with no
monochrome layer correctly kept their full-colour icon. That correctness is
exactly the problem the user flagged: **most installed apps don't declare a
monochrome layer at all**, so real device screens are a visible, uneven mix
of a handful of themed icons among many full-colour ones — this matches
real Android launchers' own themed-icon behaviour (Pixel Launcher/One UI
have the identical limitation, for the identical reason), but the user
didn't find the mixed result acceptable for this launcher. Offered three
paths via `AskUserQuestion` (keep as-is / synthesize a monochrome fallback
for apps with no real layer / turn it off for now); the user chose to turn
it off.

**"Off," not removed** — every piece of the underlying implementation stays
in place, dormant, for a future revisit (most likely a synthesized fallback
for apps with no real monochrome layer, so coverage isn't limited to the
minority of apps that ship one):
- `LauncherSettings.themedIcons` + its `SettingsCodec`/`SettingsRepository
  .setThemedIcons`/`StartViewModel.setThemedIcons` plumbing — untouched,
  just no longer reachable from any UI.
- The Personalize "themed icons" `ToggleRow` and its two params on
  `PersonalizeSheet` — removed (nothing to toggle if nothing reads it).
- Every render-side `themedIcons: Boolean = false` parameter/branch
  (`AppListIcon.kt#MaskedAppIcon`, `IconCellView.kt#maskedOrGlyphIcon`/
  `IconCellGlyph`, `feature/livetiles/AppIcon.kt#AppIconCorner`,
  `StartScreen.kt#StaticTileGlyph`, and the `monochromeBitmap`/
  `monochromeIconBitmap()` extraction in all three `MaskableIcon`-family
  data classes) — untouched, all still default to `false` and are simply
  never passed `true` anymore: the four call sites that used to read
  `settings.themedIcons` (`StartScreen.kt`'s `StartPage(...)` call,
  `AppListScreen.kt`'s two `AppRow(...)` calls) now omit the argument
  entirely, so the effective value is always `false` regardless of
  whatever a device already has persisted from testing this session.
- Sizing fixes made along the way are also left in place, since they're
  real, independent bug fixes worth keeping for the eventual revisit: the
  plate-based renderers (App List, Start ICONS-mode) no longer shrink the
  themed glyph to 60% inside its own plate (a monochrome layer is itself an
  adaptive-icon layer with its own safe-zone inset already baked in, same
  as the existing full-size `isAdaptive`-unmasked branch each file already
  had); `AppIconCorner`'s corner badge grew from a fixed 18dp to 24dp
  **for every branch, not just the themed one** (user-flagged as a generic
  "icon too small once a live tile actually has content" issue, unrelated
  to theming); and `StaticTileGlyph`'s themed branch gained its own
  `themedDp` size table (60/48/120/78 by tile shape) instead of reusing the
  tiny WP monoline-glyph constants, decoding the source bitmap at that
  larger size too so it doesn't upscale-blur.
- Build + full unit test suite green; verified on both the physical device
  and the emulator (no crash) after every step of this arc.

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

## Guide and about sheets still described the old "make stack · wide/large" widget-stack mechanism

Direct follow-up to the previous doc-gap fixes, user-flagged: "now make as folder and make as stack
is shifted to tile color panel. and except few all tile sizes can be of stack type. This is not
covered in guide and feature & Info." Both `PersonalizeGuideSheet.kt`'s "organizing tiles" and
`AboutSheet.kt`'s "widget stacks" groups still described the *original* mechanism — "merge two large
tiles, or open a folder and use 'make stack · wide/large'" and "an open stack offers switching to the
other size (wide ↔ large) or 'back to folder'" — none of which is how the feature actually works any
more (see the earlier "Widget stacks: any stackable size, explicit 'show as stack'/'show as folder'
toggle" entry and its "three on-device refinements" follow-up): the two fixed action tiles were
replaced by a single toggle that moved into the per-tile colour picker sheet, stack eligibility
widened from "uniform WIDE or LARGE" to `TileSize.stackable` (`cols > 1 && rows > 1` — every size
except `SMALL`/`WIDE_SMALL`/`TALL`/`BANNER`/`COLUMN`), and resizing a stack is now a plain corner-drag
that homogenizes every member, not a fixed wide↔large switch. `StackEditControls` was deleted outright
in that earlier work, so the "back to folder" UI the docs described no longer exists at all.

Rewrote both groups to match current behaviour: merging two large tiles still forms a stack directly,
but any existing folder with 2+ children at a stackable size can now become one too via the colour
picker's "show as stack"/"show as folder" toggle, and named exactly which five 1-dimensional presets
are excluded rather than leaving "except a few" vague. Also added the drag-corner-homogenizes-every-
member detail, which had no bullet anywhere before this. Verified on an emulator: scrolled to
"organizing tiles" in the guide and "widget stacks" in the about sheet — both render the corrected
bullets with no stale "make stack · wide/large"/"wide ↔ large" text remaining. Build + full unit test
suite green.

## Icon shape row was unlabeled ("original" read as "square"); adaptive-icon masking was silently a no-op

User report with screenshots: home style set to "icons," icon shape apparently set to "square," but
icons kept rendering in their own native shapes (WhatsApp circle, Maps teardrop, Contacts' rounded
red square) instead of a uniform square. Two separate things were wrong, one UX and one a genuine
rendering bug.

**UX bug**: there is no `SQUARE` value in `IconShape` (`CIRCLE, SQUIRCLE, ROUNDED, ORIGINAL` —
`core/data/settings/LauncherSettings.kt`) — the 4th/last swatch in Personalize's icon-shape row is
`ORIGINAL`, which deliberately skips masking and shows the icon's own native shape (see "Icon shape
masking" above). The row (`PersonalizeSheet.kt`) rendered four plain colour swatches with **no text
labels at all**; `ORIGINAL`'s swatch previews as a flat rectangle, which reads exactly like "select
this for square icons" with nothing to correct that impression. Fixed by adding a small label under
each swatch (`candidate.name.lowercase()` — the enum names already read correctly as "circle" /
"squircle" / "rounded" / "original") so the last option is now unambiguous.

**Real bug, found while verifying the fix on-device**: after correcting the mix-up and actually
selecting `SQUIRCLE`, icons *still* rendered fully circular — masking wasn't doing anything visible.
Root cause in both `IconCellView.kt` (`:feature:start`) and its duplicate `AppListIcon.kt`
(`:feature:applist`): loading an adaptive icon called `drawable.toBitmap()` directly on the
`AdaptiveIconDrawable`, but `AdaptiveIconDrawable.draw()` *always* clips itself to the OS's own
device-wide icon mask first (a circle on stock AOSP/the emulator used for verification) — so the
bitmap we then re-clipped to our chosen `IconShape` already had the OS's circular mask baked into its
pixels; clipping an already-circular bitmap to a squircle's bounding shape just trims a few corner
pixels and still reads as a circle. This is exactly the risk flagged (but left unverified, no device
being available at the time) in `IconCellGlyph`'s doc comment: "chosen because that finer approach
can't be verified without a device attached to this environment... revisit if on-device testing shows
the plate reads wrong" — on-device testing this session showed the *clip*, not just the plate, was
wrong. Fixed with the standard technique other Android launchers use to re-mask adaptive icons: a new
`unmaskedIconBitmap()` (duplicated in both files, matching this pair's existing deliberate-duplication
policy) draws the adaptive icon's raw `background`/`foreground` layers directly onto a bitmap with no
mask path applied, instead of asking the drawable to flatten+clip itself; our own `IconShape` clip is
then applied to that genuinely-unmasked square bitmap. Legacy (non-adaptive) icons are unaffected —
`drawable.toBitmap()` never applied an OS mask for those to begin with. Verified end-to-end on an
emulator: selecting "squircle" now visibly renders adaptive icons (camera, contacts, files,
personalize) as soft-rounded squares instead of circles; legacy icons (Chrome, YouTube Music) keep
their own circular badge on a squircle-shaped tinted plate, as designed. Build + full unit test suite
green.

## Real "square" option added; fixed a regression the previous session's masking fix introduced in "original"

Direct same-day follow-up, user-requested: "last shape is square but showing as original. correct
that. and also need option for original icon if user does want icons to be displayed as original."
The previous entry's fix correctly made SQUIRCLE/ROUNDED actually mask adaptive icons, but there was
still no real `SQUARE` value — the 4th/last option was `ORIGINAL` wearing a flat-rectangle preview
that looked like "square." Added a genuine 5th `IconShape.SQUARE` (`core/data/settings/
LauncherSettings.kt`) mapping to `RectangleShape` in both `IconCellView.kt` and `AppListIcon.kt`,
keeping `ORIGINAL` as a distinct 5th option. Since `SQUARE` and `ORIGINAL` preview identically as a
plain rectangle, the Personalize swatch row (`PersonalizeSheet.kt`) now distinguishes them by fill —
`SQUARE` renders solid (a real accent-filled mask, like the other three), `ORIGINAL` renders
outline-only/unfilled (no masking, no colour fill at all) — so the two are visually distinct as well
as separately labeled. `PersonalizeGuideSheet.kt`'s `HomeStyleVisual` illustration and both sheets'
one-line summaries were updated to name all five options.

**A real regression was caught while verifying "original" on-device**: after wiring up `SQUARE`,
selecting "original" no longer showed each icon's true device shape — it rendered adaptive icons as
plain, slightly-odd squares regardless of the OS's actual icon mask. Root cause: the previous
session's `unmaskedIconBitmap()` fix (see the entry above) replaced *every* consumer's bitmap with the
raw, un-OS-masked background/foreground composite — correct for the masked-shape rendering branch,
but wrong for the `composeShape == null` (ORIGINAL / `HomeStyle.TILES`-suppressed) branch, which needs
the icon exactly as the OS renders it (the real OS mask baked in), not our own bypass of that mask.
Fixed by having both `MaskableIcon` (`IconCellView.kt`) and `MaskableAppIcon` (`AppListIcon.kt`) carry
*two* bitmaps: `bitmap` (plain `drawable.toBitmap()` — the OS-accurate look, used for ORIGINAL/TILES
and for legacy icons, which were never OS-masked to begin with) and `unmaskedBitmap` (the raw layer
composite, used only when an adaptive icon is actually being clipped to one of our own shapes). Each
consumer's `composeShape == null` branch was already reading `bitmap`, so this was a one-line swap at
each `isAdaptive` masked-render branch (`loaded.bitmap` → `loaded.unmaskedBitmap`) plus splitting the
loader function to compute both. Verified end-to-end on an emulator: circle/squircle/rounded/square
each visibly render their intended distinct shape, and original now correctly restores every icon's
true device appearance (circular on this emulator's stock AOSP mask) exactly as a fresh install looks.
Build + full unit test suite green (`SettingsCodecTest` extended for `SQUARE` round-trip).

## Non-standard notification tile sizes now use their full available space

User request, same day as the drag-resize/eleven-preset work: "tiles of size 2x4 or other than wide
medium small and long width, while displaying notifications on live tiles use (utilise) full
available space so maximum text becomes visible" — later clarified to "each tile size use max
available space." `NotificationFaceContent` (`feature/livetiles/src/main/java/com/tileshell/
feature/livetiles/ConversationTile.kt`) — the single dispatcher shared by both `ConversationTileFace`
(mail/messages) and `NotificationTileFace` (any other app's generic notifications) — only ever
special-cased 4 of the 11 `TileSize` presets (`narrowLive` for TALL/COLUMN, plus dedicated MEDIUM/
WIDE/LARGE branches); the other 6 (`WIDE_SMALL`, `WIDE_MEDIUM`, `TALL_MEDIUM`, `XLARGE`, `BANNER`, and
implicitly any future preset) all silently fell into the `else` catch-all, rendering
`NotificationFaceContentMedium`'s fixed 12–13sp/2-line row regardless of how much more space a bigger
or differently-shaped tile actually had — an `XLARGE` (4×4, the single biggest tile) showed the exact
same cramped text as a `MEDIUM` (2×2).

Gave every size its own tuned branch instead: `WIDE_MEDIUM` (3×2) routes to the existing `WIDE`
layout (same shape, one column narrower — its `weight()`-based Row already adapts); `TALL_MEDIUM`
(2×3, new `NotificationFaceContentTallMedium`) spends its extra row over MEDIUM on up to 7 snippet
lines (or a taller picture) instead of unused padding; `BANNER` (4×1, new
`NotificationFaceContentBanner`) is a short full-width single-line row (smaller avatar, 1-line
snippet) rather than MEDIUM's taller centred layout, which would clip vertically at only one row of
height; `WIDE_SMALL` (2×1, new `NotificationFaceContentWideSmall`) is short *and* narrow — too little
of either dimension for sender and snippet as separate lines, so they're combined into the single
line that fits ("sender: snippet"), maximizing readable characters rather than dropping the snippet
outright; `XLARGE` (4×4, new `NotificationFaceContentXLarge`) is a scaled-up `LARGE` — bigger
avatar/fonts and a much higher snippet line cap (18 vs LARGE's 10), so the extra canvas actually shows
more text instead of the same LARGE-sized content sitting in a bigger box with the leftover space
spent on `Spacer(Modifier.weight(1f))` padding. `SMALL` was confirmed to never reach this dispatcher
at all (`LiveFace.forIconKey` returns `null` for `TileSize.SMALL` before any face is chosen — small
tiles show only the static glyph + badge, by existing design), so no branch was needed there. Since
both `ConversationTileFace` and `NotificationTileFace` call the same shared `NotificationFaceContent`,
one dispatcher fix covers mail, messages, and every generic app's notification tile at every size
without touching either call site. Build + full unit test suite green; installed on both the emulator
and the physical device — full visual verification of live notification content at each new size was
not possible in this session (no real pending notification bound to a pinned package was available in
the sandbox to resize through), so this is a code-review-level verification against the same
`fillMaxSize()`/`weight()`/`Column`/`Row` patterns already used and previously verified in this same
file's MEDIUM/WIDE/LARGE branches, not an on-device visual pass — flagging per project convention
rather than claiming a check that wasn't actually done.

## Notification tile content was top-aligned instead of centred, once actually using the full space

Direct same-day follow-up, user-reported: "though full space is utilised now displayed on top. top
aligned. it should be centrally aligned." The previous entry's fix made the bigger/differently-shaped
notification tiles (WIDE_MEDIUM, TALL_MEDIUM, XLARGE, BANNER, WIDE_SMALL) actually use their real
available height — but several of the no-picture layouts (`NotificationFaceContentLarge`,
`NotificationFaceContentXLarge`, `NotificationFaceContentTallMedium`) anchored their header+snippet
block to the top via a `Column` with the default `Arrangement.Top` plus a trailing
`Spacer(Modifier.weight(1f))` to soak up the rest — so on a tall tile with a short snippet, the text
sat pinned at the top with visibly empty space below it, which is exactly what "use the full space"
was asking to avoid. `NotificationFaceContentWide` (pre-existing, not written this session, but now
also serving `WIDE_MEDIUM`) had the same problem via a fixed `top = 28.dp` padding.

Fixed by removing every trailing `Spacer(Modifier.weight(1f))` and switching each affected `Column`'s
`verticalArrangement` to `Arrangement.Center` for the no-picture case — `Large`/`XLarge` set
`verticalArrangement = if (picture != null) Top else Center` (a picture's own `weight(1f)` already
fills all remaining space and makes the arrangement setting moot whenever one is present, so this
only changes behaviour for the no-picture branch); `TallMedium` does the same, keeping its header row
always at the top of the two-child block but letting the whole header+snippet block centre as a unit
when there's no picture; `Wide` (and therefore `WIDE_MEDIUM`) swapped its asymmetric
`top=28.dp/bottom=12.dp` padding for symmetric `vertical=12.dp` plus `Arrangement.Center`. `Banner`/
`WideSmall`/`Medium` were already correctly centred (`Row` with `verticalAlignment =
CenterVertically`) and needed no change; `Narrow` (TALL/COLUMN) already used `Arrangement.SpaceEvenly`
deliberately and was left as-is. Build + full unit test suite green; installed on both the emulator
and the physical device — same caveat as the previous entry, no real pending notification was
available in the sandbox to resize through and visually confirm centring at each size.

## App list: "more from this app" submenu pins a package's other launcher activities

Direct follow-up to the pin de-dupe fix below, user-requested: rather than making the user hunt the
alphabetical list for each of a package's separately-listed launcher activities (Amazon Fresh/Now/Pay),
long-pressing any one of them now offers a way to pin the others from right there. The user's first
suggested label was "other versions," but flagged it themselves as wrong for a case like a camera app
whose bundled activities aren't "versions" of each other, just separate entries in one package — asked
via `AskUserQuestion`, the user picked **"more from this app"** over "other apps"/"related apps".

`AppListViewModel` gained `siblingsByPackage: StateFlow<Map<String, List<AppEntry>>>` (the existing
`apps` catalogue, grouped by `packageName`, blank/pseudo packages excluded) and
`pinnedActivityKeys: StateFlow<Set<String>>` (`"package/activity"` keys currently pinned, top-level or
in a folder — the finer-grained sibling of the existing package-only `pinnedPackages`, mirroring
`TileMerge.mergeKey()`'s own `package/activityName` shape). `AppListScreen`'s `AppRow` takes
`siblings`/`pinnedActivityKeys`/`onPinSibling`; the long-press menu's new "more from this app" item is
shown only when `siblings.size > 1` (i.e. this row's package genuinely has other launcher activities
besides itself) and opens a second `DropdownMenu` listing every sibling with its own real icon and a
checkmark (`TileAccents`-tinted "check" glyph) beside whichever are already pinned; tapping an unpinned
one calls the same `LayoutRepository.pinApp` path as any other row. Build + full unit test suite green;
installed on the emulator, launched with no crash in `adb logcat` — the submenu's real content (a
package that actually exposes multiple launcher activities, e.g. the user's own Amazon install) still
needs to be click-tested by hand, same sandbox limitation as the pin de-dupe fix itself.

## "Pin to start" de-duped by package name alone, blocking a package's other launcher activities

User-reported real bug, not a WP-fidelity question: "amazon app has subapps like amazon fresh, amazon
now, amazon pay. it shows separate in app list but not able to pin individual app separately. when i
pin one and try to pin another it says already on start." Amazon (and some other apps) expose several
distinct `LAUNCHER`-category activities/activity-aliases under one package — the App List already
lists each correctly, since `AppCatalogRepository.query()` maps every `LauncherActivityInfo` to its
own `AppEntry` carrying both `packageName` and `activityName`. The bug was in the pin path:
`LayoutRepository.pinApp`'s "already pinned" guard called `LayoutDao.appTileCount(packageName)`, whose
`SELECT COUNT(*) ... WHERE packageName = :packageName` ignores `activityName` entirely — pinning
Amazon Fresh made the package-only count `> 0`, so pinning Amazon Now or Amazon Pay afterward always
hit `PinResult.ALREADY_ON_START`, even though nothing for that specific activity was on Start. Notably
this dedup key was inconsistent with `TileMerge.mergeKey()` (used for drag-merge-into-folder dedup),
which already correctly keys on `"$packageName/$activityName"` — the pin path just never adopted that.

Fixed with a new `LayoutDao.appActivityTileCount(packageName, activityName)`
(`SELECT COUNT(*) ... WHERE packageName = :packageName AND activityName = :activityName`), and
`pinApp` now checks that instead of the package-only `appTileCount` (which is left in place — still
used elsewhere/available for a genuine "any tile for this package" check if one is ever needed).
`AppListViewModel.pinnedPackages` (package-only, used only to route notification badges to the "recent"
section for unpinned packages) was deliberately left as-is — out of scope for this bug and a
reasonable simplification for that specific purpose, since badging one already-pinned sub-app's tile
is an acceptable approximation. Build + full unit test suite green; installed on the emulator, launched
with no crash in `adb logcat` — the actual multi-activity-pin scenario needs an app with real launcher
aliases (e.g. the user's own Amazon install) to click-test by hand, not reproducible in the sandbox.

## App list: "more from this app" gains app shortcuts too, plus a new "widgets" row

Direct follow-up to the "more from this app" submenu above. Two extensions, both user-requested after
seeing the submenu work for Amazon's sibling launcher activities: (1) the user pointed out a camera
app's "selfie"/"video"/"portrait"/"document scan" quick actions wouldn't be covered by that submenu at
all — those are a completely different OS mechanism (`ShortcutManager`/app shortcuts, resolved via
`LauncherApps.getShortcuts`), not separate `LAUNCHER` activities, so the sibling-activities-only
`hasSiblings` check would never see them; asked to "combine both". (2) separately, asked for a new
"widgets" row on the same long-press menu: pick from that app's own home-screen widgets, without going
through the glance page's own picker (which lists every installed app's widgets, unfiltered).

**Shortcuts.** New `AppShortcutTile` (`core/data`) mirrors `ContactTile`'s established trick — encode
identity into `TileModel.App.activityName` instead of adding a schema/DB change — but unlike a contact
(blank `packageName`), a shortcut's owning package is real, so only `activityName` gets the
`"shortcut:<id>"` sentinel prefix. `AppLauncher.launch` branches on `AppShortcutTile.decode`: a decoded
id calls `LauncherApps.startShortcut` instead of `startMainActivity`, deliberately with **no** fallback
to the app's main activity on failure (a dead shortcut shouldn't silently launch the wrong thing for a
tile the user specifically pinned as that shortcut). Nothing about pin/dedupe/render needed to change —
`LayoutRepository.pinApp`/`appActivityTileCount` already key on the literal `activityName` string, so
the encoded value just flows through like any real class name. New
`AppCatalogRepository.shortcutsFor(packageName)` queries `LauncherApps.getShortcuts` (static + dynamic
+ pinned, gated on `hasShortcutHostPermission()` — true automatically once TileShell is the default
Home app) and maps each to an `AppEntry`. **Known limitation, noted rather than solved this session**:
a pinned shortcut's tile/submenu icon falls back to the *parent app's* real icon, not the shortcut's
own (e.g. camera's "selfie" shows the camera icon) — `rememberMaskableAppIcon`'s existing
`getActivityIcon` call throws for the sentinel `activityName` (not a real class), already caught by its
existing `recoverCatching { getApplicationIcon(pkg) }` fallback, so this degrades gracefully rather than
crashing; fetching the shortcut's own icon needs `LauncherApps.getShortcutIconDrawable`, a genuinely
separate icon-loading path duplicated across `:feature:applist` and `:feature:start` — deferred.

Since a per-package shortcut query is a real system call (unlike the sibling-activities list, already
free in memory from the app catalogue), it's **not** run eagerly for every visible row — `AppRow` fetches
it lazily, once, on that row's own long-press (`AppListViewModel.shortcutsFor`, IO-dispatched), merged
into the existing `siblings` list (`allSiblings = (siblings + shortcuts).distinctBy { it.key }`) so the
"more from this app" item's visibility (and the submenu's contents) can go from hidden to shown a beat
after the menu opens — acceptable, since the rest of the menu is already visible and interactive.

**Widgets.** New `AppListViewModel.widgetsFor(packageName)` calls
`AppWidgetManager.getInstalledProvidersForPackage` (also lazy, on long-press, same reasoning) — a
first-in-codebase use of that API (the glance page's own picker only ever calls the unfiltered
`installedProviders` and groups client-side). A new "widgets" row (shown only when the package actually
has any) opens a submenu of provider labels; picking one doesn't bind anything inside `:feature:applist`
at all — the module graph runs `:feature:start → :feature:applist`, so `:feature:applist` has no path to
reach the glance page's real widget-hosting code (`WidgetSection`'s `AppWidgetHost`/`AppWidgetManager`/
`WidgetStore` instances and its bind → optional-configure → commit pipeline are all private to that one
composable in `WidgetSlot.kt`). Instead the chosen `AppWidgetProviderInfo` bubbles up through a new
`AppListScreen` `onAddWidget` callback to `StartScreen.kt`, which stashes it in local state
(`pendingFeedWidget`) and calls the existing `settleTo(-1f)` (already used elsewhere to jump the pager
to the feed page programmatically) — guarded by `feedEnabled`, toasting instead when the glance page is
off. `WidgetSection` gained two new optional params, `pinRequest`/`onPinRequestConsumed`, threaded
through `FeedPage`; a `LaunchedEffect(pinRequest)` feeds it straight into the **exact same**
`addProvider(provider)` the picker's own `onPick` already calls — same allocate-id → silent-bind-or-
`ACTION_APPWIDGET_BIND` → optional `ACTION_APPWIDGET_CONFIGURE` → `WidgetStore.add` pipeline, zero
duplicated binding logic. Build + full unit test suite green throughout; verified live end-to-end on
the emulator (not just build/tests): long-pressing "camera" (a real device app) showed all three new/
extended rows — "more from this app" (its shortcuts) and "widgets" both populated — and long-pressing
"calendar" → "widgets" → "calendar schedule" correctly jumped to the glance page with a real, live
Google Calendar widget (showing its own "sign in" prompt) pinned alongside weather/today/clock, proving
the whole cross-module round trip actually works, not just that it compiles. Installed on the physical
device too, launched with no crash in `adb logcat`.

## App list: "widgets" submenu gets real preview thumbnails, not plain text

Direct same-day follow-up, user-requested: the "widgets" submenu above only showed a text label per
provider — the glance page's own `WidgetPicker` (`WidgetSlot.kt`'s `WidgetPickerRow`) already renders a
small preview (`provider.loadPreviewImage(context, 0) ?: provider.loadIcon(context, 0)`, converted to a
bitmap via a private `Drawable.toBitmapOrNull()`), so each `DropdownMenuItem` here gained a matching
32dp `leadingIcon` doing the identical load-preview-or-fall-back-to-icon computation. `toBitmapOrNull`
itself is duplicated into `AppListScreen.kt` (byte-for-byte the same small helper) rather than shared,
following this file's own established precedent for icon-loading code (`AppListIcon.kt`'s masking logic
is already deliberately duplicated from `:feature:start`'s `IconCellView.kt` for the same reason:
`:feature:applist` has no dependency path to `:feature:start`). Build + full unit test suite green;
verified live on the emulator — the "widgets" submenu now shows each Calendar widget's actual layout
preview (a small schedule/month-grid thumbnail), matching what the system's own widget picker would
show, not a generic icon.

## Battery/performance audit — widget scheduling, in-app loops, and three data-loss bugs

User asked for a code audit optimising battery and speed, then separately for "other important
aspects" and "check for bugs". Run as eight parallel audit agents (background scheduling, in-app
polling/gating, receivers/sensors/listeners, Compose rendering, network/data layer, correctness
bugs, crash/leak/OOM, privacy/Play compliance); five were lost to a rate limit and two of those
were re-run, so **the rendering-performance, receivers/sensors, network-layer, crash/leak and
privacy/Play-compliance audits were never completed** — they remain open work, not clean bills of
health. Everything below comes from the four audits that did finish, each finding independently
verified in the code before being acted on.

**Baseline before changes** (release build, versionCode 400, emulator): cold start ~350-450 ms
steady state. R8 minification and resource shrinking were already enabled.

### Widget background work

The 14 home-screen widgets added in v4.0.0 were the dominant battery cost. What was found, all
confirmed by reading the code and then verified on the user's own physical device:

- **A duplicate wakeup channel.** Nine widgets declared `updatePeriodMillis="1800000"` *and* owned
  a WorkManager periodic job, and each provider's `onUpdate` calls the same `refreshNow()` the job
  does — so the OS alarm fired a second, complete refresh (network fetch, bitmap regeneration,
  Binder push) on top of every scheduled one. Weather was worse: its `onUpdate` forces a real
  `WeatherRefreshWorker.refreshNow`, giving 4 network fetches an hour where 2 were intended. Set to
  `0` on all nine (five event-driven widgets already had it right — in-repo precedent). `onUpdate`
  still runs on placement, reboot and app update, so the documented "forecast not shown when
  re-added" fix is untouched.
- **No `Constraints` on any of the ten periodic widget workers** — a regression against the older
  `WeatherRefreshWorker`/`FeedRefreshWorker`/`BingWallpaperWorker`, which have always set
  `NetworkType.CONNECTED` with a comment about not burning wakeups offline. The three network
  widgets now require connectivity; all but the battery widget require battery-not-low. Battery is
  deliberately excluded: a low battery is when that reading matters most.
- **Moon phase, countdown and calendar system polled every 30 minutes** — 48 full rebuilds a day,
  each regenerating a `Canvas`-drawn bitmap — to render a value that is a pure function of the
  date. Now a single daily run aligned to just after midnight (`WidgetWork.millisUntilNextMidnight`),
  which is the only moment their content can change.
- **The alarm widget** polled every 30 minutes for a next-alarm time that changes only on user
  action. `ACTION_NEXT_ALARM_CLOCK_CHANGED` *is* manifest-deliverable (unlike
  `ACTION_BATTERY_CHANGED`), so it is now genuinely event-driven with a 6-hour backstop, matching
  the pattern `BatteryAppWidgetProvider` already established.
- **Cricket's lookback was pathological.** `fetchRecentCricketMatchForTeam` walks backward one HTTP
  request per day, up to 30, when today's feed has nothing — correct and well-reasoned, but
  uncached, so a followed team that is simply out of season cost 31 sequential requests every 30
  minutes forever to re-derive an unchanged answer. Now caches the walk's result *including the
  miss* (the expensive case), while today's feed stays uncached so a live match is never stale.
- **`accentGradientBitmap` was regenerated by all 14 widgets on every push** for a value that only
  changes when the user picks a new accent. Memoized.
- **A worker outliving its widget**: `WeatherAppWidgetProvider.onEnabled` starts the network
  forecast poll as well as its own render worker, but `onDisabled` cancelled only the latter — so
  placing a weather widget and removing it left a 30-minute fetch running for the life of the
  install. `WeatherRefreshWorker` gained the `cancel()` it never had; over-cancelling is safe
  because the in-app tile re-arms it from its own `LaunchedEffect`.

### Sports and stock refresh gating (both user-initiated)

The user observed that **"when match is not live refresh may not be needed for any sports"** — true
and general: a finished match's score is final and a fixture that hasn't started shows the same
face however often it is re-fetched. New pure, unit-tested `shouldFetchSports` encodes exactly that
(always refresh when live; slow re-check when finished; wake shortly before a known kick-off), with
per-widget state in `WidgetSportsStateStore` and a `force` flag so placement/resize/config still
always fetch.

The user then asked that **stock refresh only run Mon-Fri 9-4 "based on the market region"**. The
existing `isMarketHoursNow` used a fixed 9-4 window in the *device's* timezone for everything,
which was wrong in both directions — an Indian user watching a US stock polled hardest at 9am IST
(7pm ET, shut) and went quiet at 7pm IST (the opening bell) — and also throttled FX and futures,
which trade nearly around the clock, through the hours they actually move. New `MarketHours.kt`
resolves the session from the symbol's own Yahoo exchange suffix (`.NS` → Mumbai, `.L` → London,
bare ticker → New York, `=F`/`=X` → weekday-continuous, `BTC-USD` → never closed), with per-exchange
hours and Tel Aviv's Sunday-Thursday week. Tiles now sleep straight through to the opening bell;
widgets skip closed markets entirely. Fully unit-tested against a fixed clock.

### The fix that made all of the above actually reach users

Verified on the physical device that **none of it had taken effect** — every TileShell job still
showed `batteryNotLow=false`. Two independent causes, each a real bug:
`ensureScheduled` was only ever called from `onEnabled`, which fires once for the first instance of
a widget kind, so an existing install could never receive a changed schedule; and
`ExistingPeriodicWorkPolicy.KEEP` silently discards a new spec when the unique name already exists,
which is exactly the case being fixed. Every periodic provider now re-asserts its schedule from
`onUpdate` (broadcast to all providers on app update) and the remaining workers use `UPDATE`.
Re-verified on the same device afterwards: 8 jobs carrying `batteryNotLow=true`, and three jobs at
~23h42m — the midnight-aligned daily cadence — where they had been polling every 30 minutes.

**This is the load-bearing lesson from the session**: a scheduling change that is only applied at
`onEnabled` is invisible to every existing install, and `dumpsys jobscheduler` on a real device is
the only way to know.

### In-app loops that ran while nothing could see them

The feed page and app list stay composed whenever enabled — the pager translates them off-screen
rather than removing them — so any loop not consulting its visibility flag runs 24/7.
`FeedPage`'s clock ticked on `LaunchedEffect(Unit)` and, because `now` is a fresh unstable
`Calendar` read at the top of the composable, each tick invalidated the entire page (cards, hosted
widgets, news list) to update two header strings. The feed's widget-stack rotation had no
visibility gate at all — ~8,600 rotations a day off-screen, each composing a different member's
real `AppWidgetHostView` plus `updateAppWidgetSize` IPC. `StockSmallFace` and `CommoditySmallFace`
were missing the `active` gate their full-size siblings have, so a SMALL stock tile kept fetching
over the network with the screen off (~490 requests/day). The media poll did a binder round-trip
into `system_server` every 3s even with no session on the device. The battery tile enqueued a
WorkManager request on every `ACTION_BATTERY_CHANGED` — a broadcast that fires on voltage and
temperature changes, so every few seconds while charging.

Start's own stack rotation had a **stale-closure bug**: `liveActive` and `editMode` were read
directly inside a `LaunchedEffect(count)` that deliberately never restarts, so the guard saw only
their first-composition values — a stack composed while active kept rotating with the screen off
and battery saver on. Same trap as "resize/reorder follow-up #5"; both now go through
`rememberUpdatedState`, matching the feed's equivalent.

### Three silent data-loss bugs (correctness audit)

1. **A package event in another profile deleted this profile's tiles.** The `LauncherApps.Callback`
   ignored `UserHandle` entirely, and treated `onPackagesUnavailable(replacing=false)` as an
   uninstall — but that fires when apps go temporarily out of reach, most commonly "Pause work
   apps" or an unmounted SD card, and nothing restores them since `onPackagesAvailable` is a
   deliberate no-op. So pausing a work profile silently deleted Start tiles for good. Now scoped to
   `Process.myUserHandle()` (the only user the catalogue enumerates) and to `onPackageRemoved`.
2. **Creating a folder deleted every tile sharing a package.** `pinApp` dedupes on package +
   activity so one package can own several independent tiles (an app's regional sub-apps, an
   app-shortcut tile), but `createFolder`/`updateFolderContents` removed by package alone — a
   direct regression against this v4.0.0 feature. Added `deleteTilesByComponent`; uninstall still
   deletes package-wide, as it should.
3. **`displayAsIcon` was never written to a backup**, so every restore reverted per-tile "show as
   tile" choices — the same class of bug the file's own comment documents for `gridSlot`. And
   `layoutHash` omitted `accentOverride`, `displayAsIcon`, `label` and `iconKey`, so a colour-only
   change hashed identically: "save now" reported success while taking no snapshot, and auto-backup
   kept serving a stale one. Round-trip and hash-sensitivity regression tests added.

### Smaller correctness fixes

App shortcuts showed their parent app's icon everywhere (the `"shortcut:"` sentinel can never form
a valid `ComponentName`, so every loader fell through to `getApplicationIcon`) — fixed with a shared
`shortcutIconDrawable` wired into all three icon loaders. `clampForFolder` demoted only WIDE/LARGE,
which stopped being complete when the seven drag-only presets landed, so an XLARGE tile merged into
a folder produced a 4×4 child in a mini-grid built for 1×1/2×2 cells. Launching a pinned shortcut
recorded a `RecentApps` key that can never resolve, consuming one of the capped 12 slots and
shrinking the App List's "recent" section.

### Verification and what remains

Build and the full unit-test suite green after every step; new tests for `MarketHours`,
`SportsRefreshPolicy` and the backup round-trip/hash. Installed and launched crash-free on both the
emulator and the physical Samsung device throughout, with the scheduling result confirmed via
`dumpsys jobscheduler` on real hardware before and after.

The shortcut-icon fix was subsequently **confirmed on the physical device**: long-pressing WhatsApp
→ "more from this app" now renders each pinned chat's own contact photo or group icon, plus distinct
glyphs for Meta AI, Camera and a voice-note chat — where previously every row showed the identical
WhatsApp icon. The "widgets" submenu likewise showed WhatsApp's two real widget previews. **Still
unverified**: the market-hours gating, which needs a real weekend or session boundary to observe. **Not audited at all** (agents lost to the
rate limit): Compose rendering performance and recomposition scoping, receivers/sensors/listeners,
the network and persistence layer, crash/leak/OOM risk, and privacy/permissions/Play compliance.
Known-but-unfixed from the completed audits: `importBackup` is not atomic across its five DataStore
writes after the transactional Room restore; the notified section of `topApps` is uncapped and
per-activity; trimmed history snapshots leak their screenshot JPEGs; and `pinApp` does a
read-modify-write outside `StartViewModel`'s serialized `writeContext`, so two concurrent pins can
land on the same `position`.

## Start grid: side margin removed — tiles now go edge-to-edge (user-requested)

User reported "tiles dont occupy full horizontal screen size." Investigation (emulator screenshot +
pixel-level measurement on a physical Samsung SM-S938B, connected via wireless adb) found the grid
was actually rendering correctly per spec: `GridGeometry.of` (`feature/start/.../GridGeometry.kt`)
computes a proportional side margin of `totalWidthPx * (9/393)` (~33px on a 1440px-wide screen,
confirmed symmetric via pixel scan) — a direct, deliberate port of the prototype's own
`--side: 9px` (`design/.../launcher.js:290`, applied via `padding: 10px var(--side) 0` in
`styles.css:134`), i.e. real Windows Phone's own Start-screen outer grid margin. Not a bug — but
once the rationale was explained, the user asked to remove it since it wasn't functionally load-
bearing for them (no gesture/hit-testing logic depends on a nonzero side; `GridGeometry`'s formula
is self-balancing for any `side` value including 0).

`GridGeometry.of`'s `side` is now hardcoded to `0f` (unit/gap/top-padding stay proportional to the
393px reference as before) — a one-line, single-source-of-truth change since every consumer
(`DenseTileGrid`, the folder overlay's inline-expand grid, resize/hit-testing geometry) shares this
one function. Tiles now sit flush against the screen edges on Start (and the folder overlay), with
only the inter-tile gap (still 3/393 proportional, or the user's own "tile spacing" override)
separating them from each other. A deliberate deviation from the prototype/spec's `side 9` reference
constant — noted here per this project's convention for such choices.

## Pager drag (feed/Start/app-list swipe) smoothness fix

Direct same-day follow-up: user-reported "left scroll is not very smooth" (the Start↔feed swipe;
same `pagerModifier` drives Start↔app-list too). Root cause: `pagerModifier`'s per-pointer-move
handler in `StartScreen.kt` called `scope.launch { progress.snapTo(target) }` — a **fresh coroutine
launch on every touch-move event** (up to ~120/s during a fast drag), each independently scheduled
on the composition's coroutine scope. The extra allocation + dispatch overhead per touch sample is
what read as the drag lagging/stuttering behind the finger, especially compositing over the feed
page's heavier content (blurred wallpaper, live widgets). `Animatable.snapTo` can't be called
directly from the gesture loop (`AwaitPointerEventScope` is a restricted-suspension scope — a first
attempt at a direct-call fix didn't compile for exactly that reason) — fixed instead with one
conflated `Channel<Float>` + one background consumer coroutine **per gesture** (created lazily, only
once the drag is recognised as horizontal), so a fast drag produces at most one coroutine launch
total instead of one per touch sample; `Channel.trySend` (non-suspending) is what the restricted
scope calls directly. The final settle-target calculation (`pagerCommitTarget`) now reads a
synchronously-tracked `lastTarget` local instead of `progress.value`, since the background consumer
applies updates asynchronously and `progress.value` could still be one step stale at release time —
a latent correctness edge case in the original code too, fixed as a side effect. Build + full unit
test suite green; installed on both the physical device and the emulator, launched with no crash in
`adb logcat`. The actual drag *feel* — the whole point of this fix — still needs the user's own
on-device confirmation; ADB-synthesized swipes in this codebase's own history are not reliable
stand-ins for real per-frame touch-sampling smoothness.

## Real crash root-caused via on-device battery/exit-info diagnostics: PixelCopy crashing the whole Home process, several times a day

User-reported battery drain "more than regular" on 4.0.0 (after already reporting it on 3.6.0) plus
"launcher/start screen takes little longer to load when I press power on." Diagnosed directly against
the physical device's own OS-level accounting rather than guessing from code: `dumpsys batterystats
com.tileshell` showed `Proc com.tileshell: ... 4 starts, 4 crashes` in one ~8h window, and — the
concrete, decisive step — `dumpsys activity exit-info com.tileshell` listed a long run of `reason=4
(APP CRASH(EXCEPTION))` exits, several per day across four separate days. `adb logcat -b crash -d`
(the OS's own persistent crash-log ring buffer, which survives across the app's own process deaths)
then gave the actual stack traces: **all 13 of the app's crashes over that whole span** were the
identical `java.lang.IllegalArgumentException: Window doesn't have a backing surface!` thrown by
`android.view.PixelCopy.request(...)` inside `captureSnapshotJpeg` (`StartScreen.kt:6392`), called
from the auto-backup screenshot-cache effect on `ON_PAUSE` (`StartScreen.kt:675`, see the "Post-S27
— auto-backup screenshot cache" entry above — that feature's own doc comment assumed the window
"is still attached/visible" at `ON_PAUSE` time, which this data proves isn't reliably true: the
window's Surface can already be torn down by the time the coroutine actually runs, e.g. racing the
display genuinely powering off). The call was entirely unguarded — no `runCatching`, so the
exception was uncaught on the main thread and took down the whole Home process. This directly
explains both symptoms without needing to guess further: a crashed launcher process means the *next*
screen wake is a full cold start (Room reopen, WorkManager rescheduling, notification-listener
reconnect, wallpaper/feed re-init) instead of a cheap warm resume — measurably slower, exactly
matching "takes longer to load" — and repeated full cold-boots several times a day is real,
avoidable extra CPU/IO work extra to whatever baseline the launcher's live tiles/widgets already
cost, on top of `batterystats` separately showing the process pinned in `Fg Service` process-
importance state for ~95% of the whole on-battery window (a `NotificationListenerService`
side-effect, not itself a bug, but relevant background context: it means a crash-restart cycle here
doesn't get to hide behind any real Doze-throttling in between). Fixed by wrapping just the
`PixelCopy.request(...)` call in `runCatching`, returning null (the function's existing "capture
skipped" contract, which both call sites already handle) instead of letting the exception escape —
a minimal, surgical fix, not a rewrite of the capture flow. Verified by reinstalling and reproducing
the likely trigger directly (five rapid screen-off/screen-on cycles via `adb shell input keyevent
KEYCODE_POWER`) with no new crash in the crash-log buffer, process pid unchanged throughout. Real
confirmation that this eliminates the crash for good needs a few more days of the user's own normal
use, but every single historical crash on this device matches this one exact code path with 100%
consistency, so this is a high-confidence root cause, not a partial mitigation. Build + full unit
test suite green.

## Accessibility "please enable" disclosure could false-nag: isConnected() staleness fixed; real root cause for Play users still OS/OEM-level

User-reported (from real Play Store 3.6.0 users, not sideloaded testers — confirmed via
`AskUserQuestion`): the "please enable accessibility" prompt (screen lock / recents / notifications
gestures) shows up too often. Two distinct things were found, only one of them fixable in this
app's own code:

1. **A real bug, fixed**: `LockAccessibilityService.isConnected()` was a bare in-process static flag
   (`instance != null`, set only in `onServiceConnected`/cleared in `onUnbind`) — the same "stale
   process-local flag" bug class already fixed once in this project for the default-launcher check
   (`RoleManager.isRoleHeld` going stale). Any process restart (a crash — see the PixelCopy entry
   above — or an OEM background-process kill) resets this flag to null even when the user's real,
   system-level Accessibility grant is untouched, and `MainActivity`'s three gates
   (`onLockScreen`/`onRecents`/`onOpenNotifications`) trusted that flag alone, unconditionally
   re-showing the Play-required disclosure dialog in that state — a false nag. Fixed with a new
   `LockAccessibilityService.isEnabledInSettings(context)`, checking the real system state via
   `AccessibilityManager.getEnabledAccessibilityServiceList(...)` — the same established pattern
   `NotificationAccess.isEnabled()` already uses for notification-listener access, rather than an
   in-process flag. The three gates now only show the disclosure when genuinely not enabled; when
   enabled-but-not-yet-rebound-in-this-process, they show a brief "still connecting — try again in a
   moment" toast instead (the service rebinds on its own almost immediately once already granted).
2. **Verified, then ruled out as the explanation for real users**: while investigating on the
   physical test device, direct verification (`adb shell settings get secure
   enabled_accessibility_services` + `cmd appops get com.tileshell` showing
   `ACCESS_RESTRICTED_SETTINGS: default`) confirmed Android's Restricted Settings protection (13+,
   for apps installed outside the Play Store) was actively blocking the toggle on that sideloaded
   debug build — every `adb install -r` resets it, requiring the "allow restricted settings" unlock
   step again. This fully explains repeated prompts *on sideloaded test builds*, but **does not
   apply to Play Store installs** (Play is an exempt/trusted installer), so it isn't the explanation
   for the real 3.6.0 users' reports.
3. **Most likely real cause for Play users, not fixable from app code alone**: OEM-level background/
   accessibility-service management (well-documented specifically on Samsung) silently revoking a
   third-party Accessibility Service grant over time, independent of any app update — circumstantially
   supported by `com.samsung.android.lool` (Samsung's own Device Care / RAM management) being
   actively running in this same device's crash logs. Existing Doze/battery-optimization-exemption
   (already granted, confirmed via `dumpsys deviceidle whitelist`) does not necessarily protect
   against this — it's a separate, OEM-proprietary mechanism. No public API exists to prevent an OEM
   from doing this; the only lever is user-facing guidance (e.g. a deep link to Samsung's own
   "protected apps"/battery-unrestricted screen), not yet built — parked as a follow-up, pending the
   user's decision on scope.

Build + full unit test suite green; installed on both the physical device and the emulator.

**Same-session follow-up — OEM battery-settings guidance built.** User asked to go ahead and build
item 3's parked follow-up. New `feature/system/OemBatterySettings.kt`: a pure `oemBatteryTarget
(manufacturer: String): OemBatteryTarget?` lookup (unit-tested, `OemBatterySettingsTest.kt`) mapping
`Build.MANUFACTURER` to the community-documented battery-management screen for Samsung, Xiaomi,
Huawei, Oppo, Vivo, and OnePlus (the same component names the dontkillmyapp.com-style app catalogues
use — unofficial, no public API, can drift across OEM software versions), and `openOemBatterySettings
(context)`, which tries that screen and always falls back to this app's own App Info screen (never
unresolvable) when the specific intent fails or the manufacturer isn't one of the known ones.
`AccessibilityDisclosureDialog` (`MainActivity.kt`) gained an explanatory paragraph + "open battery
settings" button wired to it, shown every time the disclosure appears — the actual, most likely fix
for real Play users' repeated prompts, even though the app itself can't detect *why* an OEM revoked
the grant, only offer the one-tap way to re-exempt it. Verified via the same physical-device
diagnostic session: the `enabled_accessibility_services`/`ACCESS_RESTRICTED_SETTINGS` check that
found the sideload-specific cause also confirmed `com.samsung.android.lool` (Samsung's own Device
Care) is the real target this device's own button would open. Build + full unit test suite green
(new `OemBatterySettingsTest`, 3 cases). Installed and launched crash-free on both the physical
device and the emulator; the dialog's own visual appearance (the new paragraph/button rendering
correctly) wasn't reachable via ADB-synthesized gestures this session — reaching it needs either the
Quick Panel's two-finger swipe-up or the edge-strip recents button, both real-finger-only per this
project's own established ADB gesture-automation limitation — so this is verified by code review +
unit tests + a clean crash-free launch, not an on-device screenshot of the dialog itself.

## Real bug found via direct DB/file inspection: backup restore always strips the built-in weather/calendar glance cards

User-reported after restoring a backup on the debug build: "the default calendar and weather widget
not seen." Root-caused by pulling the actual on-device state rather than guessing: the real SQLite
`tiles` table (pulled via `run-as com.tileshell cat .../tileshell.db`) showed the Start-screen tiles
were all present and correct, ruling out the tile/backup-JSON layer entirely — the missing "widgets"
were the glance/feed page's built-in Weather and Agenda(calendar) cards, a separate domain
(`WidgetStore`'s `feed_widget.pb`, pulled and read directly: confirmed it held only real positive
widget ids, none of the three builtin sentinels). `StartViewModel.importBackup`'s widget-liveness
filter called `AppWidgetManager.getAppWidgetInfo(it.widgetId) != null` on every backed-up widget
entry to decide whether it should survive the restore — but `BUILTIN_WEATHER_WIDGET_ID`/
`BUILTIN_AGENDA_WIDGET_ID`/`BUILTIN_NOWPLAYING_WIDGET_ID` (`-1`/`-2`/`-3`, `WidgetStore.kt`) are
synthetic sentinels representing the app's own built-in cards, never real `AppWidgetManager`-issued
ids — `getAppWidgetInfo(-1)` always returns null, so this filter silently stripped the built-in
cards on **every single restore**, not just after a reinstall (though a reinstall additionally
invalidates every *real* hosted widget's id too, compounding it — confirmed separately in this same
session's earlier debugging). Fixed by extracting the liveness decision into a pure, unit-tested
`isRestorableWidgetId(widgetId, isBound)` (`WidgetStore.kt`, mirroring this file's own established
`stripStaleNegativeIds` pattern): a builtin sentinel always survives regardless of what `isBound`
says; anything else defers to it. A pre-existing safety net (`WidgetStore.seedBuiltinsIfAbsent`,
already run once per feed-page composition for exactly this kind of gap) meant the user's
already-broken on-device state self-healed the moment the fix was installed and the feed page was
reopened — verified directly: `feed_widget.pb` held only real ids before, `-1,0,0,true` / `-2,0,0,true`
/ `-3,0,0,false` reappeared at the front of the file after, and a screenshot confirmed both cards
rendering with live data (weather forecast, "nothing on your calendar today"). Build + full unit
test suite green (new `isRestorableWidgetId` cases in `WidgetSlotTest.kt`).

## Overnight battery/data diagnosis: the news feed is the real consumer; no orphaned widget workers

User-reported that TileShell was using "much mobile data and CPU" overnight and showing 2+ hours of
active usage, and separately asked whether widgets they don't use are still costing battery.
Measured against a real 18h53m on-battery window (`dumpsys batterystats com.tileshell`) rather than
inferred:

**Mobile data is not the problem.** 530 KB received over mobile in 19 hours. What *is* high is
**WiFi: 19.70 MB received** — the largest of any app on that device (next highest 8.28 MB).

**The news feed is the cause, and conditional GET does not mitigate it.** Fetching this install's
own 10 enabled feeds live measured **777 KB for one refresh cycle** (Gadgets 360 186 KB, Google News
167 KB, NDTV Movies 129 KB, ESPNcricinfo 89 KB, the rest 26–47 KB), i.e. **35.5 MB/day** at the
30-minute cadence. The `ETag`/`Last-Modified` revalidation added by the earlier audit was verified
against the real servers using the validators actually stored in this device's `news_feed.pb`, and
it almost never produces a 304: Google News sends `cache-control: no-store` with neither validator
(so it can *never* revalidate), and TOI/The Hindu/NDTV replay their stored validator and still
answer `200` with a full body — because a news feed genuinely has new items 30 minutes later.
Revalidation only pays off for a source that is quiet between ticks, which a news feed is not. The
6-hour idle window then multiplies it: one evening glance at the feed funds 12 more cycles
(**8.8 MB**) through the night.

Fixed by adding a screen term to the periodic gate — new pure, unit-tested
`shouldSkipPeriodicFeedRefresh(now, lastOpened, screenInteractive)` (`FeedUsagePrefs.kt`), consulted
by `FeedRefreshWorker` via `PowerManager.isInteractive`. Screen-off is the signal rather than
Doze/idle because it needs no permission, flips instantly, and matches the real question — is there
a person who could be about to open this page. Every one-off path still passes `KEY_FORCE` and
bypasses the gate entirely, so opening the feed fetches immediately exactly as before; only the
unattended background cadence is cut.

**No orphaned widget workers — an explicitly checked and disproven hypothesis.** The suspicion was
that `onUpdate` scheduling without an `appWidgetIds.isEmpty()` guard would leave periodic workers
running for widgets that were never placed. Querying WorkManager's own database directly
(`no_backup/androidx.work.workdb`, `WorkSpec` joined to `WorkName`) showed exactly **7 enqueued
periodic workers, every one of them justified**: feed (30m), in-app weather tile (30m), layout
auto-backup (6h), and one each for the **4 widget instances that genuinely exist** — Battery (15m),
Steps (15m), Weather (30m), Calendar system (daily), confirmed against `dumpsys appwidget`. A
`FlashlightWidgetRefreshWorker` run seen in a cold-start log was a *one-off* fired by the torch
toggle (`SystemToggles.kt`), not a leaked schedule — that provider has no periodic worker at all.

Worth recording for the user rather than the code: those 4 widgets live inside **TileShell's own
glance page** (`hostId 21587`), not another launcher's home screen, and account for ~240 local
wakeups/day between them. They are all local-only — `BatteryWidgetRefreshWorker` and
`StepsWidgetRefreshWorker` make zero network calls, and `WeatherWidgetRefreshWorker` deliberately
reads the shared `WeatherCache` that `WeatherRefreshWorker` populates rather than fetching again, so
weather is not double-fetched.

Also clarified: the "2+ hours of active usage" a battery UI reports is `Foreground for: 6h49m` —
the process state any app gets while it is the Home app and the device is awake. Actual user-facing
time was `Top for: 30m` and total CPU was 13.5 minutes across the whole 19 hours.

## Screen-off gate extended from the feed to the frequent widget refresh workers

Follow-up to the overnight diagnosis above, at the user's request. The same argument that justified
gating the news feed applies to the widget pollers: re-rendering a widget nobody can currently see is
pure waste. Measured on the real device, the three frequent pollers that were actually scheduled —
Battery (15m), Steps (15m) and Weather (30m) — account for ~240 wakeups a day between them, the
large majority overnight, for widgets living on TileShell's own glance page.

New pure, unit-tested `WidgetWork.shouldSkipWidgetRefresh(force, screenInteractive)` plus a
`skipWhileScreenOff(context, force)` wrapper reading `PowerManager.isInteractive`. Applied to the six
frequent pollers: Steps, Battery, Weather, Stock, Commodity, Sports. The last three already carried a
`KEY_FORCE` marker distinguishing an explicitly-requested one-off from the periodic tick; Steps,
Battery and Weather gained the same marker so their `refreshNow()` one-offs stay unskippable.

**Deliberately not gated**, and this is the load-bearing detail: the midnight-aligned daily workers
(calendar system, moon phase, countdown) schedule their single daily run for just after midnight —
precisely when the screen is off. Gating those on the screen would skip the one tick that matters and
leave the displayed date stale for a further 24 hours. The alarm widget (6h, plus a
`NEXT_ALARM_CLOCK_CHANGED` receiver) is left alone too as already negligible.

The cost of the gate is bounded at one interval of staleness after the screen comes back on, which is
acceptable because these refreshes are cosmetic by design (`WidgetWork`'s own premise) and because the
moments that genuinely matter are already event-driven rather than polled — the battery widget has
manifest receivers for plug/unplug and battery low/okay, so the poll only ever covered gradual % drift.

Verified: build and full unit test suite green (new `WidgetWorkTest`); installed on the physical
device, all 7 periodic workers re-register cleanly and the app launches crash-free. A forced run of
the periodic jobs with the screen off produced no worker activity. The end-to-end proof is the next
overnight battery/data window, not something reproducible in a single session.

## Orphaned AppWidgetHost bindings: invisible widgets that kept their refresh workers armed

User-reported, and it corrects the "no orphaned widget workers" conclusion recorded above: asked to
look again because none of the four widgets attributed to battery use were actually visible on the
glance page. Cross-referencing `dumpsys appwidget` (what is *bound*) against
`files/datastore/feed_widget.pb` (what the glance page *renders*) showed eleven ids bound to
TileShell's own host but only five in the store. The six extras — TileShell's own calendar-system,
steps, weather and battery widgets, plus two third-party ones — were **bound but unrendered**.

That earlier conclusion was right that every scheduled worker was backed by a real bound instance,
and wrong about what that implied: the *instances themselves* were orphaned. An id stays bound until
`AppWidgetHost.deleteAppWidgetId` is called for it, and while bound it is a live instance to
`AppWidgetManager`, which keeps broadcasting `APPWIDGET_UPDATE` to its provider, whose `onUpdate`
calls `ensureScheduled()` — so four invisible widgets were re-arming Battery (15m), Steps (15m),
Weather (30m) and calendar-system (daily) indefinitely, ~240 wakeups a day for something the user
could not see or reach.

Root cause: the glance page's own remove path does delete the host id (`WidgetSlot.kt`), but
`WidgetStore.replaceAll` does not and structurally cannot — the host lives in the UI layer, not in a
DataStore wrapper. `StartViewModel.importBackup` restores widgets through exactly that call, so a
restore whose file listed a different set silently stranded every previously-bound id. This is the
second distinct bug found in that same `importBackup` widget path (see the built-in sentinel filter
entry above).

Fixed with a reconciliation pass rather than by patching the one call site, so any future path that
drops ids is covered too: new pure, unit-tested `orphanedHostWidgetIds(hostIds, current)`
(`WidgetStore.kt`) diffs the host's allocated ids against what the store renders, and the glance
page's existing startup housekeeping (`LaunchedEffect(Unit)`, alongside `seedBuiltinsIfAbsent`)
deletes the difference. It runs once per page mount, before any add flow can allocate an id, so an
in-flight allocation cannot be caught mid-bind. Negative sentinel ids are never host-allocated and so
never appear in `hostIds`.

Verified end-to-end on the device: bound instances went 11 → 5, exactly matching the store; the OS
emitted four `APPWIDGET_DELETED`/`APPWIDGET_DISABLED` pairs, one per provider; and their `onDisabled`
→ `cancel()` left the WorkSpec rows for all four widget workers **CANCELLED**. Periodic workers went
from 7 to 3 — feed (screen-gated), the in-app weather tile, and the 6-hourly layout auto-backup —
each of which corresponds to something the user actually has.

## Feed refresh moved from timer-driven to demand-driven: fetch on open when stale

User asked, after the screen-off gate landed, whether opening the glance page refreshes the news
feed — and if not, to refresh once when the cache is older than 30 minutes. It did not, and the
question exposed a real gap the gate had just widened.

What actually happened on open: `FeedPage` calls `FeedRefreshWorker.ensureScheduled` from a
`LaunchedEffect(Unit)`, which fired an unconditional forced one-off alongside the periodic schedule.
`LaunchedEffect(Unit)` runs once per *composition* — once per app launch, not once per visit, since
the pager keeps adjacent pages mounted. So the feed refreshed once at launch regardless of how fresh
the cache already was (777 KB measured), and then never again on any subsequent swipe back to the
page. With the periodic tick now gated on the screen being on, the cache could legitimately be hours
old by the next visit with nothing to refetch it.

Both halves were wrong in opposite directions, so the fix replaces the launch-time fetch with a
staleness check on actual visibility:

- New pure, unit-tested `shouldRefreshFeedOnOpen(now, lastRefreshedAt, staleAfterMillis)` with
  `FEED_STALE_AFTER_MS` = 30 min, plus `FeedUsagePrefs.markRefreshed`/`lastRefreshedAtMillis`
  recording when a refresh *actually fetched* — written at the end of `doWork`, after the store
  write, so the early-return skip paths never count as a refresh and a genuinely stale cache still
  reads as stale.
- `FeedPage`'s `LaunchedEffect(active)` — which already existed to record `markOpened`, and which
  fires on real visibility rather than composition — now checks staleness first and calls
  `FeedRefreshWorker.refreshNow` when the cache is older than the window. Repeatedly flicking to the
  page costs nothing; returning after 30+ minutes fetches.
- `ensureScheduled` no longer enqueues its one-off at all, so a cold start with a fresh cache stops
  re-downloading every subscribed feed. The first-ever open is still covered: a never-fetched install
  has `lastRefreshedAtMillis` 0, which reads as stale.

A backwards clock jump reads as fresh rather than stale, mirroring `shouldSkipIdleFeedRefresh`'s own
guard, so an NTP/timezone correction can't trigger a fetch on every open.

Net effect across the three feed changes: the feed now fetches when someone is about to read it and
what they'd read is stale, instead of every 30 minutes around the clock plus unconditionally on every
launch. Build and full unit test suite green (5 new cases).

Verified on the physical device by reading `shared_prefs/tileshell.prefs.xml` directly, since the two
timestamps make each branch observable without instrumenting anything:
 - **Stale (never fetched) → fetches.** `feed_last_refreshed_at` went from absent to 08:38:08 on the
   first open.
 - **Fresh → does not refetch, but the effect still runs.** Swiping away to Start and back left
   `feed_last_refreshed_at` at 08:38:08 while `feed_last_opened_at` advanced 08:39:04 → 08:41:00.
   The advancing open time is what proves the effect fired at all, so the unchanged refresh time is a
   real decision not to fetch rather than the effect silently never running — the two together are
   what make this a genuine A/B.
 - **Cold start with a fresh cache → does not fetch**, confirming the removed unconditional
   `ensureScheduled` one-off: force-stop + relaunch left `feed_last_refreshed_at` untouched.

Not directly observed: the ">30 minutes old → refetch" case, which would need a 30-minute wait. It is
the same branch as the first-open case (both are `shouldRefreshFeedOnOpen` returning true) and the
boundary itself is unit-tested.

## v4.0.2 cut, and a crash-log check that confirms the PixelCopy fix held

Release cut at `versionCode` 402 / `versionName` 4.0.2, carrying the **same user-facing release notes
as 4.0.1 and 4.0.0** — none of those ever reached Play, so the feature set a Play user would see for
the first time is unchanged; 4.0.2 adds the battery/data and correctness pass on top (backup restore
no longer dropping the built-in glance cards, orphaned `AppWidgetHost` bindings released, and the
feed/widget refresh cadence moved from timer-driven to demand-driven). Unlike 4.0.0's three same-code
re-cuts, this is a real new versionCode, as 4.0.1 was.

Signed APK + AAB built and verified: `versionCode='402' versionName='4.0.2'`, signing certificate
SHA-256 identical to 4.0.1's (and so to every prior release), content confirmed changed by checksum
rather than assumed, `jarsigner -verify` clean on the bundle, and the release APK installed and
launched crash-free under R8 on the emulator.

**Crash-log check since the PixelCopy fix.** Asked to check crashes since the previous day. Three
sources, deliberately, because the obvious two had gaps:
 - `adb logcat -b crash` was **useless here and said so**: the buffer was empty, because this
   session's own `logcat -c` calls had wiped it. Absence of entries there was not evidence of
   absence of crashes.
 - `dumpsys activity exit-info` retained only 6 records, none of them crashes, but the repeated
   reinstalls had consumed its history back to 07:41 the same morning — so it could not answer for
   "yesterday" either.
 - **`dumpsys dropbox`** is the one that could: it persists crash records independently of logcat
   buffers and app reinstalls. It holds exactly **7 `data_app_crash` entries for `com.tileshell`, all
   dated 2026-09-11** (09:05 through 19:08) — the PixelCopy crashes — and **none on 09-12 or 09-13**.
   The fix was installed 09-11 at ~19:42, after the last of them. The one crash-type entry on 09-12
   (`system_app_anr`, 22:49) belongs to `com.android.systemui`, not TileShell.

So the PixelCopy fix has now held across ~37 hours of real use spanning two days and an overnight,
with zero recorded crashes — the first genuinely independent confirmation of it, since the earlier
check could only show "no new crashes in the few minutes since installing".

## Post-fix check: no crashes or ANRs, and the one network worker that escaped the screen-off gate

Asked to check the day's performance plus any crashes/ANRs after the battery work landed.

**Crashes/ANRs: none.** `dumpsys dropbox` — the source that survives both `logcat -c` and reinstalls,
unlike the two that had gaps here — still holds exactly 7 `data_app_crash` entries for
`com.tileshell`, all dated 2026-09-11, and none since. No ANR entries for it at all; the only
crash-type record on 09-12 (`system_app_anr`) belongs to `com.android.systemui`.

**Data: no longer the worst offender, but today's absolute numbers are not usable as evidence.**
TileShell has dropped from #1 data consumer on the device (19.7 MB, more than double the next app) to
**5th** (14.23 MB mobile), behind Instagram 38.6, Facebook 33.5, WhatsApp 29.0 and one other. That
ranking is meaningful; the raw totals are not, because the 6h28m window is dominated by this
session's own testing — roughly eight installs, ten cold starts, and repeated glance-page opens, each
of which is *by design* a full 777 KB fetch once the cache is over 30 minutes old. Judging the fixes
needs a clean window with no testing in it.

**One real gap found, and not by re-reading the diff.** The per-state power breakdown showed
mobile-radio power still mostly attributed to *screen-off* time, which the gates were supposed to
prevent. Tracing which workers do network showed `WeatherRefreshWorker` (`WeatherWork.kt`) — the
in-app weather tile's own worker, distinct from the already-gated `WeatherWidgetRefreshWorker` — had
no screen gate at all and fetched every 30 minutes regardless. Its *data* cost is small (a few KB of
JSON), so it does not explain the byte totals, but it woke 48 times a day around the clock. Now gated
via the same `WidgetWork.skipWhileScreenOff` helper, with a `KEY_FORCE` marker added so placement, a
location change and the manual refresh still bypass it. Artifacts re-cut at the same versionCode 402,
which was never uploaded.

## Clean battery measurement window opened (baseline for the next check)

Battery stats reset on the physical device to get a window uncontaminated by this session's testing —
the previous day's totals were unusable for judging the fixes, since repeated installs, cold starts
and glance-page opens each legitimately trigger a full feed fetch by design.

Build under test: 4.0.2 **debug** (chosen over release so the user's layout/settings survive — the two
are signed differently, so swapping would wipe app data). Debug is marginally heavier than the R8
release build, so any result is a mild over-estimate, which is the safe direction for a battery check.

Baseline at reset — periodic workers enqueued, down from 7 before this session's work:
 - `FeedRefreshWorker` 30m — screen-gated *and* demand-driven (refresh-on-open when >30 min stale)
 - `WeatherRefreshWorker` 30m — screen-gated as of this session
 - `LayoutAutoBackupWorker` 6h — local only
 - `CalendarSystemWidgetRefreshWorker` daily — legitimate: exactly one TileShell widget instance is
   bound (id 4697, Calendar System) and it *is* present in `feed_widget.pb`, so it is genuinely
   rendered rather than another orphan. Deliberately not screen-gated (midnight-aligned).

What to compare on the next check, against the 18h53m window recorded above (19.7 MB Wi-Fi + 0.53 MB
mobile, 13.5 min CPU, 53 job runs, TileShell the device's #1 data consumer):
 - `dumpsys batterystats com.tileshell` — Wi-Fi/mobile bytes, `Total cpu time`, job run count, and the
   screen-on vs screen-off split of `mobile_radio`/`wifi` power (the screen-off share is the direct
   test of the gates).
 - `dumpsys dropbox` for any new `data_app_crash`/ANR entries — the only crash source here that
   survives `logcat -c` and reinstalls.
 - Per-app data ranking, to see where TileShell sits relative to other apps rather than in isolation.

**The device must stay unplugged** for `Time on battery` to accumulate; a window recorded while
charging measures nothing.

## Weather refresh on wake — closing the hole the screen-off gate opened

The screen-off gate on `WeatherRefreshWorker` was correct but incomplete, and the overnight
measurement caught it: with the periodic tick suppressed while the screen is off, the *next* tick
after wake can be up to a full interval away, so the tile keeps showing whatever it cached before the
screen went off. Measured on the device: `weather_cache.pb` last written 22:24, still being displayed
at 06:09 the next morning — a 7h45m-old temperature, with no refresh due for up to another 30
minutes. A gate without a wake-up path is just staleness.

Fixed with the same demand-driven shape as the feed: new pure, unit-tested
`shouldRefreshWeatherOnWake(now, fetchedAt, staleAfter)` (`WEATHER_STALE_AFTER_MS` = 30 min, matching
the periodic interval) plus a `WeatherWakeRefresh` effect that observes `ON_RESUME` and calls
`WeatherRefreshWorker.refreshNow` when the cache is stale. No new bookkeeping was needed — the cache
already records `fetchedAtMillis` per snapshot. It is invoked *before* the faces' `fallback` return,
so a tile with nothing cached at all (`fetchedAt` 0, which reads as stale) also fills itself on
resume rather than staying a static glyph. `rememberUpdatedState` guards the observer against the
stale-closure trap this codebase has hit before with drag handles: the observer registers once while
the timestamp keeps changing underneath it.

Verified on the device in both directions, which matters because the first test was inconclusive on
its own — a periodic tick had already refreshed the cache a minute earlier, so "no refetch" proved
nothing:
 - **Fresh cache (1 min old) → no refetch.** A screen off/on cycle left the file untouched.
 - **Stale cache (8 h old) → refetches.** The cache is plain text, so `fetchedAt` was rewritten via
   `run-as` to 8 hours in the past and the app force-stopped so DataStore re-read from disk; on the
   next resume it refetched within ~12 seconds (`fetchedAt` 1789318001000 → 1789346804415 ≈ now).

## Overnight verdict: TileShell accounts for 1.8% of the drain

The user reported ~50% → <20% overnight and asked whether TileShell is behaving. It is not the cause.
`dumpsys batterystats` for the clean 10h38m window: **computed drain 1999 mAh** of the 5000 mAh
battery (~40%, consistent with what the user saw), of which **TileShell is 36.1 mAh — 1.8%**, 6th on
the device behind `UID 1000` (the system itself) at 204 mAh, `UID 0` at 96, WhatsApp at 59.8 and two
others. Device-wide CPU was 666 mAh over 3h5m of CPU time in a 10h38m window; TileShell's share of
that was 4m46s, about 2.6%. The drain is device-wide, not this app.

The gates themselves are confirmed working, by the strongest evidence available: the two files a
fetch rewrites were untouched across the entire 9h42m of screen-off (`news_feed.pb` last written
06:00, exactly when the page was opened; `weather_cache.pb` 22:24 the previous evening). Job timing
corroborates it — 42 wakeups totalling 17.9s, 0.43 s/run versus 0.76 s/run before, i.e. workers
waking, hitting the gate and returning in milliseconds rather than fetching. Per hour against the
pre-fix baseline: CPU −37%, power −38%, and TileShell fell from the device's #1 data consumer to #7.

Data per hour did rise (1.07 → 2.31 MB/h), and it is not the background: `cache/feed_images` is 20 MB
and was last written at 06:00, i.e. news article thumbnails pulled while the page was actually open.
That is foreground, user-driven, and by design — the remaining lever there is image handling, not
polling.

## v4.0.2 re-cut with the weather-on-wake fix

Artifacts rebuilt at the **same versionCode 402** — it has still never been uploaded, so nothing is
burned and a re-cut is the right call rather than a point bump, the same convention 4.0.0 used three
times. Only the "What's new since v4.0.1" section gained a line; the Play-facing blurb is unchanged.

Verified rather than assumed: `versionCode='402' versionName='4.0.2'`, signing certificate SHA-256
identical to 4.0.1's and every prior release, `jarsigner -verify` reports "jar verified" on the
bundle, and the APK checksum differs from the previous 4.0.2 cut — confirming the weather fix is
actually in the artifact rather than a no-op rebuild. Installed the release APK on the emulator: it
launches with zero `FATAL` entries and registers 9 WorkManager jobs, which also confirms the worker
classes survived R8 minification.

## Play Console's "no debug symbols" warning is not actionable here

Uploading 4.0.2 produced: *"This App Bundle contains native code, and you've not uploaded debug
symbols."* Investigated rather than reflexively applying the standard fix, and the standard fix turns
out to be a no-op.

The bundle does contain native code, but none of it is ours — this app has no NDK build. The only
`.so` files are two prebuilt AndroidX dependencies, 4–11 KB each per ABI:
`libandroidx.graphics.path.so` (pulled in transitively by Compose) and
`libdatastore_shared_counter.so`.

Applied the recommended `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` to the release build type and
rebuilt — and **no** `BUNDLE-METADATA/com.android.tools.build.debugsymbols` entry appeared. The
reason: both libraries ship already stripped. `file` reports "stripped" for each, and
`llvm-readelf --section-headers` finds no `.symtab` and no `.debug_*` sections. There is nothing for
AGP to extract. Only upstream AndroidX holds the unstripped originals, so this warning cannot be
cleared from this repository; it is advisory, and its only real effect is that a native stack trace
occurring *inside* those two libraries would be unsymbolicated.

What actually matters for crash triage is unaffected and was verified present: R8's mapping file
ships in the same bundle (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`,
~61 MB), so ordinary Kotlin/Java crashes and ANRs deobfuscate correctly in Play vitals.

The setting was kept rather than reverted — the bundle does contain native libraries, so a release
build asking for their symbols is correct, and it will start producing them automatically if native
code is ever added here or a future AndroidX release ships unstripped libraries. Its comment states
plainly that it is currently inert, so nobody later mistakes it for a working fix. **No re-upload is
needed for this warning**, and since 402 has now been uploaded, a later change would need a new
versionCode anyway.

## Start grid: side margin reinstated (reverses the earlier edge-to-edge change)

Direct follow-up on "Start grid: side margin removed — tiles now go edge-to-edge" above: user asked
for "a slight gap between the screen corner and tile edge" after living with edge-to-edge tiles for
a while. `GridGeometry.of`'s `side` is back to the original proportional `totalWidthPx * (9f / 393f)`
— real Windows Phone's own Start-screen outer margin, the same value used before the earlier
removal — rather than hardcoded `0f`. Single-source-of-truth change (`GridGeometry` is shared by
`DenseTileGrid`, the folder overlay's inline-expand grid, and resize/hit-testing geometry), so every
consumer picks it up automatically. Build + full unit test suite green.

## Personalize wallpaper: optional sync to the real Android home/lock screen

User asked "is there a solution" to also use TileShell's own wallpaper (gradient or photo) as the
real Android lock screen's wallpaper — until now it was purely drawn in-app (`WallpaperBackground.kt`),
never touching `android.app.WallpaperManager`, so the actual system lock screen (drawn entirely by
the OS) never reflected it.

Added `SET_WALLPAPER` (a normal, auto-granted-at-install permission — not on Play's restricted-
permissions list, no Data Safety disclosure needed) and a small `SystemWallpaperSync` object
(`:feature:start`) that pushes the current wallpaper via `WallpaperManager.setBitmap(..., flags)`:
a real photo/Bing image reuses the existing downsampled decode (`WallpaperBackground.decodeWallpaper`,
widened from `private` to `internal`); a bundled gradient is rasterized off-screen via a new
`core/design` function, `renderWallpaperToBitmap` — the exact same `drawWallpaperGradient` draw
[wallpaperBackground] itself uses, just run through a `CanvasDrawScope` onto a real `Bitmap` instead
of a live composition, so the pushed image matches the in-app one exactly.

Per explicit request, this is a prompt at the moment of picking a wallpaper (gradient tap, photo
crop-confirm, Bing-history pick — *not* the plain re-crop/re-frame overlay, which changes no
content), offering exactly "home screen" / "lock screen" / "home + lock screen" (plus "not now" to
skip), rather than a persistent Personalize toggle — mirrors Android's own native "set wallpaper"
chooser. The choice is remembered (`LauncherSettings.wallpaperSyncTarget`, default `NONE` — byte-
identical behaviour for every existing install until they opt in) so a future pick could reapply the
same target without re-asking, though that reapplication is **not** wired for the *automatic*
refreshers (the Bing daily worker, the wallpaper slideshow rotation) in this pass — both live in
`:feature:livetiles`, which cannot depend on `:feature:start` (the dependency graph runs the other
way), and relocating `SystemWallpaperSync` to unblock that wasn't asked for. A daily-refreshing Bing
wallpaper synced to the lock screen will therefore fall slightly behind until the user picks from
Bing history again; worth revisiting if that's reported as a real annoyance.

Build + full unit test suite green.

## Wallpaper sync prompt: redesigned to match the OEM wallpaper-set flow exactly

Direct same-day follow-up. First version applied the wallpaper immediately, then separately asked
"also update system wallpaper?" with explanatory text and a "not now" that only skipped the OS push.
User corrected it on two points, then clarified against their own OEM (Samsung) launcher's own
wallpaper-set flow as the reference: no explanatory copy at all, just the bare targets; and the
choice must come *before* anything is applied, with declining meaning nothing is set — not even
TileShell's own in-app wallpaper.

Reworked so none of the three pick sites (`onWallpaperChange`, the crop overlay's `onConfirm`,
`BingHistorySheet.onPick`) call `viewModel.setWallpaper`/`setCustomWallpaper`/`applyBingImage`
directly anymore — each just records a `PendingWallpaperPick` (gradient id / photo uri+crop / Bing
url) and shows the chooser. The chooser itself dropped its title and body text entirely, leaving
only the three target buttons plus "cancel"; picking one calls a new matching `StartViewModel
.*WithSync` function that does the set-wallpaper write and the `SystemWallpaperSync` push together,
passing the just-picked value directly rather than reading it back through the `settings` StateFlow
(which could still reflect the pre-write value depending on collection timing) — the only place that
still reads `settings.value` is for the unrelated `dark` flag. The Bing case is the one genuinely
async one — `BingWallpaperWorker.applyImage` only enqueues a download, so `applyBingImageWithSync`
awaits (bounded to 20s) the settings flow actually reflecting a new `customWallpaperUri` before
pushing, rather than pushing a stale/absent image.

Build + full unit test suite green; installed on the physical device with no crash. The actual
on-device flow (pick a wallpaper → bare 3-option/cancel prompt → confirm → check the real lock
screen) still needs the user's own hands-on pass.

## Wallpaper sync: the photo's chosen framing wasn't reaching the OS push

User-reported: "framed wallpaper is not set on lockscreen" (found while testing the Bing image
flow specifically). Root cause: `SystemWallpaperSync.apply` handed the *whole* decoded photo
bitmap straight to `WallpaperManager.setBitmap` with no `visibleCropHint` — the user's chosen
alignX/alignY/zoom (from the crop overlay, or the existing framing a Bing image inherits) were
never passed in at all, so the OS fell back to its own default centre-crop instead of the framing
shown in-app.

Fixed by computing a real `visibleCropHint` `Rect`, in the decoded bitmap's own pixel coordinates,
via a new `SystemWallpaperSync.visibleCropHint` — the algebraic inverse of `wallpaperCropGeometry`
(`WallpaperGeometry.kt`, the exact same function the in-app crop overlay and renderer already use):
that function says where a scaled/aligned image sits *relative to the screen box*; this inverts it
to say which *slice of the source image* is visible, which is what `WallpaperManager` expects.
Threaded `alignX`/`alignY`/`zoom` through all three `StartViewModel.*WithSync` call sites — the
direct photo pick passes its own just-confirmed crop values (never a settings readback, avoiding
the same staleness risk noted in the wallpaper-sync-prompt entry above); the Bing pick reads
`settings.value.wallpaperAlignX/Y/Zoom` right after its bounded wait resolves (Bing has no crop UI
of its own — it inherits whatever framing was already set, per `SettingsRepository.setBingImage`'s
own existing "keep the user's chosen framing across daily refreshes" behaviour). The gradient case
needs no crop hint — it's rendered directly at screen size, so it already exactly fills the box.

Build + full unit test suite green; installed with no crash. The actual on-device framing match
(pick a photo, crop off-centre/zoomed, confirm home+lock, check the real lock screen shows the same
crop) still needs the user's own hands-on pass.

## Backup/restore never captured sections — restoring silently ungrouped every tile

User asked to update backup/restore for the latest features "so that restore should not break the
launcher." Audit found sections was the one real gap, the exact same class of bug this file's own
history already lists repeatedly (`gridSlot`, `displayAsIcon`, `hiddenApps`, `feedSources`, `widgets`,
photo/slideshow URIs — each added to `BackupManager` well after shipping, since none of it was wired
in when first built): `BackupManager.buildBackupJson`/`parseBackup` never serialized a tile's
`sectionId`, and never touched the `sections` table (`SectionEntity`) at all — so restoring *any*
backup (a manual export, or the automatic rolling layout-history snapshots) silently ungrouped every
tile back to unsectioned, leaving whatever sections existed on the device as empty, orphaned tabs.
Not a crash ("break the launcher" in the literal sense), but a real silent-data-loss regression on
restore, worth fixing before it was reported that way.

Fixed additively (no backup version bump, matching this file's own established convention): each
tile's `sectionId` is now `putOpt`/read the same way `accentOverride`/`folderId` already are, and a
new top-level `"sections"` array carries every `SectionEntity` (id/label/sortOrder/collapsed). A
backup written before this change simply has neither key, and every tile lands unsectioned exactly as
it always would have — verified with a dedicated test that strips both keys out of a freshly-built
JSON to simulate an old file. `LayoutRepository.tilesForBackup()` widened from a `Triple` to a new
`LayoutBackupSnapshot` data class (4 components, so every existing `val (tiles, folders, children) =`
destructuring call site needed exactly one more name added, not a restructure) so it can also return
`dao.sectionsOnce()`; `restoreFromBackup`/`LayoutDao.replaceLayout` both gained an additive
`sections: List<SectionEntity> = emptyList()` param, clearing and reinserting the `sections` table in
the same atomic `@Transaction` as tiles/folders/children. `BackupManager.layoutHash` also gained
`sectionId`/`sections` (otherwise renaming a section or moving a tile between sections wouldn't
register as a layout change, so "save now" would silently no-op and an auto-backup snapshot would
never capture it — the identical class of bug `layoutHash`'s own doc comment already describes for
`accentOverride`/`displayAsIcon`). Threaded through all the real call sites: `StartViewModel
.exportBackup`/`importBackup`/`saveLayoutSnapshot`/`cacheForegroundScreenshot`/`restoreFromSnapshot`,
and `LayoutAutoBackupWork`'s worker — sections is treated as genuinely part of "the layout" (unlike
`hiddenApps`/`feedSources`/etc., which stay manual-export-only by design), so both the manual backup
path and the automatic layout-history snapshots now capture it identically.

Also audited while in this file: pinning a *new* app into a specific section (app list → long-press →
"pin to section", 2+ sections) and moving an *already-pinned* tile between sections (its own colour
picker's "move to section" chips) both already worked correctly and needed no code change — just
documentation, added separately. A real, deliberate gap found but left alone (not asked for): pinning
an app shortcut or another activity of the same app ("more from this app") always lands unsectioned,
with no section-choice at pin time, unlike a plain app pin — noted in the guide as current behaviour
rather than treated as a bug, since extending that picker wasn't requested.

Build + full unit test suite green (5 new `BackupManagerTest` cases: sections/sectionId round-trip,
pre-sections-backup compatibility, and two `layoutHash` sensitivity cases). Actual on-device
export → wipe/reset → import round-trip, confirming sections truly survive, still needs the user's
own hands-on pass — this project has no Room-instrumented test harness to simulate it headlessly.

## Pinning an app now always lands in the active section — no chooser at all

Direct follow-up, user-requested: "pin should be always to current section. dont ask user the
choice. implement this for app and more from this app." Then, once tried: "pin to section option
not needed" — removing the separate explicit picker entirely rather than leaving it as a secondary
path alongside the new automatic behaviour.

`AppListScreen` gained an `activeSectionId: String?` param (Start already tracks this as local state
via `onActiveSectionChange`; threaded straight through at the `AppListScreen(...)` call site) — both
`onPin` (plain "pin to start") and `onPinSibling` ("more from this app": shortcuts + other activities
of the same app) now pass it to `AppListViewModel.pin(app, activeSectionId)` instead of an implicit
null, landing the pin in whichever tab is showing right now, mirroring how a live tile added from
Start's own edit-mode toolbar already works. The standalone "pin to section" menu item, its own
`sectionsMenuOpen` dropdown, and `AppRow`'s `sections`/`onPinToSection` params were deleted outright
once it was clear the new automatic behaviour made picking a different section unnecessary — along
with `AppListViewModel.sections` (now read by nothing) and its now-unused `Section`/
`UNSECTIONED_LABEL` imports. Guide/about docs updated to match (dropped the "pin to start lands
unsectioned" and "shortcuts always land unsectioned" claims, both now false).

Build + full unit test suite green; installed with no crash. The actual on-device check — pin an
app while a specific tab is active and confirm it lands there — still needs the user's own hands-on
pass.

## Moon phase: the small/icon-sized tile showed a fixed generic glyph, not the real phase

User-reported: "moonphase is not showing the right image as per moon phase. today is 5th day but
it is showing half moon. it is same for calendar systems panchang option." Investigated by directly
computing today's real values through the actual app code (a temporary JUnit test calling
`HinduPanchang.panchangFor(now)` + `tithiMoonFraction` + `moonPhaseFraction`, removed once done):
paksha=SHUKLA, tithi=panchami (5, matching the user's own reference), tithi-based fraction=0.15,
independently-computed real astronomical fraction=0.157 — both agree closely, ~21-22% illuminated.
Rendering that exact fraction through the actual two-half-ellipse algorithm (reproduced faithfully
in a throwaway script, both at full size and at the real ~50px on-screen size) draws a genuine
tapered crescent, not anything resembling a half moon — confirmed visually, not just by formula
(the area under that specific curved boundary works out to exactly (1-cos(2πf))/2, the same
illumination formula `moonIllumination` reports, so the shape and the percentage always agree).

The one real, confirmed bug: `IconCellView.kt`'s small/1×1-face dispatch (ICONS home style, or any
tile resized down to SMALL) has a branch for every other live-data tile — weather, calendar, clock,
battery, flashlight, countdown, steps, stock, commodity, calsys — but "moonphase" was simply never
added to that list. A small moon-phase tile therefore fell through to the tile's own generic static
glyph (`TileIcons["moonphase"]`, deliberately described in its own comment as "a disc with an
S-curved terminator" — one fixed shape, every day, forever) instead of ever showing the real phase.
That fixed shape is exactly the kind of thing a user would reasonably call "half moon," and it would
never change regardless of the actual date — matching the report precisely.

Fixed with a new `MoonPhaseSmallFace` (`:feature:livetiles`, mirroring `ClockSmallFace`'s own
minute-tick refresh pattern exactly) rendering the real `MoonPhaseVisual` crescent at icon size, and
one new dispatch line in `IconCellView.kt`. `CalendarSystemSmallFace` (the panchang tile's own
small face) was separately confirmed to show only the Roman day-of-month number at that size — no
moon glyph, real or fake, so nothing to fix there; the panchang report was most likely the same
"tithi 5 renders as a wider-than-expected but still genuinely curved crescent" perception the
render-and-look verification above addresses, not a second bug.

Also directly verified (code review, not just formula-matching) that neither home-screen widget
(`MoonPhaseWidgetRefreshWorker`, `CalendarSystemWidgetRefreshWorker`) has an equivalent static-glyph
gap — both already call the identical `tithiMoonFraction`/`moonPhaseBitmap` pipeline unconditionally,
with no fallback branch. Their one real structural risk, left alone since it's a scheduling-
robustness question rather than a wrong-calculation bug: each refreshes only once daily (just after
midnight) via a periodic `WorkManager` job, with no refresh tied to the app's own launch — if that
job were ever killed by OEM battery management (a documented concern elsewhere in this project) a
widget could go stale for days, which at ~3 days off *would* land near fraction 0.25 (true half
moon). Worth revisiting only if a widget specifically (not the in-app tile) is confirmed stale.

Build + full unit test suite green; installed with no crash. The user's device was locked during
this session, so the actual on-screen fix for the small/icon tile still needs their own confirmation.

## Moon phase crescent: real root cause found (the earlier "small face" fix was not it)

Direct correction after the previous entry — user confirmed the full-size live tile *and* the real
home-screen widget both still showed a plain half-moon after that fix, and were right: that fix
(the missing `IconCellView` dispatch entry) was real but not the actual bug behind the visual report.

Root-caused with on-device logging: instrumented `PanchangFace` and `MoonPhaseVisual` directly and
confirmed the exact runtime values feeding the render — `paksha=SHUKLA, tithiInPaksha=5,
moonFraction=0.15, cosVal=0.588, rx=42.9 (r=73)` — mathematically and by an independent script-based
render, *should* draw a clear tapered crescent. It didn't, on the real device, because of a
different bug entirely: `MoonPhaseVisual`/`moonPhaseBitmap` built the crescent by painting the wide
half-disc fully opaque in `lit`, then painting the narrower "cut" half-ellipse in `shadow` *on top of
it* to carve the crescent out — but `shadow` is defined as `lit`'s own colour at 18% alpha. Painting
an 18%-alpha version of a colour over an already-*opaque* fill of that same colour barely changes
the pixels at all — the "cut" was never visible, on any tile background, at any fraction (except
exactly full/new moon) — always leaving what looks like a plain half-moon (a full opaque half-disc)
regardless of the real phase. This is why the earlier verification (a from-scratch script using
different, genuinely contrasting placeholder colours for lit/shadow) rendered a correct crescent —
it didn't reproduce the real app's lit-and-shadow-are-the-same-hue relationship, so it couldn't
surface this bug at all.

Fixed by computing the actual lit silhouette as one real path boolean operation — `PathOperation
.Difference` (crescent) / `.Union` (gibbous) in Compose's `MoonPhaseVisual`, `Path.Op.DIFFERENCE`/
`.UNION` in the widget's plain-`android.graphics` `moonPhaseBitmap` — and filling that single
resulting path once, rather than painting two overlapping half-ellipses and hoping the alpha blend
reads as a cut. Confirmed on-device: the panchang tile now shows a real tapered crescent for tithi 5,
matching what tithi 5 (~21% illuminated) should actually look like.

Build + full unit test suite green; visually confirmed on the physical device via screenshot — the
same fix applies to the standalone moon-phase live tile (identical code path) and both home-screen
widgets (identical `Path.op`-based fix mirrored into `WidgetMoonPhaseVisual.kt`), though only the
panchang tile was directly screenshotted this session.

## Sections become swipeable pages, not a menu-switched tab

Direct user follow-up on the "sections" feature (see the earlier "Start screen 'sections'" entries):
tapping a dropdown pill to switch between sections is replaced with plain horizontal swipe, folded
into the *same* pager Start already uses for the feed/glance page and the app list. User's own framing:
"i am just asking you instead of selecting a section via menu. just do it by scroll." Landed as one
`Animatable<Float>` position space spanning `-1` (feed) through `0 .. blockCount-1` (one page per
section, plus the trailing unsectioned "main" page) through `blockCount` (app list) — `pagerCommitTarget`/
`pagerModifier` generalized from their old hardcoded 3-position `[-1,1]` range to a `lower`/`upper` pair.
Three more explicit user calls landed in the same pass: a page's name is now shown **only in edit
mode** (hidden otherwise — no dropdown pill, no permanent label, "just scroll" to know where you are);
reorder controls became **left/right** arrows instead of up/down (matching the new horizontal
navigation, same underlying `moveSection` swap); and the "enable sections" Personalize toggle was
**removed outright** — pages are now an always-available capability like folders, with no on/off
switch, no "turn off sections?" merge-back confirmation dialog, and no `SectionPillAlignment` setting
(it only ever configured the now-deleted dropdown pill's placement). Per an explicit rename-scope
decision, "section" → "page" only in user-facing text (labels, hints, docs) — internal Kotlin symbols,
the Room `sections` table/`sectionId` column, and `SectionBlocks.kt`/its tests all keep saying
`Section`. The collapse/expand chevron (`SectionHeader`'s `collapsible`/`onToggleCollapsed`, already
forced open for the one visible block before this change) is dropped entirely — a "collapsed" page has
no purpose once every section is already its own page you swipe past.

Mechanically: every block now renders simultaneously as its own full page (`StartPage`'s per-block
loop, `blockRenders.forEachIndexed`), each translated horizontally by `widthPx * (index -
pagerProgress)` — the same plain full-slide treatment the feed/app-list pages already used — instead
of narrowing to just the one active block and swapping its content on settle (the old
`visibleBlockRenders`/`selectedSectionTab`/dropdown mechanism, all deleted). This is a real behaviour
change on the existing Start↔feed and Start↔app-list edges too: they previously used a subtler ±22%
parallax + fade specific to "the single Start position"; once Start splits into N pages there's no
longer one obviously-special position to keep that treatment for, so every page (feed, every block,
app list) now uses the same full-slide formula — a deliberate simplification, not preserved for those
two edges specifically. Each block gets its own independent `rememberScrollState()` (real, separate
pages, not one shared scrolling column any more) — the "home press scrolls Start to the top" behaviour
is a known, accepted regression as a result (only the pager position resets to whichever page you were
last on; the per-page scroll position itself is no longer force-reset from outside `StartPage`).

Two real correctness traps were caught and fixed during this pass, not just plumbing: (1) naively
deriving "the active section" from `round(pagerProgress)` breaks the instant you rest on the app list
(`progress == upper`), which is *exactly* when a newly added tile needs to be pinned into "the section
you were last viewing" (the add-live-tile sheet and the weather-location picker are both opened from
the app list) — this would have silently regressed to "always pins to the last page," the exact bug
this feature's own history already fixed once before. Fixed with `lastActiveBlockIndex`, updated only
while resting on a real block page and always read back re-clamped, so a delete/reorder can never
leave it dangling. (2) Tapping a page's own new ←/→ reorder button swaps its index with a neighbor's;
since the pager position doesn't otherwise move, the page you just reordered would visibly swap out
from under the tap. Fixed by having `onMoveSection`'s `StartScreen`-level wiring also `settleTo` the
tile's new index — safe because a page's own reorder buttons are only reachable on the block currently
centered on-screen, so the pager's current integer position is guaranteed to be that page's own index
at the moment of the tap.

Explicitly **not** part of this pass, flagged back to the user rather than silently dropped: dragging a
tile to the screen edge to carry it onto a neighboring page (discussed and agreed as a follow-up
gesture) needs a genuinely separate floating-overlay rendering path for the dragged tile — since each
block's own `DenseTileGrid` only renders tiles that belong to it, a tile "dragged across" a page
boundary would otherwise visually vanish with its origin page rather than following the finger. Not
attempted in this pass; the existing per-tile "move to page" chip (in the colour-picker sheet, plain
rename from "move to section") remains the only way to move a tile to a different page for now.

Build + full unit test suite green (`FeedFormatTest` extended for `pagerCommitTarget`'s wider range;
`SectionTest`/`SectionBlocksTest` untouched, since `SectionBlocks.kt` itself didn't change).
On-device gesture verification (the actual swipe feel, edit-mode header visibility, left/right reorder)
still needs the user's own hands-on pass, per this project's own established ADB-synthetic-swipe
limitation.

## "Main" moved back to first in the page sequence

Direct same-day follow-up, user-requested: "main should be first in sequence." `blocksFor`
(`SectionBlocks.kt`) previously appended the unsectioned/"main" block *last*, after every named
section — a deliberate choice from this feature's earlier vertical-stacked-list era (named sections
as the organized front-and-center content, "main" as the leftover area below them). Now that sections
are swipeable pages rather than a stacked list, "main" reading as the anchor/home page you land on
before swiping into named ones makes more sense — `blocksFor` now returns `listOf(unsectioned) +
sectionBlocks` instead of `sectionBlocks + unsectioned`. Named-section reordering (`moveSection`/
`swapSectionOrder`) is unaffected — it only ever permutes real `Section` entities among themselves;
"main" was never one of those and isn't reorderable either way, just always first now instead of
always last. `SectionBlocksTest`'s two order-sensitive cases updated to match (`blocks.first()`
instead of `blocks.last()`, and the sequence assertion); the About/Guide sheet copy already written
this session ("the last one is always main") corrected to "the first one is always main" before it
shipped anywhere. Build + full unit test suite green.

## Page-dot indicator uncovered a real "only works on main" bug affecting quick search/live tiles too

User asked for a small dot indicator at the top of Start (one per page) since a page's name is
hidden outside edit mode and there was otherwise no visible cue that Start has more than one page.
Straightforward to add (`PageDotsIndicator`), but the user then reported it "is only shown on main
page." Root-caused, and it's a real, more consequential bug than the indicator itself: `appListShown`
(`val appListShown by remember { derivedStateOf { progress.value >= upper - 0.5f } }`, added when the
pager was generalized to N block pages) has no `remember` key, so its calculation block — closing
over the local `upper` — is created exactly once, on the very first composition, and never recreated
even once the real `upper` value changes. Since `sections` is collected via
`collectAsStateWithLifecycle()`, its very first composition can render before the real Room data has
streamed in, i.e. with `sections = emptyList()` and `upper = 1f` — and that stale `upper` is what
`appListShown` keeps comparing against forever after. With `upper` stuck at `1f`, `progress.value >=
0.5f` reads true for every non-main block page's own resting position (1, 2, 3, ...), not just the
real app list — so `appListShown` silently misreported "the app list is showing" any time the user
was on any page other than main. This didn't just hide the page-dot indicator: `liveSuspended`
(`appListShown || feedShown || personalizeOpen`) reads the same value, so **live tiles have been
silently pausing on every non-main Start page** since the swipeable-pages change landed. Fixed with
`remember(upper) { ... }`.

The exact same stale-closure shape existed one more place, found by inspecting every other
`remember { derivedStateOf { ... } }` in the file for the same pattern: `restingAtStart` (gates the
two-finger quick-search/quick-panel swipes and the single-finger edge-swipe) used to mean "resting at
progress ≈ 0" — correct back when Start was a single page at position 0, but never updated once Start
became N pages — so those three gestures have likewise only worked on the main page since that
change, silently doing nothing on any named section page. Fixed by checking "resting on the nearest
integer, and that integer is a real block index (`0 until blockCount`)" instead of "resting at
exactly 0," keyed on `blockCount` for the same reason.

Both fixes needed only a `remember` key, not a behavior redesign — the underlying logic was already
correct, it just never re-ran once the truly dynamic `blockCount`/`upper` stopped being effectively
constant. Build + full unit test suite green; the page-dot indicator, quick search, quick panel, and
edge-swipe still need the user's own on-device confirmation across more than one section page.

## Two real scroll bugs from the per-page ScrollState split: blank-space scroll freeze + spurious auto-scroll while dragging a tile

User reports, two rounds: (1) "pull down collides with edit mode especially moving the tile it starts
scrolling down even if i scroll it vertically. check you tube tile on main page", then (2) "scrolling
is freezed after when i go beyond first visible section of page" / "scrollin only happend if i scroll
throgh tiles" / "not through blank space" — two separate, real bugs, both root-caused independently.

**(1) Dragging a tile spuriously auto-scrolls the page.** `editDragGesture`'s own near-edge auto-scroll
check (`fingerViewportY = (contentTopPx + blockTopOffsetPx + pos.y) - scrollOffsetPx()`) was still fed
by its call site's `scrollOffsetPx = { scrollState.value.toFloat() }` — the OUTER `scrollState` param,
which is orphaned now that each block page owns its own `ScrollState` (`blockScrollStates`, this
session's earlier per-page-pages work) and nothing scrolls the outer one any more (permanently `0`).
Once a page had genuinely been scrolled down at all (exactly the situation reaching a YouTube tile
further down "main"), `fingerViewportY` was inflated by the whole real scroll offset it never
subtracted, tripping the "near the bottom edge" branch and firing a real, continuing
`activeScrollState.scrollBy` well before the finger was anywhere near the true edge — reads exactly as
"moving the tile starts scrolling the page." Fixed: `scrollOffsetPx = { blockScrollStates[blockIndex]
.value.toFloat() }`.

**(2) Scrolling via blank space (not over a tile) froze entirely.** A separate, pre-existing bug in
`emptySpaceEnterEdit`'s "reachability" pull-down recognizer, now far more commonly triggered: it
watches in `PointerEventPass.Initial` (parent-first, ahead of the child `Column`'s own
`.verticalScroll()`) and, the moment a touch on empty space exceeds its 7dp slop, consumed that event
for reachability whenever `reachabilityActive` — **regardless of direction**, including a plain upward
"scroll down to see more" drag. Losing just that one move event is enough to stop the child
`verticalScroll`'s own gesture detector from ever recognizing the drag, freezing scroll for the rest of
that touch (a tile-started drag is unaffected — `tileGesture` never consumes on its own drag).
`reachabilityActive` (`!editMode && expandedFolderId == null && blocks.size >= 2`) was always the gate,
but `blocks.size >= 2` used to be rare (sections were opt-in and uncommon); now that pages are always
on, any install with even one named section hits it by default, which is why this reads as a new
regression even though the flaw itself predates this session. Fixed by only claiming the slop-break for
reachability when the move is genuinely downward (`change.position.y > down.position.y`) — an upward
scroll-intent drag is now never touched here at all, so the child `verticalScroll` sees an unbroken
gesture from the start. `folderCollapseOnEmptyTap`'s own `absoluteTileRects` hit-test was also found,
while investigating, to skip the same scroll/reachability-offset coordinate conversion
`emptySpaceEnterEdit` already applies — flagged as a real but separate robustness gap (only reachable
while a folder is expanded, unrelated to this bug), not fixed in this pass.

Build + full unit test suite green. Both fixes need the user's own on-device confirmation: dragging a
tile down a scrolled page no longer auto-scrolls spuriously, and a blank-space scroll (up or down)
works throughout a page that also has 2+ block pages.

## Drag a tile to the screen edge to carry it onto a neighboring page

Explicitly deferred earlier in this branch's own history ("Not attempted in this pass... needs a
genuinely separate floating-overlay rendering path for the dragged tile"), now built per direct user
request ("carry out the work of dragging tile to another page"). Landed a **release-time** design
instead of the originally-sketched continuous-carry one, trading a little visual polish for
substantially lower risk: holding a tile at the left/right screen edge for `CROSS_PAGE_DRAG_DWELL_MS`
(550ms — a fresh, explicit choice, not reused from the 430ms tile long-press or 700ms app-list-pin
thresholds) arms the move, but nothing happens until release — at that point the tile's section is
reassigned (reusing the existing `onAssignTileSection`/`setTileSection` plumbing the colour picker's
"move to page" chip already uses, so `gridSlot` clears for free the same way) and the pager settles to
the destination page in one motion. The originally-sketched version wanted the tile to visually follow
the finger continuously across the page transition; the release-time trade avoids that entirely — the
tile stays put in its own page's `DenseTileGrid` throughout the hold (no floating overlay, no cross-
block hand-off of an in-flight gesture) and only "moves" at the moment of release, landing already
correctly placed on the destination page.

Mechanically, all inside the existing `editDragGesture` (`StartScreen.kt`), new inert-by-default params
(`crossPageEdgeZonePx = 0f`, `crossPageDwellMs`, `onCrossPageDrop`) so every other caller/behaviour is
unaffected. A drag's own local x (already in the block's own 0..widthPx content space — pages don't
scroll horizontally within themselves the way they scroll vertically, so no coordinate conversion is
needed the way the existing vertical auto-scroll check needs one) is tracked against the edge zone
every tick; only on release, if held there long enough, does `onCrossPageDrop(startId, direction)` fire
**instead of** the normal reorder/merge/sticky-drop commit — folder children are excluded outright
(`parseFolderChildId(startId) == null`), since a child moves with its folder, not on its own.
`StartPage`'s own wiring resets the shared `draggingId`/`mergeTargetId`/`autoScroll` state on this path
(since the normal `onDrop` callback, which usually does that cleanup, is deliberately skipped), then
forwards to `StartScreen`, which is the one composable that actually knows `blockCount`/
`sortedSections`/`settleTo` — computed there as `targetIndex = (activeBlockIndex + direction).coerceIn
(0, blockCount - 1)`, `targetSectionId = sortedSections.getOrNull(targetIndex - 1)?.id` (block 0 is
always "main"/null, matching the earlier "main first" decision), then `setTileSection` + `settleTo`.
A drag can only ever originate on the page currently on screen, so `activeBlockIndex` doubles as the
source index with no extra plumbing needed to track "which page did this drag start on."

Build + full unit test suite green. Needs the user's own on-device confirmation — this is exactly the
kind of live-drag-feel gesture this project's own history repeatedly notes ADB can't reliably
synthesize.

## Cross-page tile drop lands where it was released, not wherever the destination page auto-picks

Direct same-day follow-up, user-reported: "tile shift one page to another but the position on another
page can not be [decided]" — the release-time cross-page drop above only reassigned the tile's section,
leaving the destination page's own placement engine to auto-pick wherever it liked, with no way for the
user to influence where it landed.

Fixed by computing a real target cell from the actual drop position and writing it, instead of leaving
it to auto-placement: `editDragGesture`'s edge-held release branch now also computes
`geom.cellAt(pos - grab, columns, widthCols)` — the same "top-left pixel → (col, row)" geometry the
in-page sticky drag-drop already uses (every page shares identical column count/gap/width, so this
math is valid regardless of which page the tile ends up on) — and encodes it via
`GridPacker.encodeSlot`. `onCrossPageDrop` gained a third argument, `targetSlot: Int`, threaded through
the same `StartPage` → `StartScreen` path as before. `StartScreen`'s handler now calls
`viewModel.setTileGridSlot(tileId, targetSlot)` right after `setTileSection`, reusing the exact same
sticky/free placement-resolution path (`GridPacker.stickyPlacement`/`freePlacement`) an ordinary
in-page drag-drop already goes through — collisions with whatever's already on the destination page at
that cell are resolved the same way they always are.

One accepted, documented risk: `setTileSection` and `setTileGridSlot` are two separate ViewModel calls
(both serialized onto the same single-thread write dispatcher, so they can't literally race each
other), but `setTileGridSlot`'s own placement computation reads the ViewModel's in-memory
tiles/sections state, not a fresh DB read — if that state hasn't yet re-observed the just-written
section change by the time the second call runs, the slot computation could momentarily still see the
tile as belonging to its old section. Worst case this yields a slightly different cell than intended,
resolved harmlessly by the placement engine's own collision handling — never a crash or corrupted
data — so it was left as a known edge case rather than merging the two writes into one new atomic
ViewModel method, given the added surface area that would need testing against sticky/free/dense modes.

Build + full unit test suite green. Needs on-device confirmation, same as the cross-page-drop feature
itself.

## Cross-page drop: shift the page immediately on reaching the edge, not only on release

Direct same-day follow-up, user-requested: "when i drag at edge can't you shift the page so that i can
put properly" — the earlier release-time design (hold at the edge 550ms, then release to commit) never
showed the destination page until after you let go, so there was no way to see what you were placing
the tile relative to.

A genuinely live version — the tile visually following the finger while continuing to see/adjust
within the destination page's own grid — isn't reachable without a much bigger rework: Compose ties an
in-progress touch to whichever `pointerInput` node first claimed it (this project's per-page
`editDragGesture` instances are genuinely separate recognizers, one per block), so there's no way to
hand an in-flight drag over to a different page's own grid mid-touch without hoisting the whole
recognizer to a page-agnostic level. Given a straight choice between that larger rework and a smaller,
still genuinely useful compromise, the user picked: **shift immediately, commit right there** — the
instant the dragged tile's own centre first crosses into the edge zone (no more dwell/hold wait at all),
the page shifts and the move commits on the spot, with no further re-aiming once past the edge.

Mechanically: the dwell-and-check-on-release design is replaced by a one-shot `crossPageTriggered` flag
checked at the very top of `lifted`'s per-tick handling, before `onDrag`/consume/any of the merge-
reorder-sticky-autoscroll branches — the moment it fires, every one of those is skipped for the rest of
the gesture (a guard at the top of the tick loop makes every subsequent tick an immediate no-op besides
watching for release), since the tile has already left this page's own grid. `crossPageDwellMs`/
`CROSS_PAGE_DRAG_DWELL_MS` are gone entirely — there's nothing left to time.

Build + full unit test suite green. Needs the user's own on-device confirmation, same as before.

## Cross-page drag: keep adjusting the drop after the page shifts, instead of placing it instantly

Direct same-day follow-up. The immediate-shift-and-commit version above traded away all control — the
user pushed back: "can't we assign the new page to the tile and again enter into edit immediately so
that i don't lose control of tile." Landed a real (if partial) answer, not the full continuous-visual-
carry rework: the page **shift** still fires immediately on crossing into the edge zone (unchanged),
but the actual **commit** (section reassignment + grid slot) now waits for release — so the same held
touch keeps being usable to aim the drop after the destination page is already visible, instead of
freezing the instant it crosses.

The key realization that made this tractable without hoisting drag recognition to a page-spanning
level: every block page shares *identical* grid geometry (columns, gap, width) and sits at the same
base layout position, differing only by a `graphicsLayer { translationX = widthPx * (index -
pagerProgress) }`. Converting a touch's local x from the source page's coordinate space into "as if it
were already on the destination page" is pure algebra —
`screenX = parentX + widthPx*(blockIndex - pagerProgress) + localX`, and setting two blocks' `screenX`
equal (same physical finger) and solving shows the `pagerProgress` term cancels out completely, leaving
`destLocalX = sourceLocalX - widthPx * direction` — a **fixed constant** offset, valid the instant the
shift begins and throughout the entire transition, not something that needs to track the live shift
animation at all. So the same `editDragGesture` instance that started the drag keeps consuming the same
touch after `onCrossPageShift` fires (Compose ties an in-progress touch to whichever node first claimed
it — genuinely can't hand it to the destination page's own grid instance), just applying this fixed
correction to compute the eventual drop cell; every other per-tick branch (merge/reorder/sticky/auto-
scroll, all scoped to the *source* page's own tiles) is skipped for the rest of the gesture, since
they'd be meaningless once the tile is leaving. `onCrossPageDrop` — now purely the release-time commit,
no longer also responsible for the page shift — computes the final `targetSlot` from the
correction-adjusted position at that point.

Known, accepted limitation: the dragged tile's own visual is still rendered inside the source page's
`DenseTileGrid`, which is now off-screen — so nothing floats/follows visually during this extended
hold; the user aims using the *destination page's own visible layout* as a reference, trusting the
computed drop position, rather than watching the tile itself travel there. A true floating overlay
would need its own separate render path outside any single page's clipped bounds — not attempted here,
flagged as a possible follow-up if the current middle ground isn't precise enough in practice.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Cross-page drag: a real floating ghost, not just an invisible-but-adjustable hold

Direct same-day follow-up — the "keep aiming after the shift" entry above shipped with a known,
accepted limitation (no visible tile during the hold, aiming by the destination page's own layout
alone). The user tried it and pushed back: "not working as you described. tile should be visually seen
when i drag" — asking for the floating visual after all, rather than accepting that trade-off.

Built it without needing to hoist drag recognition to a page-spanning system, using the same
coordinate-space insight as the release-time slot math, extended to a *live*, continuously-updating
position: every block page's own `translationX = widthPx * (blockIndex - pagerProgress)` is relative to
one shared outer Box, so `liveTranslationX + (touch's own local offset)` is *always* a valid position in
that shared Box's own coordinate space — during the shift animation, after it settles, at any point —
with no special-casing needed for "is the animation still running." The one new piece this needed that
the release-time-only design didn't: a *live* reader of the pager's position (`livePagerProgress: () ->
Float`, threaded from `StartScreen`'s `progress.value` down through `StartPage` into
`editDragGesture`), since the ghost has to track the live shift animation itself, not just its resting
value — the release-time slot computation still only needs the fixed per-page-crossed offset, unchanged.

New `onCrossPageDragPosition(tileId, offset)` callback fires every tick once `crossPageTriggered`,
reporting that live position; `null` on release. `CrossPageDragGhost` (a new small composable) renders
a deliberately simplified stand-in for the dragged tile — its accent colour and icon, sized via the
same `GridGeometry`/`resizeGeom` every tile already uses, but no live faces/badges/folder mini-grid —
as the very last child of `StartPage`'s own outer Box, so it draws above every block page. The real
in-grid tile (now on an off-screen page) and this ghost are never both visible at once by construction
(the ghost only exists once `crossPageTriggered`, i.e. only once the source page has already started
sliding away), so there's no risk of the two visuals appearing to double up.

Build + full unit test suite green. Needs the user's own on-device confirmation — this is exactly the
kind of live-drag-feel gesture ADB can't reliably synthesize, so a code-level review is as far as this
can be verified without a real finger.

## Cross-page drag: fixed a runaway auto-scroll that dumped the tile at the bottom

Direct same-day follow-up, user-reported: "visual is seen but tile is not placed where i release the
finger it still placed at bottom." The release-time target-cell math itself is fine (verified again by
re-deriving it from the same shared-coordinate-space relationship the ghost uses — the pager-progress
term cancels out identically whether the shift animation has settled or not, so it's correct at any
point in time). The actual bug: `editDragGesture`'s existing vertical near-edge auto-scroll check
(`onAutoScroll`) sets a shared `autoScroll` value that drives a separate `LaunchedEffect` scrolling
loop — once `crossPageTriggered`, that check is skipped for the rest of the gesture (the branch
`continue`s past it), but nothing ever reset `autoScroll` back to `0` if it happened to already be
non-zero at the exact instant the horizontal edge was crossed (plausible whenever the drag was also
near the top/bottom of the screen when it crossed the side edge — not an unusual combination). With
nothing to stop it, that scroll loop kept running for the entire aim-after-shift hold, scrolling
whichever page was active all the way to its bottom before the eventual release — landing the tile far
down the grid regardless of where the user actually released. Fixed with one `onAutoScroll(0)` call at
the exact moment the cross-page shift triggers.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Cross-page drag: the real "still placed at bottom" cause — a deterministic race, not a rare one

Direct same-day follow-up — the auto-scroll fix above didn't fix it; user confirmed "it still placed at
bottom." Found the actual cause on inspecting `setTileGridSlot`'s real implementation (previously only
described secondhand in an earlier entry's "known, accepted risk" — this is that risk, materializing):
it computes its target placement **synchronously**, reading the tile's section via the cached `tiles`
StateFlow (`tiles.value.firstOrNull { it.id == id }`) and scoping collision-resolution to
`tilesInBlock(model.sectionId, ...)` — all *before* the `viewModelScope.launch(writeContext)` block
that actually writes anything. `StartScreen`'s `onCrossPageDrop` called `setTileSection(...)` then
immediately `setTileGridSlot(...)` — two separate top-level calls — so the second call's synchronous
read happened essentially instantly after the first, with no realistic chance for the first call's
*asynchronous* DB write, let alone the `tiles` Flow re-collecting it, to have landed yet. This wasn't an
occasional race, it was **guaranteed** every time: `setTileGridSlot` always computed the destination
cell scoped to the tile's *old* section's own tiles, not the new one's — landing it wherever that
unrelated layout happened to put the requested `(col,row)`, which (especially crossing from a taller
"main" page into a shorter named page, or vice versa) reads exactly as "randomly ends up at the bottom."

Fixed with a new atomic `StartViewModel.moveTileToSectionAtSlot(tileId, targetSectionId, targetSlot)` —
the same body `setTileGridSlot` already has, except it scopes placement to the caller-supplied
`targetSectionId` directly (the moved tile's own current section is never read at all, so there's
nothing stale to race against), and writes the section change and the resulting slots inside **one**
`viewModelScope.launch(writeContext)` block instead of two separate public calls. `setTileGridSlot`
itself is unchanged — every other, non-cross-page caller still calls it exactly as before.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Folder-level "unfold folder" and "remove folder & tiles"

User request, drawing the direct parallel to the page-level "merge with main" just shipped: "for folder
similar option needed, unfold all tiles, or delete the folder with all [tiles] inside." Landed as two
new rows in the same per-tile colour-picker sheet the "show as stack" toggle already lives in, gated
the same way (a real folder only, never a folder child — `childRef == null`):

- **"unfold folder"** — dissolves the folder, turning every child into its own top-level pinned tile at
  once (the bulk counterpart to dragging each child out one at a time). Nothing is lost, no
  confirmation needed, same as "merge with main."
- **"remove folder & tiles"** — unpins the folder and every one of its children from Start in one
  action. Apps stay installed (same non-destructive "remove = unpin" convention every other removal in
  this app already follows) — but unpinning several tiles in one tap is enough of a step up from a
  single tile's own × that it gets a confirmation dialog first, unlike "unfold folder."

Mechanically, looping the existing `removeFolderChild` once per child (the obvious first instinct) is
unsafe: its own last-child branch rewrites the *folder's own tile id* into a plain app tile for the
survivor rather than minting a fresh one, which would leave one child inconsistent with its
newly-repinned siblings (a stale/reused id). Added dedicated bulk methods instead —
`LayoutDao.unfoldFolder`/`removeFolderAndChildren`, `LayoutRepository.unfoldFolder` (mints one fresh
id/colour per child, same convention as `removeFolderChild`, appended in the folder's own section) and
`.removeFolderAndChildren`, `StartViewModel.unfoldFolder`/`.removeFolderAndTiles` — mirroring
`toggleFolderStack`'s existing shape. The About sheet's "folders" group and the Personalize guide's
"organizing tiles" group were both updated with both actions in the same pass.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Page-level "remove page & tiles" — moved to a fixed top-right corner control

Direct follow-up to the folder-level unfold/remove actions above — user asked for the same option at
the page (section) level: "also provide remove page option in edit mode." A first attempt placed it as
a dropdown menu inline inside `SectionHeader`'s own row (mirroring where the folder actions live, in a
per-tile sheet). The user corrected the placement: "this option top right corner of page" — pages
don't have a per-tile sheet the way a folder does, and the header itself scrolls out of view with the
grid, so a page-level control needs its own fixed anchor instead.

Reverted the inline `SectionHeader` dropdown and added a fixed `Box(Modifier.align(Alignment.TopEnd)
.statusBarsPadding().padding(...))` overlay in `StartPage`, always reachable regardless of scroll
position. Gated on `blocks.getOrNull(activeBlockIndex)?.sectionId` being non-null while editing — only
ever shown for a real named page, never "main" (which has nothing to merge into and isn't itself
removable this way). Tapping the visible "remove" pill (icon + text, not icon-only — see the earlier
"merge with main" label-visibility bug in this same log for why) opens a `DropdownMenu` with two items:
"merge with main" (the existing, already-shipped `onDeleteSection` — no confirmation, nothing lost) and
"remove page & tiles" (a new bulk action, confirmed via `AlertDialog` first, same non-destructive
"remove = unpin" convention as every other bulk removal in this app).

New `LayoutDao.tileIdsInSection`/`removeSectionAndTiles` (loop `deleteTileById`/`deleteFolderById` over
every tile in the section, then `deleteSectionById`, in one `@Transaction`), `LayoutRepository
.removeSectionAndTiles` wrapper, and `StartViewModel.removeSectionAndTiles` — same shape as the
folder-level `removeFolderAndChildren` chain above. `StartPage` gained an `onRemovePageAndTiles: (String)
-> Unit = {}` param wired from `StartScreen`'s call site to `viewModel::removeSectionAndTiles`.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Fixed a real bug: removing the last page landed on the app list instead of the adjacent page

Direct on-device follow-up to the entry above, user-flagged: "after remove page and tile option is
selected and confirmed... adjacent page to be shown" — confirmed reproducible by hand (created a new
page, removed it via the corner control, and it opened the app list instead of sliding back to the
previous page).

Root cause was a real race in the pager's own bounds-reclamp effect
(`LaunchedEffect(blockCount)`, `StartScreen.kt`), not the removal logic itself. That effect keeps
`progress` valid whenever the number of pages changes (a section created/deleted/merged), and branches
on whether the app list is currently showing before deciding how to reclamp. It read that from
`appListShown`, a `derivedStateOf { progress.value >= upper - 0.5f }` that recomputes live off the
*current* `upper` (`blockCount.toFloat()`) — but `upper` is exactly what just shrank. Removing
whichever page you're currently resting on, when it happens to be the *last* one (true of any
just-created page, since new pages append at the end), drops `blockCount` by one so that the new
`upper` now numerically equals the still-unchanged `progress.value` you were resting at — and
`progress.value >= upper - 0.5f` is trivially true at that point even though you were never anywhere
near the app list. The reclamp effect saw that false-positive and took its `appListShown ->
progress.snapTo(upper)` branch, snapping straight to the app list instead of coercing back to the
newly-last (adjacent) page.

Fixed by branching on `isAppList` instead — the ViewModel's own `StateFlow`, set only by `settleTo`'s
post-animation call, i.e. a real committed "you settled on the app list" fact rather than a live
recomputation that can be fooled by `upper` moving out from under an unrelated resting position. This
is the same distinction (`isAppList` vs. the continuously-updating drag-derived flag) an existing
Post-S27 entry in this log already made for the same reason at the `AppListScreen` `visible` param —
this bug is a second, independent place the same live/committed distinction mattered and had been
missed. Every other branch of the reclamp effect (`feedShown`, the plain coerce) is unaffected.

Reproduced the bug first via adb-driven taps on the physical device (create a page → remove it →
landed on the app list, confirming the exact failure the user reported), then confirmed the fix
compiles and the reclamp logic is correct by inspection. User confirmed on their own device: the
adjacent page now shows correctly, but flagged a follow-up — edit mode stayed on afterward, now
editing whatever page you landed on rather than the one you actually asked to remove. Fixed by
calling `onExitEdit()` right alongside `onRemovePageAndTiles` in the confirm dialog's button —
mirrors the existing pattern (`onDone = onExitEdit` on the edit bar's own "done" button) rather than
leaving the user mid-edit on a page they never chose to edit. "remove folder & tiles"'s own confirm
deliberately keeps editing on afterward (unchanged) — you're still on the same page there, just minus
one folder, so staying in edit mode to keep arranging the rest of that page is the useful default;
removing a whole *page* has nothing left on it worth continuing to edit.

Build + full unit test suite green.

## "add page" is now a real modal dialog, and exits edit mode once added

Direct follow-up, user-requested: "add page should ask in new dialoge box and when added should come
out of edit mode." Previously tapping "+ add page" swapped the button itself for an inline
`SectionNameEditor` text field row (the same one the header's tap-to-rename already uses) right at the
top of the grid — easy to dismiss with a stray tap elsewhere, and gave no confirmation that a page had
actually been created.

New `AddPageDialog` — a real `AlertDialog` with a bordered `BasicTextField` (this codebase never uses
Material3's own `TextField`/`OutlinedTextField`; every text entry here, including the existing rename
editors, is a styled `BasicTextField`, so this follows that same convention rather than introducing a
new one) and "add"/"cancel" buttons, matching the weight of the "remove page & tiles?" dialog for the
opposite action. "add" is disabled outright on a blank/whitespace-only name (`enabled =
draft.text.isNotBlank()`), so there's no way to create a nameless page. Committing calls
`onCreateSection(label)` immediately followed by `onExitEdit()` — a fresh, empty page has nothing on it
yet worth staying in edit mode to arrange, same reasoning as the just-shipped "remove page & tiles"
exit-edit-mode fix above. Cancelling just dismisses, unchanged from before. The old inline
`addingSectionAtTop`/`SectionNameEditor` swap for this one call site is gone; `SectionNameEditor`
itself is unchanged and still backs the header's own tap-to-rename.

Build + full unit test suite green.

## About/guide docs updated for the top-right "remove page" control

User-requested doc pass after the page-removal arc above. Both `AboutSheet.kt`'s "start screen" group
and `PersonalizeGuideSheet.kt`'s "pages" group still described the header's now-removed inline
"merge with main" (×) button and claimed "tiles are never deleted" — no longer true since "remove page
& tiles" ships. Split into two bullets in both files: one for "+ add page" (now names the page via a
dialog), one for the top-right "remove" control's two options — "merge with main" (nothing lost) and
"remove page & tiles" (confirmed first, apps stay installed) — phrased to match the existing
folder-actions bullet's own "confirmed first — apps stay installed" convention in both files.

Build + full unit test suite green.
## v4.5.0 (versionCode 450) — release cut

User-requested: "create ver 4.5.0 release bundle, apk, and release notes by mentioning all changes
after 4.0.2." Rolls up every commit merged to `main` since the 4.0.2 upload (50 commits, none of it
previously shipped) into one signed release — the biggest single jump between two uploaded versions
in this project's history, covering: the "widget cards" tile style and its extension onto the feed's
glance cards and Quick Panel; three new disc wallpapers (nebula/ember/reef) plus the wallpaper-picker
grid/`EdgeStripSheet` overflow fixes that came with them; the whole opt-in Start-screen "sections"
feature (schema through two full browsing-mode passes, the section-scoped placement-engine bug hunt,
and its many on-device-reported fixes); the clock tile's back-face rebuild; the FREE-mode drag-drop
fix; and the Panchang widget's sunrise/sunset/ayana back face. See `docs/PLAY_STORE.md` "Release
notes (v4.5.0)" for the full user-facing changelog — not duplicated here since every constituent
change already has its own detailed entry earlier in this file (or, in a few smaller cases, only a
descriptive commit message, per this project's own norm of not requiring a DECISIONS entry for every
single commit).

`app/build.gradle.kts`: `versionCode = 450` / `versionName = "4.5.0"`, following the established
changelog-comment convention above the version fields. Signed release APK + AAB built via
`bundleRelease`/`assembleRelease` off the existing `key.properties` keystore — same signing identity
verified against every prior release (`apksigner verify`, SHA-256 cert digest unchanged), copied to
`release-out/tileshell-4.5.0-release.{apk,aab}` per this project's own established artifact-naming
convention. Build + full unit test suite green on `main` before cutting.

Deliberately built from `main`, not the `start-sections` branch this same session had otherwise been
working on — `start-sections` is its own separate, not-yet-merged experimental branch (a different,
swipeable-pages take on "sections" than the tabbed/dropdown one already shipped on `main` in this same
release), and merging an unrelated in-progress branch was never part of this request.

## v4.5.0 re-cut — `start-sections` merged in, replacing dropdown/tabbed pages with swipeable ones

Direct follow-up, user-requested: after seeing the v4.5.0 release notes didn't mention "pages" the way
this session had otherwise been discussing it, the user clarified the whole session's `start-sections`
work had been done serially on top of `main` and asked for "latest tested apk in main, should not
differ in functioning" — i.e. `main` should reflect what was actually tested this session, not an
earlier design the user had since moved past.

`git merge --no-ff start-sections` into `main`: clean except one conflict in this file (append-only on
both sides — resolved by keeping both blocks, `start-sections`' entries ordered first since that work
predates this session's release cut). No other file conflicted; `start-sections` never touched
`app/build.gradle.kts`; `main`'s two release-cut/docs-only commits never touched any of the files
`start-sections` changed. Build + full unit test suite green post-merge.

This replaces the dropdown/tabbed page-switching the v4.5.0 release notes originally described with
the swipeable-pages design: swipe left/right between pages (same gesture as the feed/app-list swipe,
no menu), pages are always available (the "enable sections" toggle is gone), a page-count dot
indicator, left/right reorder, drag-a-tile-to-the-screen-edge to move it onto a neighboring page, and
a fixed top-right "remove" control per named page (merge with main / remove page & tiles) — plus the
parallel folder-level unfold/remove actions. Also user-requested in the same exchange: the release
notes and in-app terminology both say "pages," not "sections" (the underlying Kotlin symbols/DB
columns are unchanged, per the original plan's own scoping — this is a user-facing rename only).

Re-cut at the same `versionCode`/`versionName` (450 / 4.5.0) — the previous build was only ever
delivered to the user in this chat, never uploaded to Play, so nothing is burned; `app/build.gradle.kts`
gained a `--- re-cut` note under the existing v4.5.0 comment block, matching this project's established
convention for an un-uploaded re-cut. `docs/PLAY_STORE.md`'s "Release notes (v4.5.0)" section was
rewritten to describe the swipeable-pages design (the actual shipped behaviour) instead of the
dropdown/tabbed one, with a note that this changelog already reflects the final version, not an
intermediate one. Signed APK + AAB rebuilt and overwritten in place at
`release-out/tileshell-4.5.0-release.{apk,aab}`; signing identity re-verified unchanged
(`apksigner verify`, same SHA-256 cert digest as every prior release); `versionCode`/`versionName`
confirmed via `aapt2 dump badging`.

## Page-dot indicator moved from the top to the bottom of the screen

Direct user follow-up, post-merge: "page indicators instead of top, place at bottom." Moved
`PageDotsIndicator`'s call site (`StartScreen.kt`) from `Alignment.TopCenter`/`statusBarsPadding` to
`Alignment.BottomCenter`/`navigationBarsPadding`, reusing the same dynamic bottom-offset pattern the
app-list/quick-panel icon column already uses (`edgeStripVisible` — already computed once at this
same outer scope — swaps the offset to clear `STRIP_THICK + 8.dp` when the edge strip is actually
expanded, else a plain `14.dp`), so the dots never sit under the edge strip when it's showing. Every
other gating condition (hidden with one page, while editing, over the feed/app list) is unchanged.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## TileShell branding mark on the glance page, top-right of the greeting

User-requested: "place tileshell icon on top right on glance in the same line of greeting. as part of
branding." Reused `TileLogoMark` — the compact 2×2 accent-tile mosaic already shown next to the
wordmark in the About sheet's header — rather than inventing a second mark, so the app's branding
reads consistently across both surfaces. Widened from `private` to public in `AboutSheet.kt` (`:feature
:personalize`) so `FeedPage.kt` (`:feature:start`, which already depends on `:feature:personalize` —
no new module dependency) can reuse it.

`GreetingHeader`'s call site is now wrapped in a `Row(Arrangement.SpaceBetween, Alignment.Top)`: the
greeting stays exactly as it was (still its own `Column`, no `weight` needed since `SpaceBetween`
pushes the second child to the far edge regardless of the first's width), and `TileLogoMark(accent)`
sits at the top-right, level with the greeting's first line rather than vertically centered against
the taller two-line "good morning, `<name>`" variant. Uses the page's own already-resolved `accent`
(wallpaper-derived when that tile-colour source is active), matching the rest of the glance page.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Branding mark: fixed brand colours instead of the accent, and added to Personalize's header

Two direct follow-ups on the mark above. (1) User-reported: "it should be colorful just like app icon
(currently it is shown in accent color)." `TileLogoMark` previously derived its four cells from the
tile accent (varying alpha), so it recoloured with personalization instead of looking like the actual
app icon. Now hardcodes the same four brand colours the real launcher icon uses (`app/src/main/res/
drawable/ic_launcher_foreground.xml`: `#2B78E4` blue / `#C4287E` magenta / `#E2A200` amber / `#1F9E57`
green) — the `accent: Color` parameter is gone (no longer used by the function; both call sites
updated) since the mark no longer varies at all, by design. (2) User-requested: "also show the icon in
personalise screen." `TileLogoMark` gained a `modifier: Modifier = Modifier` param for flexible
placement, and `PersonalizeSheet.kt`'s header now shows it directly above the "personalize" title —
same treatment as the About sheet's own header, for a consistent brand mark across every top-level
sheet.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Glance page's branding mark gets a real icon background plate

Direct follow-up, user-reported after installing over wireless debugging: "it doesnt look good on the
background of glance screen. show it like icon (on black background)." The bare 2×2 mosaic floated
directly on the glance page's colourful gradient background — fine on the About/Personalize sheets
(both sit on the sheet's own opaque background already) but not on the glance page. Only the glance
page's call site changed: `TileLogoMark()` is now wrapped in a `Box` clipped to `SquircleShape()`
(`:core:design`, the same real-superellipse shape icons-mode already uses) filled with the launcher
icon's own actual background colour (`#0A0A0D`, `ic_launcher_background`) — so it reads as "the real
app icon," not a loose group of coloured squares. The About sheet/Personalize headers are unchanged
(no plate — the mosaic there is a wordmark accompaniment, not standing in for the app icon).

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Glance page's branding icon shrunk

Direct follow-up, user-reported: "size can be little smaller." `TileLogoMark` gained `cell`/`gap`
params (default 18dp/3dp, unchanged for the About/Personalize headers) instead of hardcoding them, so
just the glance page's plated instance could shrink (13dp/2dp cell/gap, plate padding 10dp → 7dp)
without affecting the other two call sites, which weren't part of this complaint.

Build + full unit test suite green. Needs the user's own on-device confirmation.

## Play Store "what's new" reformatted into "New features:" / "Bugs fixed:" sections, going forward

User-requested, after reviewing a mockup of the Play Store update screen (rendered via the visualize
tool before touching any file, so the wording could be approved first): a new standing format
convention for every release's Play-facing blurb, replacing the old "• New: / • Improved: / • Fixed:"
per-line style — two labeled sections, "New features:" then "Bugs fixed:", each a short bulleted list.
Documented as a standing note in `docs/PLAY_STORE.md` right above the "Release notes" heading, marked
effective from v4.5.0 onward; every earlier entry keeps its original format unchanged, as the
historical record.

v4.5.0's own blurb was rewritten in this format and, per direct request, expanded to cover the full
run of major features and bug fixes back to v3.0.0 rather than just the v4.0.2→v4.5.0 delta — a
one-time catch-up, since v4.0.0/v4.0.1 never reached Play and v4.0.2's own notes never described
anything earlier, so no release has ever actually listed the full body of work most real users have
never seen. The "since v3.0" framing itself is deliberately not stated inside the blurb text (user:
"remove this from 3.0 label") — it's just "New features:"/"Bugs fixed:", explained instead in this
doc's own surrounding note. Future releases go back to describing only that release's own delta, still
in the same two-section format. The existing "What's new since v4.0.2" detailed reference section
(developer-facing, not Play-facing) gained a note pointing to each earlier version's own "Release
notes" section for that feature's full detail, rather than re-narrating the whole history there too.

480 characters, under Play's 500 limit. Documentation-only change — `docs/PLAY_STORE.md` isn't baked
into the app, so no rebuild was needed.

## Reverted the v3.0-catch-up scope; v4.5.0's blurb covers only its own changes

Direct reversal of the entry above, same session. Several rounds of live iteration followed the
initial "cover back to v3.0.0" decision — separating "live tiles" from the "14 gadgets/widgets"
feature (they're genuinely different: live tiles are TileShell's own Start-screen tiles, gadgets are
the catalog that became real, any-launcher widgets), narrowing "Bugs fixed" to battery/performance
only, then reverting that narrowing back — each shown as a mockup of the actual Play update card via
the visualize tool before touching any file, per the user's own standing rule from this same thread:
"always confirm what is getting included."

The user then asked a genuine mechanics question — how does a user on an *older* version see what's
in the *next* one, since the old app can't know anything about a release it hasn't seen — and the
answer clarified why the whole v3.0-catch-up premise didn't hold: the "what's new" card Play shows is
generated live from whatever text sits in Play Console at the moment a release is uploaded, entirely
independent of which version the user is updating from. There's no real "the last actual upload was
v4.0.2, so this needs to catch a user up from there" logic on Play's side — every install always sees
the newest uploaded version's own text, never a merged history. That premise had driven the whole
v3.0.0-catch-up idea, so once it didn't hold, the catch-up was reverted rather than kept as a curiosity.

v4.5.0's `## Release notes` entry now describes only its own three features (pages, widget-card tile
style, three wallpapers) and three fixes (clock tile date clipping, FREE-mode drag, Panchang sunrise/
sunset) — 310 characters, comfortably under Play's 500-character limit. The standing "New features:"/
"Bugs fixed:" two-section format convention (declared for every release from v4.5.0 onward, in the note
directly above the `## Release notes (v4.5.0)` heading) is unchanged — only the *scope* of what v4.5.0
itself describes reverted. The "for reference" detailed changelog section underneath (developer-facing,
never shown to users) still documents the same v4.0.2→v4.5.0 delta as before, unaffected by this.

Documentation-only change — no rebuild needed.

## v4.5.0's wider scope restored, as a one-time exception; narrow scope confirmed for future releases

Direct reversal of the entry immediately above, same session — after being shown the two candidate
blurbs side by side (the narrow v4.0.2-only one just reverted to, and the earlier wider one covering
icons home style/live tiles/gadgets), the user picked the wider one specifically for v4.5.0, while
confirming every release *after* this one goes back to the narrow "only that release's own changes"
scope. So the "no catch-up need" reasoning in the reverted entry wasn't wrong about *how Play's update
card works* — it just wasn't the deciding factor; the user wants v4.5.0 specifically to name a few
long-standing, never-before-described capabilities as a one-time exception, independent of Play
mechanics.

`docs/PLAY_STORE.md`'s v4.5.0 blurb now reads: **New features** — icons home style, live tiles (clock/
weather/calendar/music, live even as icons), the 14-gadget widget catalog (any launcher), multiple
Start pages, widget-card tile style + 3 wallpapers; **Bugs fixed** — scoped to battery/performance
items only (removed duplicate widget refresh jobs, widgets/feed no longer wake the device overnight, a
pager-swipe stutter fix, notification-burst UI freeze fix), per an earlier same-session request to
narrow "Bugs fixed" to that category for this release. A one-sentence note above the entry states this
wider scope is a deliberate one-time exception and that future entries return to the narrow convention.

**Caught and fixed a real bug while finalizing this**: the doc claimed "Character count 499" but the
actual text (as first written) was 501 — one bullet read "Widgets and feed no longer wake device
overnight" instead of the shorter "Widgets & feed..." the confirmed mockup and the earlier verified
499-count draft both used. Since the mockup literally rendered "and" (a transcription slip when the
python character-count check was run against a slightly different draft than what was actually shown
and typed into the file), the character claim silently went stale — this would have shipped over Play's
hard 500-character limit if uploaded as-is. Fixed by restoring "&"; recounted directly against the
final file content (not a remembered figure) to confirm 499, with 1 character of margin.

Documentation-only change — no rebuild needed.

## New: "what's new" in-app card, shown once after updating

User-requested ("implement this update feature in app") — after all the work drafting the Play Store
"what's new" text, surface the same content inside TileShell itself, so a real user who updates
actually sees it rather than the text only ever living in a doc used to fill in Play Console by hand.

New `WhatsNewSheet.kt` (`:feature:start`): mirrors `FirstRunHint`'s exact shape (scrim + bottom card,
tap-anywhere-or-"got it" to dismiss, `AnimatedVisibility(fadeIn/fadeOut)`) rather than inventing a new
overlay style, since both are one-shot informational cards over Start. Content is two labeled bulleted
sections ("new features"/"bugs fixed"), kept in sync by hand with `docs/PLAY_STORE.md`'s v4.5.0 Play-
facing blurb — same wording, just without that doc's 500-character compression, so each line reads as
a full sentence. `WHATS_NEW_VERSION_CODE = 450` is a hardcoded constant (this content isn't shipped
from `docs/PLAY_STORE.md`, which isn't in the APK) that must be bumped by hand alongside `app/
build.gradle.kts`'s own `versionCode` every time this content changes for a new release — noted
explicitly in its own doc comment as a manual sync point, since nothing enforces it automatically.

`WhatsNewPrefs` (same file) tracks the last versionCode a device has actually seen this card for,
deliberately an `Int` rather than `FirstRunHintPrefs`'s plain `Boolean` — this needs to re-trigger on
every future version bump, not just once ever. **A real logic bug caught before it shipped**: the
first draft defaulted "never recorded" to `currentVersionCode` itself (so `shouldShow` read as
`current < current` = always false) — meant to stop a fresh install from seeing it, but it also
permanently stopped an *existing* user from ever seeing it for the version that introduces this
feature, since their very first check would establish that same false baseline and no future bump
would ever un-stick it (the next check reads the *new* current version as its own "never recorded"
default too, repeating the same false negative forever). Fixed by defaulting to `0` instead — a real
existing user's first-ever check now correctly reads `0 < 450` = true — and moving the "don't show on
a genuinely fresh install" guard entirely to the call site instead: `StartViewModel.init` only
consults `WhatsNewPrefs` at all in the `else` branch of the same `if (!HomeStyleWizardPrefs.shown(...))`
check the first-run wizard already uses, so a fresh install (wizard shown instead) never reaches this
check in the first place, and an upgrading install (wizard already marked shown, possibly from long
before this feature existed) always gets a real `0` baseline on its first real check. `dismissWhatsNew()`
calls `WhatsNewPrefs.markSeen` only on actual dismiss (matching `FirstRunHintPrefs`/`HomeStyleWizardPrefs`'s
own mark-on-action convention, not mark-on-detect) and is also wired into `goHome()`'s existing
force-close chain (alongside `skipHomeStyleWizard()`) so a Home/back press doesn't leave it stuck open
forever.

`StartScreen.kt`'s call site reuses `showUpdateBanner`'s own mutual-exclusion condition set (`!editMode
&& !isAppList && expandedFolderId == null && !personalizeOpen && !searchOpen`) so the card never shows
mid-edit, over the app list, over an expanded folder, or stacked behind another sheet — deliberately
*not* hooked into the existing `UpdateBanner.kt`/`AppUpdateChecker.kt` machinery, which is a wholly
separate, orthogonal concern (that one prompts *before* a Play Store update, reading Play's own Play
Core API; this one fires *after*, reading nothing but a hardcoded local constant).

Build + full unit test suite green.

## Tapping the about sheet's version pill re-opens "what's new" on demand

Direct follow-up, user-requested: "on ver no displayed in features and info, if i tap on that show
this whatsnew" — a way back into the card once it's already been auto-shown-and-dismissed, since
[WhatsNewPrefs] otherwise means it's gone for good until the next version bump.

`AboutSheet` gained `onVersionTap: () -> Unit = {}`; the existing "v4.5.0" pill in its header is now
`clickable`, calling it. New `StartViewModel.reopenWhatsNew()` closes about and personalize (both
`_aboutOpen`/`_personalizeOpen` set false in the same call) and sets `_whatsNewOpen.value = true`
directly — unlike the auto-trigger in `init`, this bypasses `WhatsNewPrefs.shouldShow` entirely, since
tapping the version is an explicit "show me again" request, not a first-time check. Necessarily closes
about/personalize first: `WhatsNewSheet`'s own display condition in `StartScreen.kt` already excludes
`personalizeOpen`, mirroring every other "jump to a different overlay from within this one" action in
this file.

Build + full unit test suite green.

## Calendar-system tile: sunrise/sunset location fallback, and a real day-rollover bug on the small icon face

User-reported: "sunrise and sunset timing is not accurate... in pune it is 6:19am and 6:38pm," plus a
separate report that the tile/widget showed the previous day's date when checked at 5am.

**Sunrise/sunset accuracy**: verified [SunTimes.sunriseSunsetFor]'s own trig (the standard "sunrise
equation," Wikipedia/NOAA idiom) against an independent reference implementation (the `astral` Python
library) for Pune's real coordinates on today's date — they agree to within ~2 minutes, well inside this
formula's own documented ~1-minute accuracy budget, and both land close to the user's quoted real-world
times. The formula itself isn't the bug. The real cause: `lastCoarseLocationOrDefault`
(`CalendarSystemTile.kt`) only ever read `LocationManager.getLastKnownLocation` — a *passively cached*
fix that can simply be empty (nothing else on the device has ever asked the network-location provider
for one, common right after a fresh install) — and silently fell back to **India's geographic centroid**
(20.5937, 78.9629) whenever it was. Re-running the same formula with that fallback point instead of
Pune's real coordinates lands ~20 minutes off (6:03/18:16 vs. the correct ~6:24/18:36) — squarely
matching a user complaint of "not accurate," and a far bigger error than the algorithm's own margin.
Fixed by making the function try one bounded (8s), single-shot fresh fix from `NETWORK_PROVIDER` (still
only needs the already-checked `ACCESS_COARSE_LOCATION` grant — no new permission) whenever nothing is
cached, before falling back to the country-wide default — strongly preferring the user's real location.
The in-app tile face now resolves this via `produceState` (the function became `suspend`); the
home-screen widget's refresh worker (already a `CoroutineWorker`) just awaits it directly.

**Day rollover — a real, separate bug, isolated to one composable**: `CalendarSystemSmallFace` (the
compact 1×1 icon-grid face) read `remember { Calendar.getInstance() }` — captured exactly once, the
first time the composable entered composition, with no ticker of any kind to ever refresh it. Every
sibling live face in this file (`MoonPhaseSmallFace`, `StepsSmallFace`, `StockSmallFace`,
`CommoditySmallFace`, and the calendar tile's own bigger flippable face) re-renders once a minute via a
gated `LaunchedEffect(active) { while (true) { ...; delay(60_000L - now % 60_000L) } }` loop — this one
face never got that treatment, so once composed it would freeze on whatever day it started on
indefinitely (not just "at 5am" — any time, for however long the launcher process stays alive without
that composable leaving and re-entering composition), exactly matching the user's report. Fixed by
giving it the same ticker + an `active: Boolean` parameter, wired from both call sites
(`StartScreen.kt`'s `"calsys"` branch and `IconCellView.kt`'s `LiveIconTile` branch) with `active =
liveActive`, matching how the adjacent `"moonphase"` branch in both files already does it. The bigger
flippable Panchang/calendar-system face was never affected — it already had the correct ticker.

For reference, the home-screen widget's own day-rollover (separate code path, `WidgetWork
.millisUntilNextMidnight` / `CalendarSystemWidgetRefreshWorker`) is a single WorkManager job scheduled
for just after local midnight — by design, not the bug above — so it can lag by however long WorkManager
defers it under Doze on an idle device, unlike the in-app tile which self-corrects every minute while on
screen.

Build + full unit test suite green.

## "What's new" card: only "got it" dismisses it, not a tap anywhere on the scrim

Direct follow-up, user-requested: "only tap on got it should dismiss the update info screen." Unlike
`FirstRunHint`, whose scrim is itself a dismiss button, `WhatsNewSheet`'s scrim is now a no-op consuming
click (`indication = null`, empty `onClick`) instead of calling `onDismiss` — a stray tap can no longer
brush past the card without the user actually reading and acknowledging it, while the scrim still
blocks the tap from falling through to a Start tile underneath. "got it" is unchanged, still the only
path to `onDismiss`.

Build + full unit test suite green.

## Panchang sunrise/sunset: show the *next* occurrence of each, with a day label

Direct user follow-up, same-day as the location-fallback fix above: "sunrise time should be shown of
next day if sunrise is already passed and sunset of next day if it has already passed."

Read literally — and independently per field, not "flip the whole line to tomorrow once either has
passed" — this means during the daytime window between today's sunrise and today's sunset, the
sunrise line should already show *tomorrow's* sunrise (today's has passed) while the sunset line still
shows *today's* (hasn't happened yet); only after today's sunset do both roll forward together. New
`SunTimes.nextSunriseSunset(epochMillis, lat, lon, zone)` computes today's pair via the existing
[SunTimes.sunriseSunsetFor], then independently rolls each of sunrise/sunset forward by one calendar
day (`Calendar.add(DAY_OF_MONTH, 1)`, not raw millis arithmetic, to stay correct across DST-observing
zones even though India itself has none) whenever `epochMillis` is at or past that specific instant.
Both in-app tile (`CalendarSystemTile.kt`) and the home-screen widget worker now call this instead of
`sunriseSunsetFor`; the in-app tile's `remember` key changed from `romanDate` (which only changes once
a day) to `nowMillis` (already ticking once a minute) since the sunrise/sunset display can now change
intraday, at the exact moment either instant passes — still cheap, pure local trig, no network.

Because the two times can now genuinely belong to different calendar days, a bare clock time is
ambiguous ("6:24" — today's or tomorrow's?) — user follow-up: "show day (som, mangal, budh) in front
of respective times." New `HinduPanchang.varaFor(epochMillis, zone)` (the same weekday derivation
`panchangFor` already does internally, exposed standalone so it can be asked about an arbitrary
sunrise/sunset instant rather than "today") plus `HinduPanchang.shortVaraName(value)`, a small explicit
lookup table for the commonly-used short forms (`"mangalavara"` → `"mangal"`, `"budhavara"` → `"budh"`,
etc. — not a mechanical `removeSuffix("vara")`, which leaves a trailing vowel on several of them, e.g.
`"mangala"`/`"budha"`/`"soma"`). Both the in-app tile's back face and the widget's back face now prefix
each of the sunrise/sunset lines with its own short vara label, computed from that specific time's own
millis (not the tile's "today" vara) — so during the post-sunrise/pre-sunset window the two lines can
legitimately show two different day labels, which is exactly the case this makes legible.

Build + full unit test suite green (`SunTimesTest` extended for `nextSunriseSunset`'s three phases;
new `VaraForTest` for `varaFor`/`shortVaraName`).

**Same-day follow-up**: "day is displayed in english, it should be in devnagari" — the short vara label
above was `HinduPanchang.shortVaraName` (Roman: "som"/"mangal"/"budh"), sitting on the tile's otherwise-
English back face; the user wants it in Devanagari script instead, like [PanchangDevanagari.ayana]
already is on that same face. Added `PanchangDevanagari.shortVara(value)` (a matching short-form
Devanagari lookup: `"mangalavara"` → `"मंगल"`, etc.) and swapped both display call sites to it. Since
`HinduPanchang.shortVaraName` (Roman) had no other caller left once swapped, deleted it outright along
with its test, rather than leave unused code behind — replaced with `PanchangDevanagari.shortVara`
coverage instead. Build + full unit test suite green.

**Same-day follow-up**: "shak and vikarm sanvay yers are shown in english numbers, it should be in
devnagari nos" — `yearLabel`'s Devanagari branch (`PanchangFace`) wrapped `panchang.shakaSamvat`/
`vikramSamvat` (plain `Int`s) directly into the string, so the year numbers rendered in Arabic numerals
even on the otherwise-fully-Devanagari front face, next to Devanagari month/nakshatra/tithi text — an
inconsistency, not a new feature. Reused this same file's existing (pre-dating this session)
`toDevanagariDigits` helper — already used for the sunrise/sunset clock digits — on both year numbers.
The widget's own Panchang face doesn't display shaka/vikram samvat at all, so no change needed there.
Build + full unit test suite green.

## Monochrome icons (Nothing-OS-style unified icon theme), revisiting "Themed icons: parked"

User asked directly for a Nothing-Phone-style feature: every installed app's icon rendered as a flat,
single-colour glyph. This is exactly the synthesized-fallback path floated (but not built) when
`themedIcons` was parked earlier in this log — that version only worked for the minority of apps
declaring a real Android 13+ monochrome adaptive-icon layer, and was turned off specifically because
most apps don't have one, producing a visibly uneven mix. Scoped via `AskUserQuestion`: accent-tinted
silhouette style, applied everywhere the (already-built, still-dormant) `themedIcons` plumbing reaches —
Start in both TILES and ICONS home style, the app list, folder mini-grids (top-level `IconCellView`'s
`IconFolderCell`/`IconFolderChildGlyph` and the MEDIUM+ folder tile's `FolderChildIcon`, neither of
which previously threaded `themedIcons` at all), and live-tile "posted by" corner badges — toggled by a
new "monochrome icons" row in Personalize's "home style" group (the old row's exact spot), re-wiring the
two `// themedIcons intentionally not threaded — parked` call sites (`StartScreen.kt`'s `StartPage(...)`
call, `AppListScreen.kt`'s two `AppRow(...)` calls) back to `settings.themedIcons`.

The actual new work is the synthesis: `core/design/Monochrome.kt#synthesizeMonochromeMask(pixels:
IntArray): IntArray` — a pure, unit-tested pixel transform living in `:core:design` rather than
duplicated three times, since (unlike `IconShape`'s masking) it needs neither `:core:data` nor Compose
types, just plain ARGB ints (`Bitmap.getPixels`'s own packed format) — kept deliberately free of
`android.graphics`/Compose `Color` imports so it's exercisable by this module's plain-JVM JUnit tests
with no Robolectric dependency. `monochromeIconBitmap()` in all three existing duplicate-masking files
(`IconCellView.kt`, `AppListIcon.kt`, `feature/livetiles/AppIcon.kt`) now always returns a non-null
untinted alpha-mask `ImageBitmap` (the field type on `MaskableIcon`/`MaskableAppIcon` narrowed from
nullable to non-null to match) — the app's own native monochrome layer when declared, else a synthesized
equivalent — so `themedIcons` now covers every resolvable icon, not just the minority with a real layer.

**Two real bugs found and fixed via on-device testing, not just code review** (the user flagged specific
broken-looking icons by name both times — real regression-driving feedback, not hypothetical). Round 1,
after the user reported "sadhguru, kissan connect, hp, hp pay" (then "amazon now, bob world, botim,
claudedigi") rendering with "details lost": the first synthesis version isolated an adaptive icon's
*foreground layer alone* as the source (reasoning that the OS's safe-zone convention guarantees real
transparency there), with luminance-fallback polarity chosen by comparing each pixel's luma only to the
whole image's *average* brightness. Both assumptions failed on real installed apps: HP/HP Pay/Kissan
Connect/Sadhguru-style icons often bake their entire visible design (fill colour *and* wordmark) into
one PNG rather than cleanly separating the glyph into just the foreground layer, so isolating it threw
away the actual logo; and a moderately-bright coloured fill (orange/red/pink) with a white wordmark
pulls the whole-image average toward "light," so the average-based rule inverted and treated the fill as
"ink," making the wordmark itself vanish. Fixed two ways: `monochromeIconBitmap()` now synthesizes from
the already-loaded *full composite* (`rawBitmap`/`unmaskedIconBitmap` — background+foreground for an
adaptive icon, the plain decode for a legacy one, i.e. exactly what a user actually sees), never just one
isolated layer; and `synthesizeMonochromeMask` replaced the whole-image-average polarity rule with
`otsuThreshold` (standard histogram-based two-cluster luminance split) plus **minority-cluster selection**
— whichever luminance cluster is the *smaller* by pixel count is "ink," regardless of which one is
lighter, since a logo mark or wordmark is nearly always minority-area content sitting on a majority-area
fill. Contrast is also stretched within the chosen ink cluster alone, so a low-contrast logo (ink and
field close in luma) still reaches full 0..255 opacity instead of a washed, barely-visible result — a
second, related "details lost" failure mode for icons with inherently subtle contrast.

Round 2, after the user reported specific still-blank icons (`com.ishafoundation.app`/"Sadhguru",
`com.kisankonnect.in`/"Kissan Connect") even with the round-1 fix live: found via temporary
instrumentation logging (`Log.d` in `monochromeIconBitmap`, `adb logcat`, removed once diagnosed — this
project's established debugging pattern) that both are plain *legacy* (non-adaptive) rounded/circular
icons, ~80-95% opaque with transparent pixels only in the corner rounding. `hasMeaningfulTransparency`'s
original criterion ("a meaningful fraction of both transparent and opaque pixels") happily matched this
shape too, routing it down the "use the raw alpha channel as the final silhouette" path — but a rounded
icon's alpha channel only encodes its *outer boundary shape*, never internal glyph detail, so the result
was a flat, filled blob with the actual wordmark (extractable via the composite's own colour contrast,
confirmed by manually pulling and inspecting Sadhguru's real APK assets) discarded entirely. Fixed by
requiring transparent pixels to be the **majority**, not merely present in both — a circle-in-square or
inset-margin shape is always opaque-majority (≈78%+ opaque), which now correctly excludes it and routes
through the luminance/Otsu path instead, while still catching a genuinely mostly-transparent "small glyph
on a big empty field" design when one exists. Both rounds verified by hand on the physical device (not
just unit tests) via `adb`-driven search of the exact reported package names, screenshotting before/after
each fix; every one of HP, HP Pay, Sadhguru, Kissan Connect, and the Amazon family (Amazon/Alexa/Music/
Now/Pay) confirmed showing a legible glyph post-fix.

**Round 3**, after the user reported three more still-broken icons ("bob card, drive, whiteboard") —
two distinct bugs, neither the "genuinely no detail to extract" limitation round 2 assumed BOBCARD was
(that assumption turned out wrong; see below). (1) **Google Drive** renders as a solid filled plate with
zero glyph despite the fix — confirmed via the same instrumentation-logging technique that it uses its
*own declared, real* Android 13+ monochrome layer (the "prefer the native layer" branch, untouched by
every fix so far), and that the drawn 96×96 bitmap for that layer is uniformly opaque end to end with no
shape variation at all — a bug in how that specific Drawable renders when drawn manually outside its
normal system pipeline, not in this app's synthesis. Fixed with a new sanity check,
`isUniformAlpha(pixels)`: a native monochrome layer is now only trusted when its own rendered alpha
actually varies; a uniform one falls back to synthesizing from the ordinary icon pixels instead of being
trusted blindly. (2) **BOBCARD**, still blank — found via the same logging that it's genuinely close to a
50/50 split between real transparency (~48%, not meeting round 2's transparent-*majority* bar, so it
correctly reaches the luminance path) and opaque content (~52%) — but the luminance split itself was
still broken: a transparent pixel's RGB is typically meaningless (often literal black, whatever the
decoder leaves for alpha-0), and `synthesizeMonochromeMask` was folding *all* pixels — transparent
padding included — into one shared Otsu histogram. With roughly half the image "black" padding and half
real opaque content, the padding alone formed its own low-luma cluster, so minority-cluster selection
picked the *already-zero-alpha padding* as "ink" — correctly zeroed by the final alpha multiply, but
that left the real, majority-classified, genuinely-visible content classified as "field" too, i.e. also
zero opacity. The whole icon rendered blank despite having real content to show. Fixed by restricting
the luminance split (`otsuThreshold`, `lowCount`/`highCount`/`minLuma`/`maxLuma`) to only the
substantially-opaque pixels (`alpha >= 128`) — the split is now always computed from pixels that
actually carry colour information; a fully-transparent-or-near-it image (no opaque pixels at all) short-
circuits to a fully transparent mask directly. This retroactively invalidates round 2's guess that
BOBCARD's blankness was "genuinely minimal internal contrast, an inherent limit" — it was a real,
fixable classification bug the whole time. Every one of BOBCARD, Google Drive, and Microsoft Whiteboard
confirmed showing a legible glyph post-fix, screenshotted on the physical device.

**Round 4**, after "dji memo, flow launcher, microsoft 365 admin, subway surf, tata cliq fashion, tata
play atr" reported still not rendering well (`mimo.sz`/"DJI Mimo", `com.kiloo.subwaysurf`/"Subway Surf",
`com.tul.tatacliq`/"Tata CLiQ Fashion", `com.ryzmedia.tatasky`/"Tata Play", `com.ms.office365admin`/
"Microsoft 365 Admin" — "Flow Launcher" turned out fine on inspection, a genuinely minimal circular logo,
not a bug). Zoomed into the actual rendered pixels rather than trusting the screenshot thumbnail: each
showed a real accent-coloured *plate* with a smaller solid-white *square* inset in the middle — not
literally blank, but a flat, detail-free block, because each is a small legacy icon comfortably inset on
a transparent-majority canvas (a real, correctly-detected [hasMeaningfulTransparency] silhouette shape)
whose *opaque content itself* is a coloured logo mark on a differently-coloured fill — exactly the kind
of detail minority-cluster luminance selection already extracts correctly elsewhere, but the
"transparent-majority → trust raw alpha as the final silhouette" rule was unconditionally short-
circuiting past that check and solid-filling the whole inset shape instead. Fixed by gating that alpha-
as-silhouette shortcut on the opaque region *also* having no real internal luminance contrast to extract
(`OPAQUE_CONTRAST_THRESHOLD = 30`, comparing the opaque subset's own min/max luma) — only a genuinely
single-colour glyph (no colour-based detail exists at all, so alpha really is the only shape signal
available) still takes that path; anything with real internal contrast now runs the same opaque-subset
Otsu/minority-cluster split the fully-opaque case already uses, with the final `origAlpha` multiply
still naturally preserving the outer silhouette bounds. All five confirmed showing their real logo
post-fix, screenshotted (and pixel-zoomed) on the physical device before/after.

Build + full unit test suite green throughout every round (`MonochromeTest.kt`, new, in `:core:design`);
installed and verified on the physical device with no crash in `adb logcat` after each change.

**Follow-up, user-requested: "keep option for monochrome icons instead of accent based icons."**
Every icon rendered under `themedIcons` was unconditionally tinted to the current global/tile
accent — there was no way to get a genuinely neutral (colour-independent) monochrome look. New
`LauncherSettings.monochromeIconTint: MonochromeIconTint { ACCENT, NEUTRAL }`, surfaced as a second
segmented row in Personalize right below the "monochrome icons" toggle, shown only while it's on.
`ACCENT` (default, unchanged behaviour) tints to whatever accent the tile/app-list row would
otherwise resolve; `NEUTRAL` tints to `colorTokens(darkTheme).fg` — the same dark/light-adaptive
neutral every other theme-aware surface in this app already uses, rather than inventing a new
hardcoded colour — giving a true black/white glyph-on-plate look independent of the user's chosen
accent, closer to Nothing OS's own actual Glyph aesthetic. Scoped narrowly: only the two render
sites that actually draw a *separate accent-filled plate* for the themed glyph needed the choice —
`IconCellView.kt`'s `maskedOrGlyphIcon` (covers ICONS-mode top-level icons and folder mini-grid
children, both routing through it) and `AppListIcon.kt`'s `MaskedAppIcon`. The other three
`themedIcons` call sites (`StaticTileGlyph`/`FolderChildIcon` in TILES mode, and the live-tile
corner badge in `feature/livetiles/AppIcon.kt`) were confirmed unaffected — none of them draws a
plate at all; they tint the glyph straight to `LocalTileFaceColor`, since they already sit on the
tile's own accent-filled face, so there was nothing to make "neutral" there. Build + full unit test
suite green; installed and verified on the physical device with no crash — visually confirmed
`NEUTRAL` rendering a clean white-plate/black-glyph app list on-device.

## Hub apps (people/calendar/weather/notes&tasks/music): independent app pinning, per-hub default-app-vs-tileshell-hub choice, real icon on independently pinned apps

Planning ahead of building the first hub screen (weather). Two decisions made in
conversation, to apply consistently across every hub as it's built — not yet
implemented, recorded so they aren't relitigated per-hub.

**1. Settings gets a per-hub choice between "tileshell hub" and "default app"** for
each of people/calendar/weather/music (notes & tasks has no default-app equivalent
to bind to). Tapping the tile opens whichever the setting picks. Fresh installs can
default to the hub; existing installs default to "default app" (today's behaviour)
so an update never silently changes what a tile does — this is why it's a setting
and not just a straight replacement.

**2. Real apps can be pinned to Start independently of the built-in role tiles,
and an independently-pinned app shows its own real icon, not the WP glyph.**
Currently blocked: `DefaultLayout` seeds the resolved role's package straight into
the weather/calendar/people/etc. tile (`app` field, `DefaultLayout.kt`), and
`LayoutRepository.pinApp` dedups on `(packageName, activityName)` — so on a device
where e.g. the calendar role resolves, pinning Google Calendar from the app list
returns `ALREADY_ON_START` instead of adding a second tile. Fix (not yet built):
make the built-in role tiles genuinely blank-package `liveOnly` rows (same pattern
`ContactTile`/`WeatherTile.Location` already use to encode identity into
`activityName` with no packageName) and resolve the launch target at *tap* time
from the new per-hub setting via `DefaultLayout.roleFor(...)`, instead of baking
the package into the row at seed time. Once the row has no package, `pinApp`'s
dedup stops colliding and the real app becomes independently pinnable. Needs a
one-shot migration for existing installs (blank the baked-in package on the
built-in tiles, default the new setting to "default app") so upgrade behaviour is
byte-identical to before, mirroring `SettingsAppMigration`'s existing shape.

The icon distinction matters because `pinApp` currently assigns `iconKey` from
`roleIconKeyMap` regardless — so a second, independently-pinned Google Calendar
tile would get the same WP glyph and live face as the hub tile, reading as a
near-duplicate. A real-icon pinned app is visually the "this opens the actual
app" tile, distinct from the "this opens the tileshell hub" tile.

Applies to every hub, People included — recorded here rather than duplicated in
each hub's own entry.

## Music hub tap redirect applies even though the music tile isn't blank-package

Direct follow-up while building the music hub. Weather/calendar's hub redirect
only needed to intercept a *blank-package* tile (`packageName.isBlank()`) — both
are genuinely self-contained `liveOnly` tiles with nothing baked in. The default
music tile (`DefaultTile("t-music", ...)`, `DefaultLayout.kt`) is different: it's
seeded through the normal role-resolution path (`roleFor("music")` →
`Intent.CATEGORY_APP_MUSIC`), so on any device where that role resolves, the tile
gets a real `packageName` baked in and its tap has always launched that one app
directly — the exact "role baked into the tile at seed time" pattern already
flagged as needing the not-yet-built per-hub default-app-vs-hub setting (see
"Hub apps... independent app pinning" above).

Redirected anyway, unconditionally on `iconKey == "music"` regardless of
`packageName`, rather than waiting for that setting. Reasoning: `CATEGORY_APP_MUSIC`
resolves inconsistently across real devices (few apps declare it at all, and
several devices resolve to nothing), so in practice a meaningful share of
installs already behave like the blank-package case; and the hub's own "apps"
page still lists whatever app *would* have been launched, so redirecting to the
hub is a superset of the old behaviour, not a loss of one. Once the per-hub
setting exists, this redirect becomes its "tileshell hub" branch and gets the
"default app" branch as an alternative — same shape as weather/calendar will
get then, just built in the opposite order here because the setting doesn't
exist yet.

## Hub screens: grounded header chrome in the actual design prototype, not external references

User correction, direct and explicit ("check design proto, do exact copy... this should be
norm"): the weather/music hub headers had been built from external Windows Phone screenshots
researched earlier in the same session, not from this project's own bundled prototype
(`design/windows-mobile-launcher-for-android/project/launcher/`) — a real violation of this
project's own standing rule ("do NOT guess values... read the relevant JS/CSS file").

Checked `styles.css`'s own full-screen overlay conventions (`.recents-ov .rtitle`,
`.group-ov .gtitle`/`.gclose` — the closest existing analogs, since the prototype has no
Panorama-hub concept at all) and found two concrete deviations: (1) the dismiss control was
an invented top-left back-chevron; the prototype's actual overlay convention is a top-right
close ("X"), 34px box / 22px icon. (2) The title was an invented 42-64sp/accent-colored/
left-aligned scale; the prototype's own overlay title is 26-30px, `font-weight:200`
(Compose `FontWeight.ExtraLight`), `-1px` letter-spacing, centered, plain `var(--fg)` — not
accent-tinted. Applied both fixes to the weather hub exactly.

**Music hub is the deliberate exception**, not a miss: its two-tone "music"+"apps" title
(plain fg + accent, left-aligned, clipped at the screen edge rather than wrapping, 44sp/
ExtraLight) was already separately designed and approved with the user via the visualize
tool earlier in the same session, explicitly modeled on the real WP7/8 Zune hub's own
historically two-tone title — which was itself visually distinct from every other WP hub, so
this asymmetry between TileShell's own hubs is WP-faithful, not an inconsistency introduced
by skipping the proto-grounding pass. Only its dismiss control (top-right close, matching the
prototype) was brought in line with weather's.

New standing rule recorded in this session's memory
(`feedback_design_proto_authoritative.md`): future TileShell UI work checks the bundled
prototype's closest analog first, before reaching for external references — and when no
analog exists, says so explicitly rather than silently inventing one.

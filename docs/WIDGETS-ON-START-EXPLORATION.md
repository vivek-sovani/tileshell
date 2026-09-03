# Real Android Widgets on the Start Screen — Exploration & Reference

Status: **parked** — explored in conversation only, nothing implemented, not
scheduled in `design/SESSION-PLAN.md`. This document exists so a future
session doesn't have to re-derive the analysis from scratch.

## Why this came up

Users coming from mainstream Android launchers (Pixel Launcher, Samsung One
UI, Nova) expect real third-party widgets to sit directly on the home
screen grid. TileShell already hosts real Android widgets (`AppWidgetHost`/
`AppWidgetHostView`) — but only on the glance/feed page
(`FeedAppWidgetHost`, `feature/start/src/main/java/com/tileshell/feature/
start/feed/WidgetSlot.kt`), never inside the Start screen's own WP-style
tile grid. Some users may see the absence of widgets on Start specifically
as a limitation versus other launchers, even though the capability exists
one swipe away on glance.

Explored: whether/how real widgets could be embedded directly into Start's
tile grid, and what would have to give to make that work.

## Conclusion (read this first)

- **Technically reachable.** Every platform primitive this would need
  already works elsewhere in this app: widget binding + permission model,
  `AndroidView`-hosted `AppWidgetHostView` inside Compose, corner-round
  theming of a real widget, and state-driven (not gesture-raced) touch
  ownership during edit mode.
- **Not free.** It genuinely conflicts with the WP tile grid's fixed-size
  identity, and — at small tile sizes — with room for a safe gesture
  boundary.
- **Scoping to bigger tiles only** (WIDE/LARGE/XLARGE — the same MEDIUM+
  threshold Tasks/Notes already use for "needs real room") resolves most of
  the risk. An earlier pass at this analysis assumed a fundamental
  gesture-race problem; that was a misdiagnosis of a bug that's since been
  fixed for an unrelated reason — see "Gesture model" below.
- **Decision: not being pursued now.** If revisited, start from
  "If this is ever built" below rather than re-deriving the analysis.

## What already works today, reusable as-is

- Widget bind/configure flow, including the permission model
  (`bindAppWidgetIdIfAllowed` / `ACTION_APPWIDGET_BIND` fallback) — proven
  and shipped on the glance page.
- `AndroidView`-hosted `AppWidgetHostView` inside Compose — same file.
- Corner-rounding a real widget's own visual chrome to match TileShell's
  tile identity — proven this session for the standalone `AppWidgetProvider`
  widgets (a self-rounding background `Drawable` + `setClipToOutline`, NOT
  launcher-side view clipping, which was tried first and confirmed not to
  work).
- State-driven touch ownership during edit mode. `WidgetEditOverlay`
  already gives the launcher's own chrome 100% of touches once
  `editing == true`, with zero ambiguity, because it's driven by explicit
  boolean state rather than a live gesture guess. This pattern transfers
  directly.
- Widget stacking (multiple real widgets cycling through one swipeable
  carousel slot) — `isStackMergeEligible` / `mergeIntoStack` /
  `WidgetStackView` / `WidgetStackMemberView`, already built and working on
  the glance page. This matches what Samsung One UI and other mainstream
  launchers call "widget stacks" (a real, common feature, not a TileShell
  invention) and would extend to Start tiles as integration work, not new
  invention.
- Binding permission is tied to the app + the widget id, not to which
  screen inside the app displays the result — there's no OS-level
  distinction between "hosted on glance" and "hosted on Start."

## Where it genuinely conflicts with the WP tile grid

### 1. Fixed sizes vs. arbitrary widget footprints

Start's tiles are a small set of discrete WP sizes (SMALL/MEDIUM/WIDE/LARGE
plus the drag-resize-only presets from the android-home-style branch). A
real widget's own `minWidth`/`minHeight` is effectively continuous.
Restricting real-widget tiles to WIDE/LARGE/XLARGE gives enough slack to
absorb most real widgets without clipping or excess dead space — the same
"only bigger sizes get this content" precedent Tasks/Notes already
establish (`AppCategories.requiresTallTile`).

This does **not** fully resolve resizing: corner-dragging a real-widget
tile smaller than the provider's own declared minimum will still clip or
break its content. That needs an explicit rule (floor the resize at the
provider's declared minimum), not something tile-size scoping fixes on its
own.

### 2. Folders — genuinely incompatible, but matches platform convention

A folder shrinks its children to small static icons in a mini-grid; a live
`AppWidgetHostView` has no equivalent shrunk representation. This isn't a
TileShell-specific gap — stock Android launchers don't allow widgets inside
folders either. Conclusion: exclude real-widget tiles from folder
membership outright. This is consistent with the platform, not a
compromise.

### 3. Gesture model — corrected finding, less risky than first assessed

An earlier pass at this analysis assumed embedding a live widget would
reintroduce the "long-press accidentally enters edit mode" bug found and
removed from the glance page's own cards this session (see the
`WhatsNewGlanceGadgets`/`CustomCardKind` removal and, before that, the
glance long-press-to-edit removal in project history). On reconsideration
that diagnosis doesn't hold up:

- The glance bug fired uniformly across *every* card regardless of
  content — meaning it was a scroll-vs-hold disambiguation defect in a
  continuously vertically-scrolling column sharing an axis with the hold
  gesture, not a widget-vs-launcher touch-ownership race. Even a rewrite
  with an explicit movement-cancellation threshold didn't fix it, which is
  further evidence the defect was in that disambiguation logic itself, not
  in any particular widget's behavior.
- Widgets generally don't consume long-press themselves — by platform
  convention the OS reserves that gesture for the host launcher's own
  pick-up/reconfigure flow.
- Start's grid doesn't share the glance page's setup (it isn't a long
  vertically-scrolling column sharing an axis with the hold gesture), and
  its own 430ms long-press-to-edit is already shipped and working for
  ordinary tiles today, with no equivalent defect ever reported.

Remaining real (and much narrower) nuance: a widget with its own internal
scrollable content (an inbox- or agenda-style widget — a minority case) can
claim touch exclusivity for its own scroll once Android recognizes that
gesture, via standard nested-scroll-in-Compose interop. That's not a source
of spurious edit-mode entry — it just means dragging directly on that
widget's own list scrolls the list, which is what a user would expect
anyway.

Additional mitigation available specifically because bigger tiles have
room for it: reserve a real, physically separate margin/edge-strip within
the tile — the same "confine the gesture to a zone the content never
occupies" trick the widget-stack's own swipe-to-flip already uses (a 40dp
edge zone, see `WIDGET_STACK_EDGE_ZONE_DP`). Long-press-to-edit detected
only in that margin turns "which layer owns this touch" into a
deterministic bounds check (whichever view is physically under the touch
point) rather than a timing race against the widget's content.

### 4. Visual theming

Most RemoteViews-based widgets have a transparent root by convention — this
is exactly why Android 12+'s own system widget theming can inject a card
background *behind* third-party widgets. Layering TileShell's own
accent/glass fill underneath and letting the widget's transparent-rooted
content sit on top should work about as well as it did for the standalone
widgets built this session. Not guaranteed for every provider (some paint
their own opaque background), but the common case is favorable.

### 5. Performance

Each hosted widget needs `AppWidgetHost.startListening()` and continuous
RemoteViews updates. Scoping to bigger tiles only is naturally
self-limiting — few large tiles fit on one Start screen (realistically
1-3), which keeps this from becoming a real concern the way an
any-size-tile version would.

## If this is ever built — recommended shape

1. Real widgets selectable only at WIDE/LARGE/XLARGE tile sizes (reuse the
   `AppCategories.requiresTallTile`-style size-gating pattern).
2. The widget's own `AppWidgetHostView` content rect is smaller than the
   tile's full bounds by a reserved margin/edge-strip (mirroring the
   widget-stack's existing 40dp edge-zone convention). Long-press-to-edit
   is detected only in that margin, never raced against the widget's own
   content area.
3. Once `editMode == true`, a full-tile transparent overlay (same shape as
   `WidgetEditOverlay`, already used on the glance page) owns 100% of
   touches — reuse directly, don't reinvent.
4. Real-widget tiles are excluded from folder membership entirely (hard
   rule, matches platform convention — see §2 above).
5. Real-widget tiles *can* merge into a widget stack with other
   real-widget tiles of the same size class — reuse
   `isStackMergeEligible`/`mergeIntoStack`/`WidgetStackView` rather than
   building parallel logic. Start's `StackTileContent` would need a
   variant that hosts an `AndroidView` member instead of a Compose
   live-tile face, matching `WidgetStackMemberView`'s existing real-widget
   branch on the glance page.
6. Corner-drag resize floors at the provider's own declared minimum size
   rather than allowing further shrink.
7. Corner-round the tile via a background layer — the technique already
   proven for the standalone `AppWidgetProvider` widgets this session
   (self-rounding background drawable + `setClipToOutline`), not
   launcher-side view clipping (already confirmed not to work — see this
   session's own corner-rounding debugging trail for the standalone
   widgets).
8. **New picker entry point needed.** Start's own "add widgets" catalog
   (`WidgetListSheet.kt`, `feature/personalize`) is Start-tile-only today —
   it pins real `TileModel` tiles via `LayoutRepository` and has no concept
   of `AppWidgetProviderInfo` at all. This would need either a new tile
   kind (something like `TileModel.Widget`) or a parallel data path
   alongside the existing `TileModel.App`/`Folder` model — real,
   non-trivial modeling work, not just a UI addition on top of what's
   there.

## Explicitly out of scope even if this is revisited

- **Small/Medium tile widget hosting.** Not enough room for a safe
  reserved-margin edge-strip, and most providers' own declared minimums
  wouldn't fit anyway.
- **Widgets inside folders.** Matches platform convention across every
  mainstream launcher — not worth fighting.
- **Winning a long-press race directly against a widget's own content
  area.** This project already tried that approach once (the glance page's
  removed long-press-to-edit) and it didn't hold up on real hardware even
  after a careful rewrite. Don't repeat it — route edit-mode entry through
  a reserved margin (§ "If this is ever built," point 2) instead.

## Related, broader context (a separate topic — not detailed here)

The same conversation also compared TileShell against mainstream Android
launchers more broadly (multi-user/work-profile support, dynamic app
shortcuts, icon packs, per-app icon override, a persistent dock row,
multi-page home screens, themed/monochrome icon theming). Most of those are
either free wins (invisible plumbing — work profile, app shortcuts) or
naturally scoped to ICONS mode only (icon packs, per-app icons, themed
icons) without touching WP identity at all. Two of them — a dock row and
multi-page home screens — would actively work against WP fidelity rather
than fill a real gap, since real Windows Phone had neither (Start was
always one continuous scrolling grid with no fixed dock zone). Worth its
own reference document if that broader comparison is ever acted on; it's a
distinct topic from widgets-on-Start specifically.

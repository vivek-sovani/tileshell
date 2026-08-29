# TileShell — Play Store Listing & Data Safety

*v3.0.0 listing draft — update before each release*

---

## Store Listing

### App name
`TileShell`

### Short description (80 chars max)
`Windows Phone–style launcher with live tiles, feed, and deep personalization`

### Full description (4 000 chars max)

```
TileShell brings the iconic Windows Phone / Windows 10 Mobile Start screen to Android — rebuilt in Kotlin, faithful to the original.

★ LIVE TILES
Clock with date, weekday, and your real next alarm. Live weather with temperature, forecast, and high/low. Next calendar event. Now-playing with album art and transport controls — works with any music app, or pin a dedicated music tile. People mosaic that cycles contact photos. Photos slideshow. Notification counts and message previews on every app tile. All flip on a gentle cycle; pauses automatically in battery saver.

★ NOTIFICATIONS
Unread count badge on every pinned tile. Notification tiles flip to cycle through each pending message — sender, photo, and snippet. A closed folder shows both its combined total and which app inside actually has something pending. Unpinned apps with notifications appear in App List Recent with a badge count. Works with any app, no configuration needed.

★ WIDGET STACKS
Merge two Large (3×3) tiles into a swipeable full-size carousel, or open a folder and choose "make stack · wide" or "· large" — "keep as folder" sits right alongside so you're never one accidental tap from converting. Each member keeps its own colour and shows its own notification badge as it rotates. Every live face stays interactive inside. Swipe near the right edge to flip members; swipe anywhere else scrolls Start as normal.

★ START SCREEN
4, 5, or 6 columns. Small, Medium, Wide, and Large (3×3) tiles on every grid size. Long-press to enter edit mode: drag, merge into folders, resize, recolour per tile or from the app's own icon, or unpin. Lock the layout from Personalize so nothing moves by accident. Landscape mode puts the feed and Start side by side.

★ QUICK SEARCH
Two-finger swipe down on Start. Search apps, contacts, and the web in one gesture. Long-press a contact to call, message, or pin it to Start. Choose your search engine — Google, Bing, ChatGPT, Gemini, Claude, or Perplexity.

★ EDGE STRIP
A quick-launch bar along the bottom of the screen. Search on the left, favourite apps in the centre, recent apps on the right. Collapses to a thin handle when not in use.

★ FEED PAGE (swipe right)
A personalized greeting, live weather card, and today's calendar agenda side by side, now-playing, and Android widgets that size to fit and can sit side by side — drag to reorder. News feed with RSS articles across 8 categories. Pick any number of news regions — India plus ~20 other countries — defaulting to your device's own. Add any custom RSS or Atom feed.

★ APP LIST (swipe left)
Alphabetical grid with A–Z jump grid and instant search. Recent and newly-installed apps at the top with badge counts, even unpinned. Long-press to pin, hide, or uninstall.

★ PERSONALIZATION
14 accent colours · per-tile colour override · colour from the app's own icon · dark/light themes · glass tiles tinted by each tile's own colour · blur · wallpaper-behind-tiles mode · 6 gradient wallpapers · custom photo wallpaper · slideshow · daily Bing wallpaper with history · corner radius · gradient fill · 3 font styles · adjustable tile spacing · lock layout · quick "set as default launcher" · built-in guide.

★ BACKUP & RESTORE
Auto-save on a schedule. Browse a visual history of snapshots and restore any version. Export to a file — save it to Google Drive to restore on your next device.

★ SCREEN LOCK
Long-press the gear icon to lock the screen instantly. Uses the Accessibility API so biometric unlock (fingerprint / face) is preserved.

★ PRIVACY FIRST
No accounts. No analytics. No ads. All data stays on your device. Only weather requests leave (Open-Meteo, no API key), and only when you grant location permission.

Requires Android 8.0 (API 26) or higher.
```

*(~3 650 chars, under Play's 4 000 limit.)*

### Category
Personalization

### Content rating
Everyone

### Tags / Keywords
launcher, windows phone, live tiles, start screen, personalization, home screen,
widget, tiles, windows mobile, wp8, wp10, metro, fluent

---

## Data Safety Form (Play Console answers)

### Does your app collect or share any of the required user data types?
**Yes** — location is sent to a third-party weather API.

### Data types

#### Location — Approximate location
| Field | Answer |
|---|---|
| Collected? | Yes |
| Shared with third parties? | Yes — sent to Open-Meteo (weather API) |
| Required or optional? | Optional (the Weather tile stays static if denied) |
| Processed ephemerally? | No — coordinates are sent over the network per weather refresh |
| Why collected? | App functionality (weather forecast for the live tile) |

*Note: location is NOT stored on developer servers, only sent device→Open-Meteo.*

#### Contacts
| Field | Answer |
|---|---|
| Collected? | Yes — contact names and profile photos |
| Shared? | No — stays on device |
| Required or optional? | Optional (People tile stays static if denied) |
| Processed ephemerally? | Yes — read at display time, not persisted by the app |
| Why collected? | App functionality (People live tile mosaic) |

#### Calendar events
| Field | Answer |
|---|---|
| Collected? | Yes — upcoming event titles and times |
| Shared? | No — stays on device |
| Required or optional? | Optional (Calendar tile shows date-only without permission) |
| Processed ephemerally? | Yes — polled every 5 min while tile is visible, not persisted |
| Why collected? | App functionality (Calendar live tile next-event display) |

#### App activity — App interactions
| Field | Answer |
|---|---|
| Collected? | Yes — recently launched apps (capped at 12, local only) |
| Shared? | No |
| Required or optional? | Core functionality (Recent apps section in App List) |
| Processed ephemerally? | No — stored in local DataStore |
| Why collected? | App functionality (Recent / newly installed apps section) |

### Data types NOT collected
- Personal information (name, email, phone, address, SSN)
- Financial information
- Health and fitness
- Messages / SMS content
- Photos or videos *selected by user* for wallpaper are stored **on-device only**
- Files and docs
- Web browsing
- Device or other IDs
- Audio / Voice

### Encryption in transit?
**Yes** — all network requests (Open-Meteo, RSS feeds) use HTTPS.

### Can users request data deletion?
**Yes** — all data is stored locally on the device. Uninstalling TileShell removes all
data. Users can also clear app data via Android Settings at any time.

---

## Graphic Assets Checklist

| Asset | Size | Notes |
|---|---|---|
| App icon | 512 × 512 px PNG | No alpha, high-res launcher icon |
| Feature graphic | 1024 × 500 px | Required for listing banner |
| Phone screenshots | Min 2, 1080×1920 or 1440×2560 | Start screen, feed, personalize, edit mode |
| 10" tablet screenshots | Optional | |

## Release notes (v3.3.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 3.3.0

• New: in icons mode, stretch any app icon from 1x1 up to 4x4 — pick "show as
  icon" or "show as tile" per app from the colour picker
• New: wallpaper crop/apply shows a live preview of your real Start screen on
  the photo, with an apply animation
• Changed: gradient tile fill now also applies to Quick Panel and glance cards
• Fixed: icon blur at bigger sizes, notification badge scale/position on
  resized icons, icon shape masking, wallpaper-behind-tiles leak in preview
```

*(Character count 494, under Play's 500 limit.)*

### Full changelog since v3.2.0 (for reference — not the Play-facing blurb above)

- **Resizable icons-mode app icons**, a OneUI/Nothing-OS-style capability: in "icons" home
  style, any app icon can now be stretched from 1×1 up to 4×4 (previously fixed at 1×1, with
  anything bigger auto-rendering as a live tile). A new per-app **"show as icon" / "show as
  tile"** toggle lives in the tile's colour picker (mirroring the folder stack toggle) — icons
  mode still defaults every app to a plain icon, and the toggle only appears once the app is
  resized past 1×1, since a stretched icon can't also show live content.
- **Icon-shape masking made consistent at every size** — the chosen shape (circle/squircle/
  rounded/square/original) previously only applied correctly to 1×1 icons; icons rendered
  inside bigger tiles (notification/music/photos corner icons, folder mini-grid children, and
  the new resizable icons above) now all mask to the same shape.
- **Icon blur at larger sizes fixed** — icon bitmaps now decode at a resolution matching their
  actual on-screen size instead of a fixed low resolution, so a stretched-up icon stays sharp.
- **Notification badges on resized icons** now anchor to the icon's own corner (not the whole
  cell), scale proportionally with icon size, and sit right at the corner edge instead of
  inset inward.
- **Wallpaper crop/apply gets a real live preview**: choosing a photo — from Personalize,
  sharing one into TileShell, or another app's "apply via" / "set as wallpaper" chooser — now
  shows the actual Start screen (real tiles, real grid) composited on the candidate photo,
  instead of a bare photo or an approximate mockup. Confirming plays a short apply animation
  and, when opened from Personalize, returns straight to Start instead of leaving Personalize
  open.
- **Fixed**: the new wallpaper preview leaked the full photo behind everything when
  "wallpaper behind tiles" mode was active, instead of respecting the flat-background/
  tiled-window split that mode uses everywhere else.
- **Gradient tile fill extended**: the existing "gradient fill" tile-style setting previously
  only painted Start tiles — it now also applies to Quick Panel tiles and the feed's glance
  cards (weather/agenda/now-playing).

## Release notes (v3.2.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

**Note:** a genuine new capability (Quick Panel and glance-page resize/reorder, One UI-
inspired), not a fix-only release — surfaced above the fixes here, per this doc's usual
split. The fixes are real bugs found and fixed during the SAME on-device testing pass that
shipped the feature, not separate pre-existing issues, so they're folded into this one
changelog entry rather than a follow-up patch release.

```
TileShell 3.2.0

• New: resize Quick Panel tiles between square and wide — drag the edge handle
• New: drag to reorder Quick Panel tiles, or weather/agenda/now-playing on the feed
• New: one edit toggle now shows move/resize handles on every tile or card at once
• Changed: distinct move (grip-dot) vs resize (bar/arc) handles, matching One UI
• Fixed: resize/reorder bugs, and personalize not following the wallpaper accent
```

*(Character count 424, under Play's 500 limit.)*

### Full changelog since v3.1.0 (for reference — not the Play-facing blurb above)

- **Quick Panel tiles are now resizable and reorderable**, mirroring One UI's own resizable
  quick-settings tiles (the explicit reference point for this whole feature) — a new pencil
  icon in the panel header toggles edit mode, showing a move handle (top) and a width-resize
  handle (right edge) on every tile at once, no per-tile long-press. Drag a tile's resize
  handle to switch it between square (1 column) and wide (2 columns); drag its move handle
  to reorder. The edit icon swaps to a checkmark while editing.
- **Weather, agenda, and now-playing on the feed page are now resizable and reorderable too**,
  folded into the exact same system the feed's hosted Android widgets already used — one
  "edit"/"done" toggle next to "widgets" now drives every card (built-in and hosted) at once,
  replacing the old per-widget "edit" tap-in. These three cards can resize both width and
  height now, not just width.
- **Handle redesign**: move and resize now use visually distinct shapes — a small grip-dot
  pill for move (previously a "≡" glyph, then briefly a plain bar indistinguishable from a
  resize handle), thin bars for single-axis resize, and a quarter-circle arc (not a dot) for
  the corner both-axes handle — applied consistently across the Quick Panel and every feed
  widget/card.
- **Live resize preview**: dragging a resize handle now shows the tile/card growing or
  shrinking smoothly in real time (a lightweight visual scale, not a full relayout), instead
  of no feedback at all until release (Quick Panel) or a discontinuous jump once you let go
  (feed cards paired side by side).
- *Fixes found and made during this session's own on-device testing (not pre-existing
  issues)*: a Quick Panel tile could occasionally resize its neighbour instead of itself; a
  resize handle sitting too far inside a feed card made it compete with the card's own
  content for the touch; and — the deepest one — resizing or reordering the *same* tile a
  second time within one edit session could silently do nothing, traced to a well-known
  Jetpack Compose gesture-handler pitfall (a drag handler's callbacks getting "frozen" after
  the very first use unless explicitly kept fresh) affecting every handle built this session.
- **Fixed**: the Personalize sheet's own chrome (selected pills, sliders, highlights) always
  showed the plain global accent colour, even when "wallpaper" was chosen as the tile colour
  source — every other screen (Start, feed, Quick Panel, app list) already followed the
  wallpaper-derived accent correctly; Personalize itself was the one screen still out of sync.

## Release notes (v3.1.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

**Note:** almost entirely fixes and polish, plus one real feature (drag a folder child to
any spot) and one widened rule (widget-stack eligibility) — per this doc's usual split,
they're both surfaced above the fixes here rather than pushed to the changelog-only section,
matching how a fix-heavy release (see v1.1.1) is handled.

```
TileShell 3.1.0

• New: drag an app out of a folder straight to any spot on the grid
• Changed: widget stacks now allow Wide Small and Banner sized tiles
• Fixed: tapping a cycling notification tile opens the message shown, not always the newest
• Fixed: weather, calendar, and Quick Panel tiles stay readable over a light wallpaper colour
• Fixed: music/weather/calendar/clock tiles no longer clip text or controls at Wide Small/Banner size
```

*(Character count 452, under Play's 500 limit.)*

### Full changelog since v3.0.0 (for reference — not the Play-facing blurb above)

- **Drag an app out of a folder to a chosen spot**: alongside the existing tap-the-corner-×
  unpin shortcut (which still always appends to the bottom), a folder child can now be
  press-and-dragged out and dropped exactly where released — onto an empty cell (sticky/free
  push-down, or dense-mode live reflow of the surrounding tiles), or onto another tile's merge
  zone to fold into it, matching how every other tile move in this app already works. Three
  real bugs were caught and fixed while building and verifying this on-device:
  - The merge zone committed to a merge the instant the drag passed through it, with no
    dwell — unlike every other merge in the app, which needs a deliberate ~250 ms pause. This
    made it read as if dense mode only ever offered "merge" or "append at the bottom," since
    almost any drag path toward a specific spot grazed some tile's merge zone along the way.
  - Dense mode's drop preview had no live reflow — sticky mode already showed the other tiles
    sliding out of the way as you dragged, but dense mode gave no visual feedback at all about
    where the tile would land. Fixed by temporarily splicing the dragged child into the real
    top-level tile order during the hover, the same mechanism an ordinary tile drag already
    gets for free.
  - The real root cause of "it still lands at the bottom sometimes": a client-side effect that
    reconciles the on-screen tile order after each database write blindly appended any
    brand-new tile id to the very end, discarding the specific position the database had
    actually just written for it. Ordinary new pins never exposed this (appending is exactly
    where they belong anyway) — this drag-out feature was the first case where a brand-new
    tile needed to land in the middle of the grid.
- **Widget stacks: Wide Small and Banner now allowed.** Stack eligibility required both tile
  dimensions greater than one cell, which excluded these two single-row (but multi-column)
  presets even though they have plenty of horizontal room. Loosened to "more than one column,"
  so Wide Small/Banner folders can now become stacks too; Tall/Column (single-*column*) stay
  excluded, since a one-cell-wide strip is still too cramped to swipe between.
- **Notification tile tap mismatch, fixed.** A cycling mail/messages/notification tile
  genuinely shows a different message every ~2.6s, but tapping it always opened the newest
  one's screen regardless of which message was actually on display — the visual cycling and
  the tap target were entirely disconnected. Fixed by having the tile report which specific
  message it's currently showing, and opening exactly that one.
- **Adaptive text colour for wallpaper-accented surfaces.** The glance screen's weather/
  today/now-playing cards and the Quick Panel's active toggle tiles (wifi, bluetooth, theme,
  etc.) always rendered white text over their accent fill — unreadable once that
  wallpaper-derived accent turned out light. Both now pick dark or light text based on their
  own fill's brightness, reusing the same contrast helper the feed page already uses elsewhere.
- **Single-row tile fixes (Wide Small/Banner): music, weather, calendar, clock.** Each of
  these live faces laid out its content as a stack of lines/controls that needed more height
  than one grid row provides, so the last item — the music tile's play/pause/skip row, or the
  weather/calendar tile's last text line — was silently clipped off. Music now switches to a
  compact horizontal layout at this size; weather and calendar shrink their fonts and padding
  to fit the same left-aligned stack in one row. Clock needed its own fix: it already
  continuously scales its font size to its measured height, but that scale is floored at 60%
  — fine at the default 4 columns, but at 5 or 6 columns (where every tile, including a
  one-row one, is smaller again) the floor stopped it shrinking far enough, clipping the date
  line specifically at higher column counts. Fixed with fixed, already-small sizes for this
  one case instead of relying on the floored scale.

## Release notes (v3.0.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

**Note:** major-version bump — this is the biggest new capability since the original Windows
Phone recreation: a second, genuinely different home-screen style living side by side with the
first. Bug fixes are deliberately left out of the Play-facing text, per this doc's usual
convention; the full changelog below carries them for reference.

```
TileShell 3.0.0

• New: "icons" home style — shaped app icons alongside live tiles, picked in a first-run wizard
• New: icon shapes — circle, squircle, rounded, square, or your device's own
• New: eleven tile sizes with drag-to-resize corners
• New: "free" arrangement — nothing moves unless you move it
• New: any tile size can become a swipeable widget stack
• New: weather/calendar/clock stay live as small icons
• Changed: notification tiles use the full tile, centred
```

*(Character count 492, under Play's 500 limit.)*

### Full changelog since v2.5.1 (for reference — not the Play-facing blurb above)

- **"Icons" home style**: a new top-level choice (Personalize · home style) alongside the
  existing Windows Phone "tiles" look — a normal Android-style launcher with shaped app icons,
  live tiles, folders, and free placement, sharing the same layout/persistence/gestures/app-
  drawer/backup as "tiles" underneath. Icon vs. live tile is derived purely from a tile's own
  size: Small renders as a shaped icon, Medium and up render exactly as in tiles mode. Shown via
  a one-time first-run wizard (fresh installs, and existing installs upgrading to this version)
  with a real live preview built from the actual tile-rendering composables, not a mockup.
- **Icon shapes**: circle, squircle (a true superellipse, not an approximated rounded rect),
  rounded, square, and original (the device's own, unmasked shape) — a real adaptive-icon
  re-masking technique is used so squircle/rounded/square actually change an adaptive icon's
  shape instead of leaving the OS's own circular mask baked in underneath.
- **Eleven tile sizes total**: the original four (Small/Medium/Wide/Large) plus seven reachable
  only by dragging a tile's corner handle (Wide Small, Tall, Wide Medium, Tall Medium, XLarge,
  Banner, Column) — growing or shrinking a tile across the Small boundary is what converts it
  between a shaped icon and a live tile.
- **"Free" tile arrangement**: a third placement mode (alongside the existing Sticky and Dense)
  that icons mode defaults to — nothing moves unless you move it, and dropping a tile onto an
  occupied cell swaps the two instead of pushing anything down.
- **Widget stacks widened**: any tile size roomy enough on both axes (not just uniform Wide/
  Large) can become a swipeable widget-stack folder via a "show as stack" toggle in the per-tile
  colour picker; dragging a stack's corner resizes and homogenizes every member at once.
- **Weather/calendar/clock stay live as small icons**: instead of a static glyph, these render as
  a genuine mini live tile (real-time clock, live weather, today's date) even at the smallest
  icon size, matching how a real Android launcher's dynamic icons behave.
- **Notification tiles use their full space**: mail/messages/any app's notification tile at
  every one of the eleven sizes now scales its avatar, font size, and number of visible snippet
  lines to that size — a big tile shows meaningfully more text instead of the same cramped
  layout a small tile uses — and centres that content vertically instead of pinning it to the
  top with empty space left below.
- Also included: narrow (Tall/Column) live tiles reflow their text stacked and centred instead of
  clipping at one column wide; the App List respects icon shape and home style too; a folder's
  closed mini-grid always shows real app icons (and a bigger icon, no background plate) in icons
  mode; tile colour source gained a "wallpaper" option matching the feed/Quick Panel's own
  wallpaper-derived accent; picking "icons" in the first-run wizard actually shrinks the seeded
  default tiles down to icon size instead of leaving them as big live tiles; the guide and about
  sheets document all of the above.

## Release notes (v2.5.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

**Note:** this blurb intentionally repeats v2.5.0's feature list alongside v2.5.1's own, so
anyone updating from v2.4.x sees everything that is new to them in one place. Bug fixes are
deliberately left out of the Play-facing text — both v2.5.0's and the widget-stack fixes made
after v2.5.1's feature landed. The full changelog below carries them for reference.

```
TileShell 2.5.1

• New: stack two glance widgets into one swipeable card
• New: hide the status bar for a full-screen Start (default on)
• New: Quick Panel docks from the top with a status header
• Changed: swipe down for Quick Panel, up for quick search
• New: brightness/volume sliders with tap-to-mute
• New: share a photo to set it as your wallpaper
• New: haptic feedback across Quick Panel and search
• Personalize, guide and about are now full-screen
```

*(Character count 457, under Play's 500 limit.)*

### Full changelog since v2.5.0 (for reference — not the Play-facing blurb above)

- **Feed widget stacks**: two hosted widgets on the glance screen can be merged into a single
  swipeable card by dragging one onto the other's centre, so a dense reading surface doesn't lose
  a whole row per widget. The card auto-rotates between its members every ~10s, a strip at its
  right edge flips them by hand, and "unstack" from the edit overlay splits them apart again.
  Only same-width widgets can merge, and a half-width stack still pairs beside a half-width
  widget rather than taking a row of its own.
- *Fixes (not in the Play blurb)*: the stack's drag handle was hidden underneath its action
  pills so it couldn't be moved at all; dragging a stack dissolved it instead of repositioning
  it; a widget dropped near a stack was absorbed into it rather than placed beside it; the card
  visibly resized as it rotated between members.
- *Fix (not in the Play blurb)*: the App List's pin/hide/uninstall long-press was raised from
  450 ms to 700 ms — it was firing on an ordinary tap-and-linger, so a press meant to launch an
  app opened the menu instead.

## Release notes (v2.5.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.5.0

• New: hide the status bar for a full-screen Start (on by default)
• New: Quick Panel now docks from the top with clock/wifi/battery status
• Changed: swipe down for Quick Panel, swipe up for quick search
• New: brightness/volume are real sliders with a tap-to-mute icon
• New: share a photo from Gallery to set it as your wallpaper
• New: haptic feedback across Quick Panel, search, and App List
• Personalize, guide, and about screens are now full-screen
```

*(Character count 424, under Play's 500 limit.)*

### Full changelog since v2.4.0 (for reference — not the Play-facing blurb above)

- **Hide status bar**: new Personalize toggle (on by default) hides the system status bar for a
  more full-screen Start; fixed the freed space not being reclaimed on some devices (a display-
  cutout inset needed skipping too) and the swipe-to-peek reveal staying shown permanently on at
  least one real device (now auto-rehides after a short delay).
- **Quick Panel redocked to the top**, with a new device-style header: clock/date on the left,
  wifi/bluetooth/cellular-or-airplane-mode/battery status on the right (battery shows a
  proportionate fill, colour-coded green/amber/red), and a second row of personalize/android-
  settings/lock-screen shortcut icons. Grid trimmed to 4 columns with better spacing.
  Brightness/ring/media volume are now real drag sliders (replacing tap-to-step), with a tap-to-
  mute icon on ring/media. A drag-down handle closes the panel.
- **Gesture directions swapped**: two-finger swipe down now opens the Quick Panel (was quick
  search); two-finger swipe up now opens quick search (was the Quick Panel), whose search box
  moved to the bottom of the screen to match its new slide-up-from-bottom animation.
- **Full-screen sheets**: Personalize, the "how to personalize" guide, and the "features & info"
  about sheet now fill the whole screen instead of a capped bottom sheet.
- **Device status moved, then removed from glance**: battery/cellular/wifi were first moved from
  the glance page into the new Quick Panel header; after further feedback the whole device-status
  card was removed from the glance page entirely, since the same information now lives in the
  Quick Panel.
- **Quick Panel background**: now synthesizes a colour gradient from the wallpaper, same mechanism
  as the glance page, and respects the glance page's own "no background" opt-out toggle in
  addition to Start's own wallpaper state.
- **Share a photo to set your wallpaper**: TileShell can now be picked as a target from another
  app's "share" sheet (e.g. Gallery/Photos) — sharing a photo imports it and opens the same crop/
  reframe overlay used by the in-app wallpaper picker, before it's saved as the wallpaper.
- **Haptic feedback** added throughout the Quick Panel (gesture/slider/tile/toggle interactions),
  quick search (app/contact/search taps, recent-search actions), and the App List's long-press
  pin/hide/uninstall menu.
- Considered showing per-SIM signal strength on dual-SIM devices; dropped in favour of the existing
  single connectivity indicator, since real per-SIM state needs the Play-restricted
  `READ_PHONE_STATE` permission.

## Release notes (v2.4.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.4.0

• New: Quick Panel rebuilt as square WP-style tiles, real toggle states
• New: swipe up from a screen edge also opens the Quick Panel
• New: swipe down from a screen edge opens notifications/quick settings
• New: occasional "enjoying tileshell?" rating prompt
• New: personalize is now a normal Start tile, not a corner icon
• Fixed: bluetooth tile now shows its real on/off state
• Fixed: Quick Panel now docks correctly in landscape mode
```

*(Character count 456, under Play's 500 limit.)*

### Full changelog since v2.3.1 (for reference — not the Play-facing blurb above)

- **Quick Panel rebuilt as a true WP Action Center**: one 5-column grid of perfect square tiles
  (was wide chip/slider rows) — toggles for wifi, bluetooth, location, airplane, flashlight,
  rotation lock, dnd; tap-to-step brightness/media-volume/ring-volume/screen-timeout
  (0/10/20/40/60/80/100%, no drag sliders); one tap-to-cycle dark/light/auto theme tile;
  "personalize", "android settings" (real device icon), and "lock screen" shortcut tiles.
- **Settings affordance moved onto Start**: the floating corner settings-gear icon is gone —
  personalize now opens via a normal, draggable/resizable/unpinnable Start tile, pinnable back
  from the App List if accidentally removed. The real Android Settings app keeps its own
  existing Start tile (now showing its real device icon) and is unhidden from the App List again.
- **Fixed**: the bluetooth toggle never showed real on/off state (hardcoded off); the Quick Panel
  spanned the full screen width and overlapped tiles in landscape instead of docking beside Start.
- **Tile order refined** across several rounds of on-device feedback: connectivity toggles
  grouped first, location moved third, dnd moved down the list, rotation lock placed next to
  brightness, media volume moved to the row's right edge.
- **New gestures**: single-finger swipe up from either screen edge opens the Quick Panel
  (alongside the existing two-finger swipe); single-finger swipe down from the left/right screen
  edge opens system notifications/quick settings respectively.
- **New**: an occasional, day-interval-gated "enjoying tileshell?" prompt using Play's native
  in-app review flow, with an email-feedback fallback for "not really."

## Release notes (v2.3.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.
2.3.0 (code 230) is already published — same feature set, same blurb, plus
one additional fix found via Play Console's pre-launch report for that
release (bitmap downsampling in the layout-history thumbnail).*

```
TileShell 2.3.1

• New: feed & personalize screens redesigned with a personalized greeting
• New: feed widgets pair side by side and reorder by drag
• New: glance background is a synthesized colour gradient, with its own toggle
• Fixed: now-playing tile no longer shows "paused" while music is playing
• Fixed: feed greeting name now reliably picks up your contact profile
• Fixed: badges & live mail now has its own row in Personalize
• Fixed: layout-history thumbnails now decode at a smaller size
```

*(Character count 499, under Play's 500 limit.)*

## Release notes (v2.3.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.
v2.2.8 (code 228) was submitted to Play but rejected under "Invalid Data
safety form" (approximate location was collected but not declared — a
console-only fix, see DECISIONS.md "Data Safety form rejection"; that
console correction still needs to be made before this build is resubmitted).
v2.3.0 supersedes it, rolling in all interim work plus three fresh bug fixes
from this session's user reports.*

```
TileShell 2.3.0

• New: feed & personalize screens redesigned with a personalized greeting
• New: feed widgets pair side by side and reorder by drag
• New: glance background is a synthesized colour gradient, with its own toggle
• Fixed: now-playing tile no longer shows "paused" while music is playing
• Fixed: feed greeting name now reliably picks up your contact profile
• Fixed: badges & live mail now has its own row in Personalize
```

*(Character count 435, under Play's 500 limit.)*

### Full changelog since v2.2.0 (for reference — not the Play-facing blurb above)

- **Tile arrangement & folders**: sticky-mode drag now shows a live push-down/push-sideways
  preview while dragging; drag-to-reorder apps inside an open folder; fixed merge-to-folder
  being unreachable in sticky (Windows-Phone-style) arrangement; closed folders show which
  app inside has a pending notification, not just the combined total; folder/stack
  notification badges resized to match a regular tile's badge.
- **News feed**: region picker is multi-select across ~20 countries (previously one at a
  time); curated real image-bearing sources for US/UK/Australia/Canada/UAE; fixed one
  high-volume region crowding out every other selected region's articles.
- **Widget stacks**: an open stack can switch directly between wide/large or go back to a
  regular folder; each rotating member shows its own notification badge instead of one
  combined total.
- **Personalize & app list**: "lock layout" toggle blocks entering edit mode by accident;
  quick "set as default launcher" row; TileShell no longer lists itself in its own app list;
  live-tile text/icons/glyphs/labels now switch to dark automatically over a light
  background instead of staying white and unreadable; people tile shows circular avatars
  and (2.2.7) a small drifting/bouncing bubble cluster instead of a flip; clock tile shows
  12-hour am/pm time.
- **Quick panel** (two-finger swipe up on Start): wi-fi/bluetooth/flashlight/dnd/airplane
  mode/location/rotation-lock toggles plus volume/brightness/screen-timeout controls,
  restyled as Start-tile-style coloured toggles; device status card (battery, storage,
  connectivity, next alarm) on the feed's glance tab; fixed turning off "show feed page"
  leaving no way to turn it back on.
- **Reliability**: fixed background refresh (feed, weather, layout backups) silently
  failing after the AGP/Kotlin/Room/Gradle toolchain upgrade; clock tile alarm/reminder now
  shows the correct title and date by matching the real scheduled trigger time instead of
  guessing from the event start; backup & restore now round-trips tile grid positions, feed
  subscriptions/custom feeds/regions, hidden apps, feed widget layout, and the photos-tile
  selection — previously several of these silently reset on restore.
- **Feed & personalize redesign** (matches newer "Metro Reforged" mockups): continuous-
  scroll feed with a personalized "good morning, `<name>`" greeting, date/clock, search
  pill, condensed weather + today's agenda side by side, now-playing, widgets, device
  status, then news; feed widgets classify as half/full-row width and pair side by side,
  with single-drag-handle reordering that survives an edit-mode-preserving row reparent;
  glance screen background is now always a synthesized colour gradient (never the raw
  wallpaper photo) with its own independent "no background" toggle, plus a real fix for
  Android's Palette API returning null swatches on a low-variance photo; Personalize
  reorganized (flat theme row, compact segmented pills, merged tile-style groups) with a
  live-tiles master toggle; enabling live tile updates now explains what it needs before
  jumping to system settings, instead of navigating straight there.
- **This session's fixes**: the feed greeting's contact-profile name seed raced the runtime
  permission dialog and never got a second attempt once access was actually granted — now
  retried automatically the moment contacts access is observed granted; the now-playing
  tile shared the generic decorative flip timer with clock/weather/mail, so a
  random tick could show a false "paused" face mid-song — gated on real playback state now;
  "badges & live mail" had been folded into the live-tiles master toggle (which defaults
  on), so the notification-access ask was never actually triggered on a fresh install —
  restored as its own row directly below the master toggle.

## Release notes (v2.2.8)

*"What's new" — newest release first. Keep under Play's 500-character limit.
2.2.7 never got a signed build submitted to Play, so 2.2.8 supersedes it and
folds in its changes too, plus the feed/personalize redesign that landed
afterward on the same versionCode (see CLAUDE.md/DECISIONS.md for the full
detail behind each item).*

```
TileShell 2.2.8

• Feed & personalize screens redesigned with a cleaner layout and a personalized greeting
• Feed widgets can now sit side by side and be reordered by drag
• Fixed: backup & restore now covers feed subscriptions, hidden apps, widgets, and photos too
• Fixed: clock tile alarm/reminder shows the right title & date
• Fixed: wallpaper reframe tool can pan top/bottom/side, not just zoom centrally
• Fixed: background refresh (feed, weather, backups) could silently fail
```

*(Character count 483, under Play's 500 limit.)*

## Release notes (v2.2.7)

*"What's new" — newest release first. Keep under Play's 500-character limit.
Superseded by v2.2.8 — never submitted to Play as its own release.*

```
TileShell 2.2.7

• Quick panel redesigned as colourful start-tile-style toggles instead of plain switches
• People tile: no more flip — a small cluster of bubbles drifts, bounces, and swaps photos
• Fixed: wallpaper reframe tool now lets you pan to just the top or bottom (or side) of a photo, not only zoom centrally
• Fixed: background refresh (feed, weather, layout backups) could silently fail to run
• Under-the-hood performance and stability improvements
```

*(Character count 460, under Play's 500 limit.)*

## Release notes (v2.2.6)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.6

• New: quick panel — swipe up with two fingers on Start for wi-fi, bluetooth, flashlight, dnd, airplane mode, location, and rotation-lock toggles, plus volume, brightness, and screen-timeout controls
• New: device status card (battery, storage, connectivity, next alarm) on the feed's glance tab
• Fixed: turning off "show feed page" left no way to turn it back on
```

*(Character count 381, under Play's 500 limit.)*

## Release notes (v2.2.5)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.5

• Fixed: live tile text and icons stayed white and hard to read on light wallpapers — now switch to dark automatically
• Fixed: notification badges on stack tiles and closed folders were too small — now match regular tile badges
• Fixed: an empty slot in a closed folder's icon grid showed as an ugly dark square
• People tile photos now show as circles instead of square crops
• Clock tile now shows 12-hour am/pm time, matching the feed's clock
```

*(Character count ~463, under Play's 500 limit.)*

## Release notes (v2.2.4)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.4

• New: an open widget stack can switch directly between wide and large, or go back to a regular folder
• New: each stack member shows its own notification badge, not just one combined total for the whole stack
• Fixed: a closed folder's notification total no longer overlaps the per-app badge on its top-right app icon
• Fixed: merging two tiles into a folder in Windows Phone-style arrangement
```

*(Character count ~410, under Play's 500 limit.)*

## Release notes (v2.2.3)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.3

• New: localized news feed — pick one or more countries in feed settings (20+ supported), with rich image-led sources for the US, UK, Australia, Canada, and UAE
• New: drag to reorder apps inside an open folder
• New: a closed folder shows which app inside it has a notification, not just the total
• Fixed: merging two tiles into a folder now works in Windows Phone-style arrangement
• Fixed: with several news regions on, one country no longer crowds out the others
```

*(Character count ~460, under Play's 500 limit.)*

## Release notes (v2.2.2)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.2

• Minor fixes and improvements
```

*(Internal: this build clarifies the accessibility prominent-disclosure wording ahead of Play
Console resubmission — see DECISIONS.md "Second Accessibility API rejection." Remember to
re-record and re-upload the disclosure walkthrough video, scrolling slowly and pausing on every
bullet, especially Contacts and Calendar.)*

## Release notes (v2.2.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.1

• New: "lock layout" toggle in Personalize prevents accidental tile edits — long-press does nothing until you unlock it
• New: quick "set as default launcher" option in Personalize
• Fixed: dragging a tile in Windows Phone-style arrangement now previews live as you drag, and slides sideways into free space in the same row before pushing down
• Fixed: TileShell no longer lists itself in its own app list
```

## Release notes (v2.2.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.2.0

• New: folders open in place on the Start screen — tap a folder to expand its apps right where it sits, tap again to collapse; no more separate folder screen
• New: Windows Phone-style tile arrangement (Personalize → tile arrangement) — gaps stay open where a tile was, instead of everything sliding to fill it
• Fixed: dragging a tile onto another now pushes it aside to make room, instead of snapping back
```

## Release notes (v2.1.3)

*Same content as 2.1.2 below — that upload failed on the Play Console, re-cut under a new version code with no functional change.*

```
TileShell 2.1.3

• Fixed: better app category suggestions for folders — ride-hailing apps no longer land in Navigation, unrelated apps no longer swept into Productivity, "Smart"-named apps no longer miscategorized as Shopping
• Fixed: edge strip no longer resets to expanded every time you open personalize, edit mode, or a folder
• Edge strip: app list and settings buttons now move out of the way when the strip is expanded; small tap animation polish
```

## Release notes (v2.1.2)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.1.2

• Fixed: better app category suggestions for folders — ride-hailing apps no longer land in Navigation, unrelated apps no longer swept into Productivity, "Smart"-named apps no longer miscategorized as Shopping
• Fixed: edge strip no longer resets to expanded every time you open personalize, edit mode, or a folder
• Edge strip: app list and settings buttons now move out of the way when the strip is expanded; small tap animation polish
```

## Release notes (v2.1.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.1.1

• Fixed: a settings-only change (wallpaper, accent, tile style, etc.) made after the last time you moved a tile could be silently skipped when saving to layout history — restoring an older snapshot could revert your personalization even though tiles looked fine. File export/import backups were never affected.
```

## Release notes (v2.1.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.1

• New: Edge Strip — a quick-launch bar at the bottom of the screen; search on the left, recents on the right, your favourite apps in the centre; collapses to a thin sliver when not in use; pin apps and choose the style from Personalize
• Fixed: widget stacks now wait longer before auto-rotating — tapping a notification is no longer a race against the timer
```

## Release notes (v2.0.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 2.0

• Notification tiles now show your unread count front-and-centre — big number, clear label ("3 unread", "5 new", "2 notifications")
• Flip the tile to cycle through each pending message in turn, one at a time — no message hidden behind a count anymore
• Works for mail, messaging apps, and every other app with notifications
• Each message shows its sender's photo as you cycle through
• New: in-app personalization guide with illustrated tips — find it in Personalize
```

## Release notes (v1.9.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.9.0

• Fixed: stock wallpapers no longer show black gaps between tiles in light theme — every gradient now adapts its tone to the active theme
• Fixed: smoother wallpaper gradients, less visible banding
• Fixed: transparent (glass) tiles now tint with each tile's own colour instead of all looking the same shade
```

## Release notes (v1.8.2)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.8.2

• New: the large (3×3) tile is now available on 4-column grids too, not just 5/6 — resize any tile, or a tile inside a folder, all the way up; "make large stack" is now offered on every grid density
```

## Release notes (v1.8.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.8.1

• Fixed: swiping to flip a widget stack no longer blocks scrolling the start screen — swipe near the right edge (by the position indicator) to flip members, swipe anywhere else to scroll as normal
```

## Release notes (v1.8.0)

*1.7.0 was built but never actually published to the Play Store — the last live release is 1.6.2,
so this note folds in everything since then, not just the 1.8.0-only delta.*

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.8.0

• New: wallpaper picker simplified to one clear choice — none, photo, slideshow, bing, or stock
• New: quick search can ask ChatGPT, Gemini, Claude, Perplexity, or search Google, Bing, and more
• New: resize widgets by width, height, or both — three independent handles
• Fixed: clock tile date was cut off on 5- and 6-column grids
• Fixed: widgets not rendering at the right size, and some vanishing after being added
```

## Release notes (v1.7.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.7.0

• New: personalize's wallpaper section is now a simple picker — none, photo, slideshow, bing, or stock — showing just what that type needs
• New: quick search can ask ChatGPT, Gemini, Claude, or Perplexity
• Fixed: clock tile date was cut off on 5- and 6-column grids
```

## Release notes (v1.6.2)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.6.2

• New: wallpaper slideshow — pick multiple photos and rotate them automatically every 15 min to 3 hours
• New: pinch to zoom when positioning your wallpaper photo
• New: in-app update notifications so you always have the latest version
```

## Release notes (v1.6.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.6.1

• Fixed: widget stacks and folders with a custom tile colour now correctly show through in "wallpaper behind tiles" and glass (transparent tiles) modes, instead of staying solid
```

## Release notes (v1.6.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.6.0

• New: Quick Search — swipe down with two fingers on Start to search apps, contacts, and the web
• Long-press a contact to call, message, or pin it to Start
• New: hide apps from the App List; apps with a notification show in Recent even if unpinned
• Gallery tile now reliably flips to show pending notifications
```

## Release notes (v1.5.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.5.1

• Fixed: widget stack tiles now keep each member's own colour while rotating
• Fixed: merging weather (or another no-app live tile) into a stack no longer makes it disappear
• Folders: set a per-app colour, and resize apps up to wide/large on 5-6 column grids
• New: turn any folder into a wide or large widget stack from its overlay
• Fixed: calendar and weather tiles no longer clip text at wide size
```

## Release notes (v1.5.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.5

• New: Backup & Restore — auto-save on a schedule, browse & restore from visual layout history, or export/import a file (save it to Google Drive for your next device)
• Fresh installs start with the classic flat blue Windows Phone look
• Fixed: app list search now clears after opening an app or pressing back
```

## Release notes (v1.4.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.4.1

• Fixed: tapping the corner button on a folder or widget stack in edit mode
  now opens the folder overlay instead of deleting all the tiles inside —
  remove apps one by one, and the folder closes automatically when it's empty
```

## Release notes (v1.4.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.4

• Widget stacks: merge two large tiles into a full-size live-tile carousel — swipe up/down or let it auto-rotate; each stack runs on its own schedule so the screen never all-flips at once
• Large tiles (3×3) now available for any app on 5 and 6-column grids
• Landscape: feed and Start screen shown side by side on wide devices
• Notification tiles on wide and large sizes now show bigger photos and more message lines
• Clock tile back face leads with your alarm time, not the date
```

## Release notes (v1.3.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.3

• Give any tile its own colour — tap the palette in edit mode to pick an accent, or let TileShell pull the colour straight from the app's icon
• Adjustable tile spacing — drag the spacing slider in Personalize to pack tiles tighter or give them more room
• Battery improvements: background weather, news, and Bing wallpaper workers now wait for a network connection before running, so they never wake the radio on airplane mode
```

## Release notes (v1.2.0)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.2

• Choose grid density — 4, 5, or 6 tiles across a row, from Personalize
• New: daily Bing wallpaper, with a viewer to pick from recent days
• Drag a tile into the empty space below the grid to send it to the bottom
• Smoother editing: a moving tile reorders cleanly — folders form only when you pause one tile over another
• Easier merging: line up two same-size tiles to combine them, wherever you grabbed
• A small weather tile now shows the current temperature
```

## Release notes (v1.1.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.1.1

• Fixed: dragging an app into a folder no longer resizes or reshuffles the apps already inside — each keeps its size and place (wide apps become medium, since folders use small and medium tiles)
• Fixed: play, pause, and skip controls on music tiles now respond to taps, even with system animations off or battery saver on
• Folders now show a combined unread badge, adding up the notification counts of the apps inside
```

## Release notes (v1.1)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.1

• Category folders: group your installed apps into a folder in seconds from Personalize — TileShell suggests the apps, you tick the ones you want
• Edit a folder anytime: current apps stay checked; suggested and other apps are listed separately to add or drop
• Organise inside a folder like the Start screen: hold a tile to edit, drag to reorder, resize between small and medium, or pull an app back to Start
• Your folder layout and tile sizes are kept when you update a folder
```

## Release notes (v1.0.4)

*(1.0.3 / code 13 was uploaded to Play then discarded — Play reserves the code, so this ships as 1.0.4.)*

```
TileShell 1.0.4

• People tile now shows photos of only your favourite contacts (and frequently-contacted on older Android) instead of all contacts
```

## Release notes (v1.0.2)

*"What's new" — newest release first. Keep under Play's 500-character limit.*

```
TileShell 1.0.2

• Clock tile shows your real next alarm and updates instantly — tap it to open your clock app
• Live mail & message tiles now show the sender, message, and photo in a clean row, not a full-tile image
• Pick your wallpaper and live photos straight from your gallery
• Grant tile permissions (contacts, calendar, location) anytime from Personalize, and clear chosen photos
• Fixed a stray dot on small tiles with transparent tiles on
```

## Release notes (v1.0)

```
TileShell 1.0 — Windows Phone–style launcher for Android

• Live tiles: clock, weather, calendar, now-playing, people, photos, notifications
• Feed page with news categories, weather card, agenda, and widget hosting
• Deep personalization: 14 accents, glass tiles, blur wallpaper, custom fonts
• Instant screen lock from the Start screen (preserves biometric unlock)
• Full TalkBack support with custom accessibility actions
• Reliable notifications on all OEMs — battery exemption guidance built in
• R8-shrunk release build; baseline profile for <300 ms cold start
```

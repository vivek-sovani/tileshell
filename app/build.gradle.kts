import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Consumes the baseline profile produced by :macrobenchmark (S26).
    alias(libs.plugins.baselineprofile)
}

// Signing: reads from key.properties (NOT checked into git — see key.properties.template).
// When absent (dev machines / CI without credentials) release falls back to the
// debug keystore so local release builds and APK comparisons still work.
val keystoreFile = rootProject.file("key.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "com.tileshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tileshell"
        minSdk = 26
        targetSdk = 36
        // versionCode: 10 = v1.0; patches → 11, 12 …; v1.1 → 20, etc.
        // (13/1.0.3 was uploaded to Play then discarded; Play burns the code, so → 14.)
        // v1.1 = 20 (category folders + in-folder tile editing).
        // v1.1.1 = 21 (folder-merge size fixes, operable music controls, folder badges).
        // v1.2 = 30 (grid columns 4/5/6, refined editing/merge, folder fixes,
        //   small-weather temp, Outfit default, Bing daily wallpaper).
        // v1.3 = 40 (per-tile colour, tile colour from app icon, adjustable tile spacing,
        //   battery: network constraints on background workers, reduced media poll).
        // v1.4 = 50 (widget stacks, large tiles 3×3, landscape two-panel, notification
        //   tile content improvements, clock alarm priority, staggered stack rotation).
        // v1.4.1 = 51 (folder/stack X button opens overlay instead of deleting all tiles).
        // v1.5 = 60 (backup & restore: auto-save, visual layout history, export/import;
        //   personalize navigation shows one screen at a time; fresh installs default to
        //   no wallpaper + solid Nokia-blue tiles; app-list search-clear fix).
        // v1.5.1 = 61 (widget stack fixes: per-member tile colour during rotation, merging
        //   a liveOnly tile like weather into a stack no longer drops it; per-tile colour
        //   picker + full small/medium/wide/large resize cycle inside a folder overlay;
        //   folder overlay "make wide/large stack" shortcut; calendar/weather tile layout
        //   fixes at WIDE size).
        // v1.6.0 = 70 (quick search: two-finger swipe-down opens apps/contacts/web search,
        //   contact call/message/pin-to-start, recent searches + suggested apps; apps with a
        //   pending notification surface in the app list's recent section even when unpinned;
        //   hide/unhide apps from the app list; Samsung Gallery "story" notification fix).
        // v1.6.1 = 71 (wallpaper-behind-tiles / glass transparency fix: widget stack members
        //   and closed-folder mini-grid cells with an individually selected colour rendered
        //   fully opaque instead of showing the wallpaper/glass through them like other tiles).
        // v1.6.2 = 72 (in-app update check + flexible-update prompt banner on Start; wallpaper
        //   crop overlay gains pinch-to-zoom alongside drag-to-position; wallpaper slideshow
        //   rotates the background through multiple picked photos on a 15m/30m/1h/3h timer).
        // v1.7.0 = 80 (personalize wallpaper section reorganized into a none/photo/slideshow/
        //   bing/stock type selector, with blur/wallpaper-behind-tiles moved there as an
        //   "effects" subsection and tile style split into glass/colour & fill/shape & spacing
        //   subgroups; quick search can ask ChatGPT, Gemini, Claude, or Perplexity; clock tile
        //   date no longer clipped at 5/6 grid columns).
        // v1.8.0 = 90 (feed search pill opens quick search directly instead of a separate "g"
        //   button that could fall through to Start's clock tile underneath; widget hosting
        //   overhaul — providers now actually receive their real render size (a Bundle.EMPTY
        //   bug silently broke this for every widget), a provider's own recommended aspect
        //   ratio sizes it instead of a raw minHeight, square widgets render centered at half
        //   width, and three independent handles resize width/height/diagonal instead of one
        //   shape-guessed handle; widgets from slow-registering OEM providers get a longer
        //   grace period before being dropped as "uninstalled").
        // v1.8.1 = 91 (widget stack: swipe-to-flip confined to a 40dp right-edge drag zone
        //   near the position indicator, so the rest of the tile never intercepts a plain
        //   scroll swipe — supersedes an initial long-press-then-drag attempt within the
        //   same version that felt sluggish on-device).
        // v1.8.2 = 92 (large (3x3) tile is now available on 4-column grids too, not just
        //   5/6 — both top-level tiles and folder children, plus the folder overlay's
        //   "make large stack" shortcut).
        // v1.9.0 = 100 (bundled wallpaper gradients adapt to light theme instead of always
        //   showing a near-black base between tiles; smoother gradient falloff, less banding;
        //   glass/transparent tiles now tint with each tile's own resolved accent colour
        //   instead of one shared neutral tint across the whole grid).
        // v2.0.0 = 200 (notification tiles overhaul: front face shows the total unread count
        //   prominently (big number + label); back face cycles through each pending notification
        //   in turn, newest first — mail/messages driven by the flip scheduler, generic apps
        //   self-manage their flip; per-notification avatar shown when cycling through grouped
        //   notifications from the same app).
        // v2.1.0 = 210 (Edge Strip: a collapsible quick-launch bar pinned to the bottom of
        //   Start — search on the left, favourite apps in the centre, recent apps on the
        //   right; widget stack auto-rotate interval increased so a tapped notification
        //   isn't a race against the timer).
        // v2.1.1 = 211 (fix: layout-history snapshot dedup hashed only tile/folder structure,
        //   not settings — a settings-only change made after the last tile move was silently
        //   dropped from history, so restoring an older snapshot could revert personalization
        //   even though the layout itself was unaffected; file export/import was never affected
        //   since it has no dedup).
        // v2.1.2 = 212 (app category matching: dropped unreliable CATEGORY_MAPS/PRODUCTIVITY
        //   OS signals, folded productivity into tools, fixed "smart"-prefixed app names
        //   falsely matching the shopping "mart" token, widened banking/shopping tokens;
        //   edge strip expand/collapse state now survives personalize/edit-mode/folder
        //   remounts instead of resetting, quick search slides it fully away instead of
        //   unmounting it, the Start app-list/gear affordance clears the strip's height
        //   while it's expanded, default handle size is now thick; feed widgets section
        //   header unified with the other feed sections).
        // v2.1.3 = 213 (no functional change — 2.1.2's Play Console upload failed;
        //   re-cut under a new version code).
        // v2.2.0 = 220 (folders: inline expand-in-place replaces the modal FolderOverlay;
        //   Windows-Phone-style gap-preserving "sticky" tile arrangement (user-selectable,
        //   now the fresh-install default) — a removed/resized tile's gap stays open
        //   instead of the grid always repacking; dragging a tile onto an occupied cell
        //   now pushes the occupant down to make room instead of rejecting the drop;
        //   Accessibility API prominent-disclosure fix for a Play Console policy rejection
        //   — itemizes all data TileShell collects, not just the accessibility service's
        //   own no-op data use, and narrows the service's declared event-type scope).
        // v2.2.1 = 221 (sticky-mode drag now shows a live push-down/push-sideways preview
        //   as the finger moves, instead of only reflowing after the drop — a colliding
        //   tile prefers sliding into a free column in the same row before falling back to
        //   a straight push down; new "lock layout" toggle in Personalize blocks entering
        //   edit mode entirely, with a toast pointing to where to unlock it; TileShell no
        //   longer lists itself in its own App List (its LAUNCHER-category MainActivity was
        //   leaking into the unscoped activity query); new "default launcher" row in
        //   Personalize, shown only while TileShell isn't already default; the existing
        //   first-run default-launcher prompt now re-asks on every fresh app open instead
        //   of only ever once).
        // v2.2.2 = 222 (second Accessibility API policy rejection — reviewer flagged the
        //   prominent disclosure as still missing Calendar events + Contacts, even though
        //   the v2.2.0 fix already itemized both; root cause was the submitted demo video,
        //   which scrolled past those two bullets too fast to read, not the app itself.
        //   Reordered the disclosure so Contacts + Calendar are the first two data items
        //   listed (previously buried at positions 2-3 of 6), tightened the wording so less
        //   scrolling is needed overall, and split the one giant string into three Text()
        //   calls for readability. Re-recording the disclosure video — pausing on every
        //   bullet this time — is the actual fix for the rejection; see DECISIONS.md).
        // v2.2.3 (code 223): localized news feed — per-country region picker
        //   (~20 countries, multi-select), rich image-led sources for US/UK/AU/CA/UAE,
        //   fixed one high-volume region crowding out the others; reorder apps inside an
        //   open folder by dragging; closed folders show which app a notification came
        //   from; fixed merge-two-tiles-into-a-folder in Windows Phone tile arrangement.
        // v2.2.4 (code 224): widget stacks gain a "back to folder" action plus switching
        //   directly between wide/large sizes without collapsing first; each rotating
        //   stack member shows its own notification badge (previously only a single
        //   consolidated total for the whole stack); a closed folder's combined
        //   notification total moved beside its name label so it no longer overlaps the
        //   per-app badge on its top-right mini-grid cell; fixed a real merge-to-folder
        //   regression in sticky (Windows-Phone-style) tile arrangement.
        // v2.2.5 (code 225): widget-stack and closed-folder notification badges now
        //   render at the same size as a regular app tile's badge instead of the tiny
        //   folder-mini-grid dot; people tile's photo mosaic shows circular avatars
        //   instead of square crops; clock tile shows 12-hour am/pm time, matching the
        //   feed/glance screen's clock; live-tile text/icons, the static tile glyph,
        //   tile labels, the Start screen's chevron/gear, and the open-folder action
        //   chip now switch to dark text automatically when the wallpaper actually
        //   showing through them (glass tiles, "wallpaper behind tiles" mode, or the
        //   plain screen background) is light, based on an actual brightness sample
        //   rather than the theme setting; fixed a closed folder's unused mini-grid
        //   slots (fewer apps than the grid's capacity) rendering as dark squares.
        // v2.2.6 (code 226): new quick panel — a two-finger swipe-up on Start (mirrors
        //   quick search's swipe-down) opens a WP-tile-style bottom sheet: coloured
        //   toggle tiles for wi-fi, bluetooth, flashlight, dnd, airplane mode, location,
        //   and rotation lock, plus wide live-tile-style bars for media/ring volume,
        //   brightness, and a tap-to-cycle screen-timeout row — all built with
        //   permissions already declared or special-access grants (WRITE_SETTINGS,
        //   notification policy access) confirmed absent from Play Console's
        //   restricted-permissions list, so no new Play Console declaration is needed.
        //   Also adds a read-only device-status card (battery, storage, connectivity,
        //   next alarm) on the feed's glance tab, toggleable from Personalize · feed &
        //   glance alongside the feed page itself (fixing a pre-existing bug where
        //   turning "show feed page" off from inside the feed's own settings left no
        //   way to turn it back on).
        // v2.2.7 (code 227): quick panel visual pass — toggles/sliders restyled as
        //   Start-tile-style coloured tiles (a mini Start screen, not a generic
        //   settings sheet), volume/brightness bars show the icon inside the bar
        //   instead of beside it, media/ring get distinct icons, alarm volume
        //   removed; people tile drops its flip (the back face's photo rendered as
        //   an inconsistent square crop) for a small physics-based cluster of
        //   circular bubbles that drift and bounce off each other and the tile
        //   edges, swapping photos on an instant cut rather than a fade; dropped
        //   the deprecated statusBarColor/navigationBarColor theme attributes
        //   (Android 15 edge-to-edge no-ops) and switched the cutout mode to
        //   "always"; upgraded AGP 8.9.1 -> 9.0.1 (Kotlin -> 2.2.10, Room -> 2.8.4,
        //   Gradle -> 9.1.0) with optimized resource shrinking enabled — the fix
        //   for Play Console's resource-shrinking recommendation — and fixed a
        //   real WorkManager regression the upgrade's more aggressive R8 pass
        //   introduced (a stripped InputMerger constructor silently broke every
        //   background worker's first run).
        // v2.2.8 (code 228): clock tile alarm/reminder fixes, plus the still-unreleased
        //   v2.2.7 changes above (2.2.7 never got a signed build submitted to Play, so
        //   2.2.8 supersedes it rather than being skipped). User-reported: a 2:50pm
        //   calendar meeting reminder showed up mislabeled as the tile's alarm; a follow-up
        //   fix (whitelisting Google/Samsung Clock packages) then made a real Google Clock
        //   alarm stop showing entirely whenever a sooner calendar reminder existed.
        //   Root-caused via on-device `adb shell dumpsys alarm`: AlarmManager
        //   .getNextAlarmClock() is a single system-wide "next" value with no per-app
        //   query, and calendar apps also register via setAlarmClock() (to bypass Doze),
        //   so a same-day meeting reminder routinely eclipses a real, later alarm — the
        //   whitelist attempt just went blank in that case instead of showing either.
        //   Reverted to always showing the next entry, and resolve its real source by
        //   matching the trigger time against CalendarContract.CalendarAlerts.ALARM_TIME
        //   (the reminder's actual scheduled fire time — not Instances.BEGIN, the event's
        //   start, which doesn't match for all-day events or reminders offset earlier than
        //   the event start): shows the matched event's own title when found, else falls
        //   back to "alarm / bedtime" as before. The date line under it now shows the
        //   alarm/reminder's own date rather than always today's, for one set on a
        //   different day. Verified end-to-end on a physical device at each step.
        //   Also folded into 2.2.8 (same release, no further version bump): a backup/
        //   restore completeness fix. User-reported "restore is not exactly the same as
        //   backup" — root cause was TileEntity.gridSlot (the sticky-mode tile-position
        //   anchor) never being included in the backup JSON at all, so every restore let
        //   tiles re-flow to different positions. A fuller audit then found several later-
        //   session features were never wired into backup/restore either: feed
        //   subscriptions/custom URLs/regions, hidden apps, feed widget layout, the
        //   photos-tile selection, and the wallpaper slideshow's photo list. All now
        //   round-trip through export/import; recent apps/searches and the article/weather
        //   caches are deliberately still excluded (MRU history and refetchable caches, not
        //   configuration — see DECISIONS.md), and restored feed widget ids are filtered to
        //   ones that still resolve via AppWidgetManager rather than restoring a broken slot.
        // v2.3.0 (code 230): rolls up all interim post-2.2.8 work (2.2.8/code 228 was
        //   submitted to Play but rejected for an "Invalid Data safety form" — a
        //   console-only fix, see DECISIONS.md — so this version supersedes it rather
        //   than shipping a separate patch) plus three fresh bug fixes from user reports
        //   this session. New: feed widgets classify as half/full-row width and pair
        //   side by side, with single-drag-handle reordering that survives a row-reparent
        //   without dropping out of edit mode; glance screen background is now always a
        //   synthesized colour gradient (never the raw wallpaper photo), independent of
        //   Start's own wallpaper, with a dedicated "no background" toggle and a real
        //   Palette null-swatch fallback bug fix; feed/personalize redesigned to match
        //   newer mockups — continuous-scroll feed with a personalized "good morning,
        //   <name>" greeting, condensed weather+agenda row, adaptive text colour;
        //   Personalize reorganized (flat theme row, compact segmented pills, merged
        //   groups) with a live-tiles master toggle. Fixed: the feed greeting's name
        //   seed from the device contact profile raced the runtime permission dialog
        //   and never got a second attempt once granted — now retried on grant; the
        //   now-playing music tile could flip to a false "paused" face while a track
        //   was actually playing, since it shared the generic decorative flip timer;
        //   "badges & live mail" was folded into the live-tiles master toggle (which
        //   defaults on) so the notification-access ask was never actually triggered on
        //   a fresh install — restored as its own row directly below the master toggle.
        // v2.3.1 (code 231): same feature set as 2.3.0 above (already published), plus one
        //   fix found via Play Console's post-publish pre-launch report for that release:
        //   the layout-history snapshot list decoded its full-screen PixelCopy screenshot
        //   at full resolution just to render a 64x104dp row thumbnail — the only
        //   BitmapFactory call site in the app that had skipped the inJustDecodeBounds ->
        //   inSampleSize downsampling pattern every other call site already used.
        // v2.4.0 (code 240): Quick Panel rebuilt as a true WP Action Center — one 5-column
        //   grid of perfect square tiles (was wide chip/slider rows): toggles for wifi,
        //   bluetooth (now with real live accent state via Settings.Global.BLUETOOTH_ON,
        //   not hardcoded off), location, airplane, flashlight, rotation lock, dnd;
        //   tap-to-step brightness/media-volume/ring-volume/screen-timeout (0/10/20/40/
        //   60/80/100%, no more drag sliders); one tap-to-cycle dark/light/auto theme
        //   tile; "personalize", "android settings" (real device icon), and "lock screen"
        //   shortcut tiles. The floating corner settings-gear icon on Start is gone —
        //   personalize now opens via a normal, draggable/resizable/unpinnable Start tile;
        //   the real Android Settings app is reachable from its own existing Start tile
        //   (now showing its real device icon) and is unhidden from the App List again.
        //   Docks to the right half above Start in landscape, like every other sheet
        //   (was spanning the full width and overlapping tiles). Tile order iterated
        //   live on-device across several rounds (connectivity-first grouping, location
        //   third, dnd moved down, rotation lock next to brightness, media volume at the
        //   row's right edge). Also new this version: single-finger swipe up from either
        //   screen edge opens the Quick Panel (alongside the existing two-finger swipe);
        //   single-finger swipe down from the left/right screen edge opens system
        //   notifications/quick settings; a day-interval-gated "enjoying tileshell?"
        //   rating prompt using Play's native in-app review flow.
        // v2.5.0 (code 250): "hide status bar" toggle (default on, with a real display-
        //   cutout-inset fix and an auto-rehide timer for the swipe-to-peek reveal); Quick
        //   Panel rebuilt to dock from the top with a device-style header row (clock/date,
        //   wifi/bluetooth/cellular-or-airplane icons, colour-coded battery fill) and a
        //   second row of personalize/android-settings/lock-screen shortcuts; the two-
        //   finger Quick Panel/quick-search gesture directions swapped (down opens Quick
        //   Panel, up opens quick search) with quick search's box moved to the bottom of
        //   the screen; Personalize, the personalize guide, and the about sheet are now
        //   full-screen instead of capped bottom sheets; device-status stats removed from
        //   the glance page entirely, now that they live in the Quick Panel header;
        //   brightness/ring/media volume in the Quick Panel are real sliders with a tap-
        //   to-mute icon; Quick Panel's synthesized wallpaper-gradient background now also
        //   respects the glance page's own "no background" toggle; haptic feedback added
        //   throughout Quick Panel, quick search, and the App List's long-press pin/hide/
        //   uninstall menu; sharing a photo into TileShell from Gallery/Photos' own share
        //   sheet now sets it as the wallpaper, going through the same crop/reframe overlay
        //   as picking one from within the app.
        // v2.5.1 — feed widget stacks: two hosted widgets on the glance screen can be
        //   merged into one swipeable card by dragging one onto the other's centre, saving
        //   vertical space on a dense reading surface. The card auto-rotates between its
        //   members and a right-edge strip flips them by hand; "unstack" splits them again.
        //   Also raises the App List's pin/hide/uninstall long-press to 700 ms, which was
        //   firing on an ordinary tap-and-linger.
        // v3.0.0 (code 300) — "icons" home style: a normal Android-style look (shaped app
        //   icons, live tiles, folders, free placement) alongside the existing Windows Phone
        //   tiles look, chosen once via a first-run wizard. Icon shapes: circle/squircle/
        //   rounded/square/original (the device's own shape). Eleven tile-size presets total,
        //   with gesture-based corner-drag resize on top of the original tap cycle. New "free"
        //   tile-arrangement mode. Widget stacks widened to any stackable size via a colour-
        //   picker toggle, not just wide/large. Weather/calendar/clock tiles stay live even at
        //   the small icon size. Notification-carrying live tiles (mail/messages/any app) at
        //   every size now scale their layout to fully use — and vertically centre within —
        //   the tile's actual space instead of a fixed cramped layout. A major-version bump
        //   since this is the biggest new capability since the original WP recreation: a
        //   second, genuinely different home-screen style living side by side with the first.
        //   See CLAUDE.md and docs/DECISIONS.md for the full session-by-session history.
        // v3.1.0 (code 310) — drag an app out of an open folder straight to any spot on
        //   the grid (sticky/free/dense arrangement, plus merging into another tile),
        //   alongside the existing tap-to-unpin-to-bottom shortcut. Widget stacks now also
        //   allow Wide Small and Banner sized members. Fixes: a cycling notification tile
        //   opened the wrong message on tap (always the newest, not whichever was shown);
        //   weather/calendar/now-playing cards and Quick Panel's active toggle tiles kept
        //   white text over a light wallpaper-derived accent; music/weather/calendar/clock
        //   tiles at Wide Small/Banner (one grid row tall) clipped their controls/text,
        //   clock specifically at 5/6 columns; a folder child dragged to a chosen position
        //   could still land at the bottom instead, from a client-side ordering bug.
        // v3.2.0 (code 320) — Quick Panel tiles and the feed's built-in weather/agenda/
        //   now-playing cards gain One UI-inspired resize (drag between square and wide,
        //   or width/height/both for feed cards) and drag-to-reorder, via a single global
        //   edit toggle instead of per-tile long-press. Distinct move (grip-dot) vs resize
        //   (bar/arc) handles; a live scale-based resize preview instead of a hard snap on
        //   release. Fixes found while shipping this: a Quick Panel tile could occasionally
        //   resize its neighbour instead of itself; a resize handle sitting too far inside
        //   a feed card competed with the card's own content for the touch; resizing or
        //   reordering the same tile a second time in one edit session could silently do
        //   nothing (a stale Compose gesture-callback closure, fixed with rememberUpdatedState).
        //   Also: the Personalize sheet's own chrome (selected pills, sliders, highlights)
        //   now correctly follows the wallpaper-derived accent when that's the chosen tile
        //   colour source, instead of always showing the plain global accent.
        // v3.3.0 (code 330) — resizable icons-mode app icons (OneUI/Nothing-OS style): any
        //   icon in "icons" home style can now be stretched 1x1 up to 4x4, with a per-app
        //   "show as icon"/"show as tile" toggle in the colour picker (defaults to icon; a
        //   stretched icon can't also show live content). Icon-shape masking (circle/
        //   squircle/rounded/square/original) now applies consistently at every size, not
        //   just 1x1. Wallpaper crop/apply now shows a real live preview of the actual Start
        //   screen composited on the candidate photo (not an approximate mockup), covering
        //   picking a photo, sharing one in, and other apps' "apply via"/"set as wallpaper"
        //   choosers — plus a visible apply animation and landing back on Start once applied.
        //   Gradient tile fill now also applies to Quick Panel tiles and feed glance cards,
        //   not just Start tiles. Fixes made shipping this: "wallpaper behind tiles" mode was
        //   leaking the full photo behind everything in the new preview; icons were blurred
        //   at larger sizes (fixed decode resolution → dynamic, matching on-screen size);
        //   notification badges on a resized icon sat at the whole tile's corner instead of
        //   the icon's own corner, at a fixed size instead of scaling with it, and inset
        //   inward instead of sitting right at the edge.
        // v3.5.0 (code 350) — glance gadgets: a catalog of tileshell's own cards for the
        //   feed's "add widget" picker, alongside real android widgets — stock market
        //   (one stock, a curated sector/country basket, or a custom list; live price,
        //   change, and sparkline), commodities (metals/energy/currency pairs), sports
        //   (football/cricket/other leagues, follow one team), calendar systems (a second
        //   calendar alongside gregorian, including hindu panchang with tithi/nakshatra/moon
        //   phase), countdown, sticky note, a shared notes card, tasks (each pinned card
        //   keeps its own independent checklist), plus battery/alarm/moon-phase/flashlight/
        //   steps at-a-glance cards. Long-press any glance card (gadget, hosted widget, or
        //   weather/agenda/now-playing) now jumps straight into edit mode, no need to tap
        //   "edit" first — covers a lone card and every member of a widget stack too.
        //   Fixed: the stock card only ever showed its first member's price when a category
        //   or multiple stocks were picked, since it gated a full list on a tile size the
        //   glance card can never reach; now shows every picked stock. Also: colourful
        //   four-accent launcher icon, replacing the old monoline outline.
        //   NOTE: v3.5.0 was submitted to Play Console and then aborted before completing
        //   rollout — its versionCode is burned (Play won't accept a re-upload at 350), so
        //   every fix below that was originally folded into this same entry moved to v3.6.0
        //   instead, at a new versionCode. This comment block is kept as the historical
        //   record of what v3.5.0 actually contained.
        // v3.6.0 (code 360) — generalized the stock-list fix: showing every picked stock/
        //   category member was gated to exactly the LARGE tile size (still true glance-card-
        //   only in v3.5.0); now any tile taller than one row (Medium/Wide/Tall/etc., not just
        //   Large) shows the full list — see StockTile.kt's showAllMembers default.
        //   Fixed: long-press-to-edit on a glance card could fire mid-gesture (most often
        //   reported during a feed scroll that paused briefly before the scroll began) —
        //   raised the long-press timeout to 900ms (from the platform's ~500ms default) and,
        //   for real hosted widgets, replaced a plain GestureDetector-based watcher with one
        //   that explicitly cancels the moment a second finger touches down.
        //   New: a one-time "what's new · glance gadgets" notice (WhatsNewGlanceGadgets.kt)
        //   tells an existing install updating into this version what was added and how to
        //   pin it, from either Start or the feed — a fresh install never sees it (it gets
        //   the home-style wizard/first-run hint instead; see WhatsNewGlanceGadgetsPrefs's
        //   doc comment for how the two are told apart without a versionCode check).
        //   Fixed: ACTIVITY_RECOGNITION (the Steps gadget's step-counter sensor permission)
        //   was bundled into the app's upfront first-launch permission batch regardless of
        //   whether the user had ever added a Steps tile/card — an unexplained, out-of-
        //   context ask for a health-adjacent permission, and part of what routes this app
        //   through Play Console's mandatory Health-apps declaration. Now requested only the
        //   first time a steps face actually renders (i.e. right after adding one), with its
        //   own in-app rationale dialog first — see StepsTile.kt's StepsPermissionGate. Privacy
        //   policy updated to disclose this permission, which it previously omitted entirely.
        // v4.0.0 (code 400) — the in-app-only "glance gadgets" (CustomCardKind) are gone,
        //   replaced by 14 real, standalone AppWidgetProviders: weather, battery, alarm, moon
        //   phase, steps, calendar systems (Hindu Panchang and others), flashlight, stock
        //   market, commodities/currency, sports, tasks, notes, sticky note, and countdown —
        //   installable from any launcher's own widget picker, not just TileShell's. Stock/
        //   commodity/sports carry their full in-app config (multi-stock lists, sector
        //   baskets, currency-pair builder, league/team picker) with a live-quote preview;
        //   sports/commodity show a game-/category-specific icon instead of one generic glyph;
        //   every widget's full+compact layout now has an icon. Fixed along the way: a crash
        //   resizing Tasks/Notes larger; both capped at a fixed row count regardless of
        //   resize (now scales 3→6 with height); missing add/delete affordances and text-
        //   cursor focus on their editor screens; backing out of a widget editor could land on
        //   TileShell's own Start instead of the real host launcher (missing task affinity);
        //   battery/steps widgets now refresh closer to their in-app counterparts.
        //   New: App List's long-press menu gains "more from this app" (a package's other
        //   launcher activities, e.g. a shopping app's regional sub-apps, plus its app
        //   shortcuts — independently pinnable) and "widgets" (that app's own home-screen
        //   widgets, shown with real preview thumbnails, pinned straight to the glance page).
        //   Fixed: pin-to-start de-dupe previously keyed on package name alone, so an app with
        //   several launcher activities could only ever have one pinned at a time.
        //   Renamed: Start's "add widgets" button → "add live tiles" (it only ever pinned
        //   TileShell's own live-tile catalogue, never a real widget — misleading name).
        //   Attempted, then parked (no user-facing change): themed/monochrome App List + Start
        //   icon rendering — built and reviewed on-device, found visually inconsistent since
        //   most apps ship no monochrome layer; disabled, code left dormant for a revisit.
        versionCode = 400
        versionName = "4.0.0"
    }

    if (keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode shrinking + obfuscation. Rules in proguard-rules.pro
            // supplement the AGP defaults and each library's consumer rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    // The baseline-profile plugin auto-creates the `benchmarkRelease` (the
    // non-debuggable, profileable, debug-signed target the Macrobenchmark runs
    // against) and `nonMinifiedRelease` (profile generation) variants from
    // `release`, so no manual benchmark build type is needed here (S26).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Every TileShell module is wired into the app so the whole graph
    // compiles as part of :app:assembleDebug.
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:start"))
    implementation(project(":feature:livetiles"))
    implementation(project(":feature:applist"))
    implementation(project(":feature:personalize"))
    implementation(project(":feature:system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Installs the bundled baseline profile on first run (S26).
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    // The baseline profile artifact consumed at build time.
    baselineProfile(project(":macrobenchmark"))
}

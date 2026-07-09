# TileShell — Play Store Listing & Data Safety

*v2.1.1 listing draft — update before each release*

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
Clock with date, weekday, and your real next alarm. Live weather with temperature, forecast, and high/low. Next calendar event with title and time. Now-playing with album art and transport controls — works with any music app, or pin a dedicated music tile. People mosaic that cycles contact photos every 2 s. Photos slideshow. Notification counts and message previews on every app tile. All flip on a gentle 2.6 s cycle; pauses automatically in battery saver.

★ NOTIFICATIONS
Unread count badge on every pinned tile. Notification tiles show a big number front-and-centre, then flip to cycle through each pending message in turn — sender, photo, and snippet. Gallery tile flips to show pending notifications. Unpinned apps with notifications appear in App List Recent with a badge count. Works with any app, no extra configuration needed.

★ WIDGET STACKS
Merge two Large (3×3) tiles into a swipeable full-size carousel. Every live face stays interactive inside the stack. Swipe near the right edge to flip between members; swipe anywhere else to scroll the Start screen. Auto-rotates on its own independent schedule. Any folder can become a wide or large stack from its overlay.

★ START SCREEN
4, 5, or 6 columns. Small, Medium, Wide, and Large (3×3) tiles — Large is available on every grid size. Long-press to enter edit mode: drag, merge tiles into folders, resize, recolour per tile or from the app's own icon, or unpin. Drag below the last row to append a tile. Landscape mode puts the feed and Start side by side.

★ QUICK SEARCH
Two-finger swipe down on Start. Search apps, contacts, and the web in one gesture. Long-press a contact to call, message, or pin it to Start. Choose your search engine — Google, Bing, ChatGPT, Gemini, Claude, or Perplexity. Recent searches and suggested apps shown before you type.

★ EDGE STRIP
A quick-launch bar along the bottom of the screen. Search on the left, favourite apps in the centre, recent apps on the right. Collapses to a thin handle when not in use. Configure which apps appear from Personalize.

★ FEED PAGE (swipe right)
Live weather card, today's calendar agenda, now-playing with transport controls, and a resizable Android widget slot. Swipe to the News tab for RSS articles — news, sports, tech, cricket, business, entertainment. Add any custom RSS or Atom feed. Refreshes every 30 minutes in the background.

★ APP LIST (swipe left)
Alphabetical grid with A–Z jump grid and instant search that clears after each use. Recent and newly-installed apps at the top with badge counts — even for apps not pinned to Start. Long-press to pin, hide, or uninstall. Hidden apps managed from Personalize.

★ PERSONALIZATION
14 accent colours · per-tile colour override · tile colour from the app's own icon · dark and light themes · glass tiles each tinted by the tile's own colour · blur · wallpaper-behind-tiles parallax mode · 6 gradient wallpapers with light-theme adaptation · custom photo wallpaper · wallpaper slideshow · daily Bing wallpaper with history picker · corner radius · gradient fill · 3 font styles · adjustable tile spacing · built-in personalization guide.

★ BACKUP & RESTORE
Auto-save on a schedule. Browse a visual history of snapshots and restore any version. Export to a file — save it to Google Drive to restore on your next device.

★ SCREEN LOCK
Long-press the gear icon to lock the screen instantly. Uses the Accessibility API so biometric unlock (fingerprint / face) is preserved.

★ PRIVACY FIRST
No accounts. No analytics. No ads. All data stays on your device. Only weather requests leave (Open-Meteo, no API key), and only when you grant location permission.

Requires Android 8.0 (API 26) or higher.
```

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

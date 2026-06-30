# TileShell — Play Store Listing & Data Safety

*v1.4 listing draft — update before each release*

---

## Store Listing

### App name
`TileShell`

### Short description (80 chars max)
`Windows Phone-style launcher with live tiles, feed, and deep personalization`

### Full description (4 000 chars max)

```
TileShell brings the iconic Windows Phone / Windows 10 Mobile Start screen to Android.

★ LIVE TILES
Tiles that actually flip and show real content — clock with date and day, weather
with live forecast, calendar events, now-playing with album art and transport
controls, people mosaic, photos slideshow, notification counts, and message previews.
Every tile flips on a gentle 2.6 s schedule; pauses on battery saver and when you
leave the screen.

★ START SCREEN
Dense grid of Small, Medium, Wide, and Large (3×3) tiles — choose 4, 5, or 6 columns.
Long-press to drag, merge into folders or widget stacks, resize, or unpin.
TalkBack-accessible — every tile is a labelled button with custom actions.
In landscape mode the feed and Start screen sit side by side.

★ FEED PAGE (swipe right)
A personal glance page: live weather card, today's calendar agenda, now-playing with
transport controls, and an Android widget slot. Swipe to the News tab for live RSS
articles across categories — news, sports, tech, cricket, business, entertainment —
from sources you choose. Refresh on demand or let the 30-minute background worker keep
it fresh.

★ APP LIST (swipe left)
Alphabetical grid with live letter headers and a full-screen A–Z jump grid. Search
filters instantly. Recent apps float to the top. Long-press to pin to Start or uninstall.

★ WIDGET STACKS
Drag two Large tiles together to form a widget stack — a 3×3 carousel of full-size
live tiles. Swipe up or down to jump between members, or let each stack auto-rotate on
its own independent schedule. Every live face stays interactive inside the stack.

★ PERSONALIZATION
14 accent colours · per-tile colour override · tile colour from the app's own icon ·
dark/light themes (follows system) · glass transparent tiles · blur wallpaper ·
wallpaper-behind-tiles window mode · 6 bundled gradient wallpapers + custom photo ·
corner radius slider · gradient fill toggle · 3 font styles · adjustable tile spacing.

★ SCREEN LOCK
Long-press the settings gear to lock the screen instantly — uses the Accessibility
API so biometric unlock is preserved (fingerprint / face).

★ BATTERY FRIENDLY
Background workers (weather, news, Bing wallpaper) only run when your device has a
network connection, so they never wake the radio on airplane mode or burn retries.
Live-tile animations pause automatically in battery saver mode.

★ PRIVACY FIRST
No accounts. No analytics. No ads. All your data stays on your device. Only weather
requests leave your device (to the no-API-key Open-Meteo service), and only when you
grant location permission.

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

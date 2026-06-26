# TileShell — Play Store Listing & Data Safety

*v1.1 listing draft — update before each release*

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
Dense 4-column grid of Small, Medium, and Wide tiles. Long-press to drag, merge into
folders, resize, or unpin. TalkBack-accessible — every tile is a labelled button with
custom actions for screen-reader users.

★ FEED PAGE (swipe right)
A personal glance page: live weather card, today's calendar agenda, now-playing with
transport controls, and an Android widget slot. Swipe to the News tab for live RSS
articles across categories — news, sports, tech, cricket, business, entertainment —
from sources you choose. Refresh on demand or let the 30-minute background worker keep
it fresh.

★ APP LIST (swipe left)
Alphabetical grid with live letter headers and a full-screen A–Z jump grid. Search
filters instantly. Recent apps float to the top. Long-press to pin to Start or uninstall.

★ PERSONALIZATION
14 accent colours · dark/light themes (follows system) · glass transparent tiles ·
blur wallpaper · wallpaper-behind-tiles window mode · 6 bundled gradient wallpapers +
custom photo · corner radius slider · gradient fill toggle · 3 font styles.

★ SCREEN LOCK
Long-press the settings gear to lock the screen instantly — uses the Accessibility
API so biometric unlock is preserved (fingerprint / face).

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

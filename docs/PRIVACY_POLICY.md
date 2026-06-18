# TileShell — Privacy Policy

*Effective date: 2026-06-18*  
*Last updated: 2026-06-18*

---

## Summary

TileShell is a launcher (home-screen replacement) for Android. It does not have a
server backend, does not create user accounts, and does not transmit personal data to
the developer. All personalization data (layout, settings, photos) stays on your device.

---

## 1. What data TileShell accesses

TileShell requests the following permissions. Each is optional — the relevant feature
simply stays inactive when a permission is denied.

| Permission | What it reads | Where the data goes |
|---|---|---|
| `READ_CONTACTS` | Contact names and profile photos for the People live tile | Stays on device |
| `READ_CALENDAR` | Upcoming calendar events for the Calendar live tile | Stays on device |
| `ACCESS_COARSE_LOCATION` | Device coarse location (city level) for the Weather live tile | Sent to Open-Meteo (see §2) |
| `NOTIFICATION_LISTENER` (special access) | Notification titles and snippets for badges and Mail/Messages live tiles | Stays on device |
| `INTERNET` | Weather forecast fetches, news RSS feeds, wallpaper URLs | Sent to Open-Meteo and each RSS source (see §2) |
| `BIND_ACCESSIBILITY_SERVICE` (optional) | Used only to issue the "lock screen" action when you long-press the settings icon | Not read; no data transmitted |

TileShell does **not** access call logs, SMS/MMS bodies, microphone, camera, files
(beyond photos you explicitly pick for the wallpaper and live-photos slideshow), or any
other sensitive system data.

---

## 2. Third-party services

### Open-Meteo (weather)
When you grant coarse location for the Weather live tile, TileShell sends your
approximate GPS coordinates to [Open-Meteo](https://open-meteo.com) to retrieve a
weather forecast. The request contains latitude/longitude only — no device identifiers,
no account information. Open-Meteo's own privacy policy applies to data they receive.

### RSS news feeds
The Feed page fetches articles from the RSS/Atom feeds you have enabled (default: a
curated set of Indian news outlets). Each request is a standard HTTP GET to the feed
URL; no personal data is included. The feed providers' own privacy policies apply.

### Google Search
Tapping the search pill on the Feed page or the "weather" tile opens a Google Search
query in your default browser or Google app. TileShell does not intercept or log these
queries.

---

## 3. On-device storage

TileShell stores the following data locally on your device (using Android Room and
DataStore):

- **Start screen layout** — tile positions, sizes, folder contents
- **Personalisation settings** — theme, accent colour, wallpaper choice, transparency
- **Photos you pick** for the live-photos slideshow and custom wallpaper
- **News feed preferences** — which categories and sources you have enabled
- **Widget bindings** — the widget IDs you have added to the Feed/Glance tab
- **Recently launched and newly installed apps** — used for the "recent" section at the
  top of the App List (capped at 12 entries, stored locally, never transmitted)

None of this data is backed up to the developer's servers. Android's standard auto-
backup to Google may back it up according to your device's backup settings (you control
this in Android Settings → Google → Backup).

---

## 4. Children's privacy

TileShell is not directed at children under 13 and does not knowingly collect personal
information from children.

---

## 5. Changes to this policy

Material changes will be noted in the app's release notes and reflected by updating the
"Last updated" date above. Continuing to use TileShell after a change constitutes
acceptance.

---

## 6. Contact

Questions about this privacy policy: **vivek.sovani@kimayainfotech.com**

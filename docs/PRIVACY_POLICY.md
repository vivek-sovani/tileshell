# TileShell — Privacy Policy

*Effective date: 2026-06-18*  
*Last updated: 2026-10-04*

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
| `READ_CONTACTS` | Contact names and profile photos for the People live tile and hub. Your favourites order and which ones show on the favourites tile are saved as contact identifiers in TileShell's settings (and in backups you export) | Stays on device |
| `READ_CALENDAR` | Upcoming calendar events for the Calendar live tile | Stays on device |
| `ACCESS_COARSE_LOCATION` | Device coarse location (city level) for the Weather live tile | Sent to Open-Meteo (see §2) |
| `ACTIVITY_RECOGNITION` | Your phone's built-in step-counter sensor reading, for the Steps tile/card. Asked only the first time you add a Steps tile or card, not at first launch. | Stays on device |
| `NOTIFICATION_LISTENER` (special access) | Notification titles and snippets for badges and Mail/Messages live tiles; the names of people who message you in chat and SMS apps, remembered for 30 days for the People hub's "recently messaged" | Stays on device |
| `INTERNET` | Weather forecast fetches, news RSS feeds, wallpaper URLs | Sent to Open-Meteo and each RSS source (see §2) |
| `NOTIFICATION_LISTENER` — Money hub (optional, on by default once you open it) | New bank SMS (as shown by your messages app) and payment-app notifications, to record transactions: amount, debit or credit, merchant or person, account last 4 digits, method (UPI, card, NEFT…), and balance. Card statements, bill-due reminders (amount and due date) and card bill payments too, shown as bills due by date. Only new notifications; SMS history is never read. OTPs and promotions are ignored. The Money tile shows amounts only if you choose it. Turn off with "read bank messages" in the Money hub's settings. | Stays on device |
| `USE_BIOMETRIC` | Nothing read; asks for your fingerprint, face or screen lock before showing the Money hub's transactions | Not read; no data transmitted |
| `PACKAGE_USAGE_STATS` (usage access, special access) | How often each app was opened over the last 30 days, and per-app screen time, to sort the People and Productivity hubs' apps pages by most used and to show screen time in the Battery hub | Stays on device |
| `READ_MEDIA_AUDIO` | Music files, albums and playlists on your device, for the Music hub library | Stays on device |
| `POST_NOTIFICATIONS` | Nothing read; shows the Music hub's playback controls and your task reminders in the notification shade | Not read; no data transmitted |
| `SCHEDULE_EXACT_ALARM` ("Alarms & reminders", special access) | Nothing read; lets a task reminder go off at the exact minute you set | Not read; no data transmitted |
| `RECEIVE_BOOT_COMPLETED` | Nothing read; sets your task reminders again after the phone restarts | Not read; no data transmitted |
| `WRITE_SETTINGS` (special access) | Nothing read; lets the Quick Panel change screen brightness and timeout | Not read; no data transmitted |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Nothing read; keeps live updates running while the screen is off | Not read; no data transmitted |
| `BIND_ACCESSIBILITY_SERVICE` (optional) | Used only to perform system actions you trigger: lock screen, recents, and opening the notification shade or quick settings with an edge swipe | Not read; no data transmitted |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Nothing read; keeps music, podcasts and radio you started playing with the screen off, with controls in the notification shade | Not read; no data transmitted |
| `READ_EXTERNAL_STORAGE` (Android 12 and older only) | Music files on your device for the Music hub, as `READ_MEDIA_AUDIO` does on newer Android | Stays on device |
| `SET_WALLPAPER` | Nothing read; sets the photo or wallpaper you choose on your home and/or lock screen | Not read; no data transmitted |
| `REQUEST_DELETE_PACKAGES` | Nothing read; opens Android's own uninstall prompt when you choose "uninstall" on an app | Not read; no data transmitted |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Whether you're online and Wi-Fi is on, for the Quick Panel and to skip fetches while offline | Not read beyond on/off; no data transmitted |
| `BIND_DEVICE_ADMIN` (optional, Android 8–9 only) | Used only to lock the screen when you ask, on Android versions without the accessibility lock action | Not read; no data transmitted |

TileShell holds **no** SMS or call-log permission and never reads your SMS inbox or call
log — bank messages are seen only as notifications, as described above. It does not use
the microphone or camera, and reads no files beyond photos you explicitly pick for the
wallpaper and live-photos slideshow and the music on your device.


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

### Stocks, commodities and sports scores
Stock and commodity tiles and widgets fetch prices from Yahoo Finance; sports tiles and
widgets fetch scores from ESPN. Each request contains only the symbol or league/team you
picked — no personal data.

### Shopping orders
The shopping hub reads order updates ("out for delivery", "delivered", delivery OTPs) from the notifications of your
shopping, food and courier apps, and from SMS and email notifications that name a store, as they arrive; it needs no
SMS permission and cannot read old messages. Orders are kept on the phone only (for up to 90 days), are not uploaded
and are not in exported backups. Delivery OTPs stay hidden until you unlock with your fingerprint, face or screen lock
(when set). You can turn reading off or clear the history in the hub's settings.

### Live news channels
The news hub's live tv page plays a news channel's live stream inside TileShell through YouTube's own embedded player, or opens it in YouTube (or your browser). When
the page is shown, TileShell fetches each listed channel's public YouTube page to see whether it is
on air; the request carries only the channel's name, no personal data. The channels you choose are
stored on the phone (and in exported backups); saved stories are stored only on the phone and are
not exported. Google's own privacy policy applies to what YouTube receives when a channel plays or is opened.

### Android Auto
If you use TileShell Music in Android Auto, the car screen shows your music library, playlists,
favourite podcasts and stations from this phone. Android Auto (Google) is the only app, besides
the system and TileShell itself, that can browse or control it. TileShell does not send any of
this anywhere, and voice requests are handled by your assistant, not by TileShell.

### Podcasts and internet radio
The Music hub searches podcasts through the Apple iTunes Search API and radio stations
through the Radio Browser directory (radio-browser.info), then streams from each show's or
station's own server. Requests contain only your search words, category or country filter.

### Bing daily wallpaper
When "bing daily wallpaper" is on, TileShell downloads Microsoft Bing's image of the day.
The request contains only your language/region (for the right image).

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
- **Battery log** — TileShell's own record of battery level, charging state, screen on/off
  and charging current, written on each 1% change and every 15 minutes, kept for 8 days, for
  the Battery tile, hub and widget (stored locally, never transmitted)
- **Money transactions** read from bank and payment notifications — kept for one year, clearable any time from the Money hub ("clear transaction history"), never transmitted
- **People who message you** — the names of senders in chat and SMS notifications, kept for 30
  days for the People hub's "recently messaged" (stored locally, never transmitted)
- **Notes and task lists** you create in the Productivity hub, with any task reminders (date,
  time and repeat), and music favourites,
  playlists and recently played podcasts/stations

You can export a backup file of this data (including notes and tasks) from Personalize, and
optionally have TileShell save one automatically to a folder you choose (for example in Google
Drive). The files go only to that folder, which you pick and can change or stop at any time.

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

Questions about this privacy policy: **vivek.sovani@gmail.com**

# Feasible Features Requiring No New Play Console Permission Declarations

Status: proposal / backlog reference, not yet scheduled in `design/SESSION-PLAN.md`.

## Purpose

TileShell already declares: `INTERNET`, `READ_CONTACTS`, `READ_CALENDAR`,
`ACCESS_COARSE_LOCATION`, `BIND_NOTIFICATION_LISTENER_SERVICE`, plus the
accessibility-service/device-admin special-access grants (screen lock) and
`<queries>` (LAUNCHER category, not `QUERY_ALL_PACKAGES`). Every permission
already on the manifest already went through one Play Console Data Safety /
Accessibility API declaration round (see `docs/DECISIONS.md` — two
Accessibility API rejections). This document scopes **only** features
buildable with:

- permissions already declared, or
- **normal** permission-level APIs (auto-granted, no manifest permission
  declared at all, no Data Safety form entry), or
- special-access grants that are **deep-link-to-settings** (user flips it in
  a system screen — same UX pattern already shipped for notification
  listener/accessibility), never a new *dangerous*-protection-level
  permission or a new Data Safety disclosure.

Anything requiring `WRITE_SETTINGS`, `SYSTEM_ALERT_WINDOW`,
`PACKAGE_USAGE_STATS`, `BIND_DEVICE_ADMIN` (new admin policies beyond lock),
`QUERY_ALL_PACKAGES`, storage/media permissions, or any new *dangerous*
permission is **out of scope** — each would trigger a fresh Play Console
review, which is the constraint this doc is designed around.

## Confirmed no-new-permission, no-new-disclosure features

### 1. Quick toggle tiles (read + launch-only, no direct set)
Live tiles or a personalize "quick actions" row that **read** system state
and **deep-link** to the relevant settings panel on tap (matches the
existing notification-listener/accessibility deep-link pattern — no new
Data Safety entry since nothing new is read/stored):
- Wi-Fi: read via `WifiManager.isWifiEnabled()` (already normal-permission,
  no location coupling needed for just on/off state) → tap opens
  `Settings.Panel.ACTION_WIFI` (in-place bottom panel, doesn't leave the app)
- Bluetooth: read via `BluetoothAdapter.isEnabled()` → tap opens
  `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` or `ACTION_BLUETOOTH_SETTINGS`
- Airplane mode: read via `Settings.Global.AIRPLANE_MODE_ON` → tap opens
  `ACTION_AIRPLANE_MODE_SETTINGS`
- Battery saver: read via `PowerManager.isPowerSaveMode()` (already used for
  live-tile gating) → tap opens battery settings
- Location on/off: read via `LocationManager.isLocationEnabled()` → tap
  opens `ACTION_LOCATION_SOURCE_SETTINGS`
- NFC on/off: read via `NfcAdapter.isEnabled()` → tap opens
  `ACTION_NFC_SETTINGS`
- Mobile data: **no read API without carrier privileges either** — omit
  entirely, or show a static "data" tile that always opens
  `ACTION_DATA_ROAMING_SETTINGS`

Design: a tile face style consistent with existing live tiles — state as
the front face text/icon tint, tap always routes through the deep link
(never attempts a silent toggle).

### 2. Genuinely settable, zero-permission system controls
- **Flashlight/torch**: `CameraManager.setTorchMode()` — `CAMERA`
  permission is **not** required for torch-only use on API 23+ (torch mode
  doesn't open a capture session). A live "flashlight" tile that toggles
  on tap with no settings screen involved.
- **Ringer mode (silent/vibrate/normal)**: `AudioManager.setRingerMode()`
  is normal-permission **except** switching *into* silent/vibrate on API
  24+ additionally requires Notification Policy Access (see §3) — but
  normal↔vibrate and volume-slider changes within the current policy are
  unrestricted.
- **Media/alarm/ring stream volume**: `AudioManager.setStreamVolume()`,
  normal permission, already implicitly usable by the existing music-tile
  transport controls (play/pause/next reuse `MediaController`, which is a
  related but separate API).
- **Screen timeout is not settable without `WRITE_SETTINGS`** — omit.

### 3. Notification Policy Access (DND) — same deep-link pattern as notification listener
`NotificationManager.isNotificationPolicyAccessGranted()` /
`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` is architecturally identical
to the already-shipped, already-Play-approved notification-listener flow:
special access, no dangerous permission, one-time deep link. Once granted,
`setInterruptionFilter()` lets a tile genuinely toggle DND on/off (not
just deep-link). Since the existing Accessibility disclosure dialog
precedent shows Play scrutinizes *any* special-access itemization closely,
this should get its own itemized line in that same disclosure dialog if
shipped, but requires **no new manifest permission and no new Data Safety
form entry** (DND state isn't personal/usage data in the Data Safety
sense — it's device configuration).

### 4. Pure system read APIs already normal-permission
No manifest change, nothing to declare, safe as live-tile data sources:
- Battery level/charging state (`BatteryManager`/`ACTION_BATTERY_CHANGED`) —
  a "battery" tile face (%, charging glyph), same shape as the clock tile.
- Storage free/total (`StatFs`) — a "storage" tile.
- Network type/connectivity (`ConnectivityManager.getActiveNetwork()` +
  `NetworkCapabilities`, no `ACCESS_NETWORK_STATE`... actually this one
  **does** require the normal permission `ACCESS_NETWORK_STATE`, which is
  auto-granted and needs no Data Safety entry since it's not in Play's
  sensitive list) — a "connectivity" tile (Wi-Fi/mobile/none).
- Device orientation lock state, dark-theme state
  (`Configuration.uiMode`) — cosmetic/informational tiles.
- Alarm clock next-alarm time: `AlarmManager.getNextAlarmClock()` —
  normal permission, directly fixes the long-standing placeholder "7:00"
  alarm value noted in `docs/DECISIONS.md`/current-status (no permission
  needed at all, unlike originally assumed).

### 5. Settings deep-links with zero new declaration burden
All of these are just `startActivity(Intent(ACTION_...))` — no permission,
no Data Safety row, same as the existing notification-access/accessibility
rows in `PersonalizeSheet`:
- App info / per-app battery & data usage: `ACTION_APPLICATION_DETAILS_SETTINGS`
- Default apps (browser/SMS/etc.): `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`
- Storage settings: `ACTION_INTERNAL_STORAGE_SETTINGS`
- Display settings (brightness/font/theme): `ACTION_DISPLAY_SETTINGS`
- Sound settings: `ACTION_SOUND_SETTINGS`
- Date & time settings: `ACTION_DATE_SETTINGS`
- Accessibility settings (already used for lock service)
- App-specific notification settings for a given package (API 26+):
  `ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE` — useful from a
  long-press tile context menu ("notification settings for this app")

Design as an expanded "quick settings" personalize group or a set of
launcher-shortcut tiles, mirroring the existing app-list pin/long-press
pattern rather than inventing new UI.

### 6. Already-covered capability, worth noting as "no incremental cost"
Torch, volume, and DND toggles above are the only *true* set-not-just-read
controls available without `WRITE_SETTINGS`. Everything else in the
classic "quick settings" mental model (Wi-Fi, Bluetooth, data, airplane
mode, rotation lock, brightness slider) is **read-and-deep-link only**
under current Android policy (locked down progressively API 17→33) —
scope any "quick toggle tile" feature expecting that split explicitly, so
it isn't scoped as if this were a real Quick Settings tile (which runs
with system-level privilege a third-party launcher does not have).

## Explicitly out of scope (needs a new declaration)
- `WRITE_SETTINGS` (brightness/rotation-lock direct set, screen timeout)
- `PACKAGE_USAGE_STATS` (app usage stats / screen time tiles)
- `SYSTEM_ALERT_WINDOW` (overlay bubbles)
- Any storage/media read permission (already deliberately dropped once for
  photo search — see current-status entry "Photo search removed")
- `BIND_DEVICE_ADMIN` capabilities beyond the existing lock-only usage
- `QUERY_ALL_PACKAGES`

## Suggested phasing (not scheduled — pick one as a SESSION-PLAN item)
1. Alarm-clock tile fix (`AlarmManager.getNextAlarmClock()`) — smallest,
   fixes a known placeholder, zero new UI surface.
2. Battery + connectivity read-only tiles — reuses existing live-tile face
   infrastructure (`LiveFace` enum, flip scheduler) as-is.
3. Flashlight toggle tile — first genuinely-interactive (not just
   launch-app) system tile.
4. Quick-toggle row (Wi-Fi/Bluetooth/airplane/location/NFC, read + deep
   link) — larger UI surface, best as its own personalize/quick-search
   addition.
5. DND set-capable toggle via Notification Policy Access — requires the
   same disclosure-dialog rigor Play already flagged twice for
   Accessibility; write the itemized disclosure text *before* implementing,
   not after.

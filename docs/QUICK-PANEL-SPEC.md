# Quick panel + glance status card — feature spec

Status: design spec, visuals attached in-chat, **not yet implemented**.
Supersedes the "quick toggle tile" framing in
`docs/NO-EXTRA-PERMISSION-FEATURES.md` — this document is the authoritative
design for that feature once scheduled as a `SESSION-PLAN.md` item.

## 1. Problem

Android reserves single-finger swipe-down starting at/near the status bar
for its own notification shade / quick settings (left and right edge pulls
both routed to the system). A launcher gesture in that zone would either be
unreachable (the system intercepts it first) or feel like it's fighting the
OS. TileShell also already owns two gestures that must not collide:

- **Two-finger swipe down** (anywhere on Start) → quick search overlay
  (`QuickSearchGesture.kt`)
- **Horizontal swipe** → pager between feed (−1) / Start (0) / app list (+1)
- **Single-finger vertical drag** → grid scroll, or tile drag in edit mode
- **Right-edge vertical swipe on a stack tile** → member flip

## 2. Chosen gesture: two-finger swipe up

**Two-finger swipe up, anywhere on the Start screen**, opens a quick panel
that **slides up from the bottom edge** — motion direction matches the
gesture direction (content follows the finger), the same physical logic as
the classic iOS Control Center swipe-up-from-bottom gesture. Two fingers
means it can never be confused with the single-finger scroll/drag/pager
gestures already in place; "up" (vs. quick search's "down") means it can
never be confused with quick search either. No new edge/corner hit-zone is
needed — full-screen two-finger detection, mirroring how
`QuickSearchGesture.kt`'s threshold check already works for the down case.

Rejected alternatives (from the earlier discussion) and why:
- Single-finger swipe from a top handle/pill — extra persistent UI element
  taking up a grid row's worth of space; two-finger swipe needs none.
- Overscroll-pull-down at grid top — discoverability and conflict risk with
  the system status-bar pull, since it's still visually "pulling down from
  near the top."
- Tap-only icon — kept as a *secondary* affordance (see §4), not instead of
  the gesture.

Implementation shape (mirrors `QuickSearchGesture.kt`): a pure
`isQuickPanelSwipe(startPositions, endPositions, pointerCount)` threshold
function, unit-tested the same way as `QuickSearchGestureTest`, wired into
`StartScreen.kt`'s outer `pointerInput` alongside the existing two-finger
down check — same block, opposite vertical sign, both gated off during edit
mode / open folder / personalize / app list (matching every other overlay's
gating convention).

## 3. Quick panel contents — scope: no new permission, no new Play Console declaration

Per `docs/NO-EXTRA-PERMISSION-FEATURES.md`, only:

| Item | Behavior | API |
|---|---|---|
| Wi-Fi | read state, tap → `Settings.Panel.ACTION_WIFI` | `WifiManager.isWifiEnabled()` |
| Bluetooth | read state, tap → `ACTION_BLUETOOTH_SETTINGS` | `BluetoothAdapter.isEnabled()` |
| Airplane mode | read state, tap → `ACTION_AIRPLANE_MODE_SETTINGS` | `Settings.Global.AIRPLANE_MODE_ON` |
| Location | read state, tap → `ACTION_LOCATION_SOURCE_SETTINGS` | `LocationManager.isLocationEnabled()` |
| Battery saver | read state, tap → battery settings | `PowerManager.isPowerSaveMode()` (already used for live-tile gating) |
| Flashlight | **true toggle**, no settings screen | `CameraManager.setTorchMode()` |
| Ringer (normal/vibrate) | **true toggle** | `AudioManager.setRingerMode()` |
| DND | **true toggle**, one-time special-access grant (same UX pattern as notification listener) | `NotificationManager.setInterruptionFilter()` / `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |

Chips are visually identical whether they're a true toggle or a
read+deep-link: state reflected in fill/icon, tap always does *something*
useful (either flips it or opens the right settings screen) — the user
never needs to know which category a given chip falls into.

### 3a. Volume — sliders, not chips

Volume is a **true, direct set** (`AudioManager.setStreamVolume()`,
normal permission, no manifest/Data Safety change) but doesn't fit the
toggle-chip grid — it's a continuous value, not on/off. Rendered as its
own row(s) below the chip grid, each a labeled slider:

- **Media** (`STREAM_MUSIC`) — always shown; the stream users adjust most,
  and it doubles as a fast path for the now-playing tile's transport
  controls already on the feed page.
- **Ring** (`STREAM_RING`) — shown only when not already in a
  vibrate/silent policy state that would make a ring slider misleading
  (checked via `AudioManager.getRingerMode()` before rendering); dragging
  it to 0 does **not** silently flip ringer mode — Android already blocks
  that without notification-policy access, so the slider clamps at a
  low-but-audible minimum in that case, same as the system's own quick
  settings does without DND access.
- **Alarm** (`STREAM_ALARM`) — shown, since a muted alarm is a genuine
  footgun; deliberately never auto-hidden even when other streams are.

Each slider reads its current level via
`AudioManager.getStreamVolume()`/`getStreamMaxVolume()` on panel open and
writes back on drag-release (not on every drag-frame, to avoid audio pop
spam) via `setStreamVolume(stream, value, 0)` (flag `0` = no UI/sound
feedback from the system, since the panel already shows the value itself).
No new permission, no new Data Safety row — `AudioManager` volume control
is a normal-permission API already implicitly available to every app.

## 4. Secondary affordance
A small chip/icon near the existing settings-gear long-press target opens
the same panel via tap, for users who don't discover the gesture — no new
navigation, reuses the gear's existing corner.

## 5. Glance page status card — view only

Per the user's steer, the pure read-only info (battery %, storage free,
connectivity type, next alarm) is **not** a panel/tile feature — it's a
single glanceable card on the feed page's **glance** tab, next to the
existing `WeatherCard`/`AgendaCard`/`NowPlayingCard`. Same visual language:
one `Card`, no interactivity beyond perhaps a tap-through to the relevant
settings screen (optional, not required for v1). Read-only, no toggles, no
gesture involvement at all — it's exactly the "info-at-a-glance" idiom the
feed page already exists for. Directly fixes the alarm-clock placeholder
value noted in current project status (`AlarmManager.getNextAlarmClock()`,
zero new permission).

## 6. Rotation lock, brightness, screen timeout (`WRITE_SETTINGS`)

Verified against Google's official Play Console documentation (the
restricted-permissions list requiring the Permissions Declaration Form:
SMS/Call Log, location, broad photo/video, `MANAGE_EXTERNAL_STORAGE`,
`QUERY_ALL_PACKAGES`, body sensors, `SYSTEM_ALERT_WINDOW`, exact alarms,
full-screen intent, AccessibilityService, VpnService, Health Connect) —
`WRITE_SETTINGS` appears in none of these categories. It's a special app-op,
granted the same way as the DND/notification-listener flows (one-time
Settings deep link via `ACTION_MANAGE_WRITE_SETTINGS`), not a manifest
dangerous permission, so it needs no Play Console declaration. Unlocks:

| Item | Behavior | API |
|---|---|---|
| Rotation lock | read always works; **true toggle** once granted, else tap deep-links to the grant screen | `Settings.System.ACCELEROMETER_ROTATION` |
| Screen brightness | slider, read always works, **write** needs the grant | `Settings.System.SCREEN_BRIGHTNESS` |
| Screen timeout | tap-to-cycle through presets (15s…30m), read always works, **write** needs the grant | `Settings.System.SCREEN_OFF_TIMEOUT` |

Rotation lock is a chip in the grid (own inline fallback: tap deep-links to
the grant screen when not yet granted). Brightness and screen timeout are
full rows below the volume sliders — while access isn't granted, both are
replaced by a single "allow modify system settings" row that deep-links,
rather than showing disabled/dead controls.

## 7. Out of scope for this spec
Anything from `docs/NO-EXTRA-PERMISSION-FEATURES.md`'s "explicitly out of
scope" list (`PACKAGE_USAGE_STATS`, etc.) is unaffected by this design and
remains excluded.

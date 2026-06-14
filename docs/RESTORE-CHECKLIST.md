# TileShell — Kill / Reboot Restore Checklist (S19)

Manual on-device verification that the persisted layout + settings survive
process death and reboot, and that a corrupt database degrades gracefully to the
WP default layout. Run after installing a debug build and setting TileShell as
the default launcher.

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.tileshell/.MainActivity
```

## A. Edit persistence across process kill

1. Open Start. Enter edit mode (long-press a tile, 430 ms).
2. **Reorder** a tile, **resize** another (cycle small→…→large), **unpin** one,
   and **merge** two into a folder. Tap **done**.
3. Force-stop the process (no graceful onPause path):
   `adb shell am force-stop com.tileshell`
4. Relaunch (Home button). **Expect:** the reordered / resized / unpinned tiles
   and the new folder are exactly as left. No flicker back to the old order.

> Reorder writes are debounced 120 ms; allow a moment after the last drop before
> killing if testing the very last reorder in isolation.

## B. Settings persistence across process kill

1. Open **personalize**: switch theme (dark↔light), pick an accent, toggle
   **transparent tiles** + adjust the slider, toggle **blur**, choose a gradient
   wallpaper (and a **custom photo** via the picker).
2. Force-stop + relaunch as in A.3–A.4.
3. **Expect:** theme, accent, glass + transparency, blur, and the chosen
   wallpaper all restore. The custom photo reloads (persistable URI grant).

## C. Reboot restore

1. With a customized layout + settings from A/B, reboot: `adb reboot`.
2. After boot, press Home. **Expect:** TileShell comes up as launcher with the
   full customized layout and settings intact (identical to pre-reboot).

## D. Corruption → default-layout fallback

1. Corrupt the database file on disk (debuggable build):
   ```
   adb shell "run-as com.tileshell sh -c 'echo garbage > files/../databases/tileshell.db'"
   ```
   (or push a truncated/garbage file over `databases/tileshell.db`).
2. Force-stop + relaunch.
3. **Expect:** no crash. The DB is wiped and rebuilt, the seeder re-seeds the WP
   default layout (mapped to installed apps). Settings (separate DataStore file)
   are unaffected.

## E. Custom-wallpaper URI revocation

1. Set a custom photo wallpaper, then delete/move the source image so the URI is
   no longer readable.
2. Relaunch. **Expect:** no crash; wallpaper falls back to the selected gradient.

## F. First-run hint + default-launcher prompt

1. Clear data: `adb shell pm clear com.tileshell`, then launch.
2. **Expect:** the default-launcher prompt appears once; after answering, the
   one-time hint card shows the gesture hints; tapping **got it** dismisses it.
3. Force-stop + relaunch. **Expect:** neither the prompt nor the hint reappears.

---

_Last executed: ____________ on ____________ (device / API ___). Result: ______._

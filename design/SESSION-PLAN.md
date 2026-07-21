# TileShell — Claude Code Session Plan (Pro plan)

Each session is scoped to finish comfortably inside one Pro 5-hour window, and every session ends with a **building APK + git commit** — so even if you stop early, nothing is wasted. Do them in order; each builds on the last.

**Before every session:** `git status` clean → open Claude Code in `~/Projects/tileshell` → paste the session prompt below.
**After every session:** confirm the build + commit happened, then ask Claude Code to update the "Current status" block in CLAUDE.md, then `/clear` or exit.

**FR references** point to `docs/TileShell-Feature-Specification.docx`. **Prototype references** point to `design/` files — Claude Code should read those directly for exact values.

---

## Phase 0 — Foundation (Sessions 1–3)

### S1 · Project scaffold + HOME activity
> Create the TileShell Android project per CLAUDE.md: Gradle Kotlin DSL, version catalog, Compose, the 8 modules listed, minSdk 26. `:app` has MainActivity declared as a HOME/DEFAULT launcher activity (singleTask, edge-to-edge, transparent system bars) showing a placeholder dark screen with "tileshell" text. Add `<queries>` for LAUNCHER category. Add RoleManager-based "set default launcher" prompt on first run. Init git, .gitignore, commit.

**Done when:** APK installs, device offers it as a Home choice, Home button returns to it.

### S2 · App catalogue repository
> Implement `:core:data` AppCatalogRepository: enumerate launchable apps via LauncherApps, expose as Flow<List<AppEntry>> (packageName, label, letter, activity), sorted alphabetically. Register a package-change receiver (install/uninstall/update) that refreshes the flow. Temporary debug list screen in :app proving it works. JUnit test for sorting/letter grouping. Build, commit.

**Done when:** debug screen lists real installed apps; installing/uninstalling an app updates the list live.

### S3 · Dense-packing grid spike (highest-risk item — do early)
> In `:feature:start`, implement the dense packer: input = ordered list of (id, size ∈ small/medium/wide/large), output = grid coordinates on a 4-column grid with dense back-fill, per FR-1.1. Pure Kotlin function + thorough JUnit tests (gap back-fill, wide/large row spans, reorder stability). Render it in a custom Compose Layout with dummy colored tiles, vertical scroll. Measure scroll smoothness with 60 dummy tiles. Build, commit.

**Done when:** packer tests pass; 60-tile grid scrolls smoothly on device.

---

## Phase 1 — Usable MVP (Sessions 4–7)

### S4 · Design tokens + icon set
> Build `:core:design`: dark/light color tokens and glass values from design/launcher/styles.css; the 14 accents and 6 wallpaper gradients from design/launcher/data.js as Compose Brushes; port all icons in design/launcher/icons.js to ImageVectors (monoline, stroke ~1.6). Preview composables for tiles in all 4 sizes. Build, commit.

### S5 · Room schema + default layout seeder
> Implement Room per spec §4.3 (Tile, FolderChild, Folder meta, AppCache) and the first-run seeder: map DEFAULT_TILES from design/launcher/data.js to actually-installed apps by role (clock→deskclock, phone→dialer, etc.), skipping unmatched. Repository exposing Flow<List<TileModel>>. Migration scaffolding + tests for the seeder mapping. Build, commit.

### S6 · Real Start screen + launching
> Wire it together: Start screen renders persisted tiles via the dense packer with real icons, accent fill, lowercase lower-left labels (icon-only on small), over the aurora wallpaper. Tap launches via LauncherApps with fallback toast. Status-bar inset + bottom padding per FR-1. Replace the S2 debug screen. Build, commit.

**Done when:** fresh install shows the WP default layout with your real apps and everything launches. *From here you can daily-drive it.*

### S7 · Tilt feedback + Home behaviour
> Add the WP 3D tilt press effect (tile rotates toward touch point, spring release) per FR-1.2. Implement goHome(): Home press closes overlays/edit (stubs ok), scrolls Start to top. Back on Start = no-op. Uninstall auto-removes tiles (FR-5). Build, commit.

---

## Phase 2 — App list (Sessions 8–11)

### S8 · Start ⇄ App list pager
> Implement the horizontal pager per FR-6 with finger-following physics: thresholds, −22%/0.4 parallax on Start, 50% commit, spring-back. Chevron affordance on Start. App list page = placeholder. Swipe disabled flags plumbed (edit/overlays). Build, commit.

### S9 · Alphabetical app list
> Real app list per FR-5: lazy list with lowercase letter section headers, rows = small accent tile icon + name, fed by AppCatalogRepository, live package updates, tap to launch. Build, commit.

### S10 · Search + jump grid
> Search bar with live case-insensitive filter + "no apps found" empty state. Letter-header tap opens the full-screen a–z jump grid (disabled letters greyed), tap scrolls to section, scrim dismisses — per FR-5 and design/launcher/screens.js. Filter unit tests. Build, commit.

### S11 · Pin from app list
> Long-press 450ms (7px cancel) on a row pins as a medium tile in the app's default colour, returns to Start, toasts "pinned X" / "already on start" per FR-5. Persisted. Build, commit. **Phase 2 complete — tag `v0.2`.**

---

## Phase 3 — Edit mode (Sessions 12–16)

### S12 · Edit mode entry/exit + chrome
> Gesture state machine per FR-3.1 (430ms long-press, haptic, selection): edit chrome (dim/shrink non-selected, corner unpin/resize controls on selected, bottom edit bar add/personalize/done), exit paths (done, empty-space tap, plain tile tap, Home/Back). Live-animation pause hooks (no-op for now). Build, commit.

### S13 · Drag to reorder
> Edit-mode drag per FR-3.2: lift with scale/shadow, follow finger, edge-zone hover reorders with live dense re-flow animation, auto-scroll near viewport edges, order persisted on drop. Reorder unit tests. Build, commit. *(Hardest session — if it overruns, land drag+drop without auto-scroll and finish auto-scroll at the start of S14.)*

### S14 · Merge to folder
> Centre-zone (22–78%) merge targeting with highlight; release merges per FR-3.3 rules (folder append de-duped / new folder with target's size-or-medium, colour, name "folder"); "grouped" toast. Folder tile renders 4-icon mini-grid face. Merge-rule unit tests incl. folder-into-folder union. Build, commit.

### S15 · Resize, unpin, edit bar
> Resize handle cycles small→medium→wide→large with animated re-pack; unpin removes tile; edit bar: add → app list + hint toast, done → exit, personalize → stub sheet. Per FR-3.4/3.5. Build, commit.

### S16 · Folder overlay + rename
> Full-screen folder overlay per FR-4: title, close, grid of medium child tiles, launch-and-dismiss, scrim dismiss; long-press title to rename (persisted). Home closes it. Build, commit. **Phase 3 complete — tag `v0.3`.**

---

## Phase 4 — Personalization (Sessions 17–19)

### S17 · Personalize sheet: theme + accent
> Bottom sheet (grip, scrim, slide-up) per FR-7 with dark/light segmented toggle and the 14 accent swatches; changes apply live behind the open sheet; persisted in Proto DataStore. Light token set finished. Build, commit.

### S18 · Glass, blur, wallpapers
> Transparent-tiles toggle + 0–100 slider using the alpha formula in CLAUDE.md; blur-wallpaper toggle (18px blur, 1.1 sat, 1.12 scale); wallpaper row with 6 bundled gradients + system photo picker for custom (persistable URI); reset-layout action + toast. Alpha-formula unit test. Build, commit.

### S19 · Persistence hardening + first run
> Debounced transactional writes after every committed edit; corruption → default-layout fallback; kill/reboot restore test checklist executed; first-run hint overlay (prototype hint text) + default-launcher prompt flow polished. Build, commit. **Tag `v0.4`.**

---

## Phase 5 — Live tiles (Sessions 20–24)

### S20 · Flip engine + clock tile
> `:feature:livetiles`: 3D flip composable, random-tile scheduler (~2.6s, one tile at a time), gating (edit mode, off-screen, screen off, battery saver, animations-off). Clock tile front/back faces per FR-2.1 with minute tick. Live faces only at medium+; small stays static. Build, commit.

### S21 · Weather + calendar tiles
> Calendar face via CalendarProvider (READ_CALENDAR opt-in, next/second event). Weather face via a pluggable provider interface + WorkManager refresh ≥30min + cache (coarse location opt-in, manual-city fallback). Both degrade to static tiles when denied. Build, commit.

### S22 · Notification listener: badges + mail/messages
> NotificationListenerService (opt-in flow with settings deep-link): per-app badge counts on tiles per FR-1.2; mail/messages live faces (sender, snippet / count back-face) for the user's default mail+SMS apps. Reconnect handling. Full graceful opt-out. Build, commit.

### S23 · People + photos tiles
> People mosaic (contacts opt-in): 2×2 / 4×2 avatar grid with single-cell refresh ~2.1s, back face = large avatar. Photos tile: cross-fade slideshow ~3.0s from photo-picker selection, never flips, label with shadow. Build, commit.

### S24 · Music tile + degradation matrix
> Music face bound to active MediaSession (EQ bars + track/artist; paused back-state). Then verify the full FR-2.3 degradation matrix: with ALL permissions denied, every tile is static, no badge, zero crashes. Fix gaps. Build, commit. **Tag `v0.5` — feature complete.**

---

## Phase 6 — Polish (Sessions 25–27)

### S25 · Motion + visual polish
> Side-by-side pass against design/ prototype: tilt curves, flip easing, pager spring, edit transitions, light theme QA, toast styling. Fix the visual diff list (prepare it yourself by comparing builds to the prototype before the session — saves tokens).

### S26 · Performance
> Baseline profile, Macrobenchmark for cold start (≤800ms) and scroll/drag jank, bitmap downsampling audit, recomposition audit (stability annotations, keys). Hit spec §3 budgets. Build, commit.

### S27 · Accessibility + compatibility
> TalkBack flows (launch, pin, full edit via custom accessibility actions), 48dp targets, animations-off mode, font scale, RTL mirroring, 3-button nav, cutouts. Build, commit. **Tag `v0.9` — release candidate.**

---

## Phase 7 — Release (Sessions 28–29)

### S28 · Beta hardening
> Triage crash/ANR reports from sideload/internal-track testing; OEM battery-killer guidance screen for notification access; fix list from your own week of daily-driving. Build, commit.

### S29 · Release engineering
> Signed AAB, release build config (R8 rules verified), versioning, privacy policy text, Play data-safety answers drafted, store-listing copy. Tag `v1.0`.

---

## Phase 8 — Build tooling maintenance (deliberately scheduled, not urgent)

Triggered by Play Console's post-launch recommendation to upgrade AGP to
9.0+ for optimized resource shrinking. Current versions (as of v2.2.6):
AGP 8.9.1, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01. Not
urgent — 8.9.1 works fine today — but AGP 10 (mid-2026) removes the DSL
opt-out AGP 9.x still offers, so this isn't indefinitely deferrable either.
Split into two sessions on purpose: the migration itself is a chain of
interdependent version bumps that either compiles or doesn't, but the real
risk (R8/resource-shrinker + Compose-compiler behavior changes) is only
visible on a real device, not in a green build.

### S30 · AGP 9 migration (build + tests only)
> Bump AGP 8.9.1 → 9.x. This drags Kotlin 2.0.21 → 2.2.10+ with it (AGP 9's
> hard KGP floor — Gradle force-upgrades a lower one anyway), which drags a
> matching KSP version and a Compose BOM validated against the newer
> in-Kotlin-plugin Compose compiler. Migrate to AGP 9's new required
> build-script DSL rather than relying on the opt-out flag (it's removed in
> AGP 10). Confirm the `:macrobenchmark`/baselineprofile plugin (1.3.4)
> still resolves against AGP 9 (androidx's own release notes list it
> compatible). Enable `android.r8.optimizedResourceShrinking=true` in
> `gradle.properties` (or confirm it's on by default at 9.0+) — the actual
> fix for Play Console's "optimised resource shrinking isn't enabled" note.
> Re-read `proguard-rules.pro` against every reflectively-referenced class
> (`LockAccessibilityService`, `TileNotificationListenerService`,
> `LockAdminReceiver`, WorkManager workers, Room DAOs/entities) since the
> new shrinker traces references across the DEX/resource boundary more
> aggressively than today's. Exit bar for this session is
> `./gradlew :app:assembleDebug` + `testDebugUnitTest` green — no on-device
> testing yet, that's S31.

### S31 · AGP 9 on-device regression pass
> Before trusting the new shrinker/compiler in a production build, a full
> manual sweep on a physical device: notification badges + mail/messages
> live faces, accessibility-service screen lock (biometric preserved), DND
> toggle from the quick panel, every WorkManager-driven refresh (feed,
> weather, Bing wallpaper, wallpaper slideshow), widget hosting
> (bind/resize/remove), backup/restore round-trip, and a cold-start/scroll
> spot-check against the S26 baseline-profile numbers — the Compose
> compiler bump is the change most likely to quietly regress that
> stability-annotation tuning. Fix anything broken; only then is the AGP 9
> upgrade actually release-ready, not just "it builds." Build, commit.

---

## Getting the most out of Pro (read once)

1. **Chat and Claude Code share the same 5-hour pool** — avoid heavy claude.ai chats on coding days.
2. **One session = one plan item.** Resist "while you're at it" additions; that's how windows evaporate.
3. **Plan mode first on complex sessions** (S3, S13, S14, S22): let Claude Code propose the approach, approve, then it executes without wasted exploration.
4. **`/clear` between sessions.** Stale context burns tokens on every prompt.
5. **Keep CLAUDE.md's status block updated** — it's what makes session N+1 start instantly.
6. **You review, Claude codes.** Test the APK on your phone between sessions yourself; bring back a concrete bug list instead of asking Claude Code to "check everything".
7. If a session hits the window limit mid-task: ask for a commit of working state + a `HANDOFF.md` note, and resume next window.

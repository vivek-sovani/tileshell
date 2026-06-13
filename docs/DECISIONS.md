# TileShell — Design Decisions

Decisions made when the spec/prototype was ambiguous, per CLAUDE.md workflow
rule 4. Newest first.

## S12 · Edit mode entry/exit + chrome

- **Tile corner controls and add/personalize are visual chrome only this
  session.** The prototype renders unpin (close, top-left) and resize (bottom-
  right) on the selected tile, and add/personalize/done in the bottom edit bar.
  Their *actions* (unpin removes a tile, resize cycles size, add → app list,
  personalize → sheet) are explicitly SESSION-PLAN S15 work and need repository
  mutators that don't exist yet. S12 therefore renders all of them but wires
  only `done` → `exitEdit` (an FR-3.1 exit path). The non-wired buttons carry no
  `clickable` (rendered, inert) rather than a no-op stub, so there are no dead
  handlers to remove in S15.

- **Edit mode state lives in `StartViewModel`, not local Compose state.** Home
  (`onNewIntent`/`goHome`) and Back (`MainActivity` back callback) both need to
  read and clear it, and entering edit must flip the existing `swipeEnabled`
  flag that gates the pager. Keeping `editMode`/`selectedTileId` as `StateFlow`
  on the VM lets all three call sites share one source of truth; `enterEdit`/
  `exitEdit` also own the swipe toggle.

- **Selection is fixed at entry (prototype-faithful).** The prototype only sets
  the selected tile via the long-press that enters edit; once editing, a plain
  tap on any tile (or empty space) exits rather than re-selecting. S12 mirrors
  this — re-selection/drag is S13. The long-press timer is only armed out of
  edit mode.

- **Jiggle uses one shared phase, composed only while editing.** Rather than a
  per-tile infinite animation, a single `rememberInfiniteTransition` drives a
  ±.5° phase that even/odd tiles apply with opposite sign (approximating the CSS
  `nth-child(2n)` −.45s delay). It is gated behind `if (!editMode) return 0f`,
  so a resting Start screen runs no animation frames. The press-tilt effect
  (S7) is suppressed while editing. This is the "live-animation pause hook":
  real live tiles aren't wired into Start yet, so pausing them is a genuine
  no-op for now.

## S11 · Pin from app list

- **A pinned app's "default colour" is derived deterministically from its
  package.** The prototype pins in each app's authored `col` (data.js), falling
  back to blue. Real Android apps declare no WP tile colour, so rather than pin
  everything blue, `TileColors.defaultIdFor` folds the package name into one of
  the 14 palette ids — the same app always pins to the same colour, giving a
  varied board while staying stable across sessions.
- **"Already on start" is checked against top-level app tiles only.** Matching
  the prototype's `tiles.some(t=>t.app===appId)`, the de-dupe (`appTileCount`)
  looks at pinned/seeded app tiles by package, not folder children — an app that
  only lives inside a folder can still be pinned as its own tile. New tiles
  append at `MAX(position)+1`; the dense packer places them.

## S10 · Search + jump grid

- **The `#` jump cell is a real, tappable section.** The prototype's `buildJump`
  forces the `#` cell `off` (`c!=='#' && have.has(c)`) because its demo apps get
  a digit header (e.g. "9 → 9"), never `#`. TileShell instead buckets every
  non-letter app under a single `#` section (`AppEntry.letter`), so `#` is a
  genuine jump target — its cell lights up as accent and scrolls there whenever
  such apps exist, matching how our headers actually group. Letters absent from
  the (filtered) list stay dimmed and dismiss the grid on tap, as in the
  prototype.
- **Jump grid reflects the filtered list.** `availableLetters`/scroll targets are
  computed from the currently displayed (post-search) apps rather than the full
  catalogue, so a jump always lands on a visible header even while filtering.

## S9 · Alphabetical app list

- **App-list rows show the real app icon, not a monoline glyph.** The
  prototype renders each app-list row as an accent square with the app's
  monoline `ic` glyph, but that only works for its curated demo set — arbitrary
  installed apps have no TileShell glyph. Rows therefore show the real launcher
  icon (loaded via `PackageManager.getActivityIcon` off the main thread,
  `produceState`) on top of the accent square, which is kept as the backing so
  transparent icons still read as a tile. The generic "app" glyph is the
  fallback when an icon fails to load. Start-screen tiles are unaffected (they
  keep monoline glyphs).

## S5 · Room schema + seeder

- **`TileSize` canonical home is `:core:data`.** S3 defined `TileSize` in
  `:feature:start` and S4 duplicated a preview-only copy in `:core:design`.
  Persisted layout models need it, so the canonical enum now lives in
  `:core:data` (`com.tileshell.core.data.TileSize`); `:feature:start` depends on
  `:core:data` and imports it. The `:core:design` preview enum stays private
  (preview-only; keeps the design module free of a data-layer dependency).

- **Schema shape (spec §4.3, not re-read — WP-faithful reconstruction).**
  Four entities: `tiles` (ordered grid items, `type` = app|folder, app columns
  nullable, `folderId` links folder tiles to their meta), `folders` (id + name),
  `folder_children` (folderId FK + position + component, `onDelete=CASCADE`),
  `app_cache` (component → label/letter/lastSeen for offline tile rendering and
  uninstall detection). A folder tile and its `folders` row share the same id
  (e.g. `g-social`); `tiles.folderId == tiles.id` for folder tiles. No FK on
  `tiles.folderId` (avoids insert-ordering constraints; Room `@Relation` does
  not require one).

- **Seeder role mapping.** Prototype app ids are generic roles. Each maps to a
  standard intent/category resolved against installed apps; the resolved
  package's *launcher* activity is stored so tapping a tile opens the app's
  entry point. Roles with no Android equivalent (weather, notes, bank, auth,
  …) have no mapping and their tiles are skipped. Folders keep only resolvable,
  de-duplicated children and are dropped entirely if none resolve. Positions
  are re-numbered contiguously after skips so dense packing is unaffected.

- **Migration scaffolding.** Database is version 1 with `exportSchema=true`
  (schema JSON under `core/data/schemas/`). `TileShellDatabase.MIGRATIONS` is an
  empty array wired into the builder, ready for future versioned migrations.

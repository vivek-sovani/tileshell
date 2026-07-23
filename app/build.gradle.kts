import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Consumes the baseline profile produced by :macrobenchmark (S26).
    alias(libs.plugins.baselineprofile)
}

// Signing: reads from key.properties (NOT checked into git — see key.properties.template).
// When absent (dev machines / CI without credentials) release falls back to the
// debug keystore so local release builds and APK comparisons still work.
val keystoreFile = rootProject.file("key.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "com.tileshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tileshell"
        minSdk = 26
        targetSdk = 36
        // versionCode: 10 = v1.0; patches → 11, 12 …; v1.1 → 20, etc.
        // (13/1.0.3 was uploaded to Play then discarded; Play burns the code, so → 14.)
        // v1.1 = 20 (category folders + in-folder tile editing).
        // v1.1.1 = 21 (folder-merge size fixes, operable music controls, folder badges).
        // v1.2 = 30 (grid columns 4/5/6, refined editing/merge, folder fixes,
        //   small-weather temp, Outfit default, Bing daily wallpaper).
        // v1.3 = 40 (per-tile colour, tile colour from app icon, adjustable tile spacing,
        //   battery: network constraints on background workers, reduced media poll).
        // v1.4 = 50 (widget stacks, large tiles 3×3, landscape two-panel, notification
        //   tile content improvements, clock alarm priority, staggered stack rotation).
        // v1.4.1 = 51 (folder/stack X button opens overlay instead of deleting all tiles).
        // v1.5 = 60 (backup & restore: auto-save, visual layout history, export/import;
        //   personalize navigation shows one screen at a time; fresh installs default to
        //   no wallpaper + solid Nokia-blue tiles; app-list search-clear fix).
        // v1.5.1 = 61 (widget stack fixes: per-member tile colour during rotation, merging
        //   a liveOnly tile like weather into a stack no longer drops it; per-tile colour
        //   picker + full small/medium/wide/large resize cycle inside a folder overlay;
        //   folder overlay "make wide/large stack" shortcut; calendar/weather tile layout
        //   fixes at WIDE size).
        // v1.6.0 = 70 (quick search: two-finger swipe-down opens apps/contacts/web search,
        //   contact call/message/pin-to-start, recent searches + suggested apps; apps with a
        //   pending notification surface in the app list's recent section even when unpinned;
        //   hide/unhide apps from the app list; Samsung Gallery "story" notification fix).
        // v1.6.1 = 71 (wallpaper-behind-tiles / glass transparency fix: widget stack members
        //   and closed-folder mini-grid cells with an individually selected colour rendered
        //   fully opaque instead of showing the wallpaper/glass through them like other tiles).
        // v1.6.2 = 72 (in-app update check + flexible-update prompt banner on Start; wallpaper
        //   crop overlay gains pinch-to-zoom alongside drag-to-position; wallpaper slideshow
        //   rotates the background through multiple picked photos on a 15m/30m/1h/3h timer).
        // v1.7.0 = 80 (personalize wallpaper section reorganized into a none/photo/slideshow/
        //   bing/stock type selector, with blur/wallpaper-behind-tiles moved there as an
        //   "effects" subsection and tile style split into glass/colour & fill/shape & spacing
        //   subgroups; quick search can ask ChatGPT, Gemini, Claude, or Perplexity; clock tile
        //   date no longer clipped at 5/6 grid columns).
        // v1.8.0 = 90 (feed search pill opens quick search directly instead of a separate "g"
        //   button that could fall through to Start's clock tile underneath; widget hosting
        //   overhaul — providers now actually receive their real render size (a Bundle.EMPTY
        //   bug silently broke this for every widget), a provider's own recommended aspect
        //   ratio sizes it instead of a raw minHeight, square widgets render centered at half
        //   width, and three independent handles resize width/height/diagonal instead of one
        //   shape-guessed handle; widgets from slow-registering OEM providers get a longer
        //   grace period before being dropped as "uninstalled").
        // v1.8.1 = 91 (widget stack: swipe-to-flip confined to a 40dp right-edge drag zone
        //   near the position indicator, so the rest of the tile never intercepts a plain
        //   scroll swipe — supersedes an initial long-press-then-drag attempt within the
        //   same version that felt sluggish on-device).
        // v1.8.2 = 92 (large (3x3) tile is now available on 4-column grids too, not just
        //   5/6 — both top-level tiles and folder children, plus the folder overlay's
        //   "make large stack" shortcut).
        // v1.9.0 = 100 (bundled wallpaper gradients adapt to light theme instead of always
        //   showing a near-black base between tiles; smoother gradient falloff, less banding;
        //   glass/transparent tiles now tint with each tile's own resolved accent colour
        //   instead of one shared neutral tint across the whole grid).
        // v2.0.0 = 200 (notification tiles overhaul: front face shows the total unread count
        //   prominently (big number + label); back face cycles through each pending notification
        //   in turn, newest first — mail/messages driven by the flip scheduler, generic apps
        //   self-manage their flip; per-notification avatar shown when cycling through grouped
        //   notifications from the same app).
        // v2.1.0 = 210 (Edge Strip: a collapsible quick-launch bar pinned to the bottom of
        //   Start — search on the left, favourite apps in the centre, recent apps on the
        //   right; widget stack auto-rotate interval increased so a tapped notification
        //   isn't a race against the timer).
        // v2.1.1 = 211 (fix: layout-history snapshot dedup hashed only tile/folder structure,
        //   not settings — a settings-only change made after the last tile move was silently
        //   dropped from history, so restoring an older snapshot could revert personalization
        //   even though the layout itself was unaffected; file export/import was never affected
        //   since it has no dedup).
        // v2.1.2 = 212 (app category matching: dropped unreliable CATEGORY_MAPS/PRODUCTIVITY
        //   OS signals, folded productivity into tools, fixed "smart"-prefixed app names
        //   falsely matching the shopping "mart" token, widened banking/shopping tokens;
        //   edge strip expand/collapse state now survives personalize/edit-mode/folder
        //   remounts instead of resetting, quick search slides it fully away instead of
        //   unmounting it, the Start app-list/gear affordance clears the strip's height
        //   while it's expanded, default handle size is now thick; feed widgets section
        //   header unified with the other feed sections).
        // v2.1.3 = 213 (no functional change — 2.1.2's Play Console upload failed;
        //   re-cut under a new version code).
        // v2.2.0 = 220 (folders: inline expand-in-place replaces the modal FolderOverlay;
        //   Windows-Phone-style gap-preserving "sticky" tile arrangement (user-selectable,
        //   now the fresh-install default) — a removed/resized tile's gap stays open
        //   instead of the grid always repacking; dragging a tile onto an occupied cell
        //   now pushes the occupant down to make room instead of rejecting the drop;
        //   Accessibility API prominent-disclosure fix for a Play Console policy rejection
        //   — itemizes all data TileShell collects, not just the accessibility service's
        //   own no-op data use, and narrows the service's declared event-type scope).
        // v2.2.1 = 221 (sticky-mode drag now shows a live push-down/push-sideways preview
        //   as the finger moves, instead of only reflowing after the drop — a colliding
        //   tile prefers sliding into a free column in the same row before falling back to
        //   a straight push down; new "lock layout" toggle in Personalize blocks entering
        //   edit mode entirely, with a toast pointing to where to unlock it; TileShell no
        //   longer lists itself in its own App List (its LAUNCHER-category MainActivity was
        //   leaking into the unscoped activity query); new "default launcher" row in
        //   Personalize, shown only while TileShell isn't already default; the existing
        //   first-run default-launcher prompt now re-asks on every fresh app open instead
        //   of only ever once).
        // v2.2.2 = 222 (second Accessibility API policy rejection — reviewer flagged the
        //   prominent disclosure as still missing Calendar events + Contacts, even though
        //   the v2.2.0 fix already itemized both; root cause was the submitted demo video,
        //   which scrolled past those two bullets too fast to read, not the app itself.
        //   Reordered the disclosure so Contacts + Calendar are the first two data items
        //   listed (previously buried at positions 2-3 of 6), tightened the wording so less
        //   scrolling is needed overall, and split the one giant string into three Text()
        //   calls for readability. Re-recording the disclosure video — pausing on every
        //   bullet this time — is the actual fix for the rejection; see DECISIONS.md).
        // v2.2.3 (code 223): localized news feed — per-country region picker
        //   (~20 countries, multi-select), rich image-led sources for US/UK/AU/CA/UAE,
        //   fixed one high-volume region crowding out the others; reorder apps inside an
        //   open folder by dragging; closed folders show which app a notification came
        //   from; fixed merge-two-tiles-into-a-folder in Windows Phone tile arrangement.
        // v2.2.4 (code 224): widget stacks gain a "back to folder" action plus switching
        //   directly between wide/large sizes without collapsing first; each rotating
        //   stack member shows its own notification badge (previously only a single
        //   consolidated total for the whole stack); a closed folder's combined
        //   notification total moved beside its name label so it no longer overlaps the
        //   per-app badge on its top-right mini-grid cell; fixed a real merge-to-folder
        //   regression in sticky (Windows-Phone-style) tile arrangement.
        // v2.2.5 (code 225): widget-stack and closed-folder notification badges now
        //   render at the same size as a regular app tile's badge instead of the tiny
        //   folder-mini-grid dot; people tile's photo mosaic shows circular avatars
        //   instead of square crops; clock tile shows 12-hour am/pm time, matching the
        //   feed/glance screen's clock; live-tile text/icons, the static tile glyph,
        //   tile labels, the Start screen's chevron/gear, and the open-folder action
        //   chip now switch to dark text automatically when the wallpaper actually
        //   showing through them (glass tiles, "wallpaper behind tiles" mode, or the
        //   plain screen background) is light, based on an actual brightness sample
        //   rather than the theme setting; fixed a closed folder's unused mini-grid
        //   slots (fewer apps than the grid's capacity) rendering as dark squares.
        // v2.2.6 (code 226): new quick panel — a two-finger swipe-up on Start (mirrors
        //   quick search's swipe-down) opens a WP-tile-style bottom sheet: coloured
        //   toggle tiles for wi-fi, bluetooth, flashlight, dnd, airplane mode, location,
        //   and rotation lock, plus wide live-tile-style bars for media/ring volume,
        //   brightness, and a tap-to-cycle screen-timeout row — all built with
        //   permissions already declared or special-access grants (WRITE_SETTINGS,
        //   notification policy access) confirmed absent from Play Console's
        //   restricted-permissions list, so no new Play Console declaration is needed.
        //   Also adds a read-only device-status card (battery, storage, connectivity,
        //   next alarm) on the feed's glance tab, toggleable from Personalize · feed &
        //   glance alongside the feed page itself (fixing a pre-existing bug where
        //   turning "show feed page" off from inside the feed's own settings left no
        //   way to turn it back on).
        // v2.2.7 (code 227): quick panel visual pass — toggles/sliders restyled as
        //   Start-tile-style coloured tiles (a mini Start screen, not a generic
        //   settings sheet), volume/brightness bars show the icon inside the bar
        //   instead of beside it, media/ring get distinct icons, alarm volume
        //   removed; people tile drops its flip (the back face's photo rendered as
        //   an inconsistent square crop) for a small physics-based cluster of
        //   circular bubbles that drift and bounce off each other and the tile
        //   edges, swapping photos on an instant cut rather than a fade; dropped
        //   the deprecated statusBarColor/navigationBarColor theme attributes
        //   (Android 15 edge-to-edge no-ops) and switched the cutout mode to
        //   "always"; upgraded AGP 8.9.1 -> 9.0.1 (Kotlin -> 2.2.10, Room -> 2.8.4,
        //   Gradle -> 9.1.0) with optimized resource shrinking enabled — the fix
        //   for Play Console's resource-shrinking recommendation — and fixed a
        //   real WorkManager regression the upgrade's more aggressive R8 pass
        //   introduced (a stripped InputMerger constructor silently broke every
        //   background worker's first run).
        // v2.2.8 (code 228): clock tile alarm/reminder fixes, plus the still-unreleased
        //   v2.2.7 changes above (2.2.7 never got a signed build submitted to Play, so
        //   2.2.8 supersedes it rather than being skipped). User-reported: a 2:50pm
        //   calendar meeting reminder showed up mislabeled as the tile's alarm; a follow-up
        //   fix (whitelisting Google/Samsung Clock packages) then made a real Google Clock
        //   alarm stop showing entirely whenever a sooner calendar reminder existed.
        //   Root-caused via on-device `adb shell dumpsys alarm`: AlarmManager
        //   .getNextAlarmClock() is a single system-wide "next" value with no per-app
        //   query, and calendar apps also register via setAlarmClock() (to bypass Doze),
        //   so a same-day meeting reminder routinely eclipses a real, later alarm — the
        //   whitelist attempt just went blank in that case instead of showing either.
        //   Reverted to always showing the next entry, and resolve its real source by
        //   matching the trigger time against CalendarContract.CalendarAlerts.ALARM_TIME
        //   (the reminder's actual scheduled fire time — not Instances.BEGIN, the event's
        //   start, which doesn't match for all-day events or reminders offset earlier than
        //   the event start): shows the matched event's own title when found, else falls
        //   back to "alarm / bedtime" as before. The date line under it now shows the
        //   alarm/reminder's own date rather than always today's, for one set on a
        //   different day. Verified end-to-end on a physical device at each step.
        versionCode = 228
        versionName = "2.2.8"
    }

    if (keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode shrinking + obfuscation. Rules in proguard-rules.pro
            // supplement the AGP defaults and each library's consumer rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    // The baseline-profile plugin auto-creates the `benchmarkRelease` (the
    // non-debuggable, profileable, debug-signed target the Macrobenchmark runs
    // against) and `nonMinifiedRelease` (profile generation) variants from
    // `release`, so no manual benchmark build type is needed here (S26).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Every TileShell module is wired into the app so the whole graph
    // compiles as part of :app:assembleDebug.
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:start"))
    implementation(project(":feature:livetiles"))
    implementation(project(":feature:applist"))
    implementation(project(":feature:personalize"))
    implementation(project(":feature:system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Installs the bundled baseline profile on first run (S26).
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    // The baseline profile artifact consumed at build time.
    baselineProfile(project(":macrobenchmark"))
}

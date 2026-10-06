# Android Auto support — v5.1.0 plan

User-requested. TileShell Music (the music hub's local library, podcasts and radio) shows up as a
media app on the Android Auto car screen. 5.0.0 is already live on Play, so this ships as **5.1.0
(versionCode 510)**, on its own, because Play reviews Android Auto apps separately.

Designs were shown and approved in the 2026-10-06 session (root tabs, browse list, now playing,
podcasts/radio with favorites + recently played, podcast episode list).

## Scope

Tabs (root of the browse tree, max 4):

| Tab | Contents |
|---|---|
| Library | Artists → albums → songs; Albums → songs; All songs. Local files only. |
| Playlists | The device's audio playlists (`LocalMusicLibrary`) → songs. |
| Podcasts | Group "Favorites" (subscribed shows) then group "Recently played" (last 10 episodes, `MusicRecents`). A show opens its episode list. |
| Radio | Group "Favorites" (`RadioFavoritesStore`) then group "Recently played" (last 10 stations). A station plays directly (no deeper level). |

Podcast show screen: "Play latest", then episodes newest first (title, age, length, state: progress /
"played" / new), 20 per page with a "Load more episodes" row. Tapping an episode plays it and queues
the rest (older ones for a back-catalogue pick, newer ones after "Play latest"). Resumes a partly
played episode. Now playing shows back 15 s / forward 30 s for podcasts and hides seek for radio.

Voice: "play <show/artist/song/station> on TileShell" via `onPlayFromSearch`.

Out of scope: video, custom Compose UI (Android Auto draws everything), Android Automotive OS
(built-in car OS), downloads, search tab, a "recently played songs" list (Library only; can add).

## Design decisions

1. **Library: `androidx.media:media` (`MediaBrowserServiceCompat` + `MediaSessionCompat`).** Not Media3.
   `LocalMusicPlayer` is a `MediaPlayer`-based singleton with gapless/overlap logic; moving to
   `MediaLibraryService` + ExoPlayer would replace it. Not worth it for this release.
2. **One session, owned by the browser service.** Android Auto needs the browser service's session
   token, and browsing must work before anything plays. Today `LocalMusicPlaybackService` creates
   the (framework) `MediaSession` only when playback starts. New `MusicMediaSession` process
   singleton holds one `MediaSessionCompat`; the new browser service sets its token; the existing
   playback service stops creating its own session and reads the shared one for the notification
   (`MediaStyle.setMediaSession(token)`). All callbacks stay as they are and gain play-by-id and
   search.
3. **Browse data stays in the stores that already exist.** `LocalMusicLibrary`, `PodcastStore`,
   `MusicRecents` (`RecentEpisodeCodec`, `FavoriteStationCodec`), `RadioFavoritesStore`,
   `PodcastFeed` (RSS episodes). The browser service only maps them to `MediaItem`s. The phone hub
   and the car therefore always agree.
4. **Media ids are a pure, tested format** (`AutoMediaId`): `root`, `tab:library`, `artist:<id>`,
   `album:<id>`, `track:<id>`, `playlist:<id>`, `show:<feedUrl>`, `episode:<feedUrl>|<guid>`,
   `station:<id>`. Parse/encode lives in `:feature:livetiles` with unit tests; no Android types.
5. **Exported service, locked down.** `onGetRoot` accepts only Android Auto
   (`com.google.android.projection.gearhead`), the system, and our own package (a
   `PackageValidator`-style allow-list on `clientPackageName` + signature for Google's packages);
   anything else gets `null`. Browse/play from an untrusted caller must be impossible.
6. **Never an empty list.** No audio permission, empty library, offline podcasts, feed failure →
   one non-playable message item ("Allow music access in TileShell", "No connection"). This is the
   most common Android Auto rejection reason.
7. **Grouping.** Favorites / Recently played use the content-style group-title extra
   (`CONTENT_STYLE_GROUP_TITLE_HINT`). If a head unit ignores it, the order still reads correctly.
8. **Async feeds.** `onLoadChildren` uses `result.detach()` and sends the list when the RSS
   fetch returns; `loading…` isn't needed because Android Auto shows its own spinner while detached.

## Work plan

1. **Dependency and manifest**
   - `gradle/libs.versions.toml`: add `androidx.media:media`; use in `:feature:livetiles`.
   - `feature/livetiles/src/main/AndroidManifest.xml`: new `AutoMediaBrowserService`
     (`exported="true"`, `MediaBrowserService` intent filter, `foregroundServiceType="mediaPlayback"`);
     `<meta-data com.google.android.gms.car.application → @xml/automotive_app_desc>`;
     `res/xml/automotive_app_desc.xml` with `<uses name="media"/>`. Merges into `:app`.
   - Optional kill switch like the others: `tileshell.androidAuto=true` in `gradle.properties`;
     false drops the service/meta-data (manifest in `app/src/androidAuto/`, merged only while on).
2. **`MusicMediaSession` + `AutoMediaBrowserService`** (new, `:feature:livetiles`)
   - Session with callbacks: `onPlay/Pause/Stop/SkipToNext/SkipToPrevious/SeekTo/FastForward/Rewind`
     (reuse the player calls from `LocalMusicPlaybackService`), `onPlayFromMediaId`,
     `onPlayFromSearch`, `onCustomAction` (favourite).
   - `PlaybackStateCompat` actions include `PLAY_FROM_MEDIA_ID`, `PLAY_FROM_SEARCH`, skip,
     seek (not for radio, matching `canSeek()`), and the 15/30 s rewind/forward for podcasts.
   - Metadata: title, artist, album, duration, art as content URI or bitmap (no file paths).
   - Queue (`setQueue`) for the "next" list.
3. **Refactor `LocalMusicPlaybackService`** to use the shared session instead of creating its own.
   The notification, noisy-output handling and audio-device callback stay as they are.
4. **Browse tree** (`AutoBrowseTree.kt`): builders per tab/level from section above; pure id
   mapping and paging helpers unit-tested; stores read through small interfaces so tests don't
   need Android.
5. **Play paths**: map a media id to `LocalMusicPlayer.playQueue / playEpisodes / playStation`.
   Episode pick builds the queue per the rules above; show "Play latest" uses the newest episode.
   Resume position for partly played episodes uses whatever `playAt(startAtMs)` already supports.
6. **Voice search** (`onPlayFromSearch`): match query against favorite shows → favorite stations →
   artists → albums → songs; empty query plays recent/shuffled library. Pure matcher, unit-tested.
7. **Docs**: About ("android auto"), personalize guide line, `docs/DECISIONS.md` entry,
   privacy policy (no new data; the car reads what the phone already holds — one line saying so),
   `docs/PLAY_STORE.md` release notes for 5.1.0, CLAUDE.md status.
8. **Version bump**: `versionCode = 510`, `versionName = "5.1.0"` with the usual changelog comment.

## Tests

Unit (JUnit, pure):
- `AutoMediaIdTest`: round-trip all id kinds, malformed input, feed URLs containing `|` or `:`.
- `AutoBrowseTreeTest`: tab order, group titles, "recently played" ≤ 10, empty-state message items,
  episode paging (20 + load-more id), played/progress state labels.
- `AutoVoiceSearchTest`: precedence and tie-breaks.
- `CallerValidatorTest`: Android Auto / system / own package allowed, others rejected.
- Episode queue builder: back-catalogue pick vs "play latest" direction.

On device (not unit-testable):
- Desktop Head Unit (DHU) with the debug build: Android Auto → developer settings → "Unknown
  sources"; start head unit server; check every tab, drill-down, play, skip, seek, voice, empty
  states (deny audio permission; airplane mode), and that the phone hub follows what the car plays.
- Real car or head unit once before the production rollout.

## Play Console checklist (5.1.0)

- [ ] Add **Android Auto** to the app's form factors (Release → Setup → Advanced settings).
- [ ] Release to **internal / closed testing** first; confirm the Android for Cars review result
      before promoting to production.
- [ ] Store listing: mention Android Auto support (music, podcasts, radio); add a car screenshot
      if the form factor asks for one.
- [ ] Reviewer notes: this is a launcher whose music hub is a full media app; where to find the
      audio permission prompt; steps to test (play a song, a favorite show, a favorite station).
- [ ] Data safety: no new data collected; re-read the form, no change expected.
- [ ] Foreground service `mediaPlayback` declaration already given for 5.0.0 — confirm it still
      matches (same service types).
- [ ] Release notes (≤500 chars) in `docs/PLAY_STORE.md`.

## Risks

| Risk | Mitigation |
|---|---|
| Android for Cars review rejects or delays | Separate release; test on DHU against the quality list first; clear empty states |
| Session ownership refactor breaks the notification / lock screen | Keep the notification code as is, only change where the token comes from; check on the phone before release |
| Exported service abused | Caller allow-list + signature check; unit-tested |
| Group titles ignored on older Android Auto | Order is still Favorites → Recently played |
| RSS fetch slow in the car | `detach()` + cache the last episode page per show |
| Samsung "Separate app sound" pins audio (see memory) | Not related to code; note for car testing |

## Order of work (one session each, per the project rules)

1. Dependency, manifest, shared session + service refactor (phone behaviour unchanged, verified).
2. Browse tree + media ids + tests (Library, Playlists).
3. Podcasts and Radio (favorites, recently played, episode list, paging, queues).
4. Voice search, custom actions, caller validation, tests.
5. DHU pass, docs, version bump, release notes, Play checklist.

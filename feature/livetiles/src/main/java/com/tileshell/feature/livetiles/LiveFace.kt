package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import kotlin.random.Random

/**
 * The animated live faces a tile can carry, keyed off the tile's monoline icon
 * key (the prototype `app.live` field — see `data.js` / `tiles.js`).
 *
 * Phase 5 lands these incrementally: S20 implements [CLOCK]; S21 adds [WEATHER]
 * and [CALENDAR]; S22 adds [MAIL] and [MESSAGES]; S23 adds [PEOPLE] and [PHOTOS];
 * S24 adds [MUSIC]. An unmapped icon key resolves to `null`; that tile renders a
 * static glyph unless its app has an active notification, in which case the Start
 * grid overlays the generic notification face (see `NotificationTileFace`).
 *
 * [WEATHER], [CALENDAR], [MAIL], [MESSAGES] and [PEOPLE] carry opt-in data
 * (coarse location / calendar read / notification access / contacts), and
 * [PHOTOS] needs a picked selection; the face composables fall back to the static
 * glyph when the permission is denied or no data is available — the mapping here
 * stays permission-agnostic.
 *
 * @property flips whether the face has a back side that the flip scheduler may
 *   turn to. Photos (a cross-fade slideshow) never flip; the clock does. People
 *   also never flips — its own bubble cluster cross-fades and pops
 *   independently (user-requested removal of the flip, see DECISIONS.md).
 *   Sticky note never flips either — the whole tile front already is the note,
 *   there's no separate "back" content worth revealing.
 */
enum class LiveFace(val flips: Boolean) {
    CLOCK(flips = true),
    WEATHER(flips = true),
    CALENDAR(flips = true),
    MAIL(flips = true),
    MESSAGES(flips = true),
    PEOPLE(flips = false),
    PHOTOS(flips = false),
    MUSIC(flips = true),
    BATTERY(flips = true),
    ALARM(flips = true),
    MOONPHASE(flips = true),
    // Only the checklist itself is useful at a glance — the flip's back face
    // (a bare "x left" count) added nothing worth the flip (user-requested:
    // "tasks will have only one face. no back face. only task list should be
    // shown").
    TASKS(flips = false),
    NOTES(flips = true),
    STICKYNOTE(flips = false),
    FLASHLIGHT(flips = false),
    COUNTDOWN(flips = true),
    STEPS(flips = false),
    SPORTS(flips = true),
    STOCK(flips = true),
    COMMODITY(flips = true),
    CALENDAR_SYSTEM(flips = true),
    ;

    companion object {
        /**
         * The live face for a tile, or `null` when it should stay static. Small
         * tiles are always static (prototype `t.size!=='small'`); larger tiles
         * map by icon key. Only the faces implemented this session are returned.
         */
        fun forIconKey(iconKey: String?, size: TileSize): LiveFace? {
            if (size == TileSize.SMALL) return null
            return when (iconKey) {
                "clock" -> CLOCK
                "weather" -> WEATHER
                "calendar" -> CALENDAR
                "mail" -> MAIL
                "messages" -> MESSAGES
                "people" -> PEOPLE
                "photos" -> PHOTOS
                "music" -> MUSIC
                "battery" -> BATTERY
                "alarm" -> ALARM
                "moonphase" -> MOONPHASE
                "tasks" -> TASKS
                "notepad" -> NOTES
                "stickynote" -> STICKYNOTE
                "flashlight" -> FLASHLIGHT
                "countdown" -> COUNTDOWN
                "steps" -> STEPS
                "sports" -> SPORTS
                "stock" -> STOCK
                "commodity" -> COMMODITY
                "calsys" -> CALENDAR_SYSTEM
                else -> null
            }
        }
    }
}

/**
 * Picks the next tile to flip from the currently visible flippable tiles —
 * the prototype's "flip one random live tile every ~2.6 s". Returns `null` when
 * there is nothing to flip. Pure so the scheduler's choice is unit-testable.
 */
fun pickFlipTarget(liveIds: List<String>, random: Random = Random.Default): String? =
    if (liveIds.isEmpty()) null else liveIds[random.nextInt(liveIds.size)]

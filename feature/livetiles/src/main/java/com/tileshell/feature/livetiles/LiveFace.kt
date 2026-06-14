package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import kotlin.random.Random

/**
 * The animated live faces a tile can carry, keyed off the tile's monoline icon
 * key (the prototype `app.live` field — see `data.js` / `tiles.js`).
 *
 * Phase 5 lands these incrementally: S20 implements [CLOCK]; S21 adds [WEATHER]
 * and [CALENDAR]; mail/messages/people/photos/music (S22+) join later. Until
 * then an unmapped icon key resolves to `null` and the tile renders as a static
 * glyph.
 *
 * [WEATHER] and [CALENDAR] carry opt-in data (coarse location / calendar read);
 * the face composables fall back to the static glyph when the permission is
 * denied or no data is cached — the mapping here stays permission-agnostic.
 *
 * @property flips whether the face has a back side that the flip scheduler may
 *   turn to. Photos (a cross-fade slideshow) never flip; the clock does.
 */
enum class LiveFace(val flips: Boolean) {
    CLOCK(flips = true),
    WEATHER(flips = true),
    CALENDAR(flips = true),
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

package com.tileshell.core.data.clock

/**
 * A timer set pinned to Start. The tile is a blank-package live tile whose
 * `activityName` carries the set's id, so each set has its own tile.
 */
object TimerSetTile {
    const val ICON_KEY = "timerset"
    private const val PREFIX = "timerset:"

    fun encode(setId: String): String = PREFIX + setId

    /** The set's id, or null for anything that isn't a timer set tile's `activityName`. */
    fun decode(activityName: String?): String? =
        activityName?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.ifEmpty { null }
}

private const val SET_MARK = '@'

/** The id a session gets when it runs the saved set [setId] ("set-1700000000000@1699999999999"). */
fun sessionIdForSet(startedAt: Long, setId: String): String = "set-$startedAt$SET_MARK$setId"

/** The saved set this session is running, or null for a timer or a set started before sets were tracked. */
fun Session.setId(): String? = id.substringAfter(SET_MARK, "").ifEmpty { null }

/** The running (or paused) session of the saved set [setId], if there is one. */
fun sessionForSet(sessions: List<Session>, setId: String?): Session? =
    if (setId == null) null else sessions.firstOrNull { it.setId() == setId }

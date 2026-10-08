package com.tileshell.core.data.clock

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.tileshell.core.data.R

/**
 * Plays the step-end chime itself, when "sound as well" is on. A notification
 * channel's sound is not enough: Android drops it on an update of an ongoing
 * notification, while notifications are off, or when it was rate-limited, so the
 * chime would stay silent. This plays on the alarm stream, like the buzz is an
 * alarm's vibration, so it follows the phone's alarm volume and do-not-disturb
 * rules. Never throws.
 */
object ClockChime {
    fun play(context: Context, finish: Boolean) {
        if (!ClockStore.soundToo(context)) return
        runCatching {
            val app = context.applicationContext
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            app.resources.openRawResourceFd(R.raw.task_reminder).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            var plays = if (finish) 2 else 1
            player.setOnCompletionListener {
                if (--plays > 0) runCatching { it.seekTo(0); it.start() } else it.release()
            }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }
    }
}

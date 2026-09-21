package com.tileshell.feature.livetiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the music hub's local-library playback
 * ([LocalMusicPlayer]) alive and controllable once the hub screen itself is
 * closed: a lock-screen/notification-visible [MediaSession] with previous/
 * play-pause/next/stop actions, mirroring a real music app. Started
 * on-demand by [LocalMusicPlayer.playQueue] the moment a track begins
 * playing; stops itself the instant playback truly ends ([LocalPlayback
 * .track] goes null — the user's own "stop", the notification being swiped
 * away, or a genuine audio-focus loss) — it never sits running with nothing
 * to show.
 *
 * Deliberately does **not** own the [android.media.MediaPlayer] itself —
 * that stays the single, app-process-wide instance in [LocalMusicPlayer],
 * unaffected by whether this service happens to be alive right now; this
 * service is purely the OS-visible face of whatever [LocalMusicPlayer.state]
 * already says, and audio-focus handling lives in [LocalMusicPlayer] too
 * (it already has the calling [android.content.Context] on hand there).
 */
class LocalMusicPlaybackService : Service() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        val mediaSession = MediaSession(this, "TileShellMusicHub").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { LocalMusicPlayer.togglePlayPause() }
                override fun onPause() { LocalMusicPlayer.togglePlayPause() }
                override fun onSkipToNext() { LocalMusicPlayer.next(this@LocalMusicPlaybackService) }
                override fun onSkipToPrevious() { LocalMusicPlayer.previous(this@LocalMusicPlaybackService) }
                override fun onStop() { LocalMusicPlayer.release() }
            })
            isActive = true
        }
        session = mediaSession

        scope.launch {
            LocalMusicPlayer.state.collect { playback ->
                val track = playback.track
                if (track == null) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }

                val art = runCatching {
                    LocalMusicLibrary.loadAlbumArt(this@LocalMusicPlaybackService, track.albumId, 512)
                }.getOrNull()

                mediaSession.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
                        .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
                        .build(),
                )
                mediaSession.setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(
                            PlaybackState.ACTION_PLAY_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackState.ACTION_STOP,
                        )
                        .setState(
                            if (playback.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                            1f,
                        )
                        .build(),
                )

                startForeground(NOTIFICATION_ID, buildNotification(track, playback.playing, mediaSession, art))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> LocalMusicPlayer.togglePlayPause()
            ACTION_NEXT -> LocalMusicPlayer.next(this)
            ACTION_PREVIOUS -> LocalMusicPlayer.previous(this)
            ACTION_STOP -> LocalMusicPlayer.release()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun buildNotification(
        track: LocalTrack,
        playing: Boolean,
        mediaSession: MediaSession,
        art: Bitmap?,
    ): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setLargeIcon(art)
            .setContentIntent(contentIntent)
            .setOngoing(playing)
            .setDeleteIntent(actionPendingIntent(ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification_prev),
                    "previous",
                    actionPendingIntent(ACTION_PREVIOUS),
                ).build(),
            )
            .addAction(
                if (playing) {
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification_pause),
                        "pause",
                        actionPendingIntent(ACTION_PLAY_PAUSE),
                    ).build()
                } else {
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification_play),
                        "play",
                        actionPendingIntent(ACTION_PLAY_PAUSE),
                    ).build()
                },
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification_next),
                    "next",
                    actionPendingIntent(ACTION_NEXT),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification_stop),
                    "stop",
                    actionPendingIntent(ACTION_STOP),
                ).build(),
            )
            .build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, LocalMusicPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager = NotificationManagerCompat.from(this)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.music_playback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "music_hub_playback"
        const val NOTIFICATION_ID = 4201
        const val ACTION_PLAY_PAUSE = "com.tileshell.music.PLAY_PAUSE"
        const val ACTION_NEXT = "com.tileshell.music.NEXT"
        const val ACTION_PREVIOUS = "com.tileshell.music.PREVIOUS"
        const val ACTION_STOP = "com.tileshell.music.STOP"
    }
}

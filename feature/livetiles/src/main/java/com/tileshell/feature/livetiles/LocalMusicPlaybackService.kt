package com.tileshell.feature.livetiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Foreground service that keeps the music hub's playback ([LocalMusicPlayer]
 * — a local track, a podcast episode, or a radio stream) alive and
 * controllable once the hub screen itself is closed: a lock-screen/
 * notification-visible [MediaSession] with previous/play-pause/next/stop
 * actions, mirroring a real music app. Started on-demand by
 * [LocalMusicPlayer]'s own `play*` methods the moment something begins
 * playing; stops itself the instant playback truly ends ([LocalPlayback
 * .item] goes null — the user's own "stop", the notification being swiped
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

    private var session: MediaSessionCompat? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * Pauses when the audio output goes away — Bluetooth headset/speaker
     * disconnecting, or wired headphones unplugged (user-requested) — so
     * playback doesn't carry on out of the phone's loudspeaker. Android's
     * standard signal for exactly this; it can't be declared in the manifest,
     * so it lives for as long as this service does, i.e. while something is
     * loaded. Paused, not stopped: the notification stays so it resumes with
     * one tap. The next resume reopens the player, so it plays on whatever
     * output is there by then (the car, the Buds) rather than the phone.
     */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) LocalMusicPlayer.pauseForNoisy()
        }
    }

    /**
     * Bluetooth or headphones connecting (or going) while paused: the paused
     * player would otherwise resume on the output it was opened on.
     * Registering reports the current devices once, which is skipped.
     */
    private var devicesPrimed = false
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (!devicesPrimed) {
                devicesPrimed = true
                return
            }
            if (added.any { it.isSink }) LocalMusicPlayer.onOutputsChanged()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.isSink }) LocalMusicPlayer.onOutputsChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        val mediaSession = MusicMediaSession.get(this).apply { isActive = true }
        session = mediaSession
        // A system broadcast, so NOT_EXPORTED is correct and still receives it.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        getSystemService(AudioManager::class.java)
            ?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        scope.launch {
            LocalMusicPlayer.state.collect { playback ->
                val item = playback.item
                if (item == null) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }

                val art = loadArt(item)

                mediaSession.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.subtitle)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.durationMs.takeIf { it > 0 } ?: LocalMusicPlayer.durationMs())
                        .apply { if (art != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art) }
                        .build(),
                )
                mediaSession.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setActions(
                            MusicMediaSession.IDLE_ACTIONS or
                                PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_STOP or
                                (if (LocalMusicPlayer.canSeek()) {
                                    PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND
                                } else {
                                    0L
                                }),
                        )
                        .setState(
                            if (playback.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                            // Real position, so the lock screen / Bluetooth device
                            // shows progress; unknown for a live radio stream.
                            if (LocalMusicPlayer.canSeek()) LocalMusicPlayer.positionMs() else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                            if (playback.playing) 1f else 0f,
                        )
                        .build(),
                )

                startForeground(NOTIFICATION_ID, buildNotification(item, playback.playing, mediaSession, art))
            }
        }
    }

    /** Local art comes from MediaStore; a podcast episode/show or radio
     * station's art is a plain remote image URL. Null (no artwork) is a
     * perfectly normal outcome, not a failure — plenty of feeds/stations
     * simply don't declare one. */
    private suspend fun loadArt(item: PlayableAudio): Bitmap? = when (item) {
        is PlayableAudio.Local ->
            runCatching { LocalMusicLibrary.loadAlbumArt(this, item.track.albumId, 512) }.getOrNull()
        is PlayableAudio.Episode ->
            loadRemoteBitmap(item.episode.imageUrl ?: item.show.artworkUrl)
        is PlayableAudio.RadioStream ->
            loadRemoteBitmap(item.station.faviconUrl)
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
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback) }
        scope.cancel()
        // The session is shared with the car's browser service, so it is
        // switched off here, not released.
        session?.apply {
            setPlaybackState(MusicMediaSession.idleState())
            isActive = false
        }
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun buildNotification(
        item: PlayableAudio,
        playing: Boolean,
        mediaSession: MediaSessionCompat,
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
            .setContentTitle(item.title)
            .setContentText(item.subtitle)
            .setLargeIcon(art)
            .setContentIntent(contentIntent)
            .setOngoing(playing)
            .setDeleteIntent(actionPendingIntent(ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken.token as MediaSession.Token)
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

/** Plain `HttpURLConnection`-free remote image fetch (a podcast/show/station's
 * own artwork URL) for the notification/lock-screen art — null on any failure
 * (unreachable, not an image, etc.), which just means no artwork shows. */
private suspend fun loadRemoteBitmap(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
    }
}

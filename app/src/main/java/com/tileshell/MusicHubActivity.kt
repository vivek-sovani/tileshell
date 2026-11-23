package com.tileshell

import android.app.ActivityManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.graphics.toArgb
import com.tileshell.core.data.HubTasks
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.PinResult
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.core.design.HubOrigin
import com.tileshell.core.design.LocalHinge
import com.tileshell.core.design.TileAccents
import com.tileshell.feature.livetiles.MusicHubScreen
import com.tileshell.feature.start.rememberHubAccentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The music + videos hub as an app of its own (experiment, switch `tileshell.hubTasks`).
 *
 * It runs in its own task (its own taskAffinity, declared in `app/src/hubTasks/AndroidManifest.xml`), so
 * Android's recents and the dock's recents button list it as "music + videos" with its own icon and colour,
 * like a music app. It hosts the same [MusicHubScreen] the layer inside [MainActivity] does; playback stays in
 * `LocalMusicPlaybackService`, so audio does not care which window the hub is in. Back finishes the task
 * (TileShell's home is underneath); Home leaves it in recents.
 *
 * Everything the hub takes from Start is small: the theme and the accent come from the settings (the accent by
 * the same rule as Start's, [rememberHubAccentId]), "pin to start" writes the layout directly, and the page to
 * open on arrives as an intent extra ([HubTasks.EXTRA_PAGE]).
 */
class MusicHubActivity : ComponentActivity() {

    private val page = mutableStateOf<String?>(null)
    private var closing = false
    private val pinMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tile tap records the tile the hub would grow out of; here there is no Start behind the hub, so drop it.
        HubOrigin.take()
        page.value = intent.getStringExtra(HubTasks.EXTRA_PAGE)
        setTaskDescription(taskDescriptionFor(TileAccents.forId("orange").toArgb()))

        val settingsRepository = SettingsRepository.create(applicationContext)
        setContent {
            val settings by produceState(LauncherSettings()) {
                settingsRepository.settings.collect { value = it }
            }
            val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.dark
            val accentId = rememberHubAccentId(settings)
            // Slides up on open and down on close, as a hub does when it has no tile to grow from.
            var visible by remember { mutableStateOf(false) }
            val window = remember { HubWindow(this@MusicHubActivity, lifecycleScope) }
            LaunchedEffect(Unit) {
                visible = true
                // Opaque once it is open, so recents gets a picture of it (see HubWindow).
                window.makeOpaqueSoon { !closing }
            }
            LaunchedEffect(visible) {
                if (!visible && closing) {
                    delay(HUB_CLOSE_MS)
                    finish()
                }
            }
            StatusBarVisibilityEffect(hide = settings.hideStatusBar)
            CompositionLocalProvider(LocalHinge provides rememberHinge(this@MusicHubActivity)) {
                MusicHubScreen(
                    visible = visible,
                    dark = dark,
                    accentId = accentId,
                    onDismiss = {
                        closing = true
                        lifecycleScope.launch {
                            window.makeTranslucent()
                            visible = false
                        }
                    },
                    rightHalf = false,
                    initialPage = page.value,
                    onPinApps = ::pinMediaApps,
                    pinMessages = pinMessages,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Opened again from a tile, the glance card or the notification: go to the page asked for, if any.
        intent.getStringExtra(HubTasks.EXTRA_PAGE)?.let { page.value = it }
    }

    /** "pin" on the hub's apps page: the same write as `StartViewModel.pinMediaApps`, on the main page. */
    private fun pinMediaApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val layout = LayoutRepository.create(applicationContext)
            val exists = layout.tiles.first().any { it is TileModel.App && it.iconKey == "mediaapps" }
            val result = if (!exists && layout.addDefaultTile("mediaapps", null)) PinResult.PINNED else PinResult.ALREADY_ON_START
            pinMessages.tryEmit(if (result == PinResult.PINNED) "pinned music + videos apps to start" else "already on start")
        }
    }

    /** The recents card: the hub's own label, icon and colour (not TileShell's). */
    @Suppress("DEPRECATION")
    private fun taskDescriptionFor(color: Int): ActivityManager.TaskDescription {
        val label = getString(R.string.music_hub_label)
        val opaque = color or 0xFF000000.toInt()
        return if (Build.VERSION.SDK_INT >= 28) {
            ActivityManager.TaskDescription.Builder()
                .setLabel(label)
                .setIcon(R.drawable.ic_hub_music)
                .setPrimaryColor(opaque)
                .build()
        } else {
            val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            ActivityManager.TaskDescription(label, icon, opaque)
        }
    }
}

private const val HUB_CLOSE_MS = 330L

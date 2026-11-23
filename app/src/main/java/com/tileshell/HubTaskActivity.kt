package com.tileshell

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import com.tileshell.core.data.HubEntries
import com.tileshell.core.data.HubTasks
import com.tileshell.core.data.WeatherTile
import com.tileshell.core.design.HubOrigin
import com.tileshell.core.design.TileAccents
import kotlinx.coroutines.launch

/**
 * One hub (any but music, which has [MusicHubActivity]) as an app of its own: its own Android task, so its own
 * card in Android's recents with the hub's name and colour (experiment, switch `tileshell.hubTasks`).
 *
 * It is the whole Start screen hosted in a task of its own, with the hub opened over it ([MainActivity]'s
 * `isHubTask` switches off the home-screen prompts). That way every hub keeps working exactly as it does on Start,
 * including the sheets they open (notes, tasks, pinning, the other hubs, which stack as layers in this task).
 * When the last hub layer closes (Back), the task finishes and you are back where you came from. Declared as a
 * "document" (`documentLaunchMode=intoExisting`) with a data URI per hub, so each hub is its own task and opening
 * the same hub again brings its card to the front instead of starting another.
 */
class HubTaskActivity : MainActivity() {

    override val isHubTask: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tile tap records the tile a hub would grow out of; the hub here is not over that tile.
        HubOrigin.take()
        openFrom(intent)
        // Finish once the hub has been closed (Back), not before it has opened.
        lifecycleScope.launch {
            var seenOpen = false
            startViewModel.anyHubOpen.collect { open ->
                if (open) seenOpen = true else if (seenOpen) finish()
            }
        }
    }

    override fun onHubTaskIntent(intent: Intent) {
        openFrom(intent)
    }

    private fun openFrom(intent: Intent) {
        val id = intent.getStringExtra(HubTasks.EXTRA_HUB_ID) ?: intent.data?.host ?: return
        val hub = HubEntries.ALL.firstOrNull { it.id == id } ?: return
        setTaskDescription(taskDescriptionFor(hub.id, hub.label, TileAccents.forId(hub.colorId).toArgb()))
        val page = intent.getStringExtra(HubTasks.EXTRA_HUB_PAGE)
        if (id == "weather") {
            val name = intent.getStringExtra(HubTasks.EXTRA_WEATHER_NAME)
            val place = if (name != null && intent.hasExtra(HubTasks.EXTRA_WEATHER_LAT)) {
                WeatherTile.Location.Fixed(
                    intent.getDoubleExtra(HubTasks.EXTRA_WEATHER_LAT, 0.0),
                    intent.getDoubleExtra(HubTasks.EXTRA_WEATHER_LON, 0.0),
                    name,
                )
            } else {
                null
            }
            startViewModel.openWeatherHub(place)
        } else {
            startViewModel.openHub(id, page)
        }
    }

    /** The recents card: the hub's name, colour and own icon (a plate in its colour with its glyph; see `ic_hub_*`). */
    @Suppress("DEPRECATION")
    private fun taskDescriptionFor(id: String, label: String, color: Int): ActivityManager.TaskDescription {
        val opaque = color or 0xFF000000.toInt()
        // Named, not looked up by name at run time, so resource shrinking keeps each icon.
        val icon = when (id) {
            "people" -> R.drawable.ic_hub_people
            "weather" -> R.drawable.ic_hub_weather
            "calendar" -> R.drawable.ic_hub_calendar
            "productivity" -> R.drawable.ic_hub_productivity
            "clock" -> R.drawable.ic_hub_clock
            "battery" -> R.drawable.ic_hub_battery
            "money" -> R.drawable.ic_hub_money
            "markets" -> R.drawable.ic_hub_markets
            "sports" -> R.drawable.ic_hub_sports
            "news" -> R.drawable.ic_hub_news
            "shopping" -> R.drawable.ic_hub_shopping
            "health" -> R.drawable.ic_hub_health
            "panchang" -> R.drawable.ic_hub_panchang
            else -> R.mipmap.ic_launcher
        }
        if (Build.VERSION.SDK_INT >= 28) {
            return ActivityManager.TaskDescription.Builder().setLabel(label).setIcon(icon).setPrimaryColor(opaque).build()
        }
        // Before 28 only a bitmap is accepted: the hub's first letter on a plate of its colour.
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = opaque })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 60f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label.first().uppercaseChar().toString(), size / 2f, size / 2f + 21f, text)
        return ActivityManager.TaskDescription(label, bitmap, opaque)
    }
}

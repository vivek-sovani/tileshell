package com.tileshell

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tileshell.feature.livetiles.widget.EXTRA_OPEN_HUB

/**
 * A "personalize" tile for the system's quick settings panel (add it from the panel's edit mode, under TileShell).
 * Tapping it closes the panel and opens TileShell's personalize sheet. It is a button, not a switch: always off.
 */
class PersonalizeTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.qs_personalize_label)
            updateTile()
        }
    }

    override fun onClick() {
        val open = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_HUB, OPEN_PERSONALIZE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // On the lock screen the panel asks to unlock first.
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(open)
            }
        }
    }

    companion object {
        /** The [EXTRA_OPEN_HUB] value that opens the personalize sheet. */
        const val OPEN_PERSONALIZE = "personalize"
    }
}

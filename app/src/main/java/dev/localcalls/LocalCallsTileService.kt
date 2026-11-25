package dev.localcalls

import android.app.role.RoleManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LocalCallsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Don't let someone holding the locked phone switch the blocker from the lock screen.
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        Prefs.setEnabled(this, !Prefs.isEnabled(this))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = Prefs.isEnabled(this)
        val roleHeld = getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(
            when {
                on && !roleHeld -> R.string.tile_needs_setup
                on -> R.string.tile_on
                else -> R.string.tile_off
            }
        )
        tile.updateTile()
    }
}

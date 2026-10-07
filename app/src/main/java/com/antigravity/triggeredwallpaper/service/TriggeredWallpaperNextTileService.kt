package com.antigravity.triggeredwallpaper.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.antigravity.triggeredwallpaper.R
import com.antigravity.triggeredwallpaper.receiver.WallpaperActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TriggeredWallpaperNextTileService : TileService() {

	private val serviceScope = CoroutineScope(Dispatchers.Main)

	override fun onStartListening() {
		super.onStartListening()
		updateTileState()
	}

	override fun onClick() {
		super.onClick()
		serviceScope.launch {
			WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
			updateTileState()
		}
	}

	private fun updateTileState() {
		val tile = qsTile ?: return
		tile.state = Tile.STATE_INACTIVE
		tile.label = getString(R.string.quick_tile_next_label)
		tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_next)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			tile.subtitle = "Tap to cycle"
		}
		tile.updateTile()
	}
}

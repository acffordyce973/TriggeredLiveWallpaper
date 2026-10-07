package com.antigravity.triggeredwallpaper.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.antigravity.triggeredwallpaper.R
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.receiver.WallpaperActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TriggeredWallpaperTileService : TileService() {

	private val serviceScope = CoroutineScope(Dispatchers.Main)

	override fun onStartListening() {
		super.onStartListening()
		updateTileState()
	}

	override fun onClick() {
		super.onClick()
		val repository = WallpaperRepository.getInstance(applicationContext)
		val objCurrentSettings = repository.flowSettings.value
		val boolNewActiveState = !objCurrentSettings.boolQuickTileActive

		serviceScope.launch {
			repository.updateAppSettings { current ->
				current.copy(boolQuickTileActive = boolNewActiveState)
			}
			WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
			updateTileState()
		}
	}

	private fun updateTileState() {
		val tile = qsTile ?: return
		val repository = WallpaperRepository.getInstance(applicationContext)
		val objSettings = repository.flowSettings.value
		val listSets = repository.flowFolderSets.value
		val objTileSet = listSets.firstOrNull { it.boolIsEnabled && it.boolQuickTileEnabled }

		val boolIsActive = objSettings.boolQuickTileActive && objTileSet != null
		tile.state = if (boolIsActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		tile.label = objTileSet?.stringName ?: getString(R.string.quick_tile_override_label)
		tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_override)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			tile.subtitle = if (boolIsActive) "Override Active (Locked)" else if (objTileSet != null) "Tap to activate" else "No set assigned"
		}
		tile.updateTile()
	}
}

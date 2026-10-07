package com.antigravity.triggeredwallpaper

import android.app.Application
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager

class TriggeredWallpaperApp : Application() {
	override fun onCreate() {
		super.onCreate()
		DeviceStateManager.initialize(this)
	}
}

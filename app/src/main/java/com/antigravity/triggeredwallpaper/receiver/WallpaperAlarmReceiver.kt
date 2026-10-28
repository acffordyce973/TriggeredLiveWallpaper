package com.antigravity.triggeredwallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.scheduler.WallpaperAlarmScheduler
import com.antigravity.triggeredwallpaper.scheduler.WallpaperGeofenceScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperAlarmReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val stringAction = intent.action ?: return

		if (stringAction == Intent.ACTION_BOOT_COMPLETED ||
			stringAction == Intent.ACTION_MY_PACKAGE_REPLACED ||
			stringAction == "android.intent.action.QUICKBOOT_POWERON"
		) {
			val repository = WallpaperRepository.getInstance(context)
			val listGeos = repository.flowGeofences.value
			val listSets = repository.flowFolderSets.value
			WallpaperGeofenceScheduler.synchronizeGeofences(context, listGeos, listSets)
			WallpaperAlarmScheduler.scheduleNextAlarm(context)
			return
		}

		if (stringAction == Intent.ACTION_POWER_CONNECTED || stringAction == Intent.ACTION_POWER_DISCONNECTED) {
			val repository = WallpaperRepository.getInstance(context)
			val objCurrentState = DeviceStateManager.getDeviceState(context)
			val listSets = repository.flowFolderSets.value
			val listGeos = repository.flowGeofences.value
			val objHomeSet = ConditionEvaluator.evaluateMatchingSet(
				objCurrentState,
				WallpaperTarget.HOME,
				listSets,
				listGeos,
				repository.flowSettings.value
			)
			if (objHomeSet?.boolChangeOnCharging != true) {
				return
			}
		}

		// Only proceed if Triggered Live Wallpaper is currently the active wallpaper
		if (!WallpaperAlarmScheduler.isLiveWallpaperActive(context)) {
			WallpaperAlarmScheduler.cancelAlarm(context)
			return
		}

		val asyncPendingResult = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				WallpaperActionReceiver.advanceToNextImage(context, boolForceNext = true)
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				asyncPendingResult.finish()
			}
		}
	}
}

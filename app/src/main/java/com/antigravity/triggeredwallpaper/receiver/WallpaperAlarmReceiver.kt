package com.antigravity.triggeredwallpaper.receiver

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.PowerManager
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.notification.WallpaperNotificationManager
import com.antigravity.triggeredwallpaper.scheduler.WallpaperAlarmScheduler
import com.antigravity.triggeredwallpaper.service.TriggeredWallpaperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperAlarmReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val stringAction = intent.action
		if (stringAction == Intent.ACTION_POWER_CONNECTED || stringAction == Intent.ACTION_POWER_DISCONNECTED) {
			val repository = WallpaperRepository.getInstance(context)
			val objCurrentState = DeviceStateManager.getDeviceState(context)
			val listSets = repository.flowFolderSets.value
			val listGeos = repository.flowGeofences.value
			val objHomeSet = ConditionEvaluator.evaluateMatchingSet(objCurrentState, WallpaperTarget.HOME, listSets, listGeos, repository.flowSettings.value)
			if (objHomeSet?.boolChangeOnCharging != true) {
				return
			}
		}

		val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
		val wakeLock = powerManager?.newWakeLock(
			PowerManager.PARTIAL_WAKE_LOCK,
			"TriggeredWallpaper:AlarmWakeLock"
		)
		wakeLock?.acquire(10000L) // 10 seconds timeout

		val asyncPendingResult = goAsync()

		CoroutineScope(Dispatchers.IO).launch {
			try {
				WallpaperActionReceiver.advanceToNextImage(context, boolForceNext = true)
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				try {
					wakeLock?.release()
				} catch (e: Exception) {
					e.printStackTrace()
				}
				asyncPendingResult.finish()
			}
		}
	}
}

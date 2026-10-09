package com.antigravity.triggeredwallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.ImageItem
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.notification.WallpaperNotificationManager
import com.antigravity.triggeredwallpaper.scheduler.WallpaperAlarmScheduler
import com.antigravity.triggeredwallpaper.service.TriggeredWallpaperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WallpaperActionReceiver : BroadcastReceiver() {

	companion object {
		const val ACTION_NEXT_IMAGE = "com.antigravity.triggeredwallpaper.ACTION_NEXT_IMAGE"

		private val advanceMutex = Mutex()
		private var dtLastAdvanceMs = 0L

		suspend fun advanceToNextImage(
			context: Context,
			boolForceNext: Boolean = true
		) {
			advanceMutex.withLock {
				val longNow = System.currentTimeMillis()
				if (boolForceNext && (longNow - dtLastAdvanceMs < 400L)) {
					return
				}
				dtLastAdvanceMs = longNow

				val repository = WallpaperRepository.getInstance(context)
				val listSets = repository.flowFolderSets.value
				val listGeos = repository.flowGeofences.value
				val objSettings = repository.flowSettings.value
				val objCurrentState = DeviceStateManager.getDeviceState(context)

				// 1. Evaluate Home Screen
				val objMatchedHomeSet = ConditionEvaluator.evaluateMatchingSet(
					objCurrentState,
					WallpaperTarget.HOME,
					listSets,
					listGeos,
					objSettings
				)
				val boolHomeSetChanged = objMatchedHomeSet?.stringId != objSettings.stringCurrentHomeSetId
				val boolNeedNewHomeImage = objMatchedHomeSet != null &&
					(boolForceNext || boolHomeSetChanged || objSettings.stringCurrentHomeImageUri == null)

				var objNextHomeImage: ImageItem? = null
				if (boolNeedNewHomeImage) {
					objNextHomeImage = repository.selectNextImage(objMatchedHomeSet)
				}

				// 2. Evaluate Lock Screen (if separated)
				var objNextLockImage: ImageItem? = null
				var objMatchedLockSet: com.antigravity.triggeredwallpaper.model.FolderSet? = null
				if (objSettings.boolSeparateHomeAndLock) {
					objMatchedLockSet = ConditionEvaluator.evaluateMatchingSet(
						objCurrentState,
						WallpaperTarget.LOCK,
						listSets,
						listGeos,
						objSettings
					)
					val boolLockSetChanged = objMatchedLockSet?.stringId != objSettings.stringCurrentLockSetId
					val boolNeedNewLockImage = objMatchedLockSet != null &&
						(boolForceNext || boolLockSetChanged || objSettings.stringCurrentLockImageUri == null)

					if (boolNeedNewLockImage) {
						objNextLockImage = repository.selectNextImage(objMatchedLockSet)
					}
				} else {
					objMatchedLockSet = objMatchedHomeSet
					objNextLockImage = objNextHomeImage
				}

				if (objNextHomeImage != null || objNextLockImage != null || boolHomeSetChanged) {
					repository.updateAppSettings { current ->
						val stringNewHomeSetId = objMatchedHomeSet?.stringId ?: current.stringCurrentHomeSetId
						val stringNewHomeUri = objNextHomeImage?.stringUri ?: current.stringCurrentHomeImageUri
						val stringNewHomeName = objNextHomeImage?.stringDisplayName ?: current.stringCurrentHomeImageName
						val stringNewHomePath = objNextHomeImage?.stringFolderPath ?: current.stringCurrentHomeImagePath

						val stringNewLockSetId = if (objSettings.boolSeparateHomeAndLock) {
							objMatchedLockSet?.stringId ?: current.stringCurrentLockSetId
						} else {
							stringNewHomeSetId
						}
						val stringNewLockUri = if (objSettings.boolSeparateHomeAndLock) {
							objNextLockImage?.stringUri ?: current.stringCurrentLockImageUri
						} else {
							stringNewHomeUri
						}
						val stringNewLockName = if (objSettings.boolSeparateHomeAndLock) {
							objNextLockImage?.stringDisplayName ?: current.stringCurrentLockImageName
						} else {
							stringNewHomeName
						}
						val stringNewLockPath = if (objSettings.boolSeparateHomeAndLock) {
							objNextLockImage?.stringFolderPath ?: current.stringCurrentLockImagePath
						} else {
							stringNewHomePath
						}

						current.copy(
							stringCurrentHomeSetId = stringNewHomeSetId,
							stringCurrentHomeImageUri = stringNewHomeUri,
							stringCurrentHomeImageName = stringNewHomeName,
							stringCurrentHomeImagePath = stringNewHomePath,
							stringCurrentLockSetId = stringNewLockSetId,
							stringCurrentLockImageUri = stringNewLockUri,
							stringCurrentLockImageName = stringNewLockName,
							stringCurrentLockImagePath = stringNewLockPath
						)
					}
				}

				// 3. Notify running WallpaperService to update display immediately
				context.sendBroadcast(Intent(TriggeredWallpaperService.ACTION_REFRESH_FRAME))

				// 4. Update persistent notification
				WallpaperNotificationManager.showOrUpdateNotification(context)

				// 5. Reset interval alarm
				WallpaperAlarmScheduler.scheduleNextAlarm(context)
			}
		}
	}

	override fun onReceive(context: Context, intent: Intent) {
		val stringAction = intent.action ?: return
		if (stringAction == ACTION_NEXT_IMAGE || stringAction == TriggeredWallpaperService.ACTION_TRIGGER_NEXT) {
			val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
			val wakeLock = powerManager?.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK,
				"TriggeredWallpaper:ActionWakeLock"
			)
			wakeLock?.acquire(10000L)

			val asyncPendingResult = goAsync()

			CoroutineScope(Dispatchers.IO).launch {
				try {
					advanceToNextImage(context, boolForceNext = true)
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
}

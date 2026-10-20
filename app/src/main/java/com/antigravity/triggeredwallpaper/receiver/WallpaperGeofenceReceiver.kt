package com.antigravity.triggeredwallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperGeofenceReceiver : BroadcastReceiver() {

	companion object {
		const val ACTION_GEOFENCE_TRANSITION = "com.antigravity.triggeredwallpaper.ACTION_GEOFENCE_TRANSITION"
		private const val TAG = "WallpaperGeofenceReceiver"
	}

	override fun onReceive(context: Context, intent: Intent) {
		val geofencingEvent = try {
			GeofencingEvent.fromIntent(intent)
		} catch (e: Exception) {
			Log.w(TAG, "Error parsing GeofencingEvent: ${e.message}")
			null
		} ?: return

		if (geofencingEvent.hasError()) {
			Log.w(TAG, "GeofencingEvent has error code: ${geofencingEvent.errorCode}")
			return
		}

		val intTransition = geofencingEvent.geofenceTransition
		if (intTransition == Geofence.GEOFENCE_TRANSITION_ENTER || intTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
			val stringType = if (intTransition == Geofence.GEOFENCE_TRANSITION_ENTER) "ENTER" else "EXIT"
			val stringIds = geofencingEvent.triggeringGeofences?.joinToString { it.requestId } ?: "unknown"
			Log.i(TAG, "Geofence transition ($stringType) for: $stringIds")

			// Update triggering location in DeviceStateManager if provided
			geofencingEvent.triggeringLocation?.let { loc ->
				DeviceStateManager.updateLocation(loc)
			}

			val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
			val wakeLock = powerManager?.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK,
				"TriggeredWallpaper:GeofenceWakeLock"
			)
			wakeLock?.acquire(10000L)

			val asyncPendingResult = goAsync()
			CoroutineScope(Dispatchers.IO).launch {
				try {
					WallpaperActionReceiver.advanceToNextImage(context, boolForceNext = false)
				} catch (e: Exception) {
					Log.e(TAG, "Failed advancing image on geofence transition: ${e.message}", e)
				} finally {
					try {
						wakeLock?.release()
					} catch (_: Exception) {}
					asyncPendingResult.finish()
				}
			}
		}
	}
}

package com.antigravity.triggeredwallpaper.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.receiver.WallpaperGeofenceReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object WallpaperGeofenceScheduler {

	private const val TAG = "WallpaperGeofenceScheduler"
	private const val REQUEST_CODE_GEOFENCE = 5050

	private fun getGeofencePendingIntent(context: Context): PendingIntent {
		val intentGeofence = Intent(context, WallpaperGeofenceReceiver::class.java).apply {
			action = WallpaperGeofenceReceiver.ACTION_GEOFENCE_TRANSITION
		}
		val intFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		} else {
			PendingIntent.FLAG_UPDATE_CURRENT
		}
		return PendingIntent.getBroadcast(context, REQUEST_CODE_GEOFENCE, intentGeofence, intFlags)
	}

	fun synchronizeGeofences(context: Context, listGeofences: List<GeofenceArea>) {
		val boolHasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
		if (!boolHasFine) {
			Log.d(TAG, "Cannot synchronize geofences: fine location permission not granted")
			return
		}

		try {
			val geofencingClient = LocationServices.getGeofencingClient(context)
			val pendingIntent = getGeofencePendingIntent(context)

			if (listGeofences.isEmpty()) {
				geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
					Log.d(TAG, "Removed all registered geofences")
				}
				return
			}

			val listPlayGeofences = listGeofences.map { objGeo ->
				Geofence.Builder()
					.setRequestId(objGeo.stringId)
					.setCircularRegion(
						objGeo.doubleLatitude,
						objGeo.doubleLongitude,
						objGeo.floatRadiusMeters.coerceAtLeast(50f)
					)
					.setExpirationDuration(Geofence.NEVER_EXPIRE)
					.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
					.build()
			}

			val request = GeofencingRequest.Builder()
				.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
				.addGeofences(listPlayGeofences)
				.build()

			geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
				try {
					geofencingClient.addGeofences(request, pendingIntent)
						.addOnSuccessListener {
							Log.i(TAG, "Successfully registered ${listPlayGeofences.size} geofence(s) with LocationServices")
						}
						.addOnFailureListener { e ->
							Log.w(TAG, "Failed to register geofences with LocationServices: ${e.message}")
						}
				} catch (e: SecurityException) {
					Log.w(TAG, "SecurityException registering geofences: ${e.message}")
				}
			}
		} catch (e: Throwable) {
			Log.w(TAG, "Google Play Services geofencing unavailable or failed: ${e.message}")
		}
	}

	fun removeAllGeofences(context: Context) {
		try {
			val geofencingClient = LocationServices.getGeofencingClient(context)
			val pendingIntent = getGeofencePendingIntent(context)
			geofencingClient.removeGeofences(pendingIntent)
		} catch (e: Throwable) {
			Log.d(TAG, "Could not remove geofences: ${e.message}")
		}
	}
}

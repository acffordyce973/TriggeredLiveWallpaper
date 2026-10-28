package com.antigravity.triggeredwallpaper.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.model.FolderSet
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

	/**
	 * Synchronizes active geofences with Google Play Services GeofencingClient.
	 *
	 * Only registers geofences if location permissions are granted and at least
	 * one enabled FolderSet uses geofence conditions, preventing unnecessary background tracking.
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 * :param listGeofences: List of configured geofence areas.
	 * :type listGeofences: List<GeofenceArea>
	 * :param listFolderSets: Optional list of folder sets; if null, reads from repository.
	 * :type listFolderSets: List<FolderSet>?
	 */
	fun synchronizeGeofences(
		context: Context,
		listGeofences: List<GeofenceArea>,
		listFolderSets: List<FolderSet>? = null
	) {
		val boolHasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
		if (!boolHasFine) {
			Log.d(TAG, "Cannot synchronize geofences: fine location permission not granted")
			return
		}

		// Only register geofences if there are active folder sets that actually use geofences
		val listSetsToCheck = listFolderSets ?: try {
			WallpaperRepository.getInstance(context).flowFolderSets.value
		} catch (e: Exception) {
			emptyList()
		}
		val boolAnySetUsesGeofence = listSetsToCheck.any { it.boolIsEnabled && it.geofenceCondition.boolEnabled }

		if (!boolAnySetUsesGeofence || listGeofences.isEmpty()) {
			removeAllGeofences(context)
			return
		}

		// Filter to only geofences actually referenced by active sets
		val setReferencedIds = listSetsToCheck
			.filter { it.boolIsEnabled && it.geofenceCondition.boolEnabled }
			.map { it.geofenceCondition.stringGeofenceId }
			.toSet()

		val listActiveGeofences = listGeofences.filter { setReferencedIds.contains(it.stringId) }
		if (listActiveGeofences.isEmpty()) {
			removeAllGeofences(context)
			return
		}

		try {
			val geofencingClient = LocationServices.getGeofencingClient(context)
			val pendingIntent = getGeofencePendingIntent(context)

			val listPlayGeofences = listActiveGeofences.map { objGeo ->
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
							Log.i(TAG, "Successfully registered ${listPlayGeofences.size} active geofence(s) with LocationServices")
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

	/**
	 * Removes all registered geofences from Google Play Services.
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 */
	fun removeAllGeofences(context: Context) {
		try {
			val geofencingClient = LocationServices.getGeofencingClient(context)
			val pendingIntent = getGeofencePendingIntent(context)
			geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
				Log.d(TAG, "Removed all registered geofences")
			}
		} catch (e: Throwable) {
			Log.d(TAG, "Could not remove geofences: ${e.message}")
		}
	}
}

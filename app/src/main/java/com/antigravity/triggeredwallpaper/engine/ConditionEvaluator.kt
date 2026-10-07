package com.antigravity.triggeredwallpaper.engine

import android.location.Location
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.ChargingCondition
import com.antigravity.triggeredwallpaper.model.DeviceState
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget

object ConditionEvaluator {

	fun evaluateMatchingSet(
		objDeviceState: DeviceState,
		targetScreen: WallpaperTarget,
		listFolderSets: List<FolderSet>,
		listGeofences: List<GeofenceArea>,
		objSettings: AppSettings? = null
	): FolderSet? {
		// 1. Quick Settings Tile override:
		// When active, force the enabled set marked with Quick Tile trigger regardless of other conditions and triggers
		if (objSettings?.boolQuickTileActive == true) {
			val objQuickTileSet = listFolderSets.firstOrNull { objSet ->
				objSet.boolIsEnabled &&
					objSet.boolQuickTileEnabled &&
					(objSet.wallpaperTarget == WallpaperTarget.BOTH || objSet.wallpaperTarget == targetScreen)
			}
			if (objQuickTileSet != null) {
				return objQuickTileSet
			}
		}

		val boolIgnoreUnsupported = objSettings?.boolIgnoreUnsupportedConditions ?: true
		val listMatchingSets = listFolderSets.filter { objSet ->
			isSetMatching(objSet, objDeviceState, targetScreen, listGeofences, boolIgnoreUnsupported)
		}

		if (listMatchingSets.isEmpty()) {
			return null
		}

		// Calculate specificity score so more specific rules take precedence over generic (ANY) rules
		return listMatchingSets.maxByOrNull { objSet ->
			calculateSpecificityScore(objSet)
		}
	}

	fun isSetMatching(
		objSet: FolderSet,
		objDeviceState: DeviceState,
		targetScreen: WallpaperTarget,
		listGeofences: List<GeofenceArea>,
		boolIgnoreUnsupported: Boolean = true
	): Boolean {
		// 0. Enabled check
		if (!objSet.boolIsEnabled) {
			return false
		}

		// 1. Target screen
		if (objSet.wallpaperTarget != WallpaperTarget.BOTH && objSet.wallpaperTarget != targetScreen) {
			return false
		}

		// 2. Orientation
		if (objSet.orientationCondition != OrientationCondition.ANY &&
			objSet.orientationCondition != objDeviceState.orientationCondition
		) {
			return false
		}

		// 3. Fold posture
		// If the device does not have fold/hinge hardware and ignore unsupported is enabled, ignore fold condition
		val boolSkipFoldCheck = boolIgnoreUnsupported && !objDeviceState.boolSupportsFold
		if (!boolSkipFoldCheck) {
			if (objSet.foldCondition != FoldCondition.ANY &&
				objSet.foldCondition != objDeviceState.foldCondition
			) {
				return false
			}
		}

		// 4. Charging
		when (objSet.chargingCondition) {
			ChargingCondition.CHARGING -> if (!objDeviceState.boolCharging) return false
			ChargingCondition.DISCHARGING -> if (objDeviceState.boolCharging) return false
			ChargingCondition.ANY -> Unit
		}

		// 5. Time window
		if (!objSet.timeWindowCondition.isWithinTimeWindow(objDeviceState.intHour, objDeviceState.intMinute)) {
			return false
		}

		// 6. Days of week (1 = Mon .. 7 = Sun)
		if (objSet.listDaysOfWeek.isNotEmpty() && !objSet.listDaysOfWeek.contains(objDeviceState.intDayOfWeek)) {
			return false
		}

		// 7. Months (1 = Jan .. 12 = Dec)
		if (objSet.listMonths.isNotEmpty() && !objSet.listMonths.contains(objDeviceState.intMonth)) {
			return false
		}

		// 8. Geofence
		if (objSet.geofenceCondition.boolEnabled) {
			val boolSkipLocationCheck = boolIgnoreUnsupported && !objDeviceState.boolSupportsLocation
			if (!boolSkipLocationCheck) {
				val objTargetArea = listGeofences.find { it.stringId == objSet.geofenceCondition.stringGeofenceId }
				if (objTargetArea != null && objDeviceState.doubleLatitude != null && objDeviceState.doubleLongitude != null) {
					val arrayDistances = FloatArray(1)
					Location.distanceBetween(
						objDeviceState.doubleLatitude,
						objDeviceState.doubleLongitude,
						objTargetArea.doubleLatitude,
						objTargetArea.doubleLongitude,
						arrayDistances
					)
					val floatDistanceMeters = arrayDistances[0]
					val boolInside = floatDistanceMeters <= objTargetArea.floatRadiusMeters
					if (boolInside != objSet.geofenceCondition.boolInside) {
						return false
					}
				}
			}
		}

		return true
	}

	fun calculateSpecificityScore(objSet: FolderSet): Int {
		var intScore = 0
		if (objSet.wallpaperTarget != WallpaperTarget.BOTH) intScore += 10
		if (objSet.orientationCondition != OrientationCondition.ANY) intScore += 10
		if (objSet.foldCondition != FoldCondition.ANY) intScore += 15
		if (objSet.chargingCondition != ChargingCondition.ANY) intScore += 5
		if (objSet.timeWindowCondition.boolEnabled) intScore += 20
		if (objSet.listDaysOfWeek.isNotEmpty()) intScore += 10
		if (objSet.listMonths.isNotEmpty()) intScore += 15
		if (objSet.geofenceCondition.boolEnabled) intScore += 25
		return intScore
	}
}

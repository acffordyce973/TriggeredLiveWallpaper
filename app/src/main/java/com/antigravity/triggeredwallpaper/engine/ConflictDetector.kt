package com.antigravity.triggeredwallpaper.engine

import com.antigravity.triggeredwallpaper.model.ConflictInfo
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.TimeWindowCondition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget

object ConflictDetector {

	fun detectConflicts(listFolderSets: List<FolderSet>): List<ConflictInfo> {
		val listConflicts = mutableListOf<ConflictInfo>()
		val listActiveSets = listFolderSets.filter { it.boolIsEnabled }

		for (intIndexA in listActiveSets.indices) {
			for (intIndexB in intIndexA + 1 until listActiveSets.size) {
				val objSetA = listActiveSets[intIndexA]
				val objSetB = listActiveSets[intIndexB]

				val stringConflictReason = checkPairConflict(objSetA, objSetB)
				if (stringConflictReason != null) {
					listConflicts.add(
						ConflictInfo(
							stringSetIdA = objSetA.stringId,
							stringSetNameA = objSetA.stringName,
							stringSetIdB = objSetB.stringId,
							stringSetNameB = objSetB.stringName,
							stringReason = stringConflictReason
						)
					)
				}
			}
		}

		return listConflicts
	}

	fun checkPairConflict(objSetA: FolderSet, objSetB: FolderSet): String? {
		// 1. Check target screen overlap
		val boolScreenOverlaps = objSetA.wallpaperTarget == WallpaperTarget.BOTH ||
			objSetB.wallpaperTarget == WallpaperTarget.BOTH ||
			objSetA.wallpaperTarget == objSetB.wallpaperTarget

		if (!boolScreenOverlaps) {
			return null
		}

		// 2. Check Orientation overlap
		val boolOrientationOverlaps = objSetA.orientationCondition == com.antigravity.triggeredwallpaper.model.OrientationCondition.ANY ||
			objSetB.orientationCondition == com.antigravity.triggeredwallpaper.model.OrientationCondition.ANY ||
			objSetA.orientationCondition == objSetB.orientationCondition

		if (!boolOrientationOverlaps) {
			return null
		}

		// 3. Check Fold condition overlap
		val boolFoldOverlaps = objSetA.foldCondition == com.antigravity.triggeredwallpaper.model.FoldCondition.ANY ||
			objSetB.foldCondition == com.antigravity.triggeredwallpaper.model.FoldCondition.ANY ||
			objSetA.foldCondition == objSetB.foldCondition

		if (!boolFoldOverlaps) {
			return null
		}

		// 4. Check Charging condition overlap
		val boolChargingOverlaps = objSetA.chargingCondition == com.antigravity.triggeredwallpaper.model.ChargingCondition.ANY ||
			objSetB.chargingCondition == com.antigravity.triggeredwallpaper.model.ChargingCondition.ANY ||
			objSetA.chargingCondition == objSetB.chargingCondition

		if (!boolChargingOverlaps) {
			return null
		}

		// 5. Check Time window overlap
		val boolTimeOverlaps = checkTimeWindowOverlap(objSetA.timeWindowCondition, objSetB.timeWindowCondition)
		if (!boolTimeOverlaps) {
			return null
		}

		// 6. Check Geofence condition overlap
		val boolGeofenceOverlaps = checkGeofenceOverlap(objSetA, objSetB)
		if (!boolGeofenceOverlaps) {
			return null
		}

		// If all dimensions overlap, there is a conflict!
		val listReasons = mutableListOf<String>()
		if (objSetA.orientationCondition != com.antigravity.triggeredwallpaper.model.OrientationCondition.ANY ||
			objSetB.orientationCondition != com.antigravity.triggeredwallpaper.model.OrientationCondition.ANY
		) {
			listReasons.add("Orientation overlap (${objSetA.orientationCondition} vs ${objSetB.orientationCondition})")
		}
		if (objSetA.timeWindowCondition.boolEnabled || objSetB.timeWindowCondition.boolEnabled) {
			val stringTimeA = if (objSetA.timeWindowCondition.boolEnabled) {
				String.format("%02d:%02d-%02d:%02d", objSetA.timeWindowCondition.intStartHour, objSetA.timeWindowCondition.intStartMinute, objSetA.timeWindowCondition.intEndHour, objSetA.timeWindowCondition.intEndMinute)
			} else "All Day"
			val stringTimeB = if (objSetB.timeWindowCondition.boolEnabled) {
				String.format("%02d:%02d-%02d:%02d", objSetB.timeWindowCondition.intStartHour, objSetB.timeWindowCondition.intStartMinute, objSetB.timeWindowCondition.intEndHour, objSetB.timeWindowCondition.intEndMinute)
			} else "All Day"
			listReasons.add("Time window overlap ($stringTimeA vs $stringTimeB)")
		}
		if (objSetA.foldCondition != com.antigravity.triggeredwallpaper.model.FoldCondition.ANY ||
			objSetB.foldCondition != com.antigravity.triggeredwallpaper.model.FoldCondition.ANY
		) {
			listReasons.add("Fold posture overlap")
		}
		if (objSetA.chargingCondition != com.antigravity.triggeredwallpaper.model.ChargingCondition.ANY ||
			objSetB.chargingCondition != com.antigravity.triggeredwallpaper.model.ChargingCondition.ANY
		) {
			listReasons.add("Charging state overlap")
		}
		if (objSetA.geofenceCondition.boolEnabled || objSetB.geofenceCondition.boolEnabled) {
			listReasons.add("Geofence condition overlap")
		}

		val stringDetails = if (listReasons.isEmpty()) "Identical default/all-matching conditions" else listReasons.joinToString(", ")
		return "Overlapping conditions on ${objSetA.wallpaperTarget}: $stringDetails"
	}

	fun checkTimeWindowOverlap(objTimeA: TimeWindowCondition, objTimeB: TimeWindowCondition): Boolean {
		if (!objTimeA.boolEnabled || !objTimeB.boolEnabled) {
			return true // At least one applies at any time
		}

		// Check hour-by-hour (1440 minutes in a day)
		for (intMinuteOfDay in 0 until 1440) {
			val intHour = intMinuteOfDay / 60
			val intMinute = intMinuteOfDay % 60
			if (objTimeA.isWithinTimeWindow(intHour, intMinute) && objTimeB.isWithinTimeWindow(intHour, intMinute)) {
				return true
			}
		}
		return false
	}

	fun checkGeofenceOverlap(objSetA: FolderSet, objSetB: FolderSet): Boolean {
		val objGeoA = objSetA.geofenceCondition
		val objGeoB = objSetB.geofenceCondition

		if (!objGeoA.boolEnabled || !objGeoB.boolEnabled) {
			return true
		}

		if (objGeoA.stringGeofenceId == objGeoB.stringGeofenceId) {
			// Same geofence: they overlap only if both trigger inside or both trigger outside
			return objGeoA.boolInside == objGeoB.boolInside
		}

		// Different geofences could potentially be entered at the same time or disjoint
		return true
	}
}

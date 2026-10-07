package com.antigravity.triggeredwallpaper.model

import kotlinx.serialization.Serializable

@Serializable
enum class WallpaperTarget {
	HOME,
	LOCK,
	BOTH
}

@Serializable
enum class OrientationCondition {
	ANY,
	PORTRAIT,
	LANDSCAPE
}

@Serializable
enum class FoldCondition {
	ANY,
	FOLDED,
	UNFOLDED,
	HALF_OPENED
}

@Serializable
enum class ChargingCondition {
	ANY,
	CHARGING,
	DISCHARGING
}

@Serializable
data class TimeWindowCondition(
	val boolEnabled: Boolean = false,
	val intStartHour: Int = 10,
	val intStartMinute: Int = 0,
	val intEndHour: Int = 22,
	val intEndMinute: Int = 0
) {
	fun isWithinTimeWindow(intCurrentHour: Int, intCurrentMinute: Int): Boolean {
		if (!boolEnabled) return true
		val intCurrentMinutes = intCurrentHour * 60 + intCurrentMinute
		val intStartMinutes = intStartHour * 60 + intStartMinute
		val intEndMinutes = intEndHour * 60 + intEndMinute

		return if (intStartMinutes <= intEndMinutes) {
			intCurrentMinutes in intStartMinutes..intEndMinutes
		} else {
			// Window wraps past midnight (e.g. 22:00 to 06:00)
			intCurrentMinutes >= intStartMinutes || intCurrentMinutes <= intEndMinutes
		}
	}
}

@Serializable
data class GeofenceCondition(
	val boolEnabled: Boolean = false,
	val stringGeofenceId: String = "",
	val boolInside: Boolean = true
)

@Serializable
data class GeofenceArea(
	val stringId: String,
	val stringName: String,
	val doubleLatitude: Double,
	val doubleLongitude: Double,
	val floatRadiusMeters: Float = 150f
)

@Serializable
enum class OverlayPosition {
	BOTTOM,
	TOP,
	CENTER
}

@Serializable
data class FolderSet(
	val stringId: String,
	val stringName: String,
	val boolIsEnabled: Boolean = true,
	val listFolderUris: List<String> = emptyList(),
	val boolQuickTileEnabled: Boolean = false,
	val intIntervalMinutes: Int = 0, // 0 = disabled, > 0 = auto change every N minutes
	val boolChangeOnRotate: Boolean = false,
	val boolChangeOnFold: Boolean = false,
	val boolChangeOnCharging: Boolean = false,
	val boolChangeOnDoubleTap: Boolean = true,
	val boolChangeOnScreenOn: Boolean = false,
	val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH,
	val orientationCondition: OrientationCondition = OrientationCondition.ANY,
	val foldCondition: FoldCondition = FoldCondition.ANY,
	val chargingCondition: ChargingCondition = ChargingCondition.ANY,
	val timeWindowCondition: TimeWindowCondition = TimeWindowCondition(),
	val geofenceCondition: GeofenceCondition = GeofenceCondition(),
	val listDaysOfWeek: List<Int> = emptyList(), // 1 = Mon .. 7 = Sun, empty = any
	val listMonths: List<Int> = emptyList() // 1 = Jan .. 12 = Dec, empty = any
)

@Serializable
data class ImageItem(
	val stringUri: String,
	val stringDisplayName: String,
	val stringFolderPath: String
)

@Serializable
data class AppSettings(
	val boolSeparateHomeAndLock: Boolean = true,
	val boolShowLabelOverlay: Boolean = true,
	val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
	val intOverlayVerticalOffsetDp: Int = 0,
	val boolRandomizeImages: Boolean = true,
	val boolShowPersistentNotification: Boolean = true,
	val boolIgnoreUnsupportedConditions: Boolean = true,
	val boolQuickTileActive: Boolean = false,
	val stringCurrentHomeSetId: String? = null,
	val stringCurrentLockSetId: String? = null,
	val stringCurrentHomeImageUri: String? = null,
	val stringCurrentLockImageUri: String? = null,
	val stringCurrentHomeImageName: String? = null,
	val stringCurrentLockImageName: String? = null,
	val stringCurrentHomeImagePath: String? = null,
	val stringCurrentLockImagePath: String? = null,
	val stringBackupFilenameTemplate: String = "TriggeredWallpaper_Config_{yyyyMMdd_HHmmss}"
)

fun formatBackupFilename(stringTemplate: String): String {
	var stringClean = stringTemplate.trim()
	while (stringClean.endsWith(".json", ignoreCase = true)) {
		stringClean = stringClean.substring(0, stringClean.length - 5).trim()
	}
	if (stringClean.isEmpty()) {
		stringClean = "TriggeredWallpaper"
	}

	val dateNow = java.util.Date()

	stringClean = stringClean
		.replace("{datetime}", java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(dateNow))
		.replace("{date}", java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(dateNow))
		.replace("{time}", java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(dateNow))

	val regexPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}")
	val matcher = regexPattern.matcher(stringClean)
	val sb = StringBuffer()
	while (matcher.find()) {
		val stringPattern = matcher.group(1) ?: ""
		val stringReplacement = try {
			java.text.SimpleDateFormat(stringPattern, java.util.Locale.US).format(dateNow)
		} catch (e: Exception) {
			stringPattern
		}
		matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(stringReplacement))
	}
	matcher.appendTail(sb)
	stringClean = sb.toString().trim()

	while (stringClean.endsWith(".json", ignoreCase = true)) {
		stringClean = stringClean.substring(0, stringClean.length - 5).trim()
	}

	if (stringClean.isEmpty()) {
		stringClean = "TriggeredWallpaper"
	}

	return "$stringClean.json"
}

data class DeviceState(
	val orientationCondition: OrientationCondition,
	val foldCondition: FoldCondition,
	val boolCharging: Boolean,
	val intHour: Int,
	val intMinute: Int,
	val intDayOfWeek: Int = 1, // 1 = Mon .. 7 = Sun
	val intMonth: Int = 1, // 1 = Jan .. 12 = Dec
	val doubleLatitude: Double? = null,
	val doubleLongitude: Double? = null,
	val boolScreenLocked: Boolean = false,
	val boolSupportsFold: Boolean = false,
	val boolSupportsLocation: Boolean = false
)

data class ConflictInfo(
	val stringSetIdA: String,
	val stringSetNameA: String,
	val stringSetIdB: String,
	val stringSetNameB: String,
	val stringReason: String
)

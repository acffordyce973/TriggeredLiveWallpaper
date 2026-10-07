package com.antigravity.triggeredwallpaper.ui.main

import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.ConflictDetector
import com.antigravity.triggeredwallpaper.model.ChargingCondition
import com.antigravity.triggeredwallpaper.model.DeviceState
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import com.antigravity.triggeredwallpaper.model.TimeWindowCondition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

	@Test
	fun testConflictDetectionWithOverlappingSets() {
		val setA = FolderSet(
			stringId = "set_a",
			stringName = "Day Landscapes",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.LANDSCAPE,
			timeWindowCondition = TimeWindowCondition(boolEnabled = true, intStartHour = 10, intEndHour = 22)
		)

		val setB = FolderSet(
			stringId = "set_b",
			stringName = "Afternoon Landscapes",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.LANDSCAPE,
			timeWindowCondition = TimeWindowCondition(boolEnabled = true, intStartHour = 14, intEndHour = 18)
		)

		val listConflicts = ConflictDetector.detectConflicts(listOf(setA, setB))
		assertEquals(1, listConflicts.size)
		assertTrue(listConflicts[0].stringReason.contains("Orientation overlap"))
	}

	@Test
	fun testNoConflictWithDisjointOrientation() {
		val setPortrait = FolderSet(
			stringId = "set_portrait",
			stringName = "Portrait Set",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.PORTRAIT
		)

		val setLandscape = FolderSet(
			stringId = "set_landscape",
			stringName = "Landscape Set",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.LANDSCAPE
		)

		val listConflicts = ConflictDetector.detectConflicts(listOf(setPortrait, setLandscape))
		assertEquals(0, listConflicts.size)
	}

	@Test
	fun testConditionEvaluatorSelectsMatchingOrientation() {
		val setPortrait = FolderSet(
			stringId = "set_portrait",
			stringName = "Portrait Set",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.PORTRAIT
		)

		val setLandscape = FolderSet(
			stringId = "set_landscape",
			stringName = "Landscape Set",
			wallpaperTarget = WallpaperTarget.HOME,
			orientationCondition = OrientationCondition.LANDSCAPE
		)

		val deviceStateLandscape = DeviceState(
			orientationCondition = OrientationCondition.LANDSCAPE,
			foldCondition = FoldCondition.ANY,
			boolCharging = false,
			intHour = 12,
			intMinute = 0
		)

		val matchedSet = ConditionEvaluator.evaluateMatchingSet(
			objDeviceState = deviceStateLandscape,
			targetScreen = WallpaperTarget.HOME,
			listFolderSets = listOf(setPortrait, setLandscape),
			listGeofences = emptyList()
		)

		assertNotNull(matchedSet)
		assertEquals("set_landscape", matchedSet?.stringId)
	}

	@Test
	fun testTimeWindowWrapsMidnight() {
		val nightWindow = TimeWindowCondition(
			boolEnabled = true,
			intStartHour = 22,
			intStartMinute = 0,
			intEndHour = 6,
			intEndMinute = 0
		)

		assertTrue(nightWindow.isWithinTimeWindow(23, 30))
		assertTrue(nightWindow.isWithinTimeWindow(2, 15))
		assertTrue(nightWindow.isWithinTimeWindow(6, 0))
		assertTrue(!nightWindow.isWithinTimeWindow(12, 0))
	}
}

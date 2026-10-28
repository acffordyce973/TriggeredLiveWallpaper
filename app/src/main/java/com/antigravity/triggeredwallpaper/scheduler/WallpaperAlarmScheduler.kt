package com.antigravity.triggeredwallpaper.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.receiver.WallpaperAlarmReceiver
import com.antigravity.triggeredwallpaper.service.TriggeredWallpaperService
import java.util.Calendar

object WallpaperAlarmScheduler {

	const val ACTION_ALARM_TRIGGER = "com.antigravity.triggeredwallpaper.ACTION_ALARM_TRIGGER"
	private const val REQUEST_CODE_ALARM = 4040

	/**
	 * Checks whether Triggered Live Wallpaper is currently active on the system.
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 * :return: True if active, false otherwise.
	 * :rtype: Boolean
	 */
	fun isLiveWallpaperActive(context: Context): Boolean {
		if (TriggeredWallpaperService.isEngineActive()) return true
		return try {
			val wallpaperManager = WallpaperManager.getInstance(context)
			wallpaperManager.wallpaperInfo?.packageName == context.packageName
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * Schedules the next wallpaper change alarm using battery-efficient non-wakeup alarms.
	 *
	 * Uses ELAPSED_REALTIME so device deep sleep (Doze) is not broken while the screen is off.
	 * When the device naturally wakes up or the screen is turned on, the alarm is delivered.
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 */
	fun scheduleNextAlarm(context: Context) {
		val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

		// If live wallpaper is not active on device, cancel any pending alarm
		if (!isLiveWallpaperActive(context)) {
			cancelAlarm(context)
			return
		}

		val repository = WallpaperRepository.getInstance(context)
		val listSets = repository.flowFolderSets.value

		val longNextTriggerMs = getNextTriggerTimestampMs(listSets)
		if (longNextTriggerMs == null) {
			// No interval triggers or time windows enabled; cancel alarm to prevent battery drain
			cancelAlarm(context)
			return
		}

		val intentAlarm = Intent(context, WallpaperAlarmReceiver::class.java).apply {
			action = ACTION_ALARM_TRIGGER
		}
		val pendingIntent = PendingIntent.getBroadcast(
			context,
			REQUEST_CODE_ALARM,
			intentAlarm,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		try {
			val longDeltaMs = (longNextTriggerMs - System.currentTimeMillis()).coerceAtLeast(1000L)
			val longTriggerElapsed = SystemClock.elapsedRealtime() + longDeltaMs

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, longTriggerElapsed, pendingIntent)
			} else {
				alarmManager.set(AlarmManager.ELAPSED_REALTIME, longTriggerElapsed, pendingIntent)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	/**
	 * Cancels any scheduled wallpaper alarm.
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 */
	fun cancelAlarm(context: Context) {
		val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
		val intentAlarm = Intent(context, WallpaperAlarmReceiver::class.java).apply {
			action = ACTION_ALARM_TRIGGER
		}
		val pendingIntent = PendingIntent.getBroadcast(
			context,
			REQUEST_CODE_ALARM,
			intentAlarm,
			PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
		)
		if (pendingIntent != null) {
			alarmManager.cancel(pendingIntent)
		}
	}

	/**
	 * Computes the earliest next trigger timestamp in milliseconds across enabled sets.
	 *
	 * Returns null if no sets have interval rotation or time window conditions enabled,
	 * ensuring the device does not wake up unnecessarily.
	 *
	 * :param listSets: List of folder sets to evaluate.
	 * :type listSets: List<FolderSet>
	 * :return: Next trigger epoch timestamp in milliseconds, or null if no triggers exist.
	 * :rtype: Long?
	 */
	fun getNextTriggerTimestampMs(listSets: List<FolderSet>): Long? {
		val longNowMs = System.currentTimeMillis()
		val listCandidateTimestamps = mutableListOf<Long>()

		for (objSet in listSets) {
			if (!objSet.boolIsEnabled) continue

			// 1. Check interval triggers
			if (objSet.intIntervalMinutes > 0) {
				val longIntervalMs = objSet.intIntervalMinutes * 60 * 1000L
				listCandidateTimestamps.add(longNowMs + longIntervalMs)
			}

			// 2. Check time window transitions (start hour and end hour)
			if (objSet.timeWindowCondition.boolEnabled) {
				val longStartMs = getNextOccurrenceMs(
					objSet.timeWindowCondition.intStartHour,
					objSet.timeWindowCondition.intStartMinute
				)
				val longEndMs = getNextOccurrenceMs(
					objSet.timeWindowCondition.intEndHour,
					objSet.timeWindowCondition.intEndMinute
				)
				if (longStartMs > longNowMs) listCandidateTimestamps.add(longStartMs)
				if (longEndMs > longNowMs) listCandidateTimestamps.add(longEndMs)
			}
		}

		if (listCandidateTimestamps.isEmpty()) {
			return null
		}

		return listCandidateTimestamps.minOrNull()
	}

	private fun getNextOccurrenceMs(intHour: Int, intMinute: Int): Long {
		val calendar = Calendar.getInstance().apply {
			set(Calendar.HOUR_OF_DAY, intHour)
			set(Calendar.MINUTE, intMinute)
			set(Calendar.SECOND, 0)
			set(Calendar.MILLISECOND, 0)
		}
		if (calendar.timeInMillis <= System.currentTimeMillis()) {
			calendar.add(Calendar.DAY_OF_YEAR, 1)
		}
		return calendar.timeInMillis
	}
}

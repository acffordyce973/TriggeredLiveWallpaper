package com.antigravity.triggeredwallpaper.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.receiver.WallpaperAlarmReceiver
import java.util.Calendar

object WallpaperAlarmScheduler {

	const val ACTION_ALARM_TRIGGER = "com.antigravity.triggeredwallpaper.ACTION_ALARM_TRIGGER"
	private const val REQUEST_CODE_ALARM = 4040

	fun scheduleNextAlarm(context: Context) {
		val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
		val repository = WallpaperRepository.getInstance(context)
		val listSets = repository.flowFolderSets.value

		val longNextTriggerMs = getNextTriggerTimestampMs(listSets) ?: return

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
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					if (alarmManager.canScheduleExactAlarms()) {
						alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longNextTriggerMs, pendingIntent)
					} else {
						alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longNextTriggerMs, pendingIntent)
					}
				} else {
					alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longNextTriggerMs, pendingIntent)
				}
			} else {
				alarmManager.setExact(AlarmManager.RTC_WAKEUP, longNextTriggerMs, pendingIntent)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

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

	fun getNextTriggerTimestampMs(listSets: List<FolderSet>): Long? {
		val longNowMs = System.currentTimeMillis()
		val listCandidateTimestamps = mutableListOf<Long>()

		for (objSet in listSets) {
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
			// Default fallback: check every 30 minutes in background
			return longNowMs + (30 * 60 * 1000L)
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

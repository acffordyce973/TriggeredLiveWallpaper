package com.antigravity.triggeredwallpaper.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antigravity.triggeredwallpaper.MainActivity
import com.antigravity.triggeredwallpaper.R
import com.antigravity.triggeredwallpaper.data.ImageScanner
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.receiver.WallpaperActionReceiver
import com.antigravity.triggeredwallpaper.ui.OpenImageActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object WallpaperNotificationManager {

	const val CHANNEL_ID = "triggered_wallpaper_active"
	const val NOTIFICATION_ID = 4001
	private const val REQUEST_CODE_MAIN = 100
	private const val REQUEST_CODE_OPEN = 101
	private const val REQUEST_CODE_NEXT = 102

	fun createNotificationChannel(context: Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
			// Clean up previous silent low-importance channel if present
			try {
				notificationManager?.deleteNotificationChannel("triggered_wallpaper_current")
			} catch (e: Exception) {
				// Ignore
			}

			val channel = NotificationChannel(
				CHANNEL_ID,
				"Active Wallpaper",
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = "Persistent notification with quick actions for current wallpaper"
				setShowBadge(false)
				setSound(null, null)
				enableVibration(false)
			}
			notificationManager?.createNotificationChannel(channel)
		}
	}

	fun showOrUpdateNotification(context: Context, explicitSettings: AppSettings? = null) {
		val appContext = context.applicationContext
		createNotificationChannel(appContext)

		val repository = WallpaperRepository.getInstance(appContext)
		val objSettings = explicitSettings ?: repository.flowSettings.value

		if (!objSettings.boolShowPersistentNotification) {
			cancelNotification(appContext)
			return
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
				return
			}
		}

		val stringImageName = objSettings.stringCurrentHomeImageName ?: "Triggered Wallpaper"
		val stringImagePath = objSettings.stringCurrentHomeImagePath ?: "No active wallpaper"
		val stringImageUri = objSettings.stringCurrentHomeImageUri

		// 1. Notification body click -> Open MainActivity
		val intentMain = Intent(appContext, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		val pendingIntentMain = PendingIntent.getActivity(
			appContext,
			REQUEST_CODE_MAIN,
			intentMain,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		// 2. Button 1: "Open Current Image" -> OpenImageActivity chooser
		val intentOpen = Intent(appContext, OpenImageActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
			putExtra(OpenImageActivity.EXTRA_IMAGE_URI, stringImageUri)
		}
		val pendingIntentOpen = PendingIntent.getActivity(
			appContext,
			REQUEST_CODE_OPEN,
			intentOpen,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		// 3. Button 2: "Next Image" -> WallpaperActionReceiver
		val intentNext = Intent(appContext, WallpaperActionReceiver::class.java).apply {
			action = WallpaperActionReceiver.ACTION_NEXT_IMAGE
		}
		val pendingIntentNext = PendingIntent.getBroadcast(
			appContext,
			REQUEST_CODE_NEXT,
			intentNext,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val builderNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle(stringImageName)
			.setContentText(stringImagePath)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setOnlyAlertOnce(true)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setContentIntent(pendingIntentMain)
			.addAction(
				android.R.drawable.ic_menu_gallery,
				"Open Current Image",
				pendingIntentOpen
			)
			.addAction(
				android.R.drawable.ic_media_next,
				"Next Image",
				pendingIntentNext
			)

		val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
		notificationManager?.notify(NOTIFICATION_ID, builderNotification.build())

		// Decode thumbnail asynchronously for the large icon
		if (!stringImageUri.isNullOrBlank()) {
			CoroutineScope(Dispatchers.IO).launch {
				val bitmapThumbnail = decodeThumbnail(appContext, stringImageUri)
				if (bitmapThumbnail != null) {
					builderNotification.setLargeIcon(bitmapThumbnail)
					withContext(Dispatchers.Main) {
						notificationManager?.notify(NOTIFICATION_ID, builderNotification.build())
					}
				}
			}
		}
	}

	fun cancelNotification(context: Context) {
		val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
		notificationManager?.cancel(NOTIFICATION_ID)
	}

	private fun openImageStream(context: Context, uriImage: Uri): InputStream? {
		return try {
			if (uriImage.scheme == "file") {
				val file = File(uriImage.path ?: return null)
				if (file.exists() && file.canRead()) {
					FileInputStream(file)
				} else {
					null
				}
			} else {
				context.contentResolver.openInputStream(uriImage)
			}
		} catch (e: Exception) {
			val filePhysical = ImageScanner.resolvePhysicalFile(uriImage.toString())
			if (filePhysical != null && filePhysical.exists() && filePhysical.canRead()) {
				try {
					FileInputStream(filePhysical)
				} catch (e2: Exception) {
					null
				}
			} else {
				null
			}
		}
	}

	private fun decodeThumbnail(context: Context, stringUri: String): Bitmap? {
		return try {
			val uriImage = Uri.parse(stringUri)
			openImageStream(context, uriImage)?.use { streamFirst ->
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				BitmapFactory.decodeStream(streamFirst, null, options)
				val intTargetSize = 192
				var intSampleSize = 1
				if (options.outHeight > intTargetSize || options.outWidth > intTargetSize) {
					val intHalfHeight = options.outHeight / 2
					val intHalfWidth = options.outWidth / 2
					while ((intHalfHeight / intSampleSize) >= intTargetSize && (intHalfWidth / intSampleSize) >= intTargetSize) {
						intSampleSize *= 2
					}
				}
				openImageStream(context, uriImage)?.use { streamSecond ->
					val decodeOptions = BitmapFactory.Options().apply {
						inSampleSize = intSampleSize
						inPreferredConfig = Bitmap.Config.RGB_565
					}
					BitmapFactory.decodeStream(streamSecond, null, decodeOptions)
				}
			}
		} catch (e: Exception) {
			null
		}
	}
}

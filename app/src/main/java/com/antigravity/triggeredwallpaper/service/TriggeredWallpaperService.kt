package com.antigravity.triggeredwallpaper.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.antigravity.triggeredwallpaper.data.ImageScanner
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.notification.WallpaperNotificationManager
import com.antigravity.triggeredwallpaper.receiver.WallpaperActionReceiver
import com.antigravity.triggeredwallpaper.scheduler.WallpaperAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Collections

class TriggeredWallpaperService : WallpaperService() {

	companion object {
		const val ACTION_TRIGGER_NEXT = "com.antigravity.triggeredwallpaper.TRIGGER_NEXT"
		const val ACTION_REFRESH_FRAME = "com.antigravity.triggeredwallpaper.ACTION_REFRESH_FRAME"
		const val EXTRA_TARGET = "extra_target"

		private val listActiveEngines = Collections.synchronizedList(mutableListOf<TriggeredWallpaperEngine>())

		fun isEngineActive(): Boolean {
			return synchronized(listActiveEngines) {
				listActiveEngines.any { !it.isPreview }
			}
		}
	}

	private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		// On rotation or fold state change, check if set or orientation changed
		serviceScope.launch(Dispatchers.IO) {
			val repository = WallpaperRepository.getInstance(applicationContext)
			val objCurrentState = DeviceStateManager.getDeviceState(applicationContext)
			val objSettings = repository.flowSettings.value
			val listSets = repository.flowFolderSets.value
			val listGeos = repository.flowGeofences.value
			val objHomeSet = ConditionEvaluator.evaluateMatchingSet(objCurrentState, WallpaperTarget.HOME, listSets, listGeos, objSettings)
			val boolChangeOnRotate = objHomeSet?.boolChangeOnRotate ?: false
			val boolChangeOnFold = objHomeSet?.boolChangeOnFold ?: false
			if (boolChangeOnRotate || boolChangeOnFold) {
				WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
			} else {
				// Re-evaluate in case active rule/set changed (e.g. Portrait set vs Landscape set vs Folded set)
				WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = false)
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		WallpaperAlarmScheduler.scheduleNextAlarm(applicationContext)
		WallpaperNotificationManager.showOrUpdateNotification(applicationContext)
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
	}

	override fun onCreateEngine(): Engine {
		return TriggeredWallpaperEngine()
	}

	inner class TriggeredWallpaperEngine : Engine() {

		private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
		private val objRepository by lazy { WallpaperRepository.getInstance(applicationContext) }
		private val objKeyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

		private var intSurfaceWidth = 0
		private var intSurfaceHeight = 0
		private var boolIsVisible = false

		private var stringLoadedHomeUri: String? = null
		private var stringLoadedLockUri: String? = null
		private var bitmapCachedHome: Bitmap? = null
		private var bitmapCachedLock: Bitmap? = null

		private val gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
			override fun onDoubleTap(e: MotionEvent): Boolean {
				val objCurrentState = DeviceStateManager.getDeviceState(applicationContext)
				val listSets = objRepository.flowFolderSets.value
				val listGeos = objRepository.flowGeofences.value
				val objHomeSet = ConditionEvaluator.evaluateMatchingSet(objCurrentState, WallpaperTarget.HOME, listSets, listGeos, objRepository.flowSettings.value)
				val boolChangeOnDoubleTap = objHomeSet?.boolChangeOnDoubleTap ?: true
				if (boolChangeOnDoubleTap) {
					engineScope.launch(Dispatchers.IO) {
						WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
					}
					return true
				}
				return false
			}
		})

		private val stateReceiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				val stringAction = intent?.action ?: return
				when (stringAction) {
					ACTION_REFRESH_FRAME -> {
						engineScope.launch {
							syncBitmapsAndDraw(objRepository.flowSettings.value)
						}
					}
					Intent.ACTION_USER_PRESENT -> {
						drawFrame()
						// Tablet edge case: lockscreen was landscape, homescreen is portrait.
						// Give the window manager / launcher 1.2s to settle into portrait, then double-check conditions
						scheduleDelayedConditionCheck(1200L)
					}
					Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> {
						drawFrame()
					}
				}
			}
		}

		override fun onCreate(surfaceHolder: SurfaceHolder?) {
			super.onCreate(surfaceHolder)
			listActiveEngines.add(this)
			setTouchEventsEnabled(true)

			val filter = IntentFilter().apply {
				addAction(ACTION_REFRESH_FRAME)
				addAction(Intent.ACTION_USER_PRESENT)
				addAction(Intent.ACTION_SCREEN_ON)
				addAction(Intent.ACTION_SCREEN_OFF)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
			} else {
				registerReceiver(stateReceiver, filter)
			}

			// Schedule alarms and notification once
			WallpaperAlarmScheduler.scheduleNextAlarm(applicationContext)

			// Observe settings for live wallpaper updates
			engineScope.launch {
				objRepository.flowSettings.collectLatest { objSettings ->
					syncBitmapsAndDraw(objSettings)
				}
			}

			// Observe fold changes for immediate adaptation
			engineScope.launch {
				DeviceStateManager.flowFoldCondition.collectLatest {
					checkAndReevaluateConditions()
				}
			}

			// If no current image selected yet and not preview, trigger first image selection
			if (!isPreview && objRepository.flowSettings.value.stringCurrentHomeImageUri == null) {
				engineScope.launch(Dispatchers.IO) {
					WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = false)
				}
			}
		}

		override fun onDestroy() {
			super.onDestroy()
			listActiveEngines.remove(this)
			try {
				unregisterReceiver(stateReceiver)
			} catch (e: Exception) {
				e.printStackTrace()
			}
			engineScope.cancel()
			bitmapCachedHome?.recycle()
			bitmapCachedLock?.recycle()
			bitmapCachedHome = null
			bitmapCachedLock = null
		}

		override fun onVisibilityChanged(visible: Boolean) {
			boolIsVisible = visible
			if (visible) {
				drawFrame()
			}
		}

		private var jobDelayedConditionCheck: kotlinx.coroutines.Job? = null

		private fun scheduleDelayedConditionCheck(longDelayMs: Long = 1200L) {
			jobDelayedConditionCheck?.cancel()
			jobDelayedConditionCheck = engineScope.launch(Dispatchers.IO) {
				kotlinx.coroutines.delay(longDelayMs)
				checkAndReevaluateConditions()
			}
		}

		private suspend fun checkAndReevaluateConditions() {
			val objSettings = objRepository.flowSettings.value
			val listSets = objRepository.flowFolderSets.value
			val listGeos = objRepository.flowGeofences.value

			val currentSurfaceOrientation = when {
				intSurfaceWidth > intSurfaceHeight -> com.antigravity.triggeredwallpaper.model.OrientationCondition.LANDSCAPE
				intSurfaceHeight > intSurfaceWidth -> com.antigravity.triggeredwallpaper.model.OrientationCondition.PORTRAIT
				else -> null
			}
			val objCurrentState = DeviceStateManager.getDeviceState(applicationContext, currentSurfaceOrientation)

			// Check Home screen match
			val objMatchedHomeSet = ConditionEvaluator.evaluateMatchingSet(
				objCurrentState,
				WallpaperTarget.HOME,
				listSets,
				listGeos,
				objSettings
			)
			val boolHomeSetChanged = objMatchedHomeSet?.stringId != objSettings.stringCurrentHomeSetId

			val objCurrentHomeSet = listSets.find { it.stringId == objSettings.stringCurrentHomeSetId }
			val boolHomeOrientationMismatch = objCurrentHomeSet != null &&
				objCurrentHomeSet.orientationCondition != com.antigravity.triggeredwallpaper.model.OrientationCondition.ANY &&
				objCurrentHomeSet.orientationCondition != objCurrentState.orientationCondition

			val boolHomeFoldMismatch = objCurrentHomeSet != null &&
				objCurrentHomeSet.foldCondition != com.antigravity.triggeredwallpaper.model.FoldCondition.ANY &&
				objCurrentHomeSet.foldCondition != objCurrentState.foldCondition

			val boolLockFoldMismatch = if (objSettings.boolSeparateHomeAndLock) {
				val objCurrentLockSet = listSets.find { it.stringId == objSettings.stringCurrentLockSetId }
				objCurrentLockSet != null &&
					objCurrentLockSet.foldCondition != com.antigravity.triggeredwallpaper.model.FoldCondition.ANY &&
					objCurrentLockSet.foldCondition != objCurrentState.foldCondition
			} else {
				false
			}

			if (boolHomeSetChanged || boolHomeOrientationMismatch || boolHomeFoldMismatch || boolLockFoldMismatch) {
				WallpaperActionReceiver.advanceToNextImage(
					applicationContext,
					boolForceNext = false,
					overrideOrientation = currentSurfaceOrientation
				)
			}
		}

		override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
			super.onSurfaceChanged(holder, format, width, height)
			val boolDimensionsChanged = (intSurfaceWidth != width || intSurfaceHeight != height)
			intSurfaceWidth = width
			intSurfaceHeight = height

			if (boolDimensionsChanged) {
				engineScope.launch {
					syncBitmapsAndDraw(objRepository.flowSettings.value, boolForceReload = true)
				}
				scheduleDelayedConditionCheck(1000L)
			} else {
				drawFrame()
			}
		}

		override fun onTouchEvent(event: MotionEvent?) {
			super.onTouchEvent(event)
			if (event != null) {
				gestureDetector.onTouchEvent(event)
			}
		}

		private suspend fun syncBitmapsAndDraw(
			objSettings: AppSettings,
			boolForceReload: Boolean = false
		) {
			val stringHomeUri = objSettings.stringCurrentHomeImageUri
			val stringLockUri = objSettings.stringCurrentLockImageUri

			val boolNeedHomeReload = boolForceReload ||
				bitmapCachedHome == null ||
				stringHomeUri != stringLoadedHomeUri

			val boolNeedLockReload = objSettings.boolSeparateHomeAndLock &&
				(boolForceReload || bitmapCachedLock == null || stringLockUri != stringLoadedLockUri)

			if (boolNeedHomeReload && !stringHomeUri.isNullOrBlank() && intSurfaceWidth > 0 && intSurfaceHeight > 0) {
				val bitmapHome = withContext(Dispatchers.IO) {
					decodeSampledBitmap(applicationContext, Uri.parse(stringHomeUri), intSurfaceWidth, intSurfaceHeight)
				}
				if (bitmapHome != null) {
					bitmapCachedHome?.recycle()
					bitmapCachedHome = bitmapHome
					stringLoadedHomeUri = stringHomeUri
				} else {
					// Image file was deleted or invalid; immediately advance to next valid image
					engineScope.launch(Dispatchers.IO) {
						WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
					}
					return
				}
			}

			if (boolNeedLockReload && !stringLockUri.isNullOrBlank() && intSurfaceWidth > 0 && intSurfaceHeight > 0) {
				val bitmapLock = withContext(Dispatchers.IO) {
					decodeSampledBitmap(applicationContext, Uri.parse(stringLockUri), intSurfaceWidth, intSurfaceHeight)
				}
				if (bitmapLock != null) {
					bitmapCachedLock?.recycle()
					bitmapCachedLock = bitmapLock
					stringLoadedLockUri = stringLockUri
				} else {
					// Image file was deleted or invalid; immediately advance to next valid image
					engineScope.launch(Dispatchers.IO) {
						WallpaperActionReceiver.advanceToNextImage(applicationContext, boolForceNext = true)
					}
					return
				}
			}

			withContext(Dispatchers.Main) {
				drawFrame()
			}
		}

		private fun drawFrame() {
			val holder = surfaceHolder ?: return
			if (intSurfaceWidth <= 0 || intSurfaceHeight <= 0) return

			val objSettings = objRepository.flowSettings.value
			val boolIsLocked = objKeyguardManager.isKeyguardLocked
			val boolUseLock = objSettings.boolSeparateHomeAndLock && boolIsLocked

			val bitmapToDraw = if (boolUseLock) bitmapCachedLock ?: bitmapCachedHome else bitmapCachedHome ?: bitmapCachedLock
			val stringFolderPath = if (boolUseLock) objSettings.stringCurrentLockImagePath ?: "" else objSettings.stringCurrentHomeImagePath ?: ""
			val stringFileName = if (boolUseLock) objSettings.stringCurrentLockImageName ?: "" else objSettings.stringCurrentHomeImageName ?: ""

			var canvas: Canvas? = null
			try {
				canvas = holder.lockCanvas()
				if (canvas != null) {
					if (bitmapToDraw != null && !bitmapToDraw.isRecycled) {
						drawCenterCroppedBitmap(canvas, bitmapToDraw, intSurfaceWidth, intSurfaceHeight)

						if (objSettings.boolShowLabelOverlay && stringFileName.isNotEmpty()) {
							drawOverlayLabel(
								canvas,
								intSurfaceWidth,
								intSurfaceHeight,
								stringFolderPath,
								stringFileName,
								objSettings
							)
						}
					} else {
						drawFallbackBackground(canvas, intSurfaceWidth, intSurfaceHeight)
					}
				}
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				if (canvas != null) {
					try {
						holder.unlockCanvasAndPost(canvas)
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
			}
		}

		private fun drawCenterCroppedBitmap(canvas: Canvas, bitmap: Bitmap, intWidth: Int, intHeight: Int) {
			val floatScale = maxOf(
				intWidth.toFloat() / bitmap.width.toFloat(),
				intHeight.toFloat() / bitmap.height.toFloat()
			)
			val floatScaledW = bitmap.width.toFloat() * floatScale
			val floatScaledH = bitmap.height.toFloat() * floatScale
			val floatDx = (intWidth.toFloat() - floatScaledW) / 2f
			val floatDy = (intHeight.toFloat() - floatScaledH) / 2f

			val matrix = Matrix()
			matrix.postScale(floatScale, floatScale)
			matrix.postTranslate(floatDx, floatDy)

			val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
			canvas.drawBitmap(bitmap, matrix, paint)
		}

		private fun drawOverlayLabel(
			canvas: Canvas,
			intWidth: Int,
			intHeight: Int,
			stringPath: String,
			stringFileName: String,
			objSettings: AppSettings
		) {
			val floatDensity = resources.displayMetrics.density
			val floatScaledDensity = resources.displayMetrics.scaledDensity
			val floatMaxLabelWidth = intWidth.toFloat() * 0.90f
			val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.WHITE
				textSize = 15f * floatScaledDensity
				typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
				setShadowLayer(6f * floatDensity, 0f, 2f * floatDensity, Color.argb(220, 0, 0, 0))
			}

			// Full path to the image
			val stringFullPath = when {
				stringPath.isEmpty() -> stringFileName
				stringPath.endsWith(stringFileName) -> stringPath
				stringPath.endsWith("/") -> "$stringPath$stringFileName"
				else -> "$stringPath/$stringFileName"
			}
			val floatFullPathWidth = textPaint.measureText(stringFullPath)

			val stringTextToDraw: String
			if (floatFullPathWidth <= floatMaxLabelWidth) {
				stringTextToDraw = stringFullPath
			} else {
				// Too long: show filename or truncate filename
				val floatFileNameWidth = textPaint.measureText(stringFileName)
				if (floatFileNameWidth <= floatMaxLabelWidth) {
					stringTextToDraw = stringFileName
				} else {
					stringTextToDraw = TextUtils.ellipsize(
						stringFileName,
						android.text.TextPaint(textPaint),
						floatMaxLabelWidth,
						TextUtils.TruncateAt.END
					).toString()
				}
			}

			val floatMeasuredWidth = textPaint.measureText(stringTextToDraw)
			val floatPillPaddingX = 22f * floatDensity
			val floatPillPaddingY = 10f * floatDensity
			val floatPillWidth = floatMeasuredWidth + (floatPillPaddingX * 2f)
			val floatPillHeight = textPaint.textSize + (floatPillPaddingY * 2f)

			val floatOffsetPx = objSettings.intOverlayVerticalOffsetDp.toFloat() * floatDensity
			val floatLeft = (intWidth.toFloat() - floatPillWidth) / 2f
			val floatTop = when (objSettings.overlayPosition) {
				com.antigravity.triggeredwallpaper.model.OverlayPosition.TOP -> {
					(48f * floatDensity) + floatOffsetPx
				}
				com.antigravity.triggeredwallpaper.model.OverlayPosition.CENTER -> {
					((intHeight.toFloat() - floatPillHeight) / 2f) + floatOffsetPx
				}
				com.antigravity.triggeredwallpaper.model.OverlayPosition.BOTTOM -> {
					val floatBottomMargin = 108f * floatDensity
					intHeight.toFloat() - floatBottomMargin - floatPillHeight - floatOffsetPx
				}
			}
			val rectPill = RectF(floatLeft, floatTop, floatLeft + floatPillWidth, floatTop + floatPillHeight)

			val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.argb(190, 18, 18, 22)
				style = Paint.Style.FILL
			}
			canvas.drawRoundRect(rectPill, 16f * floatDensity, 16f * floatDensity, bgPaint)

			// Border
			val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.argb(80, 255, 255, 255)
				style = Paint.Style.STROKE
				strokeWidth = 1.5f * floatDensity
			}
			canvas.drawRoundRect(rectPill, 16f * floatDensity, 16f * floatDensity, borderPaint)

			// Draw text centered in pill
			val floatTextX = floatLeft + floatPillPaddingX
			val floatTextY = floatTop + floatPillPaddingY + textPaint.textSize - (2f * floatDensity)
			canvas.drawText(stringTextToDraw, floatTextX, floatTextY, textPaint)
		}

		private fun drawFallbackBackground(canvas: Canvas, intWidth: Int, intHeight: Int) {
			canvas.drawColor(Color.rgb(24, 28, 36))
			val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.rgb(180, 190, 210)
				textSize = 42f
				textAlign = Paint.Align.CENTER
			}
			canvas.drawText("Triggered Live Wallpaper", intWidth / 2f, intHeight / 2f - 30f, paint)

			paint.textSize = 28f
			paint.color = Color.rgb(130, 140, 160)
			canvas.drawText("Add folder sets in app to begin", intWidth / 2f, intHeight / 2f + 30f, paint)
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

		private fun decodeSampledBitmap(
			context: Context,
			uriImage: Uri,
			intReqWidth: Int,
			intReqHeight: Int
		): Bitmap? {
			return try {
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				openImageStream(context, uriImage)?.use { inputStream ->
					BitmapFactory.decodeStream(inputStream, null, options)
				}

				var inSampleSize = 1
				val intHeight = options.outHeight
				val intWidth = options.outWidth

				if (intHeight > intReqHeight || intWidth > intReqWidth) {
					val intHalfHeight = intHeight / 2
					val intHalfWidth = intWidth / 2
					while ((intHalfHeight / inSampleSize) >= intReqHeight && (intHalfWidth / inSampleSize) >= intReqWidth) {
						inSampleSize *= 2
					}
				}

				val decodeOptions = BitmapFactory.Options().apply {
					this.inSampleSize = inSampleSize
					inPreferredConfig = Bitmap.Config.ARGB_8888
				}

				openImageStream(context, uriImage)?.use { inputStream ->
					BitmapFactory.decodeStream(inputStream, null, decodeOptions)
				}
			} catch (e: Exception) {
				e.printStackTrace()
				null
			}
		}
	}
}

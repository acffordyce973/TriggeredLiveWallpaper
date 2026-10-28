package com.antigravity.triggeredwallpaper.engine

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.antigravity.triggeredwallpaper.model.DeviceState
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Proxy
import java.util.Calendar

object DeviceStateManager {

	private const val TAG = "DeviceStateManager"

	@Volatile
	private var floatLastHingeAngle: Float? = null

	@Volatile
	private var foldFromSamsungListener: FoldCondition? = null

	@Volatile
	private var cachedBestLocation: Location? = null

	private var boolFoldListenersInitialized = false
	private var boolLocationListenerInitialized = false

	private val _flowFoldCondition = MutableStateFlow(FoldCondition.ANY)
	val flowFoldCondition: StateFlow<FoldCondition> = _flowFoldCondition.asStateFlow()

	private val _flowLocation = MutableStateFlow<Location?>(null)
	val flowLocation: StateFlow<Location?> = _flowLocation.asStateFlow()

	fun updateLocation(location: Location) {
		cachedBestLocation = location
		_flowLocation.value = location
	}

	fun initialize(appContext: Context) {
		initFoldListeners(appContext)
		initLocationListener(appContext)
	}

	fun isFoldableDevice(context: Context): Boolean {
		val packageManager = context.packageManager
		if (packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
			packageManager.hasSystemFeature("android.hardware.type.foldable")
		) {
			return true
		}
		val stringModel = Build.MODEL.uppercase()
		if (stringModel.contains("FOLD") || stringModel.contains("FLIP") || stringModel.startsWith("SM-F")) {
			return true
		}
		val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
		if (sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null) {
			return true
		}
		return false
	}

	@Synchronized
	fun initFoldListeners(appContext: Context) {
		if (boolFoldListenersInitialized) return
		boolFoldListenersInitialized = true

		if (!isFoldableDevice(appContext)) {
			return
		}

		// 1. Register Samsung FoldStateListener via reflection
		var boolSamsungRegistered = false
		try {
			val semClass = Class.forName("com.samsung.android.view.SemWindowManager")
			val foldStateListenerClass = Class.forName("com.samsung.android.view.SemWindowManager\$FoldStateListener")
			val getInstanceMethod = semClass.getMethod("getInstance")
			val semInstance = getInstanceMethod.invoke(null)
			val proxyListener = Proxy.newProxyInstance(
				semClass.classLoader,
				arrayOf(foldStateListenerClass)
			) { _, method, args ->
				if (method.name == "onFoldStateChanged") {
					val boolIsFolded = args?.getOrNull(0) as? Boolean ?: false
					val condition = if (boolIsFolded) FoldCondition.FOLDED else FoldCondition.UNFOLDED
					foldFromSamsungListener = condition
					_flowFoldCondition.value = condition
					Log.i(TAG, "Samsung FoldStateListener changed: isFolded=$boolIsFolded -> $condition")
				}
				null
			}
			val registerMethod = semClass.getMethod("registerFoldStateListener", foldStateListenerClass, Handler::class.java)
			registerMethod.invoke(semInstance, proxyListener, Handler(Looper.getMainLooper()))
			boolSamsungRegistered = true
			Log.i(TAG, "Successfully registered Samsung FoldStateListener")
		} catch (e: Throwable) {
			Log.d(TAG, "Could not register Samsung FoldStateListener: ${e.message}")
		}

		// 2. Register Sensor.TYPE_HINGE_ANGLE only if Samsung listener is unavailable
		if (!boolSamsungRegistered) {
			try {
				val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
				val hingeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
				if (hingeSensor != null) {
					sensorManager.registerListener(object : SensorEventListener {
						override fun onSensorChanged(event: SensorEvent?) {
							val floatAngle = event?.values?.getOrNull(0) ?: return
							floatLastHingeAngle = floatAngle
							val condition = when {
								floatAngle < 30f -> FoldCondition.FOLDED
								floatAngle in 30f..150f -> FoldCondition.HALF_OPENED
								else -> FoldCondition.UNFOLDED
							}
							if (_flowFoldCondition.value != condition) {
								_flowFoldCondition.value = condition
							}
						}

						override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
					}, hingeSensor, SensorManager.SENSOR_DELAY_UI)
					Log.i(TAG, "Successfully registered Sensor.TYPE_HINGE_ANGLE listener")
				}
			} catch (e: Throwable) {
				Log.d(TAG, "Could not register Hinge Angle sensor: ${e.message}")
			}
		}
	}

	/**
	 * Initializes battery-efficient background location caching.
	 *
	 * Uses cached last known locations and zero-drain passive provider updates
	 * instead of polling GPS hardware in the background.
	 *
	 * :param appContext: Application context.
	 * :type appContext: Context
	 */
	@Synchronized
	fun initLocationListener(appContext: Context) {
		if (boolLocationListenerInitialized) return

		val boolHasPerm = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
			appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
		if (!boolHasPerm) return

		boolLocationListenerInitialized = true
		val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

		// 1. Seed initial cached location immediately from last known without powering GPS radio
		try {
			val listProviders = mutableListOf<String>()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				listProviders.add(LocationManager.FUSED_PROVIDER)
			}
			listProviders.add(LocationManager.PASSIVE_PROVIDER)
			listProviders.add(LocationManager.NETWORK_PROVIDER)
			listProviders.add(LocationManager.GPS_PROVIDER)
			for (stringProvider in listProviders) {
				try {
					val loc = locationManager.getLastKnownLocation(stringProvider)
					if (loc != null) {
						if (cachedBestLocation == null || loc.time > (cachedBestLocation?.time ?: 0L)) {
							cachedBestLocation = loc
							_flowLocation.value = loc
						}
					}
				} catch (_: SecurityException) {}
			}
		} catch (e: Exception) {
			Log.d(TAG, "Error querying initial last known location: ${e.message}")
		}

		// 2. Register zero-battery PASSIVE_PROVIDER (only receives updates when other apps query location)
		try {
			if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
				val passiveListener = object : LocationListener {
					override fun onLocationChanged(location: Location) {
						if (cachedBestLocation == null || location.accuracy <= (cachedBestLocation?.accuracy ?: Float.MAX_VALUE) ||
							location.time >= (cachedBestLocation?.time ?: 0L)
						) {
							cachedBestLocation = location
							_flowLocation.value = location
						}
					}
					override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
					override fun onProviderEnabled(provider: String) {}
					override fun onProviderDisabled(provider: String) {}
				}
				locationManager.requestLocationUpdates(
					LocationManager.PASSIVE_PROVIDER,
					60000L,
					50f,
					passiveListener,
					Looper.getMainLooper()
				)
				Log.i(TAG, "Successfully registered zero-battery PASSIVE_PROVIDER listener")
			}
		} catch (e: SecurityException) {
			Log.d(TAG, "Location permission not granted for passive updates: ${e.message}")
		} catch (e: Exception) {
			Log.d(TAG, "Could not register passive location listener: ${e.message}")
		}
	}

	/**
	 * Requests a single fresh location update on demand (e.g. when opening diagnostics UI).
	 *
	 * :param context: Context instance.
	 * :type context: Context
	 */
	fun requestSingleLocation(context: Context) {
		val boolHasPerm = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
			context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
		if (!boolHasPerm) return

		val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				val stringProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
					LocationManager.FUSED_PROVIDER
				} else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
					LocationManager.NETWORK_PROVIDER
				} else {
					LocationManager.GPS_PROVIDER
				}
				locationManager.getCurrentLocation(
					stringProvider,
					null,
					context.mainExecutor
				) { location ->
					if (location != null) {
						updateLocation(location)
					}
				}
			} else {
				@Suppress("DEPRECATION")
				locationManager.requestSingleUpdate(
					LocationManager.NETWORK_PROVIDER,
					object : LocationListener {
						override fun onLocationChanged(location: Location) {
							updateLocation(location)
						}
						override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
						override fun onProviderEnabled(provider: String) {}
						override fun onProviderDisabled(provider: String) {}
					},
					Looper.getMainLooper()
				)
			}
		} catch (e: Exception) {
			Log.d(TAG, "Could not request single on-demand location: ${e.message}")
		}
	}

	fun getFoldCondition(context: Context): FoldCondition {
		if (!isFoldableDevice(context)) {
			return FoldCondition.ANY
		}

		initFoldListeners(context.applicationContext)

		// 1. Query Samsung SemWindowManager synchronously
		try {
			val semClass = Class.forName("com.samsung.android.view.SemWindowManager")
			val getInstanceMethod = semClass.getMethod("getInstance")
			val semInstance = getInstanceMethod.invoke(null)
			val isFoldedMethod = semClass.getMethod("isFolded")
			val boolIsFolded = isFoldedMethod.invoke(semInstance) as? Boolean
			if (boolIsFolded != null) {
				val condition = if (boolIsFolded) {
					FoldCondition.FOLDED
				} else {
					val floatAngle = floatLastHingeAngle
					if (floatAngle != null && floatAngle in 35.0f..145.0f) {
						FoldCondition.HALF_OPENED
					} else {
						FoldCondition.UNFOLDED
					}
				}
				_flowFoldCondition.value = condition
				return condition
			}
		} catch (e: Throwable) {
			Log.d(TAG, "Samsung SemWindowManager.isFolded query failed: ${e.message}")
		}

		// 2. Query cached listener value from Samsung
		val fromListener = foldFromSamsungListener
		if (fromListener != null && fromListener != FoldCondition.ANY) {
			_flowFoldCondition.value = fromListener
			return fromListener
		}

		// 3. Query Hinge Angle Sensor
		val floatAngle = floatLastHingeAngle
		if (floatAngle != null) {
			val condition = when {
				floatAngle < 30f -> FoldCondition.FOLDED
				floatAngle in 30f..150f -> FoldCondition.HALF_OPENED
				else -> FoldCondition.UNFOLDED
			}
			_flowFoldCondition.value = condition
			return condition
		}

		// 4. Configuration fallback on foldable devices
		// On Samsung Galaxy Z Fold (SM-F9xx), unfolded has smallestScreenWidthDp >= 600 (e.g. 750dp)
		// Folded has smallestScreenWidthDp < 600 (e.g. 411dp)
		val config = context.resources.configuration
		val stringModel = Build.MODEL.uppercase()
		if (stringModel.contains("FOLD") || stringModel.startsWith("SM-F9")) {
			val condition = if (config.smallestScreenWidthDp >= 600) {
				FoldCondition.UNFOLDED
			} else {
				FoldCondition.FOLDED
			}
			_flowFoldCondition.value = condition
			return condition
		}

		return FoldCondition.ANY
	}

	fun getDeviceOrientation(context: Context): OrientationCondition {
		val intConfigOrientation = context.resources.configuration.orientation
		if (intConfigOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			return OrientationCondition.LANDSCAPE
		}
		if (intConfigOrientation == Configuration.ORIENTATION_PORTRAIT) {
			return OrientationCondition.PORTRAIT
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val objWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
			val objBounds = objWindowManager?.currentWindowMetrics?.bounds
			if (objBounds != null) {
				return if (objBounds.width() > objBounds.height()) {
					OrientationCondition.LANDSCAPE
				} else {
					OrientationCondition.PORTRAIT
				}
			}
		}

		val objDisplayMetrics = context.resources.displayMetrics
		return if (objDisplayMetrics.widthPixels > objDisplayMetrics.heightPixels) {
			OrientationCondition.LANDSCAPE
		} else {
			OrientationCondition.PORTRAIT
		}
	}

	fun getDeviceState(context: Context): DeviceState {
		val orientation = getDeviceOrientation(context)

		val intentBattery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
		val intStatus = intentBattery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
		val boolCharging = intStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
			intStatus == BatteryManager.BATTERY_STATUS_FULL

		val calNow = Calendar.getInstance()
		val intHour = calNow.get(Calendar.HOUR_OF_DAY)
		val intMinute = calNow.get(Calendar.MINUTE)

		val intDayOfWeek = when (calNow.get(Calendar.DAY_OF_WEEK)) {
			Calendar.MONDAY -> 1
			Calendar.TUESDAY -> 2
			Calendar.WEDNESDAY -> 3
			Calendar.THURSDAY -> 4
			Calendar.FRIDAY -> 5
			Calendar.SATURDAY -> 6
			Calendar.SUNDAY -> 7
			else -> 1
		}
		val intMonth = calNow.get(Calendar.MONTH) + 1

		val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
		val boolLocked = keyguardManager?.isKeyguardLocked ?: false

		val boolSupportsFold = isFoldableDevice(context)
		val foldCondition = getFoldCondition(context)

		val boolHasLocationPerm = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
			context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
		val boolHasLocationFeature = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LOCATION)
		val boolSupportsLocation = boolHasLocationPerm && boolHasLocationFeature

		var doubleLat: Double? = null
		var doubleLng: Double? = null
		if (boolHasLocationPerm) {
			var bestLoc = cachedBestLocation
			if (bestLoc == null) {
				val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
				try {
					val listProviders = mutableListOf<String>()
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
						listProviders.add(LocationManager.FUSED_PROVIDER)
					}
					listProviders.add(LocationManager.PASSIVE_PROVIDER)
					listProviders.add(LocationManager.NETWORK_PROVIDER)
					listProviders.add(LocationManager.GPS_PROVIDER)

					for (stringProvider in listProviders) {
						try {
							val loc = locationManager?.getLastKnownLocation(stringProvider)
							if (loc != null) {
								if (bestLoc == null || loc.time > bestLoc.time) {
									bestLoc = loc
								}
							}
						} catch (_: SecurityException) {}
					}
					if (bestLoc != null) {
						cachedBestLocation = bestLoc
						_flowLocation.value = bestLoc
					}
				} catch (e: Exception) {
					Log.d(TAG, "Error fetching cached location: ${e.message}")
				}
			}
			if (bestLoc != null) {
				doubleLat = bestLoc.latitude
				doubleLng = bestLoc.longitude
			}
		}

		return DeviceState(
			orientationCondition = orientation,
			foldCondition = foldCondition,
			boolCharging = boolCharging,
			intHour = intHour,
			intMinute = intMinute,
			intDayOfWeek = intDayOfWeek,
			intMonth = intMonth,
			doubleLatitude = doubleLat,
			doubleLongitude = doubleLng,
			boolScreenLocked = boolLocked,
			boolSupportsFold = boolSupportsFold,
			boolSupportsLocation = boolSupportsLocation
		)
	}
}

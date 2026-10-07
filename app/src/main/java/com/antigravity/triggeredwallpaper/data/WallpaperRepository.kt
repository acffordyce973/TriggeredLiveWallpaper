package com.antigravity.triggeredwallpaper.data

import android.content.Context
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.ImageItem
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class WallpaperConfigData(
	val listFolderSets: List<FolderSet> = emptyList(),
	val listGeofences: List<GeofenceArea> = emptyList(),
	val objSettings: AppSettings = AppSettings()
)

class WallpaperRepository private constructor(private val appContext: Context) {

	private val jsonSerializer = Json {
		ignoreUnknownKeys = true
		prettyPrint = true
		encodeDefaults = true
	}

	private val configFile = File(appContext.filesDir, "wallpaper_config.json")
	private val objMutex = Mutex()
	private val repositoryScope = CoroutineScope(Dispatchers.IO)

	private val _flowFolderSets = MutableStateFlow<List<FolderSet>>(emptyList())
	val flowFolderSets: StateFlow<List<FolderSet>> = _flowFolderSets.asStateFlow()

	private val _flowGeofences = MutableStateFlow<List<GeofenceArea>>(emptyList())
	val flowGeofences: StateFlow<List<GeofenceArea>> = _flowGeofences.asStateFlow()

	private val _flowSettings = MutableStateFlow(AppSettings())
	val flowSettings: StateFlow<AppSettings> = _flowSettings.asStateFlow()

	// In-memory cache of scanned images per folder set and shuffle queues
	private val mapScannedImages = mutableMapOf<String, List<ImageItem>>()
	private val mapShuffleQueues = mutableMapOf<String, MutableList<ImageItem>>()

	init {
		loadConfiguration()
	}

	fun loadConfiguration() {
		repositoryScope.launch {
			objMutex.withLock {
				try {
					if (configFile.exists()) {
						val stringJson = configFile.readText()
						val objData = jsonSerializer.decodeFromString<WallpaperConfigData>(stringJson)
						_flowFolderSets.value = objData.listFolderSets
						_flowGeofences.value = objData.listGeofences
						_flowSettings.value = objData.objSettings
					} else {
						// Create initial sample configuration
						val objInitialData = WallpaperConfigData(
							listFolderSets = listOf(
								FolderSet(
									stringId = "sample_default",
									stringName = "All Wallpapers",
									listFolderUris = emptyList(),
									boolQuickTileEnabled = true,
									intIntervalMinutes = 60,
									boolChangeOnRotate = true,
									boolChangeOnFold = true,
									boolChangeOnCharging = false,
									boolChangeOnDoubleTap = true,
									wallpaperTarget = WallpaperTarget.BOTH
								)
							),
							listGeofences = listOf(
								GeofenceArea(
									stringId = "geo_home",
									stringName = "Home",
									doubleLatitude = 37.4220,
									doubleLongitude = -122.0841,
									floatRadiusMeters = 200f
								)
							),
							objSettings = AppSettings()
						)
						saveConfigInternal(objInitialData)
						_flowFolderSets.value = objInitialData.listFolderSets
						_flowGeofences.value = objInitialData.listGeofences
						_flowSettings.value = objInitialData.objSettings
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	}

	private fun saveConfigInternal(objData: WallpaperConfigData) {
		try {
			val stringJson = jsonSerializer.encodeToString(objData)
			configFile.writeText(stringJson)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	suspend fun saveFolderSet(objSet: FolderSet) = withContext(Dispatchers.IO) {
		objMutex.withLock {
			val listCurrent = _flowFolderSets.value.toMutableList()
			val intExistingIndex = listCurrent.indexOfFirst { it.stringId == objSet.stringId }
			if (intExistingIndex >= 0) {
				listCurrent[intExistingIndex] = objSet
			} else {
				listCurrent.add(objSet)
			}
			_flowFolderSets.value = listCurrent
			// Invalidate cache for this set
			mapScannedImages.remove(objSet.stringId)
			mapShuffleQueues.remove(objSet.stringId)

			saveConfigInternal(
				WallpaperConfigData(
					listFolderSets = listCurrent,
					listGeofences = _flowGeofences.value,
					objSettings = _flowSettings.value
				)
			)
		}
	}

	suspend fun deleteFolderSet(stringSetId: String) = withContext(Dispatchers.IO) {
		objMutex.withLock {
			val listCurrent = _flowFolderSets.value.filterNot { it.stringId == stringSetId }
			_flowFolderSets.value = listCurrent
			mapScannedImages.remove(stringSetId)
			mapShuffleQueues.remove(stringSetId)

			saveConfigInternal(
				WallpaperConfigData(
					listFolderSets = listCurrent,
					listGeofences = _flowGeofences.value,
					objSettings = _flowSettings.value
				)
			)
		}
	}

	suspend fun saveGeofence(objGeofence: GeofenceArea) = withContext(Dispatchers.IO) {
		objMutex.withLock {
			val listCurrent = _flowGeofences.value.toMutableList()
			val intIndex = listCurrent.indexOfFirst { it.stringId == objGeofence.stringId }
			if (intIndex >= 0) {
				listCurrent[intIndex] = objGeofence
			} else {
				listCurrent.add(objGeofence)
			}
			_flowGeofences.value = listCurrent

			saveConfigInternal(
				WallpaperConfigData(
					listFolderSets = _flowFolderSets.value,
					listGeofences = listCurrent,
					objSettings = _flowSettings.value
				)
			)
		}
	}

	suspend fun deleteGeofence(stringGeofenceId: String) = withContext(Dispatchers.IO) {
		objMutex.withLock {
			val listCurrent = _flowGeofences.value.filterNot { it.stringId == stringGeofenceId }
			_flowGeofences.value = listCurrent

			saveConfigInternal(
				WallpaperConfigData(
					listFolderSets = _flowFolderSets.value,
					listGeofences = listCurrent,
					objSettings = _flowSettings.value
				)
			)
		}
	}

	suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
		objMutex.withLock {
			val objUpdated = transform(_flowSettings.value)
			_flowSettings.value = objUpdated
			saveConfigInternal(
				WallpaperConfigData(
					listFolderSets = _flowFolderSets.value,
					listGeofences = _flowGeofences.value,
					objSettings = objUpdated
				)
			)
		}
	}

	fun exportConfigurationJson(): String {
		val objData = WallpaperConfigData(
			listFolderSets = _flowFolderSets.value,
			listGeofences = _flowGeofences.value,
			objSettings = _flowSettings.value
		)
		return jsonSerializer.encodeToString(objData)
	}

	suspend fun importConfigurationJson(stringJson: String): Boolean = withContext(Dispatchers.IO) {
		objMutex.withLock {
			try {
				val objData = jsonSerializer.decodeFromString<WallpaperConfigData>(stringJson)
				_flowFolderSets.value = objData.listFolderSets
				_flowGeofences.value = objData.listGeofences
				_flowSettings.value = objData.objSettings
				saveConfigInternal(objData)
				clearImageCache()
				true
			} catch (e: Exception) {
				e.printStackTrace()
				false
			}
		}
	}

	fun clearImageCache() {
		mapScannedImages.clear()
		mapShuffleQueues.clear()
	}

	fun isImageFileAccessible(objImage: ImageItem): Boolean {
		return try {
			val stringUri = objImage.stringUri
			if (stringUri.startsWith("file://") || stringUri.startsWith("/")) {
				val filePhysical = ImageScanner.resolvePhysicalFile(stringUri)
					?: File(android.net.Uri.parse(stringUri).path ?: stringUri)
				filePhysical.exists() && filePhysical.canRead() && filePhysical.length() > 0L
			} else {
				val uri = android.net.Uri.parse(stringUri)
				appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
			}
		} catch (e: Exception) {
			false
		}
	}

	suspend fun getImagesForSet(objSet: FolderSet, boolForceRescan: Boolean = false): List<ImageItem> = withContext(Dispatchers.IO) {
		if (!boolForceRescan) {
			val listCached = mapScannedImages[objSet.stringId]
			if (!listCached.isNullOrEmpty()) {
				return@withContext listCached
			}
		}

		val listFound = mutableListOf<ImageItem>()
		for (stringUri in objSet.listFolderUris) {
			val listFolderImages = ImageScanner.scanFolderImages(appContext, stringUri)
			listFound.addAll(listFolderImages)
		}

		val listValid = listFound.filter { isImageFileAccessible(it) }
		mapScannedImages[objSet.stringId] = listValid
		return@withContext listValid
	}

	suspend fun selectNextImage(objSet: FolderSet): ImageItem? = withContext(Dispatchers.IO) {
		val listImages = getImagesForSet(objSet)
		if (listImages.isEmpty()) {
			return@withContext null
		}

		val boolRandomize = _flowSettings.value.boolRandomizeImages
		if (!boolRandomize) {
			for (objCandidate in listImages) {
				if (isImageFileAccessible(objCandidate)) {
					return@withContext objCandidate
				}
			}
			val listFresh = getImagesForSet(objSet, boolForceRescan = true)
			return@withContext listFresh.firstOrNull { isImageFileAccessible(it) }
		}

		var queue = mapShuffleQueues[objSet.stringId]
		if (queue == null || queue.isEmpty()) {
			val listShuffled = listImages.filter { isImageFileAccessible(it) }.shuffled().toMutableList()
			mapShuffleQueues[objSet.stringId] = listShuffled
			queue = listShuffled
		}

		while (queue.isNotEmpty()) {
			val objCandidate = queue.removeAt(0)
			if (isImageFileAccessible(objCandidate)) {
				return@withContext objCandidate
			}
		}

		// Queue exhausted or all candidates were deleted; perform fresh scan
		val listFresh = getImagesForSet(objSet, boolForceRescan = true)
		val listFreshValid = listFresh.filter { isImageFileAccessible(it) }.shuffled().toMutableList()
		if (listFreshValid.isNotEmpty()) {
			val objSelected = listFreshValid.removeAt(0)
			mapShuffleQueues[objSet.stringId] = listFreshValid
			return@withContext objSelected
		}

		return@withContext null
	}

	companion object {
		@Volatile
		private var instance: WallpaperRepository? = null

		fun getInstance(context: Context): WallpaperRepository {
			return instance ?: synchronized(this) {
				instance ?: WallpaperRepository(context.applicationContext).also { instance = it }
			}
		}
	}
}

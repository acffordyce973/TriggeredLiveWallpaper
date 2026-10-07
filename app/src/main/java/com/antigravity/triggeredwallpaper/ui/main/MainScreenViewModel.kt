package com.antigravity.triggeredwallpaper.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import com.antigravity.triggeredwallpaper.engine.ConflictDetector
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.ConflictInfo
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.formatBackupFilename
import com.antigravity.triggeredwallpaper.receiver.WallpaperActionReceiver
import com.antigravity.triggeredwallpaper.service.TriggeredWallpaperService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
	val listFolderSets: List<FolderSet> = emptyList(),
	val listGeofences: List<GeofenceArea> = emptyList(),
	val objSettings: AppSettings = AppSettings(),
	val listConflicts: List<ConflictInfo> = emptyList(),
	val mapSetImageCounts: Map<String, Int> = emptyMap(),
	val boolIsLoading: Boolean = false
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

	private val repository = WallpaperRepository.getInstance(application)
	private val _mapImageCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

	val flowUiState: StateFlow<MainUiState> = combine(
		repository.flowFolderSets,
		repository.flowGeofences,
		repository.flowSettings,
		_mapImageCounts
	) { listSets, listGeos, objSettings, mapCounts ->
		val listConflicts = ConflictDetector.detectConflicts(listSets)
		MainUiState(
			listFolderSets = listSets,
			listGeofences = listGeos,
			objSettings = objSettings,
			listConflicts = listConflicts,
			mapSetImageCounts = mapCounts,
			boolIsLoading = false
		)
	}.stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		MainUiState(boolIsLoading = true)
	)

	init {
		viewModelScope.launch {
			repository.flowFolderSets.collect { listSets ->
				if (listSets.isNotEmpty()) {
					refreshImageCounts()
				}
			}
		}
	}

	fun refreshImageCounts() {
		viewModelScope.launch {
			repository.clearImageCache()
			val listSets = repository.flowFolderSets.value
			val mapCounts = mutableMapOf<String, Int>()
			for (objSet in listSets) {
				val listImages = repository.getImagesForSet(objSet)
				mapCounts[objSet.stringId] = listImages.size
			}
			_mapImageCounts.value = mapCounts
		}
	}

	fun saveFolderSet(objSet: FolderSet) {
		viewModelScope.launch {
			repository.saveFolderSet(objSet)
			refreshImageCounts()
		}
	}

	fun toggleFolderSetEnabled(objSet: FolderSet, boolEnabled: Boolean) {
		viewModelScope.launch {
			val objUpdated = objSet.copy(boolIsEnabled = boolEnabled)
			repository.saveFolderSet(objUpdated)
			refreshImageCounts()
			WallpaperActionReceiver.advanceToNextImage(getApplication())
		}
	}

	fun deleteFolderSet(stringSetId: String) {
		viewModelScope.launch {
			repository.deleteFolderSet(stringSetId)
			refreshImageCounts()
		}
	}

	fun addFolderToSet(objSet: FolderSet, uriFolder: Uri, context: Context) {
		try {
			context.contentResolver.takePersistableUriPermission(
				uriFolder,
				Intent.FLAG_GRANT_READ_URI_PERMISSION
			)
		} catch (e: Exception) {
			e.printStackTrace()
		}

		val stringUri = uriFolder.toString()
		if (!objSet.listFolderUris.contains(stringUri)) {
			val listUpdatedUris = objSet.listFolderUris + stringUri
			val objUpdatedSet = objSet.copy(listFolderUris = listUpdatedUris)
			saveFolderSet(objUpdatedSet)
		}
	}

	fun removeFolderFromSet(objSet: FolderSet, stringFolderUri: String) {
		val listUpdatedUris = objSet.listFolderUris.filterNot { it == stringFolderUri }
		val objUpdatedSet = objSet.copy(listFolderUris = listUpdatedUris)
		saveFolderSet(objUpdatedSet)
	}

	fun saveGeofence(objGeofence: GeofenceArea) {
		viewModelScope.launch {
			repository.saveGeofence(objGeofence)
		}
	}

	fun deleteGeofence(stringGeofenceId: String) {
		viewModelScope.launch {
			repository.deleteGeofence(stringGeofenceId)
		}
	}

	fun duplicateFolderSet(objSet: FolderSet) {
		viewModelScope.launch {
			val objDuplicated = objSet.copy(
				stringId = java.util.UUID.randomUUID().toString(),
				stringName = "${objSet.stringName} (Copy)"
			)
			repository.saveFolderSet(objDuplicated)
			refreshImageCounts()
		}
	}

	fun exportConfiguration(onResult: (fileExported: java.io.File?) -> Unit) {
		viewModelScope.launch {
			try {
				val stringJson = repository.exportConfigurationJson()
				val fileDownloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
				if (!fileDownloadDir.exists()) fileDownloadDir.mkdirs()
				val objSettings = repository.flowSettings.value
				val stringFilename = formatBackupFilename(objSettings.stringBackupFilenameTemplate)
				val fileExport = java.io.File(fileDownloadDir, stringFilename)
				fileExport.writeText(stringJson)
				onResult(fileExport)
			} catch (e: Exception) {
				e.printStackTrace()
				onResult(null)
			}
		}
	}

	fun importConfiguration(stringJson: String, onResult: (Boolean) -> Unit) {
		viewModelScope.launch {
			val boolSuccess = repository.importConfigurationJson(stringJson)
			if (boolSuccess) {
				refreshImageCounts()
				WallpaperActionReceiver.advanceToNextImage(getApplication())
			}
			onResult(boolSuccess)
		}
	}

	fun updateSettings(transform: (AppSettings) -> AppSettings) {
		viewModelScope.launch {
			repository.updateAppSettings(transform)
		}
	}

	fun triggerNextWallpaper(context: Context) {
		viewModelScope.launch {
			WallpaperActionReceiver.advanceToNextImage(context)
		}
	}
}

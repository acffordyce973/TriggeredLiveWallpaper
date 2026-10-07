package com.antigravity.triggeredwallpaper.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScannedFolderInfo(
	val stringName: String,
	val stringRelativePath: String,
	val stringUri: String,
	val intImageCount: Int
)

object FolderTreeScanner {

	suspend fun scanSubfoldersByName(
		context: Context,
		stringRootPathOrUri: String?,
		stringTargetName: String,
		intMaxDepth: Int = 8,
		onProgress: ((stringCurrentDir: String, intFoldersScanned: Int, intMatchesFound: Int) -> Unit)? = null
	): List<ScannedFolderInfo> = withContext(Dispatchers.IO) {
		val listResults = mutableListOf<ScannedFolderInfo>()
		val stringCleanTarget = stringTargetName.trim()
		if (stringCleanTarget.isEmpty()) {
			return@withContext emptyList()
		}

		val fileRoot: File = if (!stringRootPathOrUri.isNullOrBlank()) {
			ImageScanner.resolvePhysicalFile(stringRootPathOrUri)
				?: File(stringRootPathOrUri)
		} else {
			Environment.getExternalStorageDirectory()
		}

		if (!fileRoot.exists() || !fileRoot.canRead()) {
			return@withContext emptyList()
		}

		var intFoldersScanned = 0
		var dtLastProgressReport = 0L

		fun reportProgress(stringCurrent: String) {
			val dtNow = System.currentTimeMillis()
			if (dtNow - dtLastProgressReport > 60L) {
				dtLastProgressReport = dtNow
				onProgress?.invoke(stringCurrent, intFoldersScanned, listResults.size)
			}
		}

		fun searchTree(
			objCurrentDir: File,
			intCurrentDepth: Int
		) {
			if (intCurrentDepth > intMaxDepth) return
			intFoldersScanned++
			reportProgress(objCurrentDir.name)

			val arrayFiles = objCurrentDir.listFiles() ?: return
			for (objChild in arrayFiles) {
				if (objChild.isDirectory) {
					val stringChildName = objChild.name
					if (stringChildName.equals(stringCleanTarget, ignoreCase = true)) {
						val intImages = countFolderImages(objChild)
						listResults.add(
							ScannedFolderInfo(
								stringName = stringChildName,
								stringRelativePath = objChild.absolutePath,
								stringUri = objChild.absolutePath,
								intImageCount = intImages
							)
						)
						reportProgress(stringChildName)
					} else {
						searchTree(objChild, intCurrentDepth + 1)
					}
				}
			}
		}

		searchTree(fileRoot, 0)
		onProgress?.invoke("Complete", intFoldersScanned, listResults.size)
		listResults
	}

	fun countFolderImages(objDir: File, intDepth: Int = 0): Int {
		if (intDepth > 3) return 0
		var intCount = 0
		val arrayFiles = objDir.listFiles() ?: return 0
		for (objFile in arrayFiles) {
			if (objFile.isDirectory) {
				intCount += countFolderImages(objFile, intDepth + 1)
			} else if (ImageScanner.isImageFileName(objFile.name)) {
				intCount++
			}
		}
		return intCount
	}
}

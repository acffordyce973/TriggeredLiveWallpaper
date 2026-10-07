package com.antigravity.triggeredwallpaper.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.antigravity.triggeredwallpaper.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageScanner {

	private val listSupportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")

	fun resolvePhysicalFile(stringFolderUri: String): File? {
		try {
			if (stringFolderUri.startsWith("file://")) {
				val stringPath = Uri.parse(stringFolderUri).path ?: ""
				val file = File(stringPath)
				if (file.exists() && file.canRead()) return file
			}
			if (stringFolderUri.startsWith("/")) {
				val file = File(stringFolderUri)
				if (file.exists() && file.canRead()) return file
			}
			val stringDecoded = Uri.decode(stringFolderUri)
			val stringDocId = when {
				stringDecoded.contains("/document/primary:") -> stringDecoded.substringAfter("/document/primary:")
				stringDecoded.contains("/tree/primary:") -> stringDecoded.substringAfter("/tree/primary:").substringBefore("/document/")
				else -> null
			}
			if (stringDocId != null) {
				val file = File(Environment.getExternalStorageDirectory(), stringDocId)
				if (file.exists() && file.canRead()) return file
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return null
	}

	suspend fun scanFolderImages(context: Context, stringFolderUri: String): List<ImageItem> =
		withContext(Dispatchers.IO) {
			val listImages = mutableListOf<ImageItem>()

			// 1. Try physical file access first (fastest and bypassed SAF permission denials if storage access granted)
			val filePhysical = resolvePhysicalFile(stringFolderUri)
			if (filePhysical != null && filePhysical.exists() && filePhysical.canRead()) {
				try {
					traversePhysicalFolder(filePhysical, filePhysical.absolutePath, listImages)
					if (listImages.isNotEmpty()) {
						return@withContext listImages
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}

			// 2. Fall back to SAF DocumentFile
			try {
				val uriTree = Uri.parse(stringFolderUri)
				val objRootDoc = DocumentFile.fromTreeUri(context, uriTree)
					?: DocumentFile.fromSingleUri(context, uriTree)
					?: return@withContext emptyList()
				val stringFolderName = objRootDoc.name ?: "Folder"

				traverseFolder(context, objRootDoc, stringFolderName, listImages)
			} catch (e: Exception) {
				e.printStackTrace()
			}
			listImages
		}

	private fun traversePhysicalFolder(
		objDir: File,
		stringCurrentPath: String,
		listAccumulator: MutableList<ImageItem>
	) {
		val arrayFiles = objDir.listFiles() ?: return
		for (objFile in arrayFiles) {
			if (objFile.isDirectory) {
				val stringSubPath = objFile.absolutePath
				traversePhysicalFolder(objFile, stringSubPath, listAccumulator)
			} else if (isImageFileName(objFile.name)) {
				listAccumulator.add(
					ImageItem(
						stringUri = Uri.fromFile(objFile).toString(),
						stringDisplayName = objFile.name,
						stringFolderPath = objDir.absolutePath
					)
				)
			}
		}
	}

	private fun traverseFolder(
		context: Context,
		objFolder: DocumentFile,
		stringCurrentPath: String,
		listAccumulator: MutableList<ImageItem>
	) {
		val arrayFiles = objFolder.listFiles()
		for (objFile in arrayFiles) {
			if (objFile.isDirectory) {
				val stringSubPath = "$stringCurrentPath/${objFile.name ?: "Subfolder"}"
				traverseFolder(context, objFile, stringSubPath, listAccumulator)
			} else if (isImageFile(objFile)) {
				val stringName = objFile.name ?: "Unknown"
				listAccumulator.add(
					ImageItem(
						stringUri = objFile.uri.toString(),
						stringDisplayName = stringName,
						stringFolderPath = stringCurrentPath
					)
				)
			}
		}
	}

	fun isImageFileName(stringName: String?): Boolean {
		val stringLower = stringName?.lowercase() ?: return false
		val stringExtension = stringLower.substringAfterLast('.', "")
		return listSupportedExtensions.contains(stringExtension)
	}

	fun isImageFile(objFile: DocumentFile): Boolean {
		val stringType = objFile.type
		if (stringType != null && stringType.startsWith("image/")) {
			return true
		}
		return isImageFileName(objFile.name)
	}
}

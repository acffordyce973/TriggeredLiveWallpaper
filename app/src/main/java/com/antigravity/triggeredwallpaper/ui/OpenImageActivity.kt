package com.antigravity.triggeredwallpaper.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.antigravity.triggeredwallpaper.data.ImageScanner
import com.antigravity.triggeredwallpaper.data.WallpaperRepository
import java.io.File

class OpenImageActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val stringUri = intent.getStringExtra(EXTRA_IMAGE_URI)
			?: WallpaperRepository.getInstance(applicationContext).flowSettings.value.stringCurrentHomeImageUri

		if (stringUri.isNullOrBlank()) {
			Toast.makeText(this, "No active wallpaper image to open", Toast.LENGTH_SHORT).show()
			finish()
			return
		}

		try {
			val fileImage = resolvePhysicalFile(stringUri)

			val uriTarget: Uri
			val stringMimeType: String

			if (fileImage != null && fileImage.exists() && fileImage.canRead()) {
				// 1. Try to get native MediaStore URI so gallery apps open it in album context
				val uriMediaStore = getMediaStoreUri(this, fileImage)
				stringMimeType = getMimeType(fileImage.name)

				if (uriMediaStore != null) {
					uriTarget = uriMediaStore
				} else {
					// Trigger scanner in background for next time
					MediaScannerConnection.scanFile(this, arrayOf(fileImage.absolutePath), arrayOf(stringMimeType), null)
					uriTarget = FileProvider.getUriForFile(this, "${packageName}.fileprovider", fileImage)
				}
			} else {
				val uriParsed = Uri.parse(stringUri)
				uriTarget = uriParsed
				stringMimeType = getMimeType(stringUri)
			}

			val intentView = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(uriTarget, stringMimeType)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				clipData = ClipData.newRawUri("Wallpaper Image", uriTarget)
			}

			// Open directly in default gallery app without forcing chooser
			try {
				startActivity(intentView)
			} catch (e: ActivityNotFoundException) {
				// Fallback to chooser if no default handler
				val intentChooser = Intent.createChooser(intentView, "Open image with").apply {
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}
				startActivity(intentChooser)
			}
		} catch (e: Exception) {
			Toast.makeText(this, "Could not open image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
		} finally {
			finish()
		}
	}

	private fun resolvePhysicalFile(stringUri: String): File? {
		try {
			val uriParsed = Uri.parse(stringUri)
			if (uriParsed.scheme == "file") {
				val stringPath = uriParsed.path ?: stringUri.removePrefix("file://")
				val fileDecoded = File(Uri.decode(stringPath))
				if (fileDecoded.exists() && fileDecoded.canRead()) return fileDecoded
				val fileRaw = File(stringPath)
				if (fileRaw.exists() && fileRaw.canRead()) return fileRaw
			}

			if (stringUri.startsWith("/")) {
				val fileDirect = File(stringUri)
				if (fileDirect.exists() && fileDirect.canRead()) return fileDirect
			}

			val fileFromScanner = ImageScanner.resolvePhysicalFile(stringUri)
			if (fileFromScanner != null && fileFromScanner.exists() && fileFromScanner.canRead()) {
				return fileFromScanner
			}

			// Fallback: Check using current image name/path in settings
			val objSettings = WallpaperRepository.getInstance(applicationContext).flowSettings.value
			val stringName = objSettings.stringCurrentHomeImageName
			val stringSubPath = objSettings.stringCurrentHomeImagePath
			if (!stringName.isNullOrBlank()) {
				val listSearchDirs = listOf(
					Environment.getExternalStorageDirectory(),
					File(Environment.getExternalStorageDirectory(), "Pictures"),
					File(Environment.getExternalStorageDirectory(), "DCIM"),
					File(Environment.getExternalStorageDirectory(), "Download")
				)
				for (dirRoot in listSearchDirs) {
					if (!stringSubPath.isNullOrBlank()) {
						val fileNested = File(dirRoot, "$stringSubPath/$stringName")
						if (fileNested.exists() && fileNested.canRead()) return fileNested
					}
					val fileDirect = File(dirRoot, stringName)
					if (fileDirect.exists() && fileDirect.canRead()) return fileDirect
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return null
	}

	private fun getMediaStoreUri(context: Context, file: File): Uri? {
		val arrayProjection = arrayOf(MediaStore.Images.Media._ID)
		val stringSelection = "${MediaStore.Images.Media.DATA} = ?"
		val arraySelectionArgs = arrayOf(file.absolutePath)

		return try {
			context.contentResolver.query(
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				arrayProjection,
				stringSelection,
				arraySelectionArgs,
				null
			)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val longId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
					ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, longId)
				} else {
					null
				}
			}
		} catch (e: Exception) {
			null
		}
	}

	private fun getMimeType(stringPathOrName: String): String {
		val stringLower = stringPathOrName.lowercase()
		return when {
			stringLower.endsWith(".png") -> "image/png"
			stringLower.endsWith(".webp") -> "image/webp"
			stringLower.endsWith(".gif") -> "image/gif"
			stringLower.endsWith(".bmp") -> "image/bmp"
			stringLower.endsWith(".heic") || stringLower.endsWith(".heif") -> "image/heif"
			else -> "image/jpeg"
		}
	}

	companion object {
		const val EXTRA_IMAGE_URI = "extra_image_uri"
	}
}

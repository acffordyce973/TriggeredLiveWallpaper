package com.antigravity.triggeredwallpaper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.antigravity.triggeredwallpaper.notification.WallpaperNotificationManager
import com.antigravity.triggeredwallpaper.theme.TriggeredWallpaperTheme

class MainActivity : ComponentActivity() {

	private var boolShowLocationRationale by mutableStateOf(false)
	private var boolShowBackgroundRationale by mutableStateOf(false)

	private val notificationPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { boolGranted ->
		if (boolGranted) {
			WallpaperNotificationManager.showOrUpdateNotification(applicationContext)
		}
	}

	private val foregroundLocationLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { mapResults ->
		val boolGranted = mapResults[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
			mapResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true
		if (boolGranted) {
			checkBackgroundLocationPermission()
		}
	}

	private val backgroundLocationLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { _ ->
		// Handled by onResume / state updates
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		checkStoragePermission()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
			}
		}

		checkLocationPermission()

		WallpaperNotificationManager.createNotificationChannel(applicationContext)
		WallpaperNotificationManager.showOrUpdateNotification(applicationContext)

		enableEdgeToEdge()
		setContent {
			TriggeredWallpaperTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					MainNavigation()

					if (boolShowLocationRationale) {
						AlertDialog(
							onDismissRequest = { boolShowLocationRationale = false },
							icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
							title = { Text("Location Permission Needed") },
							text = {
								Text(
									"Triggered Wallpaper uses your location to trigger wallpaper changes when you enter or leave designated geofences (such as Home or Work).\n\nTo enable location-based triggers, please grant location access."
								)
							},
							confirmButton = {
								Button(
									onClick = {
										boolShowLocationRationale = false
										foregroundLocationLauncher.launch(
											arrayOf(
												Manifest.permission.ACCESS_FINE_LOCATION,
												Manifest.permission.ACCESS_COARSE_LOCATION
											)
										)
									}
								) {
									Text("Grant Location Access")
								}
							},
							dismissButton = {
								TextButton(onClick = { boolShowLocationRationale = false }) {
									Text("Not Now")
								}
							}
						)
					}

					if (boolShowBackgroundRationale) {
						AlertDialog(
							onDismissRequest = { boolShowBackgroundRationale = false },
							icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
							title = { Text("Background Location Access ('Allow all the time')") },
							text = {
								Text(
									"To automatically trigger wallpaper changes based on your location while your screen is off, device is locked, or when the app is in the background, Android requires background location access.\n\nOn the next screen, please select 'Allow all the time'."
								)
							},
							confirmButton = {
								Button(
									onClick = {
										boolShowBackgroundRationale = false
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
											backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
										}
									}
								) {
									Text("Enable 'Allow all the time'")
								}
							},
							dismissButton = {
								TextButton(onClick = { boolShowBackgroundRationale = false }) {
									Text("Not Now")
								}
							}
						)
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		WallpaperNotificationManager.showOrUpdateNotification(applicationContext)
	}

	private fun checkLocationPermission() {
		val boolHasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
		val boolHasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

		if (!boolHasFine && !boolHasCoarse) {
			boolShowLocationRationale = true
		} else {
			checkBackgroundLocationPermission()
		}
	}

	private fun checkBackgroundLocationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val boolHasBackground = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
			if (!boolHasBackground) {
				boolShowBackgroundRationale = true
			}
		}
	}

	private fun checkStoragePermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {
				try {
					val intentStorage = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
						data = Uri.parse("package:$packageName")
					}
					startActivity(intentStorage)
				} catch (e: Exception) {
					try {
						val intentFallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
						startActivity(intentFallback)
					} catch (e2: Exception) {
						// Ignored
					}
				}
			}
		} else {
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
			}
		}
	}
}


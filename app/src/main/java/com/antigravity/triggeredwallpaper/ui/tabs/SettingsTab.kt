package com.antigravity.triggeredwallpaper.ui.tabs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.OverlayPosition
import com.antigravity.triggeredwallpaper.model.formatBackupFilename
import com.antigravity.triggeredwallpaper.notification.WallpaperNotificationManager
import com.antigravity.triggeredwallpaper.ui.dialogs.GeofenceEditDialog
import java.io.File

@Composable
fun SettingsTab(
	objSettings: AppSettings,
	listGeofences: List<GeofenceArea>,
	onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
	onSaveGeofence: (GeofenceArea) -> Unit,
	onDeleteGeofence: (String) -> Unit,
	onExportConfig: ((File?) -> Unit) -> Unit = {},
	onImportConfig: (String, (Boolean) -> Unit) -> Unit = { _, _ -> }
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var objDeviceState by remember {
		mutableStateOf(DeviceStateManager.getDeviceState(context))
	}
	val flowLocation by DeviceStateManager.flowLocation.collectAsStateWithLifecycle()

	LaunchedEffect(flowLocation) {
		objDeviceState = DeviceStateManager.getDeviceState(context)
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				objDeviceState = DeviceStateManager.getDeviceState(context)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	var boolShowGeofenceDialog by remember { mutableStateOf(false) }
	var objEditingGeofence by remember { mutableStateOf<GeofenceArea?>(null) }

	val boolHasAllFilesAccess = remember {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			Environment.isExternalStorageManager()
		} else {
			true
		}
	}

	var boolFineLocationGranted by remember {
		mutableStateOf(
			context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
				context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
		)
	}

	var boolBackgroundLocationGranted by remember {
		mutableStateOf(
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
			} else {
				true
			}
		)
	}

	val backgroundLocationLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestPermission()
	) { boolGranted ->
		boolBackgroundLocationGranted = boolGranted || (
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
			} else true
		)
	}

	val locationPermissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) { mapResults ->
		val boolGranted = mapResults[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
			mapResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true
		boolFineLocationGranted = boolGranted
		if (boolGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val boolHasBg = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
			boolBackgroundLocationGranted = boolHasBg
			if (!boolHasBg) {
				backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
			}
		}
	}

	val importFileLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uriResult ->
		if (uriResult != null) {
			try {
				val inputStream = context.contentResolver.openInputStream(uriResult)
				val stringContent = inputStream?.bufferedReader()?.use { it.readText() }
				if (!stringContent.isNullOrBlank()) {
					onImportConfig(stringContent) { boolSuccess ->
						if (boolSuccess) {
							Toast.makeText(context, "Configuration imported successfully!", Toast.LENGTH_SHORT).show()
						} else {
							Toast.makeText(context, "Failed to parse imported configuration.", Toast.LENGTH_LONG).show()
						}
					}
				}
			} catch (e: Exception) {
				Toast.makeText(context, "Error importing config: ${e.message}", Toast.LENGTH_LONG).show()
			}
		}
	}

	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		// 1. All Files Access Storage Permission Section (No SAF)
		item {
			Text(
				text = "Storage & Files Access",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		item {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(
					containerColor = if (boolHasAllFilesAccess) Color(0xFF1B3D2F) else MaterialTheme.colorScheme.errorContainer
				)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							imageVector = if (boolHasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
							contentDescription = null,
							tint = if (boolHasAllFilesAccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
							modifier = Modifier.size(24.dp)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = if (boolHasAllFilesAccess) "All Files Access Granted (Direct Storage)" else "All Files Access Required",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = if (boolHasAllFilesAccess) Color.White else MaterialTheme.colorScheme.onErrorContainer
						)
					}
					Text(
						text = if (boolHasAllFilesAccess)
							"The app has direct filesystem access without Storage Access Framework (SAF) prompts or performance limits."
						else
							"Grant All Files Access so the app can browse and load wallpapers directly from storage without SAF prompts.",
						style = MaterialTheme.typography.bodySmall,
						color = if (boolHasAllFilesAccess) Color(0xFFB5E4CC) else MaterialTheme.colorScheme.onErrorContainer
					)
					if (!boolHasAllFilesAccess) {
						Button(
							onClick = {
								try {
									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
										val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
											data = Uri.parse("package:${context.packageName}")
										}
										context.startActivity(intent)
									}
								} catch (e: Exception) {
									val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
									context.startActivity(fallbackIntent)
								}
							},
							modifier = Modifier.fillMaxWidth()
						) {
							Text("Grant All Files Access")
						}
					}
				}
			}
		}

		// 2. Configuration Backup & Restore (Export / Import)
		item {
			Text(
				text = "Configuration Backup & Sharing",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		item {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Text(
						text = "Export your sets, rules, geofences, and preferences to a JSON backup file to share across your devices or keep safe.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					// Backup Filename Template
					OutlinedTextField(
						value = objSettings.stringBackupFilenameTemplate,
						onValueChange = { stringNewVal ->
							onUpdateSettings { it.copy(stringBackupFilenameTemplate = stringNewVal) }
						},
						label = { Text("Backup Filename") },
						placeholder = { Text("TriggeredWallpaper or Config_{yyyyMMdd_HHmmss}") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = "Preview: ${formatBackupFilename(objSettings.stringBackupFilenameTemplate)}",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						fontWeight = FontWeight.SemiBold
					)
					Text(
						text = "Placeholders: {date}, {time}, {datetime}, or date pattern {yyyyMMdd}. Saved as .json automatically.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = {
								onExportConfig { fileExported ->
									if (fileExported != null && fileExported.exists()) {
										Toast.makeText(context, "Saved to Downloads: ${fileExported.name}", Toast.LENGTH_SHORT).show()
										// Launch share sheet
										try {
											val uriFile = FileProvider.getUriForFile(
												context,
												"${context.packageName}.fileprovider",
												fileExported
											)
											val shareIntent = Intent(Intent.ACTION_SEND).apply {
												type = "application/json"
												putExtra(Intent.EXTRA_STREAM, uriFile)
												addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
											}
											context.startActivity(Intent.createChooser(shareIntent, "Share Configuration"))
										} catch (e: Exception) {
											e.printStackTrace()
										}
									} else {
										Toast.makeText(context, "Failed to export configuration", Toast.LENGTH_SHORT).show()
									}
								}
							},
							modifier = Modifier.weight(1f)
						) {
							Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Export Config")
						}

						OutlinedButton(
							onClick = {
								importFileLauncher.launch("application/json")
							},
							modifier = Modifier.weight(1f)
						) {
							Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Import Config")
						}
					}
				}
			}
		}

		// 3. Wallpaper Preferences Section
		item {
			Text(
				text = "Wallpaper Preferences",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		item {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					// Separate Home and Lock Screen
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Separate Home & Lock Screens", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								"Home screen and lock screen can use completely independent folder sets and rules.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(12.dp))
						Switch(
							checked = objSettings.boolSeparateHomeAndLock,
							onCheckedChange = { boolNewVal ->
								onUpdateSettings { it.copy(boolSeparateHomeAndLock = boolNewVal) }
							}
						)
					}

					HorizontalDivider()

					// Ignore Unsupported Conditions & Triggers Toggle
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Ignore Unsupported Conditions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								"When enabled, conditions this device cannot fulfill (such as fold state on non-foldable devices) are automatically ignored. Ideal for syncing configs across devices.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(12.dp))
						Switch(
							checked = objSettings.boolIgnoreUnsupportedConditions,
							onCheckedChange = { boolNewVal ->
								onUpdateSettings { it.copy(boolIgnoreUnsupportedConditions = boolNewVal) }
							}
						)
					}

					HorizontalDivider()

					// Quick Settings Tile Active Override Toggle
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Quick Settings Tile Override Active", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								"When active, the set marked with Quick Tile trigger is forced regardless of all other conditions and triggers.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(12.dp))
						Switch(
							checked = objSettings.boolQuickTileActive,
							onCheckedChange = { boolNewVal ->
								onUpdateSettings { it.copy(boolQuickTileActive = boolNewVal) }
							}
						)
					}

					HorizontalDivider()

					// Show Path & File Name Label & Position / Offset
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text("Show Image Path & Name Overlay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
								Text(
									"Displays a pill label overlay with the folder path and image name.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							Spacer(modifier = Modifier.width(12.dp))
							Switch(
								checked = objSettings.boolShowLabelOverlay,
								onCheckedChange = { boolNewVal ->
									onUpdateSettings { it.copy(boolShowLabelOverlay = boolNewVal) }
								}
							)
						}

						if (objSettings.boolShowLabelOverlay) {
							Text("Overlay Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								listOf(
									OverlayPosition.BOTTOM to "Bottom",
									OverlayPosition.CENTER to "Center",
									OverlayPosition.TOP to "Top"
								).forEach { (pos, stringLabel) ->
									FilterChip(
										selected = objSettings.overlayPosition == pos,
										onClick = {
											onUpdateSettings { it.copy(overlayPosition = pos) }
										},
										label = { Text(stringLabel) }
									)
								}
							}

							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									text = "Vertical Offset: ${objSettings.intOverlayVerticalOffsetDp} dp",
									style = MaterialTheme.typography.labelMedium,
									fontWeight = FontWeight.SemiBold
								)
								if (objSettings.intOverlayVerticalOffsetDp != 0) {
									IconButton(onClick = {
										onUpdateSettings { it.copy(intOverlayVerticalOffsetDp = 0) }
									}) {
										Icon(Icons.Default.RestartAlt, contentDescription = "Reset offset", modifier = Modifier.size(18.dp))
									}
								}
							}
							Slider(
								value = objSettings.intOverlayVerticalOffsetDp.toFloat(),
								onValueChange = { floatVal ->
									onUpdateSettings { it.copy(intOverlayVerticalOffsetDp = floatVal.toInt()) }
								},
								valueRange = -150f..300f,
								modifier = Modifier.fillMaxWidth()
							)
						}
					}

					HorizontalDivider()

					// Randomize Images
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Randomize Image Selection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								"Shuffles images non-repeating within the active folder set.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(12.dp))
						Switch(
							checked = objSettings.boolRandomizeImages,
							onCheckedChange = { boolNewVal ->
								onUpdateSettings { it.copy(boolRandomizeImages = boolNewVal) }
							}
						)
					}

					HorizontalDivider()

					// Persistent Quick-Action Notification
					val contextTab = LocalContext.current
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Persistent Quick-Action Notification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								"Displays an ongoing status notification with buttons to open the current image in an external app or skip to the next image.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(12.dp))
						Switch(
							checked = objSettings.boolShowPersistentNotification,
							onCheckedChange = { boolNewVal ->
								onUpdateSettings { it.copy(boolShowPersistentNotification = boolNewVal) }
								if (boolNewVal) {
									WallpaperNotificationManager.showOrUpdateNotification(contextTab)
								} else {
									WallpaperNotificationManager.cancelNotification(contextTab)
								}
							}
						)
					}
				}
			}
		}

		// 4. Background Execution & Battery Optimization Section
		item {
			Text(
				text = "Background Execution",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		item {
			val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
			val boolIgnoringOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(
					containerColor = if (boolIgnoringOptimizations) Color(0xFF1B3D2F) else MaterialTheme.colorScheme.surfaceVariant
				)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Text(
						text = if (boolIgnoringOptimizations) "Unrestricted Background Mode Active" else "Battery Optimization Active",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.Bold,
						color = if (boolIgnoringOptimizations) Color.White else MaterialTheme.colorScheme.onSurface
					)
					Text(
						text = if (boolIgnoringOptimizations)
							"The app is allowed to wake up the device on schedule to change wallpapers during deep sleep and Doze mode."
						else
							"Android or OEM battery managers may delay or freeze background wallpaper changes when the screen is off. Disable battery optimization to guarantee on-time changes.",
						style = MaterialTheme.typography.bodySmall,
						color = if (boolIgnoringOptimizations) Color(0xFFB5E4CC) else MaterialTheme.colorScheme.onSurfaceVariant
					)

					if (!boolIgnoringOptimizations) {
						Button(
							onClick = {
								try {
									val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
										data = Uri.parse("package:${context.packageName}")
									}
									context.startActivity(intent)
								} catch (e: Exception) {
									val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
									context.startActivity(fallbackIntent)
								}
							},
							modifier = Modifier.fillMaxWidth()
						) {
							Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Disable Battery Optimization")
						}
					}
				}
			}
		}

		// 5. Location & Geofencing Permissions Section
		item {
			Text(
				text = "Location & Geofencing Permissions",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		item {
			val boolAllLocationGranted = boolFineLocationGranted && boolBackgroundLocationGranted
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(
					containerColor = if (boolAllLocationGranted) Color(0xFF1B3D2F) else MaterialTheme.colorScheme.surfaceVariant
				)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							imageVector = if (boolAllLocationGranted) Icons.Default.CheckCircle else Icons.Default.LocationOn,
							contentDescription = null,
							tint = if (boolAllLocationGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(24.dp)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = if (boolAllLocationGranted)
								"Background Location Active ('Allow all the time')"
							else if (boolFineLocationGranted)
								"Foreground Location Only (App in use)"
							else
								"Location Access Required",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = if (boolAllLocationGranted) Color.White else MaterialTheme.colorScheme.onSurface
						)
					}
					Text(
						text = if (boolAllLocationGranted)
							"The app can monitor geofences and automatically trigger wallpaper changes when arriving or leaving locations even while your screen is locked."
						else if (boolFineLocationGranted)
							"Location is only granted while using the app. For wallpapers to trigger automatically when your screen is off or in the background, select 'Allow all the time' in Settings."
						else
							"Location access is required for geofence triggers to detect when you enter or leave locations (like Home or Work).",
						style = MaterialTheme.typography.bodySmall,
						color = if (boolAllLocationGranted) Color(0xFFB5E4CC) else MaterialTheme.colorScheme.onSurfaceVariant
					)

					if (!boolFineLocationGranted) {
						Button(
							onClick = {
								locationPermissionLauncher.launch(
									arrayOf(
										Manifest.permission.ACCESS_FINE_LOCATION,
										Manifest.permission.ACCESS_COARSE_LOCATION
									)
								)
							},
							modifier = Modifier.fillMaxWidth()
						) {
							Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Grant Location Access")
						}
					} else if (!boolBackgroundLocationGranted) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Button(
								onClick = {
									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
										backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
									}
								},
								modifier = Modifier.weight(1f)
							) {
								Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
								Spacer(modifier = Modifier.width(6.dp))
								Text("Request 'Allow all the time'")
							}

							OutlinedButton(
								onClick = {
									try {
										val intentSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
											data = Uri.parse("package:${context.packageName}")
										}
										context.startActivity(intentSettings)
									} catch (e: Exception) {
										e.printStackTrace()
									}
								},
								modifier = Modifier.weight(1f)
							) {
								Text("Open App Settings")
							}
						}
					}
				}
			}
		}

		// 6. Geofence Locations Section
		item {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "Geofence Locations",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				Button(
					onClick = {
						objEditingGeofence = null
						boolShowGeofenceDialog = true
					}
				) {
					Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(modifier = Modifier.width(4.dp))
					Text("Add Location")
				}
			}
		}

		if (listGeofences.isEmpty()) {
			item {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Text(
						text = "No geofence locations defined yet. Add locations like 'Home' or 'Work' to trigger folder sets when entering or leaving them.",
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.padding(16.dp)
					)
				}
			}
		} else {
			items(listGeofences, key = { it.stringId }) { objGeo ->
				val curLat = objDeviceState.doubleLatitude
				val curLng = objDeviceState.doubleLongitude
				val floatDistanceMeters = remember(objGeo, curLat, curLng) {
					if (curLat != null && curLng != null) {
						val arrayDistances = FloatArray(1)
						Location.distanceBetween(curLat, curLng, objGeo.doubleLatitude, objGeo.doubleLongitude, arrayDistances)
						arrayDistances[0]
					} else {
						null
					}
				}
				val boolIsInside = floatDistanceMeters != null && floatDistanceMeters <= objGeo.floatRadiusMeters
				val boolIsDark = isSystemInDarkTheme()

				val cardColor = if (boolIsInside) {
					if (boolIsDark) Color(0xFF1B4D2E) else Color(0xFFD4EDDA)
				} else {
					MaterialTheme.colorScheme.surfaceVariant
				}
				val cardBorder = if (boolIsInside) {
					BorderStroke(1.5.dp, if (boolIsDark) Color(0xFF388E3C) else Color(0xFF81C784))
				} else {
					null
				}
				val titleColor = if (boolIsInside) {
					if (boolIsDark) Color(0xFFA5D6A7) else Color(0xFF155724)
				} else {
					MaterialTheme.colorScheme.onSurface
				}
				val subtitleColor = if (boolIsInside) {
					if (boolIsDark) Color(0xFFC8E6C9).copy(alpha = 0.85f) else Color(0xFF1B5E20).copy(alpha = 0.85f)
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				}

				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(12.dp),
					colors = CardDefaults.cardColors(containerColor = cardColor),
					border = cardBorder
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(14.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Row(
							modifier = Modifier.weight(1f),
							verticalAlignment = Alignment.CenterVertically
						) {
							Icon(
								Icons.Default.LocationOn,
								contentDescription = null,
								tint = if (boolIsInside) (if (boolIsDark) Color(0xFF81C784) else Color(0xFF2E7D32)) else MaterialTheme.colorScheme.primary
							)
							Spacer(modifier = Modifier.width(12.dp))
							Column {
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text(objGeo.stringName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
									if (boolIsInside) {
										Spacer(modifier = Modifier.width(8.dp))
										Surface(
											shape = RoundedCornerShape(6.dp),
											color = if (boolIsDark) Color(0xFF2E7D32) else Color(0xFFC8E6C9)
										) {
											Text(
												text = "CURRENTLY INSIDE",
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.Bold,
												color = if (boolIsDark) Color.White else Color(0xFF1B5E20),
												modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
											)
										}
									}
								}
								val stringDistanceText = if (floatDistanceMeters != null) {
									" • ${floatDistanceMeters.toInt()}m from center"
								} else {
									""
								}
								Text(
									text = "Lat: ${objGeo.doubleLatitude}, Lng: ${objGeo.doubleLongitude} • ${objGeo.floatRadiusMeters.toInt()}m radius$stringDistanceText",
									style = MaterialTheme.typography.bodySmall,
									color = subtitleColor
								)
							}
						}
						Row {
							IconButton(onClick = {
								objEditingGeofence = objGeo
								boolShowGeofenceDialog = true
							}) {
								Icon(
									Icons.Default.Edit,
									contentDescription = "Edit",
									tint = if (boolIsInside) (if (boolIsDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)) else MaterialTheme.colorScheme.primary
								)
							}
							IconButton(onClick = {
								onDeleteGeofence(objGeo.stringId)
							}) {
								Icon(
									Icons.Default.Delete,
									contentDescription = "Delete",
									tint = if (boolIsInside) (if (boolIsDark) Color(0xFFEF9A9A) else MaterialTheme.colorScheme.error) else MaterialTheme.colorScheme.error
								)
							}
						}
					}
				}
			}
		}
	}

	if (boolShowGeofenceDialog) {
		GeofenceEditDialog(
			objInitialGeofence = objEditingGeofence,
			onDismiss = { boolShowGeofenceDialog = false },
			onSave = { objSaved ->
				onSaveGeofence(objSaved)
				boolShowGeofenceDialog = false
			}
		)
	}
}

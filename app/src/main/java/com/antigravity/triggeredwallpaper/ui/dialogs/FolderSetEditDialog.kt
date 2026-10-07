package com.antigravity.triggeredwallpaper.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredwallpaper.data.FolderTreeScanner
import com.antigravity.triggeredwallpaper.data.ScannedFolderInfo
import com.antigravity.triggeredwallpaper.model.ChargingCondition
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.GeofenceCondition
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import com.antigravity.triggeredwallpaper.model.TimeWindowCondition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderSetEditDialog(
	objInitialSet: FolderSet?,
	listGeofences: List<GeofenceArea>,
	onDismiss: () -> Unit,
	onSave: (FolderSet) -> Unit,
	onAddFolderUri: (FolderSet, Uri) -> Unit
) {
	val context = LocalContext.current
	val coroutineScope = rememberCoroutineScope()

	var boolShowScanTreeDialog by remember { mutableStateOf(false) }
	var stringSubfolderSearchPattern by remember {
		mutableStateOf(
			if (objInitialSet?.orientationCondition == OrientationCondition.LANDSCAPE) "Landscape" else "Portrait"
		)
	}
	var boolIsScanningTree by remember { mutableStateOf(false) }
	var listScannedResults by remember { mutableStateOf<List<ScannedFolderInfo>>(emptyList()) }
	var setCheckedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
	var stringScanStatus by remember { mutableStateOf<String?>(null) }

	var stringName by remember { mutableStateOf(objInitialSet?.stringName ?: "") }
	var boolIsEnabled by remember { mutableStateOf(objInitialSet?.boolIsEnabled ?: true) }
	var targetScreen by remember { mutableStateOf(objInitialSet?.wallpaperTarget ?: WallpaperTarget.BOTH) }
	var listUris by remember { mutableStateOf(objInitialSet?.listFolderUris ?: emptyList()) }

	// Triggers
	var floatIntervalMinutes by remember {
		mutableFloatStateOf(objInitialSet?.intIntervalMinutes?.toFloat() ?: 0f)
	}
	var boolQuickTile by remember { mutableStateOf(objInitialSet?.boolQuickTileEnabled ?: false) }
	var boolDoubleTap by remember { mutableStateOf(objInitialSet?.boolChangeOnDoubleTap ?: true) }
	var boolChangeOnRotate by remember { mutableStateOf(objInitialSet?.boolChangeOnRotate ?: false) }
	var boolChangeOnFold by remember { mutableStateOf(objInitialSet?.boolChangeOnFold ?: false) }
	var boolChangeOnCharging by remember { mutableStateOf(objInitialSet?.boolChangeOnCharging ?: false) }

	// Conditions
	var orientationCondition by remember {
		mutableStateOf(objInitialSet?.orientationCondition ?: OrientationCondition.ANY)
	}
	var foldCondition by remember {
		mutableStateOf(objInitialSet?.foldCondition ?: FoldCondition.ANY)
	}
	var chargingCondition by remember {
		mutableStateOf(objInitialSet?.chargingCondition ?: ChargingCondition.ANY)
	}

	// Time window
	var boolTimeWindowEnabled by remember {
		mutableStateOf(objInitialSet?.timeWindowCondition?.boolEnabled ?: false)
	}
	var intStartHour by remember {
		mutableIntStateOf(objInitialSet?.timeWindowCondition?.intStartHour ?: 10)
	}
	var intStartMinute by remember {
		mutableIntStateOf(objInitialSet?.timeWindowCondition?.intStartMinute ?: 0)
	}
	var intEndHour by remember {
		mutableIntStateOf(objInitialSet?.timeWindowCondition?.intEndHour ?: 22)
	}
	var intEndMinute by remember {
		mutableIntStateOf(objInitialSet?.timeWindowCondition?.intEndMinute ?: 0)
	}

	// Geofence
	var boolGeofenceEnabled by remember {
		mutableStateOf(objInitialSet?.geofenceCondition?.boolEnabled ?: false)
	}
	var stringSelectedGeofenceId by remember {
		mutableStateOf(
			objInitialSet?.geofenceCondition?.stringGeofenceId.takeIf { !it.isNullOrEmpty() }
				?: listGeofences.firstOrNull()?.stringId.orEmpty()
		)
	}
	var boolGeofenceInside by remember {
		mutableStateOf(objInitialSet?.geofenceCondition?.boolInside ?: true)
	}

	// Days of week condition (1 = Mon .. 7 = Sun)
	var listDaysOfWeek by remember {
		mutableStateOf(objInitialSet?.listDaysOfWeek ?: emptyList())
	}

	// Month condition (1 = Jan .. 12 = Dec)
	var listMonths by remember {
		mutableStateOf(objInitialSet?.listMonths ?: emptyList())
	}

	var boolShowFolderPicker by remember { mutableStateOf(false) }
	var boolShowTreeRootPicker by remember { mutableStateOf(false) }
	var stringTreeRootPath by remember {
		mutableStateOf(Environment.getExternalStorageDirectory().absolutePath)
	}
	var stringScanCurrentFolder by remember { mutableStateOf("") }
	var intScannedFoldersCount by remember { mutableIntStateOf(0) }

	fun startTreeScan(stringRoot: String) {
		boolIsScanningTree = true
		stringScanStatus = "Preparing to scan tree..."
		stringScanCurrentFolder = ""
		intScannedFoldersCount = 0
		listScannedResults = emptyList()
		setCheckedUris = emptySet()
		coroutineScope.launch {
			try {
				val listFound = FolderTreeScanner.scanSubfoldersByName(
					context = context,
					stringRootPathOrUri = stringRoot,
					stringTargetName = stringSubfolderSearchPattern,
					onProgress = { stringCurrentDir, intFoldersScanned, intMatchesFound ->
						stringScanCurrentFolder = stringCurrentDir
						intScannedFoldersCount = intFoldersScanned
						stringScanStatus = "Scanned $intFoldersScanned folder(s) • Found $intMatchesFound match(es)"
					}
				)
				listScannedResults = listFound
				setCheckedUris = listFound.map { it.stringUri }.toSet()
				if (listFound.isEmpty()) {
					stringScanStatus = "Scan complete: No subfolders named '$stringSubfolderSearchPattern' were found in the selected folder."
				} else {
					val intTotalImages = listFound.sumOf { it.intImageCount }
					stringScanStatus = "Scan complete: Found ${listFound.size} folder(s) with $intTotalImages total image(s)."
				}
			} catch (e: Exception) {
				stringScanStatus = "Error scanning folder: ${e.message}"
			} finally {
				boolIsScanningTree = false
			}
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = if (objInitialSet == null) "New Folder Set" else "Edit Folder Set",
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 520.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				// 0. Enable / Disable Toggle
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = if (boolIsEnabled) "Set is Enabled" else "Set is Disabled",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = if (boolIsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
						)
						Text(
							text = if (boolIsEnabled) "Evaluated actively by wallpaper triggers" else "Skipped by triggers until re-enabled",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					Switch(
						checked = boolIsEnabled,
						onCheckedChange = { boolIsEnabled = it }
					)
				}

				// 1. Name & Target
				OutlinedTextField(
					value = stringName,
					onValueChange = { stringName = it },
					label = { Text("Set Name") },
					placeholder = { Text("e.g. Nature Landscapes") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				Text("Target Screen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					WallpaperTarget.entries.forEach { target ->
						FilterChip(
							selected = targetScreen == target,
							onClick = { targetScreen = target },
							label = { Text(target.name) }
						)
					}
				}

				HorizontalDivider()

				// 2. Folders in this set
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Folders (${listUris.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(
							onClick = { boolShowFolderPicker = true }
						) {
							Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("Add")
						}
						Button(
							onClick = {
								boolShowScanTreeDialog = true
								listScannedResults = emptyList()
								stringScanStatus = null
								stringScanCurrentFolder = ""
								intScannedFoldersCount = 0
							}
						) {
							Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("Scan Tree...")
						}
					}
				}

				if (listUris.size > 1) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.End
					) {
						TextButton(onClick = { listUris = emptyList() }) {
							Text("Clear All Folders", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
						}
					}
				}

				if (listUris.isEmpty()) {
					Card(
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
						modifier = Modifier.fillMaxWidth()
					) {
						Text(
							text = "No folders added yet. Tap 'Add Folder' to select an image folder on your device.",
							style = MaterialTheme.typography.bodySmall,
							modifier = Modifier.padding(12.dp)
						)
					}
				} else {
					Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
						listUris.forEach { stringFolderUri ->
							val uri = Uri.parse(stringFolderUri)
							val stringDisplay = uri.lastPathSegment?.substringAfterLast(':') ?: stringFolderUri
							Card(
								modifier = Modifier.fillMaxWidth(),
								colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 6.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Row(
										modifier = Modifier.weight(1f),
										verticalAlignment = Alignment.CenterVertically
									) {
										Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
										Spacer(modifier = Modifier.width(8.dp))
										Text(
											text = stringDisplay,
											style = MaterialTheme.typography.bodyMedium,
											maxLines = 1
										)
									}
									IconButton(
										onClick = { listUris = listUris.filterNot { it == stringFolderUri } }
									) {
										Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
									}
								}
							}
						}
					}
				}

				HorizontalDivider()

				// 3. Triggers section
				Text("Triggers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

				// Interval slider
				Column {
					val intInterval = floatIntervalMinutes.toInt()
					val stringIntervalLabel = when (intInterval) {
						0 -> "Disabled (Manual or trigger only)"
						15 -> "Every 15 minutes"
						30 -> "Every 30 minutes"
						60 -> "Every 1 hour"
						120 -> "Every 2 hours"
						360 -> "Every 6 hours"
						720 -> "Every 12 hours"
						1440 -> "Every 24 hours"
						else -> "Every $intInterval minutes"
					}
					Text("Auto-change Interval: $stringIntervalLabel", style = MaterialTheme.typography.bodyMedium)
					Slider(
						value = floatIntervalMinutes,
						onValueChange = { floatIntervalMinutes = it },
						valueRange = 0f..720f,
						steps = 11,
						modifier = Modifier.fillMaxWidth()
					)
				}

				// Trigger switches
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Quick Settings Tile Trigger", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
						Text(
							"When the Quick Settings tile is active, always force this set regardless of other conditions and triggers",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					Spacer(modifier = Modifier.width(8.dp))
					Switch(checked = boolQuickTile, onCheckedChange = { boolQuickTile = it })
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Double-Tap Wallpaper to Change")
					Switch(checked = boolDoubleTap, onCheckedChange = { boolDoubleTap = it })
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Trigger on Screen Rotation")
					Switch(checked = boolChangeOnRotate, onCheckedChange = { boolChangeOnRotate = it })
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Trigger on Screen Fold/Unfold")
					Switch(checked = boolChangeOnFold, onCheckedChange = { boolChangeOnFold = it })
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Trigger on Power Plugged/Unplugged")
					Switch(checked = boolChangeOnCharging, onCheckedChange = { boolChangeOnCharging = it })
				}

				HorizontalDivider()

				// 4. Conditions Section
				Text("Conditions (When to Select this Set)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

				// Orientation Condition
				Text("Screen Orientation Condition:", style = MaterialTheme.typography.bodyMedium)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					OrientationCondition.entries.forEach { condition ->
						ElevatedFilterChip(
							selected = orientationCondition == condition,
							onClick = {
								orientationCondition = condition
								if (condition == OrientationCondition.PORTRAIT) {
									stringSubfolderSearchPattern = "Portrait"
								} else if (condition == OrientationCondition.LANDSCAPE) {
									stringSubfolderSearchPattern = "Landscape"
								}
							},
							label = { Text(condition.name) }
						)
					}
				}

				// Fold Condition
				Text("Foldable Posture Condition:", style = MaterialTheme.typography.bodyMedium)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					FoldCondition.entries.forEach { condition ->
						ElevatedFilterChip(
							selected = foldCondition == condition,
							onClick = { foldCondition = condition },
							label = { Text(condition.name) }
						)
					}
				}

				// Charging Condition
				Text("Charging Condition:", style = MaterialTheme.typography.bodyMedium)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					ChargingCondition.entries.forEach { condition ->
						ElevatedFilterChip(
							selected = chargingCondition == condition,
							onClick = { chargingCondition = condition },
							label = { Text(condition.name) }
						)
					}
				}

				// Time Window Condition
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Limit to Time Window", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
						Text("Active only between specific times of day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					Switch(checked = boolTimeWindowEnabled, onCheckedChange = { boolTimeWindowEnabled = it })
				}
				if (boolTimeWindowEnabled) {
					val stringStartFormatted = String.format("%02d:%02d", intStartHour, intStartMinute)
					val stringEndFormatted = String.format("%02d:%02d", intEndHour, intEndMinute)

					Card(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(12.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp)
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								// Start Time Button
								OutlinedCard(
									onClick = {
										android.app.TimePickerDialog(
											context,
											{ _, hourOfDay, minute ->
												intStartHour = hourOfDay
												intStartMinute = minute
											},
											intStartHour,
											intStartMinute,
											true
										).show()
									},
									modifier = Modifier.weight(1f),
									shape = RoundedCornerShape(8.dp)
								) {
									Column(modifier = Modifier.padding(10.dp)) {
										Text("Start Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
										Spacer(modifier = Modifier.height(2.dp))
										Row(verticalAlignment = Alignment.CenterVertically) {
											Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
											Spacer(modifier = Modifier.width(6.dp))
											Text(stringStartFormatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
										}
									}
								}

								// End Time Button
								OutlinedCard(
									onClick = {
										android.app.TimePickerDialog(
											context,
											{ _, hourOfDay, minute ->
												intEndHour = hourOfDay
												intEndMinute = minute
											},
											intEndHour,
											intEndMinute,
											true
										).show()
									},
									modifier = Modifier.weight(1f),
									shape = RoundedCornerShape(8.dp)
								) {
									Column(modifier = Modifier.padding(10.dp)) {
										Text("End Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
										Spacer(modifier = Modifier.height(2.dp))
										Row(verticalAlignment = Alignment.CenterVertically) {
											Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
											Spacer(modifier = Modifier.width(6.dp))
											Text(stringEndFormatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
										}
									}
								}
							}

							Text("Tap above to open time picker, or adjust below:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

							// Start Time adjustment
							Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Text("Start: $stringStartFormatted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
									Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
										listOf(0, 15, 30, 45, 59).forEach { intMin ->
											SuggestionChip(
												onClick = { intStartMinute = intMin },
												label = { Text(":${intMin.toString().padStart(2, '0')}") }
											)
										}
									}
								}
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
									Slider(
										value = intStartHour.toFloat(),
										onValueChange = { intStartHour = it.toInt() },
										valueRange = 0f..23f,
										steps = 22,
										modifier = Modifier.weight(1f)
									)
									Text(intStartHour.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
								}
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text("M", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
									Slider(
										value = intStartMinute.toFloat(),
										onValueChange = { intStartMinute = it.toInt() },
										valueRange = 0f..59f,
										steps = 58,
										modifier = Modifier.weight(1f)
									)
									Text(intStartMinute.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
								}
							}

							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

							// End Time adjustment
							Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Text("End: $stringEndFormatted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
									Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
										listOf(0, 15, 30, 45, 59).forEach { intMin ->
											SuggestionChip(
												onClick = { intEndMinute = intMin },
												label = { Text(":${intMin.toString().padStart(2, '0')}") }
											)
										}
									}
								}
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
									Slider(
										value = intEndHour.toFloat(),
										onValueChange = { intEndHour = it.toInt() },
										valueRange = 0f..23f,
										steps = 22,
										modifier = Modifier.weight(1f)
									)
									Text(intEndHour.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
								}
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text("M", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
									Slider(
										value = intEndMinute.toFloat(),
										onValueChange = { intEndMinute = it.toInt() },
										valueRange = 0f..59f,
										steps = 58,
										modifier = Modifier.weight(1f)
									)
									Text(intEndMinute.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
								}
							}
						}
					}
				}

				// Geofence Condition
				if (listGeofences.isNotEmpty()) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text("Geofence Condition")
						Switch(checked = boolGeofenceEnabled, onCheckedChange = { boolGeofenceEnabled = it })
					}

					if (boolGeofenceEnabled) {
						Text("Select Geofence Area:", style = MaterialTheme.typography.bodySmall)
						FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							listGeofences.forEach { geo ->
								FilterChip(
									selected = stringSelectedGeofenceId == geo.stringId,
									onClick = { stringSelectedGeofenceId = geo.stringId },
									label = { Text(geo.stringName) }
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							FilterChip(
								selected = boolGeofenceInside,
								onClick = { boolGeofenceInside = true },
								label = { Text("When Inside Area") }
							)
							FilterChip(
								selected = !boolGeofenceInside,
								onClick = { boolGeofenceInside = false },
								label = { Text("When Outside Area") }
							)
						}
					}
				}

				// 7. Days of Week Condition
				HorizontalDivider()
				Text("Days of Week Condition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
				Text(
					text = if (listDaysOfWeek.isEmpty()) "Active on all days (No filter)" else "Active only on selected days of the week",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				val listDays = listOf(
					1 to "Mon",
					2 to "Tue",
					3 to "Wed",
					4 to "Thu",
					5 to "Fri",
					6 to "Sat",
					7 to "Sun"
				)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
					listDays.forEach { (intDay, stringLabel) ->
						val boolSelected = listDaysOfWeek.contains(intDay)
						FilterChip(
							selected = boolSelected,
							onClick = {
								listDaysOfWeek = if (boolSelected) {
									listDaysOfWeek - intDay
								} else {
									listDaysOfWeek + intDay
								}
							},
							label = { Text(stringLabel) }
						)
					}
					if (listDaysOfWeek.isNotEmpty()) {
						SuggestionChip(
							onClick = { listDaysOfWeek = emptyList() },
							label = { Text("Clear All Days") }
						)
					}
				}

				// 8. Months Condition
				HorizontalDivider()
				Text("Months Condition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
				Text(
					text = if (listMonths.isEmpty()) "Active all months (No filter)" else "Active only during selected months (e.g. October for Halloween)",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				val listMonthsData = listOf(
					1 to "Jan",
					2 to "Feb",
					3 to "Mar",
					4 to "Apr",
					5 to "May",
					6 to "Jun",
					7 to "Jul",
					8 to "Aug",
					9 to "Sep",
					10 to "Oct",
					11 to "Nov",
					12 to "Dec"
				)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
					listMonthsData.forEach { (intM, stringLabel) ->
						val boolSelected = listMonths.contains(intM)
						FilterChip(
							selected = boolSelected,
							onClick = {
								listMonths = if (boolSelected) {
									listMonths - intM
								} else {
									listMonths + intM
								}
							},
							label = { Text(stringLabel) }
						)
					}
					if (listMonths.isNotEmpty()) {
						SuggestionChip(
							onClick = { listMonths = emptyList() },
							label = { Text("Clear All Months") }
						)
					}
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					val stringFinalName = stringName.ifBlank { "Folder Set" }
					val stringId = objInitialSet?.stringId ?: UUID.randomUUID().toString()
					val objResult = FolderSet(
						stringId = stringId,
						stringName = stringFinalName,
						boolIsEnabled = boolIsEnabled,
						listFolderUris = listUris,
						boolQuickTileEnabled = boolQuickTile,
						intIntervalMinutes = floatIntervalMinutes.toInt(),
						boolChangeOnRotate = boolChangeOnRotate,
						boolChangeOnFold = boolChangeOnFold,
						boolChangeOnCharging = boolChangeOnCharging,
						boolChangeOnDoubleTap = boolDoubleTap,
						wallpaperTarget = targetScreen,
						orientationCondition = orientationCondition,
						foldCondition = foldCondition,
						chargingCondition = chargingCondition,
						timeWindowCondition = TimeWindowCondition(
							boolEnabled = boolTimeWindowEnabled,
							intStartHour = intStartHour,
							intStartMinute = intStartMinute,
							intEndHour = intEndHour,
							intEndMinute = intEndMinute
						),
						geofenceCondition = GeofenceCondition(
							boolEnabled = boolGeofenceEnabled,
							stringGeofenceId = stringSelectedGeofenceId,
							boolInside = boolGeofenceInside
						),
						listDaysOfWeek = listDaysOfWeek,
						listMonths = listMonths
					)
					onSave(objResult)
				}
			) {
				Text("Save Set")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)

	if (boolShowScanTreeDialog) {
		AlertDialog(
			onDismissRequest = {
				if (!boolIsScanningTree) {
					boolShowScanTreeDialog = false
				}
			},
			title = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = "Scan & Add Subfolders",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
				}
			},
			text = {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 480.dp)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Text(
						text = "Select a grandparent or parent folder (e.g. Pictures/Normal). The app will search recursively through all subdirectories and find any folder matching the target name.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)

					OutlinedTextField(
						value = stringSubfolderSearchPattern,
						onValueChange = { stringSubfolderSearchPattern = it },
						label = { Text("Subfolder Name to Find") },
						placeholder = { Text("e.g. Portrait or Landscape") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						listOf("Portrait", "Landscape", "Square").forEach { stringPreset ->
							FilterChip(
								selected = stringSubfolderSearchPattern.equals(stringPreset, ignoreCase = true),
								onClick = {
									stringSubfolderSearchPattern = stringPreset
									if (stringPreset.equals("Portrait", ignoreCase = true)) {
										orientationCondition = OrientationCondition.PORTRAIT
									} else if (stringPreset.equals("Landscape", ignoreCase = true)) {
										orientationCondition = OrientationCondition.LANDSCAPE
									}
								},
								label = { Text(stringPreset) }
							)
						}
					}

					// Root folder selector for scanning
					Surface(
						shape = RoundedCornerShape(8.dp),
						color = MaterialTheme.colorScheme.surfaceVariant,
						modifier = Modifier.fillMaxWidth()
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 10.dp, vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text("Root Folder to Scan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
								Text(
									text = stringTreeRootPath,
									style = MaterialTheme.typography.bodySmall,
									fontWeight = FontWeight.Medium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							Spacer(modifier = Modifier.width(6.dp))
							OutlinedButton(
								onClick = { boolShowTreeRootPicker = true },
								enabled = !boolIsScanningTree
							) {
								Text("Change")
							}
						}
					}

					Button(
						onClick = { startTreeScan(stringTreeRootPath) },
						enabled = !boolIsScanningTree && stringSubfolderSearchPattern.isNotBlank(),
						modifier = Modifier.fillMaxWidth()
					) {
						Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = if (boolIsScanningTree) "Scanning ($intScannedFoldersCount)..." else "Scan Directory Tree"
						)
					}

					if (boolIsScanningTree) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 8.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp)
						) {
							LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
							Text(
								text = stringScanStatus ?: "Scanning directory tree...",
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold
							)
							if (stringScanCurrentFolder.isNotEmpty()) {
								Text(
									text = "Currently scanning: $stringScanCurrentFolder",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
					}

					if (!stringScanStatus.isNullOrBlank() && !boolIsScanningTree) {
						Card(
							colors = CardDefaults.cardColors(
								containerColor = if (listScannedResults.isEmpty()) {
									MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
								} else {
									MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
								}
							),
							modifier = Modifier.fillMaxWidth()
						) {
							Text(
								text = stringScanStatus.orEmpty(),
								style = MaterialTheme.typography.bodySmall,
								modifier = Modifier.padding(10.dp)
							)
						}
					}

					if (listScannedResults.isNotEmpty()) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = "Select folders to add:",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold
							)
							Row {
								TextButton(
									onClick = { setCheckedUris = listScannedResults.map { it.stringUri }.toSet() }
								) {
									Text("Select All")
								}
								TextButton(
									onClick = { setCheckedUris = emptySet() }
								) {
									Text("None")
								}
							}
						}

						Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
							listScannedResults.forEach { info ->
								val boolChecked = setCheckedUris.contains(info.stringUri)
								Card(
									colors = CardDefaults.cardColors(
										containerColor = if (boolChecked) {
											MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
										} else {
											MaterialTheme.colorScheme.surfaceVariant
										}
									),
									modifier = Modifier.fillMaxWidth()
								) {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 8.dp, vertical = 4.dp),
										verticalAlignment = Alignment.CenterVertically
									) {
										Checkbox(
											checked = boolChecked,
											onCheckedChange = { checked ->
												setCheckedUris = if (checked) {
													setCheckedUris + info.stringUri
												} else {
													setCheckedUris - info.stringUri
												}
											}
										)
										Spacer(modifier = Modifier.width(4.dp))
										Column(modifier = Modifier.weight(1f)) {
											Text(
												text = info.stringRelativePath,
												style = MaterialTheme.typography.bodyMedium,
												fontWeight = FontWeight.Medium
											)
											Text(
												text = "${info.intImageCount} image(s)",
												style = MaterialTheme.typography.bodySmall,
												color = MaterialTheme.colorScheme.onSurfaceVariant
											)
										}
									}
								}
							}
						}
					}
				}
			},
			confirmButton = {
				Button(
					onClick = {
						val listNewUris = (listUris + setCheckedUris).distinct()
						listUris = listNewUris
						boolShowScanTreeDialog = false
					},
					enabled = setCheckedUris.isNotEmpty() && !boolIsScanningTree
				) {
					Text("Add Selected (${setCheckedUris.size})")
				}
			},
			dismissButton = {
				TextButton(
					onClick = { boolShowScanTreeDialog = false },
					enabled = !boolIsScanningTree
				) {
					Text("Cancel")
				}
			}
		)
	}

	if (boolShowFolderPicker) {
		FolderPickerDialog(
			onDismiss = { boolShowFolderPicker = false },
			onFolderSelected = { stringSelectedFolder ->
				boolShowFolderPicker = false
				if (!listUris.contains(stringSelectedFolder)) {
					listUris = listUris + stringSelectedFolder
				}
			}
		)
	}

	if (boolShowTreeRootPicker) {
		FolderPickerDialog(
			stringInitialPath = stringTreeRootPath,
			onDismiss = { boolShowTreeRootPicker = false },
			onFolderSelected = { stringSelectedFolder ->
				boolShowTreeRootPicker = false
				stringTreeRootPath = stringSelectedFolder
			}
		)
	}
}

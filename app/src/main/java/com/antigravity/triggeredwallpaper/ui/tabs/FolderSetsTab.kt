package com.antigravity.triggeredwallpaper.ui.tabs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredwallpaper.model.ChargingCondition
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.ui.dialogs.FolderSetEditDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderSetsTab(
	listFolderSets: List<FolderSet>,
	listGeofences: List<GeofenceArea>,
	mapSetImageCounts: Map<String, Int>,
	onSaveSet: (FolderSet) -> Unit,
	onDuplicateSet: (FolderSet) -> Unit,
	onDeleteSet: (String) -> Unit,
	onAddFolderUri: (FolderSet, Uri) -> Unit,
	onToggleSetEnabled: (FolderSet, Boolean) -> Unit
) {
	var boolShowEditDialog by remember { mutableStateOf(false) }
	var objEditingSet by remember { mutableStateOf<FolderSet?>(null) }
	var stringSearchQuery by remember { mutableStateOf("") }
	var stringSelectedFilter by remember { mutableStateOf("All") }

	val listFilterOptions = listOf(
		"All",
		"Home Screen",
		"Lock Screen",
		"Both Screens",
		"Quick Tile Trigger",
		"Interval Timer",
		"Double-Tap",
		"Portrait",
		"Landscape",
		"Folded",
		"Charging",
		"Time Window",
		"Days of Week",
		"Months",
		"Geofence",
		"Enabled Only",
		"Disabled Only"
	)

	// Filter & Sort A-Z by name
	val listDisplaySets = remember(listFolderSets, stringSearchQuery, stringSelectedFilter) {
		listFolderSets
			.filter { objSet ->
				if (stringSearchQuery.isBlank()) true
				else objSet.stringName.contains(stringSearchQuery.trim(), ignoreCase = true)
			}
			.filter { objSet ->
				when (stringSelectedFilter) {
					"Home Screen" -> objSet.wallpaperTarget == WallpaperTarget.HOME
					"Lock Screen" -> objSet.wallpaperTarget == WallpaperTarget.LOCK
					"Both Screens" -> objSet.wallpaperTarget == WallpaperTarget.BOTH
					"Quick Tile Trigger" -> objSet.boolQuickTileEnabled
					"Interval Timer" -> objSet.intIntervalMinutes > 0
					"Double-Tap" -> objSet.boolChangeOnDoubleTap
					"Portrait" -> objSet.orientationCondition == OrientationCondition.PORTRAIT
					"Landscape" -> objSet.orientationCondition == OrientationCondition.LANDSCAPE
					"Folded" -> objSet.foldCondition != FoldCondition.ANY
					"Charging" -> objSet.chargingCondition != ChargingCondition.ANY
					"Time Window" -> objSet.timeWindowCondition.boolEnabled
					"Days of Week" -> objSet.listDaysOfWeek.isNotEmpty()
					"Months" -> objSet.listMonths.isNotEmpty()
					"Geofence" -> objSet.geofenceCondition.boolEnabled
					"Enabled Only" -> objSet.boolIsEnabled
					"Disabled Only" -> !objSet.boolIsEnabled
					else -> true
				}
			}
			.sortedBy { it.stringName.lowercase() }
	}

	Scaffold(
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					objEditingSet = null
					boolShowEditDialog = true
				},
				containerColor = MaterialTheme.colorScheme.primaryContainer,
				contentColor = MaterialTheme.colorScheme.onPrimaryContainer
			) {
				Icon(Icons.Default.Add, contentDescription = "Add Folder Set")
			}
		}
	) { innerPadding ->
		if (listFolderSets.isEmpty()) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding)
					.padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Icon(
					Icons.Default.Folder,
					contentDescription = null,
					modifier = Modifier.size(64.dp),
					tint = MaterialTheme.colorScheme.primary
				)
				Spacer(modifier = Modifier.height(16.dp))
				Text(
					text = "No Folder Sets Yet",
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Bold
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = "Create your first folder set to attach triggers and conditions.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(modifier = Modifier.height(20.dp))
				Button(
					onClick = {
						objEditingSet = null
						boolShowEditDialog = true
					}
				) {
					Icon(Icons.Default.Add, contentDescription = null)
					Spacer(modifier = Modifier.width(6.dp))
					Text("Create Folder Set")
				}
			}
		} else {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding)
					.padding(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				// Search by Name Header
				item {
					OutlinedTextField(
						value = stringSearchQuery,
						onValueChange = { stringSearchQuery = it },
						placeholder = { Text("Search sets by name...") },
						leadingIcon = {
							Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
						},
						trailingIcon = {
							if (stringSearchQuery.isNotEmpty()) {
								IconButton(onClick = { stringSearchQuery = "" }) {
									Icon(Icons.Default.Clear, contentDescription = "Clear search")
								}
							}
						},
						singleLine = true,
						modifier = Modifier
							.fillMaxWidth()
							.padding(bottom = 4.dp)
					)
				}

				// Filter Chips Horizontal Row
				item {
					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						modifier = Modifier.fillMaxWidth()
					) {
						items(listFilterOptions) { stringFilter ->
							FilterChip(
								selected = stringSelectedFilter == stringFilter,
								onClick = { stringSelectedFilter = stringFilter },
								label = { Text(stringFilter, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
				}

				// Count label
				item {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = "${listDisplaySets.size} set(s) (A-Z)",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							fontWeight = FontWeight.SemiBold
						)
						if (stringSearchQuery.isNotBlank() || stringSelectedFilter != "All") {
							Text(
								text = "Filtered",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary
							)
						}
					}
				}

				if (listDisplaySets.isEmpty()) {
					item {
						Card(
							modifier = Modifier.fillMaxWidth(),
							colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
						) {
							Text(
								text = "No folder sets match your search or filter.",
								style = MaterialTheme.typography.bodyMedium,
								modifier = Modifier.padding(16.dp)
							)
						}
					}
				} else {
					items(listDisplaySets, key = { it.stringId }) { objSet ->
						FolderSetCard(
							objSet = objSet,
							intImageCount = mapSetImageCounts[objSet.stringId] ?: 0,
							onEdit = {
								objEditingSet = objSet
								boolShowEditDialog = true
							},
							onDuplicate = {
								onDuplicateSet(objSet)
							},
							onDelete = {
								onDeleteSet(objSet.stringId)
							},
							onToggleEnabled = { boolEnabled ->
								onToggleSetEnabled(objSet, boolEnabled)
							}
						)
					}
				}
			}
		}
	}

	if (boolShowEditDialog) {
		FolderSetEditDialog(
			objInitialSet = objEditingSet,
			listGeofences = listGeofences,
			onDismiss = { boolShowEditDialog = false },
			onSave = { objSaved ->
				onSaveSet(objSaved)
				boolShowEditDialog = false
			},
			onAddFolderUri = onAddFolderUri
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderSetCard(
	objSet: FolderSet,
	intImageCount: Int,
	onEdit: () -> Unit,
	onDuplicate: () -> Unit,
	onDelete: () -> Unit,
	onToggleEnabled: (Boolean) -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = if (objSet.boolIsEnabled) MaterialTheme.colorScheme.surfaceVariant
				else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
		)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			// Header
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = objSet.stringName,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = if (objSet.boolIsEnabled) MaterialTheme.colorScheme.onSurface
							else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
					)
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = if (objSet.boolIsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
								else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
						) {
							Text(
								text = objSet.wallpaperTarget.name,
								style = MaterialTheme.typography.labelSmall,
								color = if (objSet.boolIsEnabled) MaterialTheme.colorScheme.primary
									else MaterialTheme.colorScheme.outline,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
							)
						}
						if (!objSet.boolIsEnabled) {
							Surface(
								shape = RoundedCornerShape(6.dp),
								color = MaterialTheme.colorScheme.errorContainer
							) {
								Text(
									text = "DISABLED",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onErrorContainer,
									fontWeight = FontWeight.Bold,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
								)
							}
						}
						Text(
							text = "${objSet.listFolderUris.size} folder(s) • $intImageCount image(s)",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}

				Row(verticalAlignment = Alignment.CenterVertically) {
					Switch(
						checked = objSet.boolIsEnabled,
						onCheckedChange = onToggleEnabled,
						modifier = Modifier.padding(end = 4.dp)
					)
					IconButton(onClick = onDuplicate) {
						Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Set", tint = MaterialTheme.colorScheme.secondary)
					}
					IconButton(onClick = onEdit) {
						Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
					}
					IconButton(onClick = onDelete) {
						Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
					}
				}
			}

			// Conditions chips
			Text("Conditions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				if (objSet.orientationCondition != OrientationCondition.ANY) {
					SuggestionChip(
						onClick = {},
						label = { Text("Orientation: ${objSet.orientationCondition.name}") }
					)
				}
				if (objSet.foldCondition != FoldCondition.ANY) {
					SuggestionChip(
						onClick = {},
						label = { Text("Fold: ${objSet.foldCondition.name}") }
					)
				}
				if (objSet.chargingCondition != ChargingCondition.ANY) {
					SuggestionChip(
						onClick = {},
						label = { Text("Charging: ${objSet.chargingCondition.name}") }
					)
				}
				if (objSet.timeWindowCondition.boolEnabled) {
					val stringStartTime = String.format("%02d:%02d", objSet.timeWindowCondition.intStartHour, objSet.timeWindowCondition.intStartMinute)
					val stringEndTime = String.format("%02d:%02d", objSet.timeWindowCondition.intEndHour, objSet.timeWindowCondition.intEndMinute)
					SuggestionChip(
						onClick = {},
						label = { Text("Time: $stringStartTime - $stringEndTime") }
					)
				}
				if (objSet.listDaysOfWeek.isNotEmpty()) {
					val mapDays = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
					val stringDays = objSet.listDaysOfWeek.sorted().mapNotNull { mapDays[it] }.joinToString(", ")
					SuggestionChip(
						onClick = {},
						label = { Text("Days: $stringDays") }
					)
				}
				if (objSet.listMonths.isNotEmpty()) {
					val mapMonths = mapOf(
						1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr", 5 to "May", 6 to "Jun",
						7 to "Jul", 8 to "Aug", 9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
					)
					val stringMonths = objSet.listMonths.sorted().mapNotNull { mapMonths[it] }.joinToString(", ")
					SuggestionChip(
						onClick = {},
						label = { Text("Months: $stringMonths") }
					)
				}
				if (objSet.geofenceCondition.boolEnabled) {
					val stringInside = if (objSet.geofenceCondition.boolInside) "Inside" else "Outside"
					SuggestionChip(
						onClick = {},
						label = { Text("Geofence: $stringInside") }
					)
				}
				if (objSet.orientationCondition == OrientationCondition.ANY &&
					objSet.foldCondition == FoldCondition.ANY &&
					objSet.chargingCondition == ChargingCondition.ANY &&
					!objSet.timeWindowCondition.boolEnabled &&
					objSet.listDaysOfWeek.isEmpty() &&
					objSet.listMonths.isEmpty() &&
					!objSet.geofenceCondition.boolEnabled
				) {
					SuggestionChip(
						onClick = {},
						label = { Text("Always active (Default)") }
					)
				}
			}

			// Triggers chips
			Text("Triggers:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				if (objSet.intIntervalMinutes > 0) {
					SuggestionChip(
						onClick = {},
						label = { Text("Interval: ${objSet.intIntervalMinutes}m") },
						colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
					)
				}
				if (objSet.boolChangeOnDoubleTap) {
					SuggestionChip(onClick = {}, label = { Text("Double-Tap") })
				}
				if (objSet.boolQuickTileEnabled) {
					SuggestionChip(
						onClick = {},
						label = { Text("Quick Tile Trigger") },
						colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
					)
				}
				if (objSet.boolChangeOnRotate) {
					SuggestionChip(onClick = {}, label = { Text("On Rotate") })
				}
				if (objSet.boolChangeOnFold) {
					SuggestionChip(onClick = {}, label = { Text("On Fold/Unfold") })
				}
				if (objSet.boolChangeOnCharging) {
					SuggestionChip(onClick = {}, label = { Text("On Plug/Unplug") })
				}
			}
		}
	}
}

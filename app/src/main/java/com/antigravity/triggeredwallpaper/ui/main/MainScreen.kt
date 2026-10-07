package com.antigravity.triggeredwallpaper.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.antigravity.triggeredwallpaper.ui.tabs.ConflictsTab
import com.antigravity.triggeredwallpaper.ui.tabs.FolderSetsTab
import com.antigravity.triggeredwallpaper.ui.tabs.SettingsTab
import com.antigravity.triggeredwallpaper.ui.tabs.StatusTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	onItemClick: ((NavKey) -> Unit)? = null,
	modifier: Modifier = Modifier,
	viewModel: MainScreenViewModel = viewModel()
) {
	val context = LocalContext.current
	val uiState by viewModel.flowUiState.collectAsStateWithLifecycle()
	var intSelectedTab by rememberSaveable { mutableIntStateOf(0) }

	val stringVersionName = remember(context) {
		try {
			val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
			packageInfo.versionName ?: "1.0.0"
		} catch (e: Exception) {
			"1.0.0"
		}
	}

	Scaffold(
		modifier = modifier,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "Triggered Live Wallpaper v$stringVersionName",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface
				)
			)
		},
		bottomBar = {
			NavigationBar {
				NavigationBarItem(
					selected = intSelectedTab == 0,
					onClick = { intSelectedTab = 0 },
					icon = { Icon(Icons.Default.Wallpaper, contentDescription = "Status") },
					label = { Text("Status") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 1,
					onClick = { intSelectedTab = 1 },
					icon = { Icon(Icons.Default.Folder, contentDescription = "Sets") },
					label = { Text("Sets") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 2,
					onClick = { intSelectedTab = 2 },
					icon = {
						if (uiState.listConflicts.isNotEmpty()) {
							BadgedBox(badge = {
								Badge { Text(uiState.listConflicts.size.toString()) }
							}) {
								Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Conflicts")
							}
						} else {
							Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Rules")
						}
					},
					label = { Text("Rules") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 3,
					onClick = { intSelectedTab = 3 },
					icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
					label = { Text("Settings") }
				)
			}
		}
	) { innerPadding ->
		if (uiState.boolIsLoading) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}
		} else {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding)
			) {
				when (intSelectedTab) {
					0 -> StatusTab(
						objSettings = uiState.objSettings,
						listFolderSets = uiState.listFolderSets,
						listGeofences = uiState.listGeofences,
						onTriggerShuffle = { viewModel.triggerNextWallpaper(context) },
						onUpdateSettings = { viewModel.updateSettings(it) }
					)
					1 -> FolderSetsTab(
						listFolderSets = uiState.listFolderSets,
						listGeofences = uiState.listGeofences,
						mapSetImageCounts = uiState.mapSetImageCounts,
						onSaveSet = { viewModel.saveFolderSet(it) },
						onDuplicateSet = { viewModel.duplicateFolderSet(it) },
						onDeleteSet = { viewModel.deleteFolderSet(it) },
						onAddFolderUri = { objSet, uri -> viewModel.addFolderToSet(objSet, uri, context) },
						onToggleSetEnabled = { objSet, boolEnabled -> viewModel.toggleFolderSetEnabled(objSet, boolEnabled) }
					)
					2 -> ConflictsTab(
						listConflicts = uiState.listConflicts,
						listFolderSets = uiState.listFolderSets
					)
					3 -> SettingsTab(
						objSettings = uiState.objSettings,
						listGeofences = uiState.listGeofences,
						onUpdateSettings = { viewModel.updateSettings(it) },
						onSaveGeofence = { viewModel.saveGeofence(it) },
						onDeleteGeofence = { viewModel.deleteGeofence(it) },
						onExportConfig = { viewModel.exportConfiguration(it) },
						onImportConfig = { json, cb -> viewModel.importConfiguration(json, cb) }
					)
				}
			}
		}
	}
}

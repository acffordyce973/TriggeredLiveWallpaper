package com.antigravity.triggeredwallpaper.ui.tabs

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.antigravity.triggeredwallpaper.engine.ConditionEvaluator
import com.antigravity.triggeredwallpaper.engine.DeviceStateManager
import com.antigravity.triggeredwallpaper.model.AppSettings
import com.antigravity.triggeredwallpaper.model.DeviceState
import com.antigravity.triggeredwallpaper.model.FoldCondition
import com.antigravity.triggeredwallpaper.model.FolderSet
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.model.OrientationCondition
import com.antigravity.triggeredwallpaper.model.OverlayPosition
import com.antigravity.triggeredwallpaper.model.WallpaperTarget
import com.antigravity.triggeredwallpaper.service.TriggeredWallpaperService
import com.antigravity.triggeredwallpaper.ui.OpenImageActivity
import java.util.Calendar

@Composable
fun StatusTab(
	objSettings: AppSettings,
	listFolderSets: List<FolderSet>,
	listGeofences: List<GeofenceArea> = emptyList(),
	onTriggerShuffle: () -> Unit,
	onUpdateSettings: (transform: (AppSettings) -> AppSettings) -> Unit = {}
) {
	val context = LocalContext.current
	val config = LocalConfiguration.current
	val boolIsLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
	val calendar = Calendar.getInstance()
	val stringTimeNow = String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

	// Check if live wallpaper is actually bound/active on the system
	val wallpaperManager = remember { WallpaperManager.getInstance(context) }
	val lifecycleOwner = LocalLifecycleOwner.current
	var boolIsLiveWallpaperActive by remember {
		mutableStateOf(
			try {
				wallpaperManager.wallpaperInfo?.packageName == context.packageName ||
					TriggeredWallpaperService.isEngineActive()
			} catch (e: Exception) {
				TriggeredWallpaperService.isEngineActive()
			}
		)
	}

	val orientation = if (boolIsLandscape) OrientationCondition.LANDSCAPE else OrientationCondition.PORTRAIT
	var objDeviceState by remember {
		mutableStateOf(
			DeviceStateManager.getDeviceState(context).copy(
				orientationCondition = orientation
			)
		)
	}

	val flowFoldCondition by DeviceStateManager.flowFoldCondition.collectAsStateWithLifecycle()
	val flowLocation by DeviceStateManager.flowLocation.collectAsStateWithLifecycle()

	LaunchedEffect(orientation, flowFoldCondition, flowLocation) {
		objDeviceState = DeviceStateManager.getDeviceState(context).copy(
			orientationCondition = orientation
		)
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				boolIsLiveWallpaperActive = try {
					wallpaperManager.wallpaperInfo?.packageName == context.packageName ||
						TriggeredWallpaperService.isEngineActive()
				} catch (e: Exception) {
					TriggeredWallpaperService.isEngineActive()
				}
				objDeviceState = DeviceStateManager.getDeviceState(context).copy(
					orientationCondition = orientation
				)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	val listEnabledSets = listFolderSets.filter { it.boolIsEnabled }
	val boolHasHomeSets = listEnabledSets.any { it.wallpaperTarget == WallpaperTarget.HOME || it.wallpaperTarget == WallpaperTarget.BOTH }
	val boolHasLockSets = listEnabledSets.any { it.wallpaperTarget == WallpaperTarget.LOCK || it.wallpaperTarget == WallpaperTarget.BOTH }

	val objActiveHomeSet = ConditionEvaluator.evaluateMatchingSet(
		objDeviceState,
		WallpaperTarget.HOME,
		listEnabledSets,
		listGeofences,
		objSettings
	)

	val objActiveLockSet = if (boolHasLockSets && objSettings.boolSeparateHomeAndLock) {
		ConditionEvaluator.evaluateMatchingSet(
			objDeviceState,
			WallpaperTarget.LOCK,
			listEnabledSets,
			listGeofences,
			objSettings
		)
	} else if (boolHasLockSets && !objSettings.boolSeparateHomeAndLock) {
		objActiveHomeSet
	} else {
		null
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		// Quick Settings Tile Override Banner
		if (objSettings.boolQuickTileActive) {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
			) {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						Icons.Default.Bolt,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onTertiaryContainer,
						modifier = Modifier.size(28.dp)
					)
					Spacer(modifier = Modifier.width(12.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Quick Settings Tile Override Active",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = "Wallpaper is locked to the Quick Tile set override. Other conditions and triggers are bypassed.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onTertiaryContainer
						)
					}
					TextButton(
						onClick = {
							onUpdateSettings { it.copy(boolQuickTileActive = false) }
						}
					) {
						Text("Turn Off")
					}
				}
			}
		}

		// Live Wallpaper Activation Warning Banner
		if (!boolIsLiveWallpaperActive) {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
			) {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						Icons.Default.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onErrorContainer,
						modifier = Modifier.size(28.dp)
					)
					Spacer(modifier = Modifier.width(12.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Live Wallpaper Not Activated",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onErrorContainer
						)
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = "Triggered Wallpaper is not active as your current wallpaper. Tap 'Set Live Wallpaper' below and choose 'Home screen' (or 'Home and lock screens') to apply it.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onErrorContainer
						)
					}
				}
			}
		}

		// 1. Actions Card
		Card(
			modifier = Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Text(
					text = "Triggered Live Wallpaper",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onPrimaryContainer
				)
				Text(
					text = "Images adapt automatically based on rotation, foldable state, location geofences, charging, and time.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
				)

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Button(
						onClick = {
							setLiveWallpaperIntent(context)
						},
						modifier = Modifier.weight(1f)
					) {
						Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(modifier = Modifier.width(6.dp))
						Text("Set Live Wallpaper")
					}

					OutlinedButton(
						onClick = onTriggerShuffle,
						colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
					) {
						Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(modifier = Modifier.width(4.dp))
						Text("Shuffle")
					}
				}
			}
		}

		// 2. Wallpaper Previews
		val stringPreviewSectionTitle = when {
			boolHasHomeSets && !boolHasLockSets -> "Home Screen Preview"
			!boolHasHomeSets && boolHasLockSets -> "Lock Screen Preview"
			objSettings.boolSeparateHomeAndLock -> "Screen Previews (Home & Lock are Separate)"
			else -> "Screen Preview (Shared Home & Lock)"
		}

		Text(
			text = stringPreviewSectionTitle,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			// Home Screen Preview (if any set targets home or both)
			if (boolHasHomeSets) {
				val stringHomeSetName = objActiveHomeSet?.stringName
					?: "Conditions Not Met"

				ScreenPreviewBox(
					modifier = if (boolHasLockSets) Modifier.weight(1f) else Modifier.weight(1f),
					stringTitle = "Home Screen",
					stringSetName = stringHomeSetName,
					stringImageUri = objSettings.stringCurrentHomeImageUri,
					stringImageName = objSettings.stringCurrentHomeImageName ?: "",
					stringPath = objSettings.stringCurrentHomeImagePath ?: "",
					boolShowOverlay = objSettings.boolShowLabelOverlay,
					boolIsActive = objActiveHomeSet != null,
					overlayPosition = objSettings.overlayPosition,
					intOverlayVerticalOffsetDp = objSettings.intOverlayVerticalOffsetDp
				)
			}

			// Lock Screen Preview (ONLY if sets target Lock or Both)
			if (boolHasLockSets) {
				val stringLockSetName = objActiveLockSet?.stringName
					?: "Conditions Not Met"

				ScreenPreviewBox(
					modifier = Modifier.weight(1f),
					stringTitle = "Lock Screen",
					stringSetName = stringLockSetName,
					stringImageUri = objSettings.stringCurrentLockImageUri,
					stringImageName = objSettings.stringCurrentLockImageName ?: "",
					stringPath = objSettings.stringCurrentLockImagePath ?: "",
					boolShowOverlay = objSettings.boolShowLabelOverlay,
					boolIsActive = objActiveLockSet != null,
					overlayPosition = objSettings.overlayPosition,
					intOverlayVerticalOffsetDp = objSettings.intOverlayVerticalOffsetDp
				)
			} else if (boolHasHomeSets) {
				// Explanatory card informing the user why Lock Screen is not displayed
				Card(
					modifier = Modifier.weight(1f),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(
								Icons.Default.Lock,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
							Spacer(modifier = Modifier.width(8.dp))
							Text(
								text = "Lock Screen",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold
							)
						}
						Text(
							text = "All of your active folder sets target Home Screen only.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						Text(
							text = "The lock screen wallpaper will not be modified unless a folder set is configured for 'LOCK' or 'BOTH'.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
						)
					}
				}
			}
		}

		// 3. Live Device State Monitor
		Text(
			text = "Live Device Trigger Conditions",
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)

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
				val stringTargetDisplay = when {
					boolHasHomeSets && !boolHasLockSets -> "Home Screen Only"
					!boolHasHomeSets && boolHasLockSets -> "Lock Screen Only"
					boolHasHomeSets && boolHasLockSets && objSettings.boolSeparateHomeAndLock -> "Separate Home & Lock"
					boolHasHomeSets && boolHasLockSets -> "Shared Home & Lock"
					else -> "No Active Sets"
				}

				val stringFoldDisplay = when {
					!objDeviceState.boolSupportsFold -> "Standard (Non-Foldable)"
					objDeviceState.foldCondition == FoldCondition.UNFOLDED -> "Unfolded"
					objDeviceState.foldCondition == FoldCondition.FOLDED -> "Folded"
					objDeviceState.foldCondition == FoldCondition.HALF_OPENED -> "Half-Opened (Flex)"
					else -> "Any / Not Detected"
				}

				val listInsideGeofences = remember(objDeviceState.doubleLatitude, objDeviceState.doubleLongitude, listGeofences) {
					val curLat = objDeviceState.doubleLatitude
					val curLng = objDeviceState.doubleLongitude
					if (curLat != null && curLng != null) {
						listGeofences.filter { objGeo ->
							val arrayDistances = FloatArray(1)
							Location.distanceBetween(curLat, curLng, objGeo.doubleLatitude, objGeo.doubleLongitude, arrayDistances)
							arrayDistances[0] <= objGeo.floatRadiusMeters
						}
					} else {
						emptyList()
					}
				}
				val boolInsideGeofence = listInsideGeofences.isNotEmpty()

				val stringLocationDisplay = when {
					objDeviceState.doubleLatitude == null || objDeviceState.doubleLongitude == null -> {
						if (objDeviceState.boolSupportsLocation) "Waiting for location..." else "Location disabled"
					}
					boolInsideGeofence -> {
						"Inside: ${listInsideGeofences.joinToString { it.stringName }}"
					}
					listGeofences.isNotEmpty() -> {
						"Outside defined geofences"
					}
					else -> {
						String.format(java.util.Locale.US, "%.4f, %.4f", objDeviceState.doubleLatitude, objDeviceState.doubleLongitude)
					}
				}

				val boolIsDark = isSystemInDarkTheme()

				StateRowItem(
					icon = Icons.Default.ScreenRotation,
					stringLabel = "Screen Orientation",
					stringValue = if (boolIsLandscape) "Landscape" else "Portrait"
				)
				StateRowItem(
					icon = Icons.Default.Devices,
					stringLabel = "Fold Posture",
					stringValue = stringFoldDisplay
				)
				StateRowItem(
					icon = Icons.Default.LocationOn,
					stringLabel = "Current Location",
					stringValue = stringLocationDisplay,
					boolIsHighlighted = boolInsideGeofence,
					containerColor = if (boolInsideGeofence) (if (boolIsDark) Color(0xFF1B4D2E) else Color(0xFFC8E6C9)) else null,
					contentColor = if (boolInsideGeofence) (if (boolIsDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)) else null
				)
				StateRowItem(
					icon = Icons.Default.StayCurrentPortrait,
					stringLabel = "Screen Target",
					stringValue = stringTargetDisplay
				)
				StateRowItem(
					icon = Icons.Default.Wallpaper,
					stringLabel = "Active Home Set",
					stringValue = objActiveHomeSet?.stringName ?: if (boolHasHomeSets) "Conditions Not Met" else "None"
				)
				if (boolHasLockSets) {
					StateRowItem(
						icon = Icons.Default.Lock,
						stringLabel = "Active Lock Set",
						stringValue = objActiveLockSet?.stringName ?: "Conditions Not Met"
					)
				}
				StateRowItem(
					icon = Icons.Default.BatteryChargingFull,
					stringLabel = "Power Connection",
					stringValue = if (objDeviceState.boolCharging) "Charging (AC/USB)" else "Battery"
				)
				StateRowItem(
					icon = Icons.Default.Refresh,
					stringLabel = "Current Local Time",
					stringValue = "$stringTimeNow (Day ${objDeviceState.intDayOfWeek}, Month ${objDeviceState.intMonth})"
				)
			}
		}
	}
}

@Composable
fun ScreenPreviewBox(
	modifier: Modifier = Modifier,
	stringTitle: String,
	stringSetName: String,
	stringImageUri: String?,
	stringImageName: String,
	stringPath: String,
	boolShowOverlay: Boolean,
	boolIsActive: Boolean,
	overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
	intOverlayVerticalOffsetDp: Int = 0
) {
	val context = LocalContext.current
	val onOpenImage = {
		if (!stringImageUri.isNullOrEmpty()) {
			val intentOpen = Intent(context, OpenImageActivity::class.java).apply {
				putExtra(OpenImageActivity.EXTRA_IMAGE_URI, stringImageUri)
			}
			context.startActivity(intentOpen)
		}
	}

	Card(
		modifier = modifier,
		shape = RoundedCornerShape(16.dp),
		elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
	) {
		Column {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(9f / 16f)
					.background(Color(0xFF141824))
					.then(if (!stringImageUri.isNullOrEmpty()) Modifier.clickable { onOpenImage() } else Modifier),
				contentAlignment = Alignment.Center
			) {
				if (!stringImageUri.isNullOrEmpty()) {
					AsyncImage(
						model = Uri.parse(stringImageUri),
						contentDescription = stringImageName,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize()
					)
				} else {
					Column(
						modifier = Modifier
							.align(Alignment.Center)
							.padding(12.dp),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						Icon(
							Icons.Default.Wallpaper,
							contentDescription = null,
							modifier = Modifier.size(36.dp),
							tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
						)
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = stringSetName,
							style = MaterialTheme.typography.labelMedium,
							color = Color.White,
							fontWeight = FontWeight.Bold
						)
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = if (boolIsActive) "Ready to trigger" else "Conditions not met",
							style = MaterialTheme.typography.labelSmall,
							color = Color.White.copy(alpha = 0.7f)
						)
					}
				}

				// Live Label Overlay Preview
				if (boolShowOverlay && stringImageName.isNotEmpty()) {
					val overlayAlignment = when (overlayPosition) {
						OverlayPosition.TOP -> Alignment.TopCenter
						OverlayPosition.CENTER -> Alignment.Center
						OverlayPosition.BOTTOM -> Alignment.BottomCenter
					}
					val scaledOffsetDp = (intOverlayVerticalOffsetDp * 0.25f).dp
					val alignmentModifier = when (overlayPosition) {
						OverlayPosition.TOP -> Modifier.padding(top = (8.dp + scaledOffsetDp).coerceAtLeast(4.dp), start = 6.dp, end = 6.dp)
						OverlayPosition.CENTER -> Modifier.offset(y = scaledOffsetDp).padding(horizontal = 6.dp)
						OverlayPosition.BOTTOM -> Modifier.padding(bottom = (8.dp + scaledOffsetDp).coerceAtLeast(4.dp), start = 6.dp, end = 6.dp)
					}

					Surface(
						modifier = Modifier
							.align(overlayAlignment)
							.then(alignmentModifier)
							.clip(RoundedCornerShape(6.dp)),
						color = Color(0xD910141D)
					) {
						Text(
							text = if (stringPath.isNotEmpty()) "$stringPath / $stringImageName" else stringImageName,
							style = MaterialTheme.typography.labelSmall,
							color = Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
						)
					}
				}
			}

			Column(modifier = Modifier.padding(12.dp)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(text = stringTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
					if (!stringImageUri.isNullOrEmpty()) {
						IconButton(
							onClick = { onOpenImage() },
							modifier = Modifier.size(24.dp)
						) {
							Icon(
								Icons.Default.OpenInNew,
								contentDescription = "Open in Gallery",
								modifier = Modifier.size(16.dp),
								tint = MaterialTheme.colorScheme.primary
							)
						}
					}
				}
				Spacer(modifier = Modifier.height(2.dp))
				Text(
					text = if (boolIsActive) "Active Set: $stringSetName" else "Status: $stringSetName",
					style = MaterialTheme.typography.bodySmall,
					color = if (boolIsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
					fontWeight = FontWeight.Medium
				)
				if (stringImageName.isNotEmpty()) {
					Text(
						text = stringImageName,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}

@Composable
fun StateRowItem(
	icon: ImageVector,
	stringLabel: String,
	stringValue: String,
	boolIsHighlighted: Boolean = false,
	containerColor: Color? = null,
	contentColor: Color? = null
) {
	val defaultContainer = MaterialTheme.colorScheme.surface
	val defaultContent = MaterialTheme.colorScheme.primary
	val effectiveContainer = containerColor ?: if (boolIsHighlighted) Color(0xFF2E7D32) else defaultContainer
	val effectiveContent = contentColor ?: if (boolIsHighlighted) Color.White else defaultContent

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				icon,
				contentDescription = null,
				modifier = Modifier.size(20.dp),
				tint = if (boolIsHighlighted && containerColor != null) effectiveContent else MaterialTheme.colorScheme.primary
			)
			Spacer(modifier = Modifier.width(10.dp))
			Text(stringLabel, style = MaterialTheme.typography.bodyMedium)
		}
		Surface(
			shape = RoundedCornerShape(8.dp),
			color = effectiveContainer
		) {
			Text(
				text = stringValue,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.SemiBold,
				color = effectiveContent,
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
			)
		}
	}
}

fun setLiveWallpaperIntent(context: Context) {
	try {
		val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
			putExtra(
				WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
				ComponentName(context, TriggeredWallpaperService::class.java)
			)
		}
		context.startActivity(intent)
	} catch (e: Exception) {
		// Fallback to general wallpaper picker
		val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
		context.startActivity(fallbackIntent)
	}
}

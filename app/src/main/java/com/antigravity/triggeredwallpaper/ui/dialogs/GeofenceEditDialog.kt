package com.antigravity.triggeredwallpaper.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.antigravity.triggeredwallpaper.model.GeofenceArea
import com.antigravity.triggeredwallpaper.ui.components.InteractiveGeofenceMap
import com.google.android.gms.location.LocationServices
import java.util.UUID

@SuppressLint("MissingPermission")
@Composable
fun GeofenceEditDialog(
	objInitialGeofence: GeofenceArea?,
	onDismiss: () -> Unit,
	onSave: (GeofenceArea) -> Unit
) {
	val context = LocalContext.current
	val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

	var stringName by remember { mutableStateOf(objInitialGeofence?.stringName ?: "") }
	var doubleLat by remember {
		mutableDoubleStateOf(objInitialGeofence?.doubleLatitude ?: 37.4220)
	}
	var doubleLng by remember {
		mutableDoubleStateOf(objInitialGeofence?.doubleLongitude ?: -122.0841)
	}
	var floatRadius by remember {
		mutableFloatStateOf(objInitialGeofence?.floatRadiusMeters ?: 200f)
	}

	val permissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) { mapPermissions ->
		val boolGranted = mapPermissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
			mapPermissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
		if (boolGranted) {
			fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
				if (location != null) {
					doubleLat = location.latitude
					doubleLng = location.longitude
				}
			}
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = if (objInitialGeofence == null) "New Geofence Location" else "Edit Geofence Location",
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				// 1. Area Name
				OutlinedTextField(
					value = stringName,
					onValueChange = { stringName = it },
					label = { Text("Location Name") },
					placeholder = { Text("e.g. Home, Office, Gym, Campus") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				// 2. Interactive Map header
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "Pin Location & Radius",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold
					)

					OutlinedButton(
						onClick = {
							val hasFine = ContextCompat.checkSelfPermission(
								context,
								Manifest.permission.ACCESS_FINE_LOCATION
							) == PackageManager.PERMISSION_GRANTED
							val hasCoarse = ContextCompat.checkSelfPermission(
								context,
								Manifest.permission.ACCESS_COARSE_LOCATION
							) == PackageManager.PERMISSION_GRANTED

							if (hasFine || hasCoarse) {
								fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
									if (location != null) {
										doubleLat = location.latitude
										doubleLng = location.longitude
									}
								}
							} else {
								permissionLauncher.launch(
									arrayOf(
										Manifest.permission.ACCESS_FINE_LOCATION,
										Manifest.permission.ACCESS_COARSE_LOCATION
									)
								)
							}
						}
					) {
						Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(4.dp))
						Text("My Location")
					}
				}

				// 3. Embedded Interactive Map
				InteractiveGeofenceMap(
					doubleLatitude = doubleLat,
					doubleLongitude = doubleLng,
					floatRadiusMeters = floatRadius,
					onLocationChanged = { lat, lng ->
						doubleLat = lat
						doubleLng = lng
					}
				)

				Text(
					text = "Tap or drag the blue pin to reposition the geofence center.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				// 4. Coordinates Readout Card
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 8.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(
								Icons.Default.LocationOn,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp)
							)
							Spacer(modifier = Modifier.width(6.dp))
							Text(
								text = String.format("%.5f, %.5f", doubleLat, doubleLng),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold
							)
						}

						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
						) {
							Text(
								text = "${floatRadius.toInt()}m radius",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
							)
						}
					}
				}

				// 5. Area Width / Radius Slider
				Column {
					val intRadius = floatRadius.toInt()
					val intFeet = (floatRadius * 3.28084).toInt()
					Text(
						text = "Area Width / Radius: $intRadius meters (~$intFeet ft)",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold
					)
					Spacer(modifier = Modifier.height(4.dp))
					Slider(
						value = floatRadius,
						onValueChange = { floatRadius = it },
						valueRange = 50f..2000f,
						steps = 38,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					val stringId = objInitialGeofence?.stringId ?: UUID.randomUUID().toString()
					val objResult = GeofenceArea(
						stringId = stringId,
						stringName = stringName.ifBlank { "Location" },
						doubleLatitude = doubleLat,
						doubleLongitude = doubleLng,
						floatRadiusMeters = floatRadius
					)
					onSave(objResult)
				}
			) {
				Text("Save Location")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}

package com.antigravity.triggeredwallpaper.ui.dialogs

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FolderPickerDialog(
	stringInitialPath: String? = null,
	onDismiss: () -> Unit,
	onFolderSelected: (String) -> Unit
) {
	val fileDefaultRoot = Environment.getExternalStorageDirectory()
	var fileCurrentDir by remember {
		val initialFile = if (!stringInitialPath.isNullOrBlank()) File(stringInitialPath) else null
		mutableStateOf(if (initialFile != null && initialFile.exists() && initialFile.isDirectory) initialFile else fileDefaultRoot)
	}
	var stringSearchQuery by remember { mutableStateOf("") }

	val listSubdirs = remember(fileCurrentDir, stringSearchQuery) {
		try {
			val arrayFiles = fileCurrentDir.listFiles() ?: emptyArray()
			arrayFiles
				.filter { it.isDirectory && !it.name.startsWith(".") }
				.filter {
					if (stringSearchQuery.isBlank()) true
					else it.name.contains(stringSearchQuery.trim(), ignoreCase = true)
				}
				.sortedBy { it.name.lowercase() }
		} catch (e: Exception) {
			emptyList<File>()
		}
	}

	val listQuickShortcuts = remember {
		listOf(
			"Internal Storage" to fileDefaultRoot,
			"Pictures" to File(fileDefaultRoot, "Pictures"),
			"DCIM" to File(fileDefaultRoot, "DCIM"),
			"Download" to File(fileDefaultRoot, "Download")
		)
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth()
			) {
				Icon(
					Icons.Default.FolderOpen,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(28.dp)
				)
				Spacer(modifier = Modifier.width(8.dp))
				Column {
					Text(
						text = "Select Folder",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					Text(
						text = "Direct filesystem access (No SAF)",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 480.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				// Shortcut chips
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.fillMaxWidth()
				) {
					items(listQuickShortcuts) { (stringLabel, fileShortcut) ->
						FilterChip(
							selected = fileCurrentDir.absolutePath == fileShortcut.absolutePath,
							onClick = {
								if (fileShortcut.exists() && fileShortcut.isDirectory) {
									fileCurrentDir = fileShortcut
									stringSearchQuery = ""
								}
							},
							label = { Text(stringLabel, style = MaterialTheme.typography.labelSmall) }
						)
					}
				}

				// Current path header with Up button
				Surface(
					shape = RoundedCornerShape(8.dp),
					color = MaterialTheme.colorScheme.surfaceVariant,
					modifier = Modifier.fillMaxWidth()
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
					) {
						val fileParent = fileCurrentDir.parentFile
						IconButton(
							onClick = {
								if (fileParent != null && fileParent.canRead()) {
									fileCurrentDir = fileParent
									stringSearchQuery = ""
								}
							},
							enabled = fileParent != null && fileParent.canRead()
						) {
							Icon(
								Icons.AutoMirrored.Filled.ArrowBack,
								contentDescription = "Go up",
								tint = if (fileParent != null && fileParent.canRead()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
							)
						}
						Spacer(modifier = Modifier.width(4.dp))
						Text(
							text = fileCurrentDir.absolutePath,
							style = MaterialTheme.typography.bodySmall,
							fontWeight = FontWeight.Medium,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f)
						)
					}
				}

				// Search filter for long directories
				OutlinedTextField(
					value = stringSearchQuery,
					onValueChange = { stringSearchQuery = it },
					placeholder = { Text("Filter subfolders...") },
					singleLine = true,
					modifier = Modifier
						.fillMaxWidth()
						.height(52.dp)
				)

				HorizontalDivider()

				// Directory list
				if (listSubdirs.isEmpty()) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f)
							.padding(16.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						Icon(
							Icons.Default.Storage,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
							modifier = Modifier.size(36.dp)
						)
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = if (stringSearchQuery.isNotBlank()) "No subfolders match '$stringSearchQuery'" else "No subfolders here (Leaf directory)",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				} else {
					LazyColumn(
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						items(listSubdirs, key = { it.absolutePath }) { fileChild ->
							Surface(
								shape = RoundedCornerShape(8.dp),
								color = MaterialTheme.colorScheme.surface,
								modifier = Modifier
									.fillMaxWidth()
									.clickable {
										fileCurrentDir = fileChild
										stringSearchQuery = ""
									}
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
								) {
									Icon(
										Icons.Default.Folder,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(22.dp)
									)
									Spacer(modifier = Modifier.width(10.dp))
									Text(
										text = fileChild.name,
										style = MaterialTheme.typography.bodyMedium,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f)
									)
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
					onFolderSelected(fileCurrentDir.absolutePath)
				}
			) {
				Text("Select Current Folder")
			}
		},
		dismissButton = {
			OutlinedButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}

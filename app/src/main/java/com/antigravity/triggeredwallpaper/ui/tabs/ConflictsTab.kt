package com.antigravity.triggeredwallpaper.ui.tabs

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredwallpaper.model.ConflictInfo
import com.antigravity.triggeredwallpaper.model.FolderSet

@Composable
fun ConflictsTab(
	listConflicts: List<ConflictInfo>,
	listFolderSets: List<FolderSet>
) {
	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		item {
			if (listConflicts.isEmpty()) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3D2F))
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							Icons.Default.CheckCircle,
							contentDescription = null,
							tint = Color(0xFF4EE49D),
							modifier = Modifier.size(36.dp)
						)
						Spacer(modifier = Modifier.width(12.dp))
						Column {
							Text(
								text = "Unique Matching Guaranteed",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = Color.White
							)
							Spacer(modifier = Modifier.height(4.dp))
							Text(
								text = "All folder sets have unambiguous trigger conditions. No overlaps or conflicts detected.",
								style = MaterialTheme.typography.bodySmall,
								color = Color(0xFFB5E4CC)
							)
						}
					}
				}
			} else {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							Icons.Default.Warning,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier.size(36.dp)
						)
						Spacer(modifier = Modifier.width(12.dp))
						Column {
							Text(
								text = "${listConflicts.size} Rule Conflict(s) Detected",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onErrorContainer
							)
							Spacer(modifier = Modifier.height(4.dp))
							Text(
								text = "Multiple folder sets can match the same trigger and conditions simultaneously. Adjust conditions to ensure each rule has a unique match.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
							)
						}
					}
				}
			}
		}

		item {
			Text(
				text = "Validation & Conflict Breakdown",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		if (listConflicts.isEmpty()) {
			item {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Active Rule Sets: ${listFolderSets.size}",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.SemiBold
						)
						Spacer(modifier = Modifier.height(4.dp))
						Text(
							text = "When conditions change (e.g. rotating the screen, changing foldable posture, time of day, charging state, or entering geofenced areas), the wallpaper engine will uniquely resolve the appropriate set without ambiguity.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
		} else {
			items(listConflicts) { objConflict ->
				ConflictCard(objConflict = objConflict)
			}
		}
	}
}

@Composable
fun ConflictCard(objConflict: ConflictInfo) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(12.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "${objConflict.stringSetNameA}  ⇄  ${objConflict.stringSetNameB}",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.error
				)
				Surface(
					shape = RoundedCornerShape(4.dp),
					color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
				) {
					Text(
						text = "Conflict",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
					)
				}
			}

			Text(
				text = objConflict.stringReason,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			Text(
				text = "Recommendation: Narrow one set's orientation, time window, or fold posture so only one set matches at a time.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.primary
			)
		}
	}
}

package com.example.sensorysync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensorysync.model.EngagementMetrics
import com.example.sensorysync.model.SessionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsDashboard(
    metrics: EngagementMetrics,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Therapeutic Focus Analytics", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Badge(
                    containerColor = when {
                        metrics.engagementScorePercent >= 70 -> Color(0xFF4CAF50)
                        metrics.engagementScorePercent >= 40 -> Color(0xFFFF9800)
                        else -> Color(0xFFE91E63)
                    }
                ) {
                    Text(
                        text = "Score: ${metrics.engagementScorePercent}%",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Metric Overview Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Active Eye Contact",
                        value = formatDuration(metrics.activeEyeContactSeconds),
                        subtitle = "Out of ${formatDuration(metrics.sessionDurationSeconds)}",
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Longest Focus Streak",
                        value = formatDuration(metrics.longestFocusStreakSeconds),
                        subtitle = "Current: ${formatDuration(metrics.currentFocusStreakSeconds)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Session History Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                if (metrics.sessionHistory.isEmpty()) {
                    Text(
                        text = "Current session metrics are accumulating live. Past session logs will appear here upon completion.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(metrics.sessionHistory) { record ->
                            SessionRecordRow(record)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close Dashboard")
            }
        }
    )
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SessionRecordRow(record: SessionRecord) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(record.timestampMs))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Duration: ${formatDuration(record.durationSeconds)}", fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Eye Contact: ${formatDuration(record.activeEyeContactSeconds)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("Streak: ${formatDuration(record.longestStreakSeconds)}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

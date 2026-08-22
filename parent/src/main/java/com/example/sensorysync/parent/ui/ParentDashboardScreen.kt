package com.example.sensorysync.parent.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensorysync.parent.mqtt.ParentControlState
import com.example.sensorysync.parent.mqtt.ParentMqttClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    state: ParentControlState,
    mqttClient: ParentMqttClient,
    onReconnectMqtt: () -> Unit
) {
    val isOnline = state.isMqttConnected && state.isTabletOnline
    val canAcquireFace = isOnline && !state.isFaceLocked && state.isGazeDetected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = "Parent Control",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SensorySync Parent", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                actions = {
                    if (!state.isMqttConnected) {
                        IconButton(onClick = onReconnectMqtt, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reconnect MQTT",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Badge(
                        containerColor = when {
                            isOnline -> Color(0xFF4CAF50)
                            state.isMqttConnected -> Color(0xFFFF9800)
                            else -> Color(0xFFE91E63)
                        }
                    ) {
                        Text(
                            text = when {
                                isOnline -> "Tablet Connected"
                                state.isMqttConnected -> "Waiting for App"
                                else -> "Offline"
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Connection Warning Banner when Offline
            AnimatedVisibility(
                visible = !isOnline,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!state.isMqttConnected) Color(0xFFE91E63).copy(alpha = 0.12f) else Color(0xFFFF9800).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!state.isMqttConnected) Color(0xFFE91E63) else Color(0xFFFF9800)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (!state.isMqttConnected) Icons.Default.WifiOff else Icons.Default.HourglassEmpty,
                            contentDescription = "Offline Warning",
                            tint = if (!state.isMqttConnected) Color(0xFFE91E63) else Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!state.isMqttConnected) "MQTT Broker Offline" else "Waiting for Child Tablet App...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!state.isMqttConnected) {
                                    "Check Wi-Fi (192.168.1.96:1883). Controls unlock when connected."
                                } else {
                                    "Open SensorySync Child App on the tablet to connect."
                                },
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        if (!state.isMqttConnected) {
                            Button(
                                onClick = onReconnectMqtt,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                            ) {
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 2. Tablet Live Status & Jellyfish Focus Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOnline) 1.0f else 0.5f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TabletAndroid,
                                contentDescription = "Child Tablet",
                                tint = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "Child Tablet Online" else "Tablet Disconnected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Badge(
                            containerColor = when {
                                !isOnline -> Color.Gray
                                state.isGazeFocusingOnJellyfish -> Color(0xFF4CAF50)
                                state.isGazeDetected -> Color(0xFF00BCD4)
                                else -> Color(0xFFFF9800)
                            }
                        ) {
                            Text(
                                text = when {
                                    !isOnline -> "Focus: --"
                                    state.isGazeFocusingOnJellyfish -> "IN FOCUS ✨"
                                    state.isGazeDetected -> "LOOKING AROUND"
                                    else -> "NO GAZE"
                                },
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text("Active Visual: 🪼 Cosmic Jellyfish (Gaze-Contingent Tracking)", fontSize = 12.sp, color = Color(0xFF00BCD4), fontWeight = FontWeight.Medium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Eye Contact",
                                tint = if (state.isFaceLocked && state.isGazeFocusingOnJellyfish) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Jellyfish Focus Time:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = formatDuration(state.activeEyeContactSeconds),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isFaceLocked && state.isGazeFocusingOnJellyfish) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 3. Specific Face Acquisition & Dual-Frame Split Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOnline) 1.0f else 0.5f),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Face Lock",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Child Target Face Lock",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Badge(
                            containerColor = when {
                                !isOnline -> Color.DarkGray
                                state.isFaceLocked -> Color(0xFF4CAF50)
                                state.isGazeDetected -> Color(0xFF00BCD4)
                                else -> Color(0xFFFF9800)
                            }
                        ) {
                            Text(
                                text = when {
                                    !isOnline -> "OFFLINE"
                                    state.isFaceLocked -> "LOCKED"
                                    state.isGazeDetected -> "DETECTED"
                                    else -> "SEARCHING"
                                },
                                color = Color.White,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // DUAL-FRAME SPLIT: Left = Real-time Camera Feed (with MediaPipe Markers), Right = Saved Target Face
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Frame 1: Live Real-Time Camera Stream with MediaPipe Markers
                        Box(modifier = Modifier.weight(1f)) {
                            CameraPreviewThumbnail(
                                base64String = state.cameraSnapshotBase64,
                                isFaceLocked = state.isFaceLocked,
                                isGazeDetected = state.isGazeDetected,
                                isOnline = isOnline
                            )
                        }

                        // Frame 2: Acquired / Saved Child Face Snapshot
                        Box(modifier = Modifier.weight(1f)) {
                            SavedFaceThumbnail(
                                base64String = state.lockedFaceSnapshotBase64,
                                isFaceLocked = state.isFaceLocked
                            )
                        }
                    }

                    // Camera Preview on Child Tablet Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (state.showChildCameraPreview) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Camera Toggle",
                                tint = if (state.showChildCameraPreview) Color(0xFF00BCD4) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Show Camera on Tablet",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (state.showChildCameraPreview) "Visible in top-right corner" else "Hidden (Default: pure visual immersion)",
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = state.showChildCameraPreview,
                            onCheckedChange = { isChecked ->
                                if (isOnline) {
                                    mqttClient.sendCommand(state.topicPrefix, "camera_preview", isChecked.toString())
                                }
                            },
                            enabled = isOnline,
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Text(
                        text = when {
                            !isOnline -> "Connect child tablet to see camera feed."
                            state.isFaceLocked -> "Tracking locked onto saved target child face (right frame). Bystanders are ignored."
                            state.isGazeDetected -> "Child face in view! Tap 'Acquire Child Face' to lock and save."
                            else -> "Position tablet camera towards your child. Button unlocks when face is detected."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (canAcquireFace) {
                                    mqttClient.sendCommand(state.topicPrefix, "command", "ACQUIRE_FACE")
                                }
                            },
                            enabled = canAcquireFace,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isGazeDetected) Color(0xFF00BCD4) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isGazeDetected) Icons.Default.CenterFocusStrong else Icons.Default.Face,
                                contentDescription = "Lock",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isGazeDetected) "Acquire Child Face" else "Aim at Child (No Face)",
                                fontSize = 11.sp
                            )
                        }

                        if (state.isFaceLocked) {
                            OutlinedButton(
                                onClick = {
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "command", "RELEASE_FACE")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                enabled = isOnline
                            ) {
                                Text("Release", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 4. Visual Pattern Selector (Cosmic Jellyfish vs Bubble Bloom)
            Text(
                text = "🎨 Visual Pattern Mode",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.alpha(if (isOnline) 1.0f else 0.5f)
            )

            TabRow(
                selectedTabIndex = if (state.activePatternId == 2) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOnline) 1.0f else 0.5f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = (state.activePatternId == 1),
                    onClick = {
                        if (isOnline) {
                            mqttClient.sendCommand(state.topicPrefix, "pattern", "1")
                        }
                    },
                    text = { Text("🪼 Cosmic Jellyfish", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = (state.activePatternId == 2),
                    onClick = {
                        if (isOnline) {
                            mqttClient.sendCommand(state.topicPrefix, "pattern", "2")
                        }
                    },
                    text = { Text("🫧 Bubble Bloom", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // 5. Pattern-Specific Control Panels
            if (state.activePatternId == 2) {
                // BUBBLE BLOOM CONTROLS
                Text(
                    text = "🫧 Bubble Bloom Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.alpha(if (isOnline) 1.0f else 0.5f)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isOnline) 1.0f else 0.5f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Popped Bubbles Stats Banner & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Bubbles Popped:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(containerColor = Color(0xFFE040FB)) {
                                    Text(
                                        text = "${state.bubblePoppedCount} 💥",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (isOnline) {
                                            mqttClient.sendCommand(state.topicPrefix, "command", "RESET_POP_COUNT")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    enabled = isOnline
                                ) {
                                    Text("Reset", fontSize = 10.sp)
                                }
                            }
                        }

                        // Remote Pop Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "command", "POP_RANDOM")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                enabled = isOnline
                            ) {
                                Text("💥 Pop Bubble", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "command", "POP_ALL")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                enabled = isOnline
                            ) {
                                Text("🎆 Pop All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "command", "RESET_BUBBLES")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                enabled = isOnline
                            ) {
                                Text("🔄 Respawn", fontSize = 11.sp)
                            }
                        }

                        if (state.focusedBubbleIndex != -1) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Bubble Focus Charge:", fontSize = 11.sp, color = Color(0xFF00BCD4))
                                    Text("${(state.bubbleDwellProgress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { state.bubbleDwellProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // Bubble Count Slider
                        Column {
                            Text(
                                text = "Bubble Count: ${state.bubbleCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.bubbleCount.toFloat().coerceIn(3f, 25f),
                                onValueChange = { count ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "bubble_count", count.toInt().toString())
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 3f..25f,
                                steps = 22
                            )
                        }

                        // Bubble Size Slider
                        Column {
                            Text(
                                text = "Bubble Size: ${"%.2f".format(state.bubbleScale)}x (${(state.bubbleScale * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.bubbleScale.coerceIn(0.5f, 2.0f),
                                onValueChange = { size ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "bubble_scale", "%.2f".format(size))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.5f..2.0f
                            )
                        }

                        // Floating Speed Slider
                        Column {
                            Text(
                                text = "Floating & Drift Speed: ${"%.1f".format(state.speedMultiplier)}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.speedMultiplier.coerceIn(0.2f, 2.0f),
                                onValueChange = { spd ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "speed", "%.2f".format(spd))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.2f..2.0f
                            )
                        }

                        // Pop Dwell Time Slider
                        Column {
                            Text(
                                text = "Gaze Pop Dwell Time: ${"%.1f".format(state.bubbleDwellTimeSec)}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.bubbleDwellTimeSec.coerceIn(0.5f, 3.0f),
                                onValueChange = { dwell ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "bubble_dwell_time", "%.1f".format(dwell))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.5f..3.0f
                            )
                        }
                    }
                }
            } else {
                // COSMIC JELLYFISH CONTROLS
                Text(
                    text = "🪼 Cosmic Jellyfish Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.alpha(if (isOnline) 1.0f else 0.5f)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isOnline) 1.0f else 0.5f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Jellyfish Size: ${"%.2f".format(state.jellyfishScale)}x (${(state.jellyfishScale * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.jellyfishScale.coerceIn(0.2f, 2.0f),
                                onValueChange = { size ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "jellyfish_scale", "%.2f".format(size))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.2f..2.0f
                            )
                        }

                        Column {
                            Text(
                                text = "Swimming Speed: ${"%.1f".format(state.speedMultiplier)}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.speedMultiplier.coerceIn(0.2f, 2.0f),
                                onValueChange = { spd ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "speed", "%.2f".format(spd))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.2f..2.0f
                            )
                        }

                        Column {
                            Text(
                                text = "Breathing Pulse Frequency: ${"%.1f".format(state.strobeFrequencyHz)} Hz (Safe Mode)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.strobeFrequencyHz.coerceIn(0.2f, 3.0f),
                                onValueChange = { freq ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "strobe_freq", "%.1f".format(freq))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.2f..3.0f
                            )
                        }

                        Column {
                            Text(
                                text = "Bioluminescent Hue: ${state.primaryHue.toInt()}°",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.primaryHue,
                                onValueChange = { hue ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "color_hue", hue.toInt().toString())
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0f..360f
                            )
                        }
                    }
                }
            }

            // 6. Eye Gaze Marker Controls & Customization
            Text(
                text = "🎯 Eye Gaze Marker Controls",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.alpha(if (isOnline) 1.0f else 0.5f)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOnline) 1.0f else 0.5f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Toggle Switch for Marker Visibility on Child Tablet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Show Gaze Marker on Child Screen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (state.showGazeMarker) "Marker visible to guide therapist/child" else "Hidden (pure immersive visual mode)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.showGazeMarker,
                            onCheckedChange = { isChecked ->
                                if (isOnline) {
                                    mqttClient.sendCommand(state.topicPrefix, "gaze_marker", isChecked.toString())
                                }
                            },
                            enabled = isOnline
                        )
                    }

                    if (state.showGazeMarker) {
                        // Marker Size Slider
                        Column {
                            Text(
                                text = "Marker Size: ${"%.2f".format(state.gazeMarkerSize)}x (${(state.gazeMarkerSize * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.gazeMarkerSize.coerceIn(0.5f, 2.5f),
                                onValueChange = { size ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "gaze_marker_size", "%.2f".format(size))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.5f..2.5f
                            )
                        }

                        // Marker Opacity Slider
                        Column {
                            Text(
                                text = "Marker Opacity: ${(state.gazeMarkerOpacity * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = state.gazeMarkerOpacity.coerceIn(0.1f, 1.0f),
                                onValueChange = { opacity ->
                                    if (isOnline) {
                                        mqttClient.sendCommand(state.topicPrefix, "gaze_marker_opacity", "%.2f".format(opacity))
                                    }
                                },
                                enabled = isOnline,
                                valueRange = 0.1f..1.0f
                            )
                        }

                        // Marker Color Picker
                        Column {
                            Text(
                                text = "Marker Color: ${state.gazeMarkerColor}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val colors = listOf(
                                    Triple("CYAN", "🔵 Cyan", Color(0xFF00E5FF)),
                                    Triple("GREEN", "🟢 Green", Color(0xFF00E676)),
                                    Triple("MAGENTA", "🌸 Pink", Color(0xFFE040FB)),
                                    Triple("GOLD", "🟡 Gold", Color(0xFFFFD54F)),
                                    Triple("WHITE", "⚪ White", Color.White)
                                )

                                for ((colorKey, label, chipColor) in colors) {
                                    val isSelected = state.gazeMarkerColor.equals(colorKey, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isOnline) {
                                                mqttClient.sendCommand(state.topicPrefix, "gaze_marker_color", colorKey)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        enabled = isOnline,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chipColor.copy(alpha = 0.35f),
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = if (isSelected) FilterChipDefaults.filterChipBorder(
                                            borderColor = chipColor,
                                            borderWidth = 1.5.dp,
                                            enabled = isOnline,
                                            selected = isSelected
                                        ) else null,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Remote Action Buttons (Calibration & Exit)
            Text(
                text = "Parent Actions & Safety",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.alpha(if (isOnline) 1.0f else 0.5f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOnline) 1.0f else 0.5f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isOnline) {
                            mqttClient.sendCommand(state.topicPrefix, "command", "CALIBRATE")
                        }
                    },
                    enabled = isOnline,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                ) {
                    Icon(imageVector = Icons.Default.CenterFocusWeak, contentDescription = "Calibration", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Calibration", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        if (isOnline) {
                            mqttClient.sendCommand(state.topicPrefix, "command", "EXIT_APP")
                        }
                    },
                    enabled = isOnline,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Close App", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Close Child App", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CameraPreviewThumbnail(
    base64String: String?,
    isFaceLocked: Boolean,
    isGazeDetected: Boolean,
    isOnline: Boolean
) {
    val bitmap = remember(base64String) {
        if (base64String != null) {
            try {
                val bytes = Base64.decode(base64String, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(
                width = 1.5.dp,
                color = when {
                    !isOnline -> Color.DarkGray
                    isFaceLocked -> Color(0xFF4CAF50)
                    isGazeDetected -> Color(0xFF00BCD4)
                    else -> Color.Gray
                },
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && isOnline) {
            Image(
                bitmap = bitmap,
                contentDescription = "Tablet Camera Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Camera",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isOnline) "Connecting..." else "Camera Offline",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        // Label overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LIVE CAMERA",
                color = if (isFaceLocked) Color(0xFF4CAF50) else Color(0xFF00BCD4),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SavedFaceThumbnail(
    base64String: String?,
    isFaceLocked: Boolean
) {
    val bitmap = remember(base64String) {
        if (base64String != null) {
            try {
                val bytes = Base64.decode(base64String, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(
                width = 1.5.dp,
                color = if (bitmap != null) Color(0xFF4CAF50) else Color.DarkGray,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Saved Target Child Face",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Saved Face",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "No Face Saved",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        // Label overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (bitmap != null) "SAVED TARGET" else "UNACQUIRED",
                color = if (bitmap != null) Color(0xFF4CAF50) else Color.LightGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

package com.example.sensorysync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sensorysync.analytics.AnalyticsTracker
import com.example.sensorysync.camera.VisionManager
import com.example.sensorysync.model.ControlState
import com.example.sensorysync.mqtt.MqttController
import com.example.sensorysync.ui.SensoryScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var controlState by mutableStateOf(ControlState())

    private lateinit var visionManager: VisionManager
    private lateinit var mqttController: MqttController
    private lateinit var analyticsTracker: AnalyticsTracker

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraVision()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Keep Screen Awake & Complete Kiosk Lockdown Flags
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersiveFullScreen()

        // 2. Start Android Kiosk Lock Task Mode (Pins screen, disables bottom swipe gestures & home/back buttons)
        try {
            startLockTask()
        } catch (_: Exception) {}

        // 3. Initialize Analytics
        analyticsTracker = AnalyticsTracker(this) { updatedMetrics ->
            controlState = controlState.copy(engagementMetrics = updatedMetrics)
        }
        analyticsTracker.start()

        // 4. Initialize MQTT Client (Connecting to 192.168.1.96:1883)
        mqttController = MqttController(
            onStateUpdate = { updateFunc ->
                val newState = controlState.updateFunc()
                controlState = newState
                analyticsTracker.updateState(newState)

                if (::visionManager.isInitialized) {
                    if (newState.shouldClearFaceProfile) {
                        visionManager.clearSavedFaceProfile()
                    }
                    visionManager.setLockedFaceId(if (newState.isFaceLocked) newState.targetFaceTrackingId else null)
                }

                if (newState.shouldExitApp) {
                    performCleanExit()
                }
            },
            onStatusChange = { isConnected, statusMsg ->
                controlState = controlState.copy(
                    isMqttConnected = isConnected,
                    mqttStatusText = statusMsg
                )
            }
        )

        mqttController.connect(
            broker = controlState.mqttBroker,
            port = controlState.mqttPort,
            topicPrefix = controlState.mqttTopicPrefix,
            username = controlState.mqttUsername,
            password = controlState.mqttPassword
        )

        // 5. Initialize Vision Engine
        visionManager = VisionManager(
            context = this,
            lifecycleOwner = this,
            onVisionUpdate = { leftHand, rightHand, eyeGaze, detectedFaceId, snapshotBase64, isAutoMatched ->
                val wasUnlocked = !controlState.isFaceLocked
                val shouldAutoLock = wasUnlocked && isAutoMatched && detectedFaceId != null

                controlState = controlState.copy(
                    leftHand = leftHand,
                    rightHand = rightHand,
                    gazeData = eyeGaze,
                    isFaceLocked = if (shouldAutoLock) true else controlState.isFaceLocked,
                    targetFaceTrackingId = if (shouldAutoLock) detectedFaceId else controlState.targetFaceTrackingId,
                    targetFaceStatusText = if (shouldAutoLock) "Auto-Locked (Saved Face)" else controlState.targetFaceStatusText
                )
                analyticsTracker.updateState(controlState)
                mqttController.publishStatus(controlState)

                if (snapshotBase64 != null) {
                    mqttController.publishCameraSnapshot(controlState.mqttTopicPrefix, snapshotBase64)
                }

                val savedFaceImg = visionManager.getSavedFaceImageBase64()
                if (savedFaceImg != null) {
                    mqttController.publishLockedFaceSnapshot(controlState.mqttTopicPrefix, savedFaceImg)
                }
            }
        )




        checkCameraPermission()

        setContent {
            SensoryScreen(
                state = controlState,
                onStateChange = { updateFunc ->
                    controlState = controlState.updateFunc()
                    analyticsTracker.updateState(controlState)
                },
                onBindCameraPreview = { previewView ->
                    visionManager.startCamera(previewView)
                },
                onExitApp = {
                    performCleanExit()
                }
            )
        }
    }

    private fun setupImmersiveFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        // Enforce strict behavior - do not show transient navigation bars on swipe
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    private fun performCleanExit() {
        try {
            stopLockTask()
        } catch (_: Exception) {}
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveFullScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Completely block back button
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block hardware volume / home / back keys
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraVision()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraVision() {
        visionManager.startCamera()
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveFullScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        performCleanExit()
        analyticsTracker.stop()
        visionManager.stop()
        mqttController.disconnect()
    }
}

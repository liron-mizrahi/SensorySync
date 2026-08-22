package com.example.sensorysync.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sensorysync.parent.mqtt.ParentControlState
import com.example.sensorysync.parent.mqtt.ParentMqttClient
import com.example.sensorysync.parent.ui.ParentDashboardScreen

class ParentActivity : ComponentActivity() {

    private var parentState by mutableStateOf(ParentControlState())
    private lateinit var mqttClient: ParentMqttClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mqttClient = ParentMqttClient(this) { updateFunc ->
            parentState = parentState.updateFunc()
        }


        mqttClient.connect(parentState)

        setContent {
            ParentDashboardScreen(
                state = parentState,
                mqttClient = mqttClient,
                onReconnectMqtt = {
                    mqttClient.connect(parentState)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient.shutdown()
    }
}


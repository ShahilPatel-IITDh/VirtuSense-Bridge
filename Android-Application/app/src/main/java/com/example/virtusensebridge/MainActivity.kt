package com.example.virtusense

import android.content.Context
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {
    private val BROKER_URL = "ws://broker.emqx.io:8083"
    private val TOPIC = "virtusense/unique_id_123/alert_output"
    private lateinit var lblStatus: TextView
    private lateinit var lblValue: TextView
    private lateinit var statusCard: CardView
    private lateinit var mqttClient: MqttClient
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lblStatus = findViewById(R.id.lblStatus)
        lblValue = findViewById(R.id.lblValue)
        statusCard = findViewById(R.id.statusCard)
        initVibrator()

        Thread { connectToMqtt() }.start()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun triggerVibration() {
        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vib.vibrate(500)
                }
            }
        }
    }

    private fun connectToMqtt() {
        try {
            val clientId = MqttClient.generateClientId()
            mqttClient = MqttClient(BROKER_URL, clientId, MemoryPersistence())

            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.connectionTimeout = 30 // Increased timeout
            options.keepAliveInterval = 60

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    updateUI("OFFLINE", "Connection Lost", "#333333")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload, StandardCharsets.UTF_8)
                        processSignal(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options)
            mqttClient.subscribe(TOPIC) // Listening to ESP32 now

            runOnUiThread {
                Toast.makeText(this, "Connected to ESP32 Gateway", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: e.toString()
            Log.e("VirtuSense", "Connection Error", e);
            runOnUiThread {
                lblStatus.text = "ERROR"
                // This will tell us if it's Timeout, DNS, or Permission
                lblValue.text = errorMsg
                statusCard.setCardBackgroundColor(Color.parseColor("#B00020"))
            }
        }
    }

    private fun processSignal(data: String) {
        try {
            // Expected Format from ESP32: "FAULT:3900" or "NORMAL:2000"
            val dataPackets = data.split(":")

            if (dataPackets.size == 2) {
                val status = dataPackets[0] // e.g., "FAULT"
                val value = dataPackets[1]  // e.g., "3900"

                // Logic is now decided by ESP32, App just displays it
                when (status) {
                    "FAULT" -> {
                        updateUI("CRITICAL FAULT", "Sensor: $value", "#D32F2F") // Red
                        triggerVibration()
                    }
                    "IDLE" -> {
                        updateUI("IDLE", "Sensor: $value", "#1976D2") // Blue
                    }
                    "NORMAL" -> {
                        updateUI("NORMAL", "Sensor: $value", "#388E3C") // Green
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VirtuSense", "Parsing Error: $data")
        }
    }

    private fun updateUI(status: String, subtext: String, colorHex: String) {
        runOnUiThread {
            lblStatus.text = status
            lblValue.text = subtext
            statusCard.setCardBackgroundColor(Color.parseColor(colorHex))
        }
    }
}
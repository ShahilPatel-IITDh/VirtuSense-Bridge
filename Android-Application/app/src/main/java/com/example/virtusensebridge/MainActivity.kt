package com.example.virtusense

import android.content.Context
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // --- CONFIGURATION ---
    // MUST match Python and ESP32. TCP is better for Android than WS.
    private val BROKER_URL = "tcp://test.mosquitto.org:1883"
    private val TOPIC = "virtusense/unique_id_123/alert_output"

    private lateinit var lblStatus: TextView
    private lateinit var lblValue: TextView
    private lateinit var txtLog: TextView
    private lateinit var statusCard: CardView
    private lateinit var iconStatus: ImageView

    private lateinit var mqttClient: MqttClient
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Bindings
        lblStatus = findViewById(R.id.lblStatus)
        lblValue = findViewById(R.id.lblValue)
        txtLog = findViewById(R.id.txtLog)
        statusCard = findViewById(R.id.statusCard)
        iconStatus = findViewById(R.id.iconStatus)

        initVibrator()

        // Start Connection in Background
        Thread { connectToMqtt() }.start()
    }

    private fun connectToMqtt() {
        try {
            val clientId = MqttClient.generateClientId()
            mqttClient = MqttClient(BROKER_URL, clientId, MemoryPersistence())

            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.connectionTimeout = 10
            options.keepAliveInterval = 20

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    updateUI("OFFLINE", "Disconnected", "#333333", R.drawable.ic_launcher_background)
                    addToLog("(!) Connection Lost")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload, StandardCharsets.UTF_8)
                        processSignal(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            addToLog("Connecting to Broker...")
            mqttClient.connect(options)
            mqttClient.subscribe(TOPIC)

            runOnUiThread {
                Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
                addToLog("Connected to Mosquitto")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                lblStatus.text = "ERROR"
                lblValue.text = e.localizedMessage
                statusCard.setCardBackgroundColor(Color.parseColor("#B00020"))
                addToLog("Error: ${e.message}")
            }
        }
    }

    private fun processSignal(data: String) {
        try {
            // Expected Format: "FAULT:3900" or "NORMAL:2000"
            val dataPackets = data.split(":")

            if (dataPackets.size == 2) {
                val status = dataPackets[0]
                val value = dataPackets[1]

                // Logging
                addToLog("RX: [$status] Val: $value")

                when (status) {
                    "FAULT" -> {
                        // Red / Warning
                        updateUI("FAULT DETECTED", value, "#D32F2F", android.R.drawable.ic_delete)
                        triggerVibration()
                        flashCard() // Visual Alarm
                    }
                    "IDLE" -> {
                        // Blue / Info
                        updateUI("IDLE MODE", value, "#1976D2", android.R.drawable.ic_menu_info_details)
                    }
                    "NORMAL" -> {
                        // Green / OK
                        updateUI("OPERATIONAL", value, "#388E3C", android.R.drawable.checkbox_on_background)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VirtuSense", "Parsing Error: $data")
        }
    }

    private fun updateUI(status: String, subtext: String, colorHex: String, iconResId: Int) {
        runOnUiThread {
            lblStatus.text = status
            lblValue.text = "Sensor Reading: $subtext"
            statusCard.setCardBackgroundColor(Color.parseColor(colorHex))
            // iconStatus.setImageResource(iconResId) // Uncomment if you have valid drawables
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun addToLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "$time > $msg\n"

        runOnUiThread {
            // Append to top or bottom
            txtLog.append(logEntry)

            // Auto scroll (optional implementation needed for ScrollView)
        }
    }

    private fun flashCard() {
        runOnUiThread {
            val anim = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 100
            anim.repeatMode = AlphaAnimation.REVERSE
            anim.repeatCount = 2
            statusCard.startAnimation(anim)
        }
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
                    vib.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vib.vibrate(300)
                }
            }
        }
    }
}
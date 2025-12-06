/*
 * Project: VirtuSense Bridge
 * Module: Edge Receiver Firmware
 * Description: Subscribes to Python 'Digital Twin' data stream 
 *              and processes it as a virtual sensor input.
 */

#include <WiFi.h>
#include <PubSubClient.h>

// --- NETWORK CONFIGURATION ---
// For Wokwi Simulation, use these exact credentials:
const char* ssid = "Wokwi-GUEST";
const char* password = "";

// For Real Hardware, un-comment and set your credentials:
// const char* ssid = "YOUR_WIFI_SSID";
// const char* password = "YOUR_WIFI_PASS";

// --- MQTT CONFIGURATION ---
const char* mqtt_server = "broker.hivemq.com";
// CRITICAL: Must match the topic in the Python script
const char* topic_sub = "virtusense/unique_id_123/signal_input"; 

WiFiClient espClient;
PubSubClient client(espClient);

// Global variable to hold the latest "Sensor" reading
int virtual_sensor_value = 0;

void setup() {
  Serial.begin(115200);
  
  setup_wifi();
  
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);
}

void loop() {
  // Ensure we stay connected to MQTT
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  // --- APPLICATION LOGIC ---
  // Here we use the data just like we would use analogRead(pin)
  
  process_sensor_data(virtual_sensor_value);

  // Small delay to make Serial Monitor readable
  delay(100); 
}

// ---------------------------------------------------------
// CALLBACK: Runs whenever a message arrives from Python
// ---------------------------------------------------------
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  
  // Convert the incoming String (e.g., "2048") to an Integer
  virtual_sensor_value = message.toInt();
}

// ---------------------------------------------------------
// DATA PROCESSING
// ---------------------------------------------------------
void process_sensor_data(int val) {
  // Convert 12-bit ADC value (0-4095) to Voltage (0-3.3V) for display
  float voltage = val * (3.3 / 4095.0);

  Serial.print("RX <- Virtual ADC: ");
  Serial.print(val);
  Serial.print("\t [");
  
  // Simple Visualizer in Serial Monitor
  int bars = map(val, 0, 4095, 0, 20);
  for(int i=0; i<bars; i++) Serial.print("=");
  
  Serial.print("] ");
  Serial.print(voltage);
  Serial.println(" V");
}

// ---------------------------------------------------------
// CONNECTION HELPERS
// ---------------------------------------------------------
void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Create a random Client ID
    String clientId = "ESP32Client-";
    clientId += String(random(0xffff), HEX);
    
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      // Resubscribe to the data topic
      client.subscribe(topic_sub);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}
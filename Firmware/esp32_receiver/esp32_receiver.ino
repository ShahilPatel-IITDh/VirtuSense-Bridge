#include <WiFi.h>
#include <PubSubClient.h>

// --- PIN DEFINITIONS ---
#define LED_RED   13 // Fault
#define LED_GREEN 12 // Normal
#define LED_BLUE  14 // Idle

// WiFi configuration for Wokwi simulation
const char* ssid = "Wokwi-GUEST";
const char* password = "";

// MQTT Configuration
const char* mqtt_server = "broker.hivemq.com";
const char* topic_sub = "virtusense/unique_id_123/signal_input";

WiFiClient espClient;
PubSubClient client(espClient);

int virtual_sensor_value = 0;

void setup() {
  Serial.begin(115200);

  // 1. SETUP LED PINS
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_BLUE, OUTPUT);
  
  // LED Test Sequence
  digitalWrite(LED_RED, HIGH); 
  delay(100); 
  digitalWrite(LED_RED, LOW);

  digitalWrite(LED_GREEN, HIGH); 
  delay(100); 
  digitalWrite(LED_GREEN, LOW);

  digitalWrite(LED_BLUE, HIGH);
  delay(100); 
  digitalWrite(LED_BLUE, LOW);

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop(); // Checks for new MQTT messages

  // Application Logic
  process_sensor_data(virtual_sensor_value);
  delay(500); 
}

// ---------------------------------------------------------
// CALLBACK: Runs whenever a message arrives from Python
// ---------------------------------------------------------
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  virtual_sensor_value = message.toInt();
}

// ---------------------------------------------------------
// DATA PROCESSING (Fixed Logic)
// ---------------------------------------------------------
void process_sensor_data(int dataValue) {
  
  // Debug print to see what is happening in Serial Monitor
  Serial.print("Value: ");
  Serial.print(dataValue);

  // If the value is extremely high (>3500) or extremely low (<300), it's a fault.
  if (dataValue > 3500 || dataValue < 300) {
      digitalWrite(LED_RED, HIGH);
      digitalWrite(LED_GREEN, LOW);
      digitalWrite(LED_BLUE, LOW);
      Serial.println(" | STATUS: [!!! DANGER !!!]");
  }
  
  // If not a fault, check if it's IDLE (Low stable value)
  // Python Idle is usually around 500
  else if (dataValue < 900) {
      digitalWrite(LED_BLUE, HIGH);
      digitalWrite(LED_GREEN, LOW);
      digitalWrite(LED_RED, LOW);
      Serial.println(" | STATUS: IDLE");
  }
  
  // If neither, it must be NORMAL Operation (Sine Wave)
  else {
      digitalWrite(LED_GREEN, HIGH);
      digitalWrite(LED_RED, LOW);
      digitalWrite(LED_BLUE, LOW);
      Serial.println(" | STATUS: NORMAL");
  }
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
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    String clientId = "ESP32Client-";
    clientId += String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      client.subscribe(topic_sub);
    } else {
      delay(5000);
    }
  }
}
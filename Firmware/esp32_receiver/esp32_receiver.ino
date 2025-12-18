#include <WiFi.h>
#include <PubSubClient.h>

// --- PIN DEFINITIONS ---
#define LED_RED   13 
#define LED_GREEN 12 
#define LED_BLUE  14 

const char* ssid = "Wokwi-GUEST";
const char* password = "";
const char* mqtt_server = "test.mosquitto.org";

// TOPIC 1: INPUT (From Python)
const char* topic_input = "virtusense/unique_id_123/signal_input"; 
// TOPIC 2: OUTPUT (To Android App)
const char* topic_output = "virtusense/unique_id_123/alert_output"; 

WiFiClient espClient;
PubSubClient client(espClient);

void setup() {
  Serial.begin(115200);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_BLUE, OUTPUT);
  
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();
  delay(50); 
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++)
  {
    message += (char)payload[i];
  }
  
  int val = message.toInt();
  process_and_send(val); // Process and Forward to App
}

// --- CORE EDGE LOGIC ---
void process_and_send(int val) {
  String status = "";
  
  // 1. Determine State & Control LEDs
  if (val > 3500 || val < 300) {
      digitalWrite(LED_RED, HIGH); 
      digitalWrite(LED_GREEN, LOW); 
      digitalWrite(LED_BLUE, LOW);
      status = "FAULT";
  }
  else if (val < 900) {
      digitalWrite(LED_BLUE, HIGH); 
      digitalWrite(LED_GREEN, LOW); 
      digitalWrite(LED_RED, LOW);
      status = "IDLE";
  }
  else {
      digitalWrite(LED_GREEN, HIGH); 
      digitalWrite(LED_RED, LOW); 
      digitalWrite(LED_BLUE, LOW);
      status = "NORMAL";
  }

  // 2. FORMAT DATA FOR APP: "STATUS:VALUE" (e.g., "FAULT:4000")
  String payload = status + ":" + String(val);
  
  // 3. PUBLISH TO APP
  client.publish(topic_output, payload.c_str());
  
  Serial.print("Processed: "); Serial.println(payload);
}

void setup_wifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
  }
}

void reconnect() {
  while (!client.connected()) {
    String clientId = "ESP32-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      client.subscribe(topic_input); // Listen only to Python
    } 
    else 
    {
      delay(5000);
    }
  }
}
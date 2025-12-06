"""
Project: VirtuSense Bridge
Module: Digital Twin Signal Generator
Author: Shahil Patel
"""

import paho.mqtt.client as mqtt
import time
import random
import math

# --- CONFIGURATION ---
MQTT_BROKER = "test.mosquitto.org" 
MQTT_PORT = 1883
TOPIC_SIGNAL = "virtusense/unique_id_123/signal_input"

# --- SIGNAL GENERATION LOGIC ---

def get_normal_signal(step_index):
    """
    Generates a smooth SINE wave.
    Ranges roughly from 1200 to 2800 (Safe Zone).
    """
    # Center: 2048, Amplitude: 800
    val = 2048 + (800 * math.sin(step_index * 0.5))
    # Add slight noise to make it realistic
    noise = random.randint(-50, 50)
    return int(val + noise)

def get_anomaly_signal():
    """
    Generates EXTREME values (Spikes).
    Forces value to be either very high (>3800) or very low (<200).
    This guarantees the ESP32 sees it as a FAULT.
    """
    if random.random() > 0.5:
        return random.randint(3800, 4095) # High Spike
    else:
        return random.randint(0, 200)     # Low Drop

def get_idle_signal():
    """Generates low, steady noise (~500)."""
    return 512 + random.randint(-30, 30)

# --- MQTT SETUP ---
client = mqtt.Client(client_id="VirtuSense_Py_Gen_Fixed")

try:
    print(f"Connecting to Broker: {MQTT_BROKER}...")
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    print(f"Connected! Target Topic: {TOPIC_SIGNAL}")
except Exception as e:
    print(f"Connection Failed: {e}")
    exit()

print("\n--- VIRTUSENSE CONTROL PANEL ---")
print("Instructions:")
print(" 1. Type 'n' and Enter -> Sends 20 packets of NORMAL Sine Wave")
print(" 2. Type 'a' and Enter -> Sends 20 packets of DANGER Spikes")
print(" 3. Type 'i' and Enter -> Sends 20 packets of IDLE Noise")
print("--------------------------------")

step_counter = 0

try:
    while True:
        mode = input("\nEnter Command (n/a/i): ").lower().strip()
        
        status_label = "UNKNOWN"
        packet_count = 20 # Send 20 packets per command so you have time to watch the LEDs
        delay_time = 0.5  # 100ms delay (Fast enough for smooth LED animation)

        if mode not in ['n', 'a', 'i']:
            print("Invalid command. Please use 'n', 'a', or 'i'.")
            continue

        print(f"--> Injecting {packet_count} packets for mode: {mode.upper()}...")

        for i in range(packet_count):
            val = 0
            
            if mode == 'n':
                val = get_normal_signal(step_counter)
                status_label = "NORMAL"
            elif mode == 'a':
                val = get_anomaly_signal() # Forces extremes now
                status_label = "FAULT !!!"
            else: # mode == 'i'
                val = get_idle_signal()
                status_label = "IDLE"

            # Clamp ensures we never go below 0 or above 4095
            val = max(0, min(4095, val))

            # Publish
            client.publish(TOPIC_SIGNAL, str(val))
            
            # Print visuals
            print(f"TX -> {val:04d} [{status_label}]")
            
            step_counter += 1
            time.sleep(delay_time)

except KeyboardInterrupt:
    print("\nDisconnected.")

client.disconnect()
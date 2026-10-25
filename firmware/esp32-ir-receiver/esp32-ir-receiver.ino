// PureClimate IR receiver for ESP32.
//
// Captures IR signals from an AC remote and prints them over USB serial
// (115200 baud) as one line per signal:
//     IR:<mark>,<space>,<mark>,...      (microseconds)
// The PureClimate app reads these lines through a USB OTG cable, identifies
// the AC protocol, or saves the raw signal as a learnable button.
//
// Wiring (VS1838B / TSOP38238 / similar 38 kHz receiver module):
//     OUT -> GPIO 14      VCC -> 3V3      GND -> GND
//
// Library: IRremoteESP8266 (Arduino Library Manager, "IRremoteESP8266").
// Board:   any ESP32 / ESP32-S2 / ESP32-S3 / ESP32-C3 (for native-USB boards,
//          enable "USB CDC On Boot").

#include <Arduino.h>
#include <IRrecv.h>
#include <IRremoteESP8266.h>
#include <IRutils.h>

const uint16_t kRecvPin = 14;      // IR receiver OUT pin
const uint16_t kBufferSize = 1024; // AC frames can be 900+ pulses
const uint8_t kTimeoutMs = 90;     // keep multi-frame AC messages together
const uint16_t kMinPulses = 20;    // ignore stray noise
#ifndef LED_BUILTIN
#define LED_BUILTIN 2
#endif

IRrecv irrecv(kRecvPin, kBufferSize, kTimeoutMs, true);
decode_results results;

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  irrecv.enableIRIn();
  delay(200);
  Serial.println("PURECLIMATE-RX 1.0");
}

void loop() {
  // Answer the app's PING so it knows the right firmware is connected.
  while (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    if (cmd == "PING") Serial.println("PURECLIMATE-RX 1.0");
  }

  if (irrecv.decode(&results)) {
    if (results.rawlen > kMinPulses && !results.overflow) {
      digitalWrite(LED_BUILTIN, HIGH);
      Serial.print("IR:");
      // rawbuf[0] is the gap before the signal; skip it.
      for (uint16_t i = 1; i < results.rawlen; i++) {
        uint32_t usec = (uint32_t)results.rawbuf[i] * kRawTick;
        Serial.print(usec);
        if (i + 1 < results.rawlen) Serial.print(',');
      }
      Serial.println();
      digitalWrite(LED_BUILTIN, LOW);
    } else if (results.overflow) {
      Serial.println("ERR:overflow");
    }
    irrecv.resume();
  }
}

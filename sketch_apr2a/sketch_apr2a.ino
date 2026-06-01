#include <DHT.h>

#define DHTPIN      2
#define DHTTYPE     DHT11
#define IR_PIN      7
#define LDR_PIN     A1
#define PUMP_PIN    8       

// New Pins for Ultrasonic and Potentiometer
#define TRIG_PIN    3
#define ECHO_PIN    4
#define POT_PIN     A0

#define RELAY_ON    LOW
#define RELAY_OFF   HIGH

DHT dht(DHTPIN, DHTTYPE);
bool pumpState = false;

void setup() {
  Serial.begin(9600);
  while (!Serial);
  delay(1500);

  dht.begin();
  pinMode(IR_PIN, INPUT);
  pinMode(PUMP_PIN, OUTPUT);
  
  // Ultrasonic Pin Setup
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  digitalWrite(PUMP_PIN, RELAY_OFF);

  Serial.println("READY");
}

void loop() {
  // 1. Read Ultrasonic Distance
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  
  long duration = pulseIn(ECHO_PIN, HIGH);
  float distance = duration * 0.034 / 2; // Distance in cm

  // 2. Read Potentiometer for Threshold (0 to 100cm)
  int potRaw = analogRead(POT_PIN);
  float threshold = (potRaw / 1023.0f) * 100.0f;

  // 3. Serial Control Logic
  if (Serial.available() > 0) {
    char cmd = Serial.read();

    if (cmd == '1') {
      pumpState = true;
    } 
    else if (cmd == '0') {
      pumpState = false;
    }
    while (Serial.available()) Serial.read();
  }

  // 4. Safety Override: If distance > threshold, tank is too empty
  if (distance > threshold && pumpState == true) {
    digitalWrite(PUMP_PIN, RELAY_OFF);
    // Optional: Serial.println("SAFETY STOP: TANK LOW");
  } else if (pumpState == true) {
    digitalWrite(PUMP_PIN, RELAY_ON);
  } else {
    digitalWrite(PUMP_PIN, RELAY_OFF);
  }

  // 5. Environmental Readings (Original Logic)
  float h = dht.readHumidity();
  float t = dht.readTemperature();

  if (isnan(h) || isnan(t)) {
    delay(2000);
    return;
  }

  int   ldrRaw = analogRead(LDR_PIN);
  float ldrPct = ((1023.0f - ldrRaw) / 1023.0f) * 100.0f;

  bool tankEmpty = (digitalRead(IR_PIN) == HIGH);
  
  // Output original data + new Distance and Threshold data
  Serial.print(t, 1); Serial.print(",");
  Serial.print(h, 1); Serial.print(",");
  Serial.print(ldrPct, 1); Serial.print(",");
  Serial.print(distance, 1); Serial.print(","); // Ultrasonic Reading
  Serial.print(threshold, 1); Serial.print(","); // Potentiometer Setting
  Serial.println(pumpState ? 1 : 0);

  delay(2000);
}
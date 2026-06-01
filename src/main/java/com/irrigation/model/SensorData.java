package com.irrigation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * SensorData — plain Java object (no JPA/Hibernate).
 * Serialized to JSON by Jackson for the REST API.
 * Persisted to MySQL by DatabaseManager using raw JDBC.
 *
 * JSON field names must match dashboard.html:
 *   d.temperature, d.humidity, d.lightIntensity, d.irRaw, d.tankEmpty, d.pump
 */
public class SensorData {

    private double temperature;
    private double humidity;

    // @JsonProperty makes Jackson serialize this as "lightIntensity"
    // even though the getter is getLightIntensity()
    @JsonProperty("lightIntensity")
    private double lightIntensity;

    private int irRaw;
    private boolean tankEmpty;
    private boolean pump;
    private double waterDistance;   // Ultrasonic distance in cm (A → 3/4)
    private double potThreshold;    // Potentiometer threshold in cm (A0)
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Constructors ──────────────────────────────────────

    public SensorData() {}

    public SensorData(double temperature, double humidity,
                      double lightIntensity, int irRaw, boolean tankEmpty) {
        this.temperature    = temperature;
        this.humidity       = humidity;
        this.lightIntensity = lightIntensity;
        this.irRaw          = irRaw;
        this.tankEmpty      = tankEmpty;
        this.timestamp      = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────

    public double getTemperature()              { return temperature; }
    public void   setTemperature(double t)      { this.temperature = t; }

    public double getHumidity()                 { return humidity; }
    public void   setHumidity(double h)         { this.humidity = h; }

    public double getLightIntensity()           { return lightIntensity; }
    public void   setLightIntensity(double l)   { this.lightIntensity = l; }

    // Alias kept so DatabaseManager / IrrigationController compile unchanged
    public double getLdr()                      { return lightIntensity; }
    public void   setLdr(double l)              { this.lightIntensity = l; }

    public int    getIrRaw()                    { return irRaw; }
    public void   setIrRaw(int ir)              { this.irRaw = ir; }

    public boolean isTankEmpty()                { return tankEmpty; }
    public void    setTankEmpty(boolean t)      { this.tankEmpty = t; }

    public boolean isPump()                     { return pump; }
    public void    setPump(boolean p)           { this.pump = p; }

    public double getWaterDistance()            { return waterDistance; }
    public void   setWaterDistance(double d)    { this.waterDistance = d; }

    public double getPotThreshold()             { return potThreshold; }
    public void   setPotThreshold(double t)     { this.potThreshold = t; }

    public LocalDateTime getTimestamp()         { return timestamp; }

    @Override
    public String toString() {
        return String.format(
            "SensorData{temp=%.1f, hum=%.1f, ldr=%.0f%%, ir=%d, tank=%b, pump=%b}",
            temperature, humidity, lightIntensity, irRaw, tankEmpty, pump);
    }
}

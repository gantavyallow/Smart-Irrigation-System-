package com.irrigation.service;

import com.irrigation.model.SensorData;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * IrrigationController — auto-pump logic + DB logging.
 *
 * Pump activates automatically when ALL THREE conditions are met:
 *   1. Temperature  ≥ 30 °C
 *   2. Light        ≤ 30 %   (low light / evening)
 *   3. Tank NOT empty        (IR sensor detects water)
 *
 * Any single condition not met → pump stays OFF.
 * Manual override from the dashboard always takes priority.
 */
@Service
public class IrrigationController {

    private static final Logger log = Logger.getLogger(IrrigationController.class.getName());

    // ── Thresholds ────────────────────────────────────────
    private static final double TEMP_THRESHOLD  = 30.0;   // °C
    private static final double LIGHT_THRESHOLD = 30.0;   // %

    private final DatabaseManager dbManager;

    public IrrigationController(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Evaluates whether the pump should auto-activate based on current sensor data.
     * Also logs the reading to MySQL.
     *
     * @param data latest sensor snapshot
     * @return true if all conditions are met and pump should turn ON
     */
    public boolean evaluateAndLog(SensorData data) {
        // Always log to DB regardless of pump decision
        dbManager.logToDB(data);

        // Condition 1: Tank must have water (IR sensor)
        if (data.isTankEmpty()) {
            log.info("Auto-pump: OFF — tank is empty.");
            return false;
        }

        // Condition 2: Temperature must be >= 30°C
        boolean tempMet   = data.getTemperature()    >= TEMP_THRESHOLD;

        // Condition 3: Light intensity must be <= 30%
        boolean lightMet  = data.getLightIntensity()  <= LIGHT_THRESHOLD;

        boolean allMet = tempMet && lightMet;

        log.info(String.format(
            "Auto-pump check → temp=%.1f°C(%s) light=%.1f%%(%s) tank=%s → %s",
            data.getTemperature(),   tempMet  ? "✓" : "✗",
            data.getLightIntensity(), lightMet ? "✓" : "✗",
            data.isTankEmpty() ? "EMPTY" : "OK",
            allMet ? "PUMP ON" : "PUMP OFF"
        ));

        return allMet;
    }
}

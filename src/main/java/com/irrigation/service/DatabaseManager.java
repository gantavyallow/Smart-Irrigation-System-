package com.irrigation.service;

import com.irrigation.model.SensorData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * DatabaseManager — persists sensor readings to MySQL using plain JDBC.
 * No Hibernate or JPA involved. DB failures are non-fatal (dashboard still works).
 *
 * Required table — run once in MySQL Workbench or CLI:
 * ─────────────────────────────────────────────────────────────────────
 *  CREATE DATABASE IF NOT EXISTS smart_garden_db;
 *  USE smart_garden_db;
 *
 *  CREATE TABLE IF NOT EXISTS garden_logs (
 *      id            INT AUTO_INCREMENT PRIMARY KEY,
 *      temperature   DOUBLE        NOT NULL,
 *      humidity      DOUBLE        NOT NULL,
 *      ldr           DOUBLE        NOT NULL,
 *      is_tank_empty BOOLEAN       NOT NULL,
 *      ir_raw        INT           NOT NULL,
 *      pump          BOOLEAN       NOT NULL DEFAULT FALSE,
 *      timestamp     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
 *  );
 * ─────────────────────────────────────────────────────────────────────
 */
@Component
public class DatabaseManager {

    private static final Logger log = Logger.getLogger(DatabaseManager.class.getName());

    // Reads db.url / db.username / db.password from application.properties
    @Value("${db.url}")
    private String url;

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;

    private static final String SQL =
        "INSERT INTO garden_logs (temperature, humidity, ldr, is_tank_empty, ir_raw, pump) " +
        "VALUES (?, ?, ?, ?, ?, ?)";

    public void logToDB(SensorData data) {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setDouble(1,  data.getTemperature());
            ps.setDouble(2,  data.getHumidity());
            ps.setDouble(3,  data.getLightIntensity());
            ps.setBoolean(4, data.isTankEmpty());
            ps.setInt(5,     data.getIrRaw());
            ps.setBoolean(6, data.isPump());

            ps.executeUpdate();
            log.fine("DB logged: " + data);

        } catch (SQLException e) {
            // Non-fatal — dashboard and serial reading continue unaffected
            log.warning("DB log failed (check MySQL is running & password is set): " + e.getMessage());
        }
    }
}

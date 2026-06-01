package com.irrigation.service;

import com.fazecast.jSerialComm.SerialPort;
import com.irrigation.model.SensorData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * ArduinoService — USB serial bridge to Arduino R4 WiFi.
 *
 * Receives CSV lines every 2 s:
 *   temperature,humidity,lightIntensity%,irRaw,tankEmpty(0/1)
 *
 * After each reading the auto-pump logic in IrrigationController
 * is evaluated. If all conditions are met the pump is activated
 * automatically by sending '1' to the Arduino.
 *
 * Manual pump commands from the dashboard always override auto logic.
 */
@Service
public class ArduinoService {

    private static final Logger log = Logger.getLogger(ArduinoService.class.getName());

    private static final int BAUD_RATE = 9600;

    // ── State ─────────────────────────────────────────────
    private SerialPort   port;
    private PrintWriter  writer;
    private final AtomicBoolean connected    = new AtomicBoolean(false);
    private final AtomicBoolean pumpOn       = new AtomicBoolean(false);
    private final AtomicBoolean manualOverride = new AtomicBoolean(false);

    private final AtomicReference<SensorData> latestData =
            new AtomicReference<>(new SensorData(0, 0, 0, 0, false));

    private final IrrigationController irrigationController;

    public ArduinoService(IrrigationController irrigationController) {
        this.irrigationController = irrigationController;
    }

    // ── Lifecycle ──────────────────────────────────────────

    @PostConstruct
    public void init() {
        if (!autoConnect()) {
            log.warning("⚠ No Arduino found. Plug in your R4 WiFi via USB and restart.");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (port != null && port.isOpen()) {
            // Make sure pump is OFF when server stops
            writeToSerial('0');
            port.closePort();
            log.info("Serial port closed — pump OFF.");
        }
    }

    // ── Port Auto-Detection ────────────────────────────────

    private boolean autoConnect() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) return false;

        log.info("Scanning " + ports.length + " serial port(s)...");
        for (SerialPort p : ports) {
            String desc = p.getPortDescription().toLowerCase();
            String name = p.getSystemPortName().toLowerCase();
            log.info("  " + p.getSystemPortName() + " — " + p.getPortDescription());

            boolean isArduino =
                desc.contains("arduino") || desc.contains("r4")     ||
                desc.contains("ch340")   || desc.contains("cp210")  ||
                desc.contains("ftdi")    || desc.contains("usb serial") ||
                name.contains("ttyusb")  || name.contains("ttyacm") ||
                name.startsWith("com");

            if (isArduino) {
                p.setBaudRate(BAUD_RATE);
                p.setNumDataBits(8);
                p.setNumStopBits(SerialPort.ONE_STOP_BIT);
                p.setParity(SerialPort.NO_PARITY);
                p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

                if (p.openPort()) {
                    port = p;
                    sleep(2000);   // R4 WiFi resets on port open — wait for READY
                    writer = new PrintWriter(
                            new OutputStreamWriter(port.getOutputStream()), true);
                    connected.set(true);
                    log.info("✅ Connected: " + p.getSystemPortName());
                    startReaderThread();
                    return true;
                }
            }
        }
        return false;
    }

    // ── Serial Reader Thread ───────────────────────────────

    private void startReaderThread() {
        Thread t = new Thread(() -> {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(port.getInputStream()));
            log.info("Reader thread started.");

            while (port != null && port.isOpen()) {
                try {
                    String line = reader.readLine();
                    if (line == null || line.isBlank()) continue;
                    if (line.trim().equals("READY")) {
                        log.info("Arduino: READY");
                        continue;
                    }

                    SensorData parsed = parseLine(line.trim());
                    if (parsed == null) continue;

                    // ── Auto-pump decision ─────────────────────────
                    // Only run auto-logic if the user has NOT manually overridden
                    boolean shouldPump;
                    if (manualOverride.get()) {
                        // Keep current state as set by dashboard button
                        shouldPump = pumpOn.get();
                    } else {
                        // Let IrrigationController decide
                        shouldPump = irrigationController.evaluateAndLog(parsed);
                        if (shouldPump != pumpOn.get()) {
                            pumpOn.set(shouldPump);
                            writeToSerial(shouldPump ? '1' : '0');
                        }
                    }

                    parsed.setPump(pumpOn.get());
                    latestData.set(parsed);

                } catch (Exception e) {
                    log.warning("Read error: " + e.getMessage());
                    break;
                }
            }
            connected.set(false);
            log.warning("Reader thread stopped.");
        }, "arduino-reader");
        t.setDaemon(true);
        t.start();
    }

    // ── CSV Parser ─────────────────────────────────────────

    /**
     * Parses: temperature,humidity,lightIntensity%,distance(cm),threshold(cm),pumpState(0/1)
     * Example: "27.3,65.0,18.4,12.5,50.0,0"
     *
     * waterDistance  = ultrasonic distance in cm (trig=3, echo=4)
     * potThreshold   = threshold set by potentiometer on A0 (0–100 cm)
     * tankEmpty      = distance > potThreshold (water level too low)
     */
    private SensorData parseLine(String line) {
        try {
            String[] p = line.split(",");
            if (p.length < 6) {
                log.warning("Short CSV (need 6): " + line);
                return null;
            }
            double  temp          = Double.parseDouble(p[0].trim());
            double  hum           = Double.parseDouble(p[1].trim());
            double  ldr           = Double.parseDouble(p[2].trim());
            double  waterDistance = Double.parseDouble(p[3].trim());
            double  potThreshold  = Double.parseDouble(p[4].trim());
            // Derive tankEmpty: if distance > threshold the water level is below limit
            boolean tankEmpty     = waterDistance > potThreshold;

            SensorData sd = new SensorData(temp, hum, ldr, 0, tankEmpty);
            sd.setWaterDistance(waterDistance);
            sd.setPotThreshold(potThreshold);
            return sd;
        } catch (NumberFormatException e) {
            log.warning("Malformed CSV: [" + line + "] → " + e.getMessage());
            return null;
        }
    }

    // ── Public API ─────────────────────────────────────────

    public SensorData getLatest()    { return latestData.get(); }
    public boolean    isConnected()  { return connected.get(); }

    /**
     * Called by the dashboard button.
     * Sets manualOverride=true so auto-logic won't fight the user's choice.
     * Call with cmd='A' to release the override and return to auto mode.
     */
    public void sendCommand(char cmd) {
        if (cmd == 'A') {
            // 'A' = Auto mode — release manual override
            manualOverride.set(false);
            log.info("Pump → AUTO mode");
            return;
        }
        // Manual command from dashboard
        manualOverride.set(true);
        boolean on = (cmd == '1');
        pumpOn.set(on);
        writeToSerial(cmd);

        // ── KEY FIX ──────────────────────────────────────────────────
        // latestData is only refreshed by the serial reader thread (every 2s).
        // Immediately stamp the new pump state so the REST API reflects
        // the change on the very next poll — not up to 2 seconds later.
        SensorData current = latestData.get();
        current.setPump(on);
        latestData.set(current);
        // ─────────────────────────────────────────────────────────────

        log.info("Pump → MANUAL " + (on ? "ON" : "OFF"));
    }

    // ── Helpers ────────────────────────────────────────────

    private void writeToSerial(char cmd) {
        if (port != null && port.isOpen() && writer != null) {
            writer.print(cmd);
            writer.flush();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}

package com.irrigation.controller;

import com.irrigation.model.SensorData;
import com.irrigation.service.ArduinoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WebController — Thymeleaf dashboard + REST API.
 *
 *  GET  /            → dashboard.html
 *  GET  /api/sensors → latest sensor JSON (polled every 1 s)
 *  POST /api/pump    → manual pump command (?action=ON|OFF|AUTO)
 *  GET  /api/status  → connection health
 */
@Controller
public class WebController {

    private final ArduinoService arduinoService;

    public WebController(ArduinoService arduinoService) {
        this.arduinoService = arduinoService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("connected", arduinoService.isConnected());
        return "dashboard";
    }

    @GetMapping("/api/sensors")
    @ResponseBody
    public SensorData getSensors() {
        return arduinoService.getLatest();
    }

    /**
     * action = ON   → manual pump ON  (overrides auto)
     * action = OFF  → manual pump OFF (overrides auto)
     * action = AUTO → release manual override, return to auto mode
     */
    @PostMapping("/api/pump")
    @ResponseBody
    public Map<String, Object> controlPump(
            @RequestParam(defaultValue = "OFF") String action) {

        char cmd;
        switch (action.toUpperCase().trim()) {
            case "ON"   -> cmd = '1';
            case "AUTO" -> cmd = 'A';
            default     -> cmd = '0';   // OFF
        }
        arduinoService.sendCommand(cmd);

        return Map.of(
            "status", "OK",
            "action", action.toUpperCase()
        );
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        boolean conn = arduinoService.isConnected();
        return Map.of(
            "online",  conn,
            "mode",    conn ? "LIVE" : "DISCONNECTED",
            "message", conn
                ? "Arduino R4 WiFi connected via USB serial."
                : "No Arduino detected — check USB and restart."
        );
    }
}

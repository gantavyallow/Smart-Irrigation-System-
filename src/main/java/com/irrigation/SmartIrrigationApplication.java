package com.irrigation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Main Entry Point for your Spring Boot Web App.
 * * @SpringBootApplication: Tells Java this is a Web Server.
 * 
 * @EnableScheduling: Tells Java to run your Arduino read logic every 2 seconds.
 */
@SpringBootApplication
@EnableScheduling
public class SmartIrrigationApplication {

    public static void main(String[] args) {
        // This replaces your old 'while(true)' loop logic
        // It starts the server on port 8080
        SpringApplication.run(SmartIrrigationApplication.class, args);

        System.out.println("========================================");
        System.out.println("  SMART IRRIGATION SYSTEM IS LIVE!      ");
        System.out.println("  Access on PC: http://localhost:8080   ");
        System.out.println("========================================");
    }
}
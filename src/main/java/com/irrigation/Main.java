package com.irrigation;

/**
 * Legacy entry point kept for backward compatibility.
 * All logic is now in SmartIrrigationApplication (Spring Boot web server).
 *
 * Run via: mvn spring-boot:run
 * Or:      java -jar target/SmartIrrigationProject-*.jar
 */
public class Main {
    public static void main(String[] args) {
        SmartIrrigationApplication.main(args);
    }
}

package com.instrumentstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application. Running this class's main() method
 * starts an embedded Tomcat server automatically - no separate Tomcat
 * install or configuration needed.
 */
@SpringBootApplication
public class InstrumentStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(InstrumentStoreApplication.class, args);
    }
}

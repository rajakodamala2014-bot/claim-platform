package com.claims.platform.paymentsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Fake Payments system for local runs and end-to-end tests. Not deployed to production. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentsSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentsSimulatorApplication.class, args);
    }
}

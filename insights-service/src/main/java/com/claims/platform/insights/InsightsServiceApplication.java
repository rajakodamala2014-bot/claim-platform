package com.claims.platform.insights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** CQRS read models built from claims events: work queue, workload, performance, exposure. */
@SpringBootApplication
public class InsightsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InsightsServiceApplication.class, args);
    }
}

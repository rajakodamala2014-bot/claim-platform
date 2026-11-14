package com.claims.platform.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.claims.platform.claims", "com.claims.platform.web"})
public class ClaimsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

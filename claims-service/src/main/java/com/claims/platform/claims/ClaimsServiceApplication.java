package com.claims.platform.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modular monolith. Top-level packages are modules; each module is layered controller -> service ->
 * repository, with interfaces separated from their implementations ({@code service.impl}, {@code repository.impl}):
 * <ul>
 *     <li>{@code core}       - the Claim aggregate and its state machine, shared by all modules</li>
 *     <li>{@code intake}     - FNOL, parties, policy snapshot, incident, documents</li>
 *     <li>{@code handling}   - assignment, assessment, info requests, reserves, decisions and approval</li>
 *     <li>{@code settlement} - settlement and payment callbacks</li>
 *     <li>{@code platform}   - outbox relay, idempotency, security, market config</li>
 * </ul>
 */
@SpringBootApplication
public class ClaimsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsServiceApplication.class, args);
    }
}

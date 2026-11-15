package com.claims.platform.claims.intake.config;

import com.claims.platform.claims.intake.cache.PolicyCache;
import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.client.impl.RestPolicyClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** The client of the external Policy system. */
@Configuration
@EnableConfigurationProperties(PolicySystemProperties.class)
public class IntakeConfig {

    @Bean
    PolicyClient policyClient(RestClient.Builder builder, PolicySystemProperties properties, PolicyCache policyCache) {
        return new RestPolicyClient(builder, properties, policyCache);
    }
}

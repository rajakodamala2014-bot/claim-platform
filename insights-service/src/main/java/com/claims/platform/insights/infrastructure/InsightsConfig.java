package com.claims.platform.insights.infrastructure;

import com.claims.platform.insights.application.CheckSlaAlerts;
import com.claims.platform.insights.application.ClaimSummaryStore;
import com.claims.platform.insights.application.InsightsQueries;
import com.claims.platform.insights.application.ProjectClaimEvent;
import com.claims.platform.insights.application.StaffInsights;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/** Wires the insights use cases. */
@Configuration
@EnableScheduling
public class InsightsConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProjectClaimEvent projectClaimEvent(ClaimSummaryStore store) {
        return new ProjectClaimEvent(store);
    }

    @Bean
    StaffInsights staffInsights(InsightsQueries queries, Clock clock,
                                @Value("${insights.sla.at-risk-window:4h}") Duration atRiskWindow) {
        return new StaffInsights(queries, clock, atRiskWindow);
    }

    @Bean
    CheckSlaAlerts checkSlaAlerts(InsightsQueries queries, CheckSlaAlerts.AlertPublisher publisher,
                                  CheckSlaAlerts.AlertLog log, Clock clock,
                                  @Value("${insights.sla.at-risk-window:4h}") Duration atRiskWindow) {
        return new CheckSlaAlerts(queries, publisher, log, clock, atRiskWindow);
    }
}

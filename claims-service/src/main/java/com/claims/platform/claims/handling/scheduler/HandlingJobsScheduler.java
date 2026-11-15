package com.claims.platform.claims.handling.scheduler;

import com.claims.platform.claims.handling.service.ClaimClosureService;
import com.claims.platform.claims.handling.service.InformationRequestService;
import com.claims.platform.claims.platform.common.JobLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Daily jobs (UTC): information-request expiry and auto-close after cooling-off; one instance each. */
@Component
@ConditionalOnProperty(name = "claims.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class HandlingJobsScheduler {
    private final InformationRequestService informationRequestService;
    private final ClaimClosureService claimClosureService;
    private final JobLock jobLock;

    public HandlingJobsScheduler(InformationRequestService informationRequestService,
                                 ClaimClosureService claimClosureService, JobLock jobLock) {
        this.informationRequestService = informationRequestService;
        this.claimClosureService = claimClosureService;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${claims.jobs.info-request-expiry-cron:0 0 1 * * *}", zone = "UTC")
    public void informationRequestService() {
        jobLock.runExclusively("info-request-expiry", Duration.ofMinutes(30), informationRequestService::expireOverdueInformationRequests);
    }

    @Scheduled(cron = "${claims.jobs.auto-close-cron:0 30 1 * * *}", zone = "UTC")
    public void autoClose() {
        jobLock.runExclusively("auto-close", Duration.ofMinutes(30), claimClosureService::closeClaimsAfterCoolingOff);
    }
}

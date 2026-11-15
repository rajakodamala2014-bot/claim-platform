package com.claims.platform.claims.intake.scheduler;

import com.claims.platform.claims.intake.service.CoverCheckRetryService;
import com.claims.platform.claims.platform.common.JobLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runs the cover-check retry every 10 minutes on one instance. */
@Component
@ConditionalOnProperty(name = "claims.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class CoverCheckRetryScheduler {
    private final CoverCheckRetryService coverCheckRetryService;
    private final JobLock jobLock;

    public CoverCheckRetryScheduler(CoverCheckRetryService coverCheckRetryService, JobLock jobLock) {
        this.coverCheckRetryService = coverCheckRetryService;
        this.jobLock = jobLock;
    }

    @Scheduled(fixedDelayString = "${claims.jobs.cover-check-retry-interval:10m}",
            initialDelayString = "${claims.jobs.cover-check-retry-interval:10m}")
    public void run() {
        jobLock.runExclusively("cover-check-retry", Duration.ofMinutes(9), coverCheckRetryService::retryPendingCoverChecks);
    }
}

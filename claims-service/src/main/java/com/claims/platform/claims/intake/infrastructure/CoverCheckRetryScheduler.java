package com.claims.platform.claims.intake.infrastructure;

import com.claims.platform.claims.intake.application.RetryCoverChecks;
import com.claims.platform.claims.platform.infrastructure.JobLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runs the cover-check retry every 10 minutes on one instance. */
@Component
@ConditionalOnProperty(name = "claims.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class CoverCheckRetryScheduler {
    private final RetryCoverChecks retryCoverChecks;
    private final JobLock jobLock;

    public CoverCheckRetryScheduler(RetryCoverChecks retryCoverChecks, JobLock jobLock) {
        this.retryCoverChecks = retryCoverChecks;
        this.jobLock = jobLock;
    }

    @Scheduled(fixedDelayString = "${claims.jobs.cover-check-retry-interval:10m}",
            initialDelayString = "${claims.jobs.cover-check-retry-interval:10m}")
    public void run() {
        jobLock.runExclusively("cover-check-retry", Duration.ofMinutes(9), retryCoverChecks::runOnce);
    }
}

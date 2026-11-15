package com.claims.platform.claims.handling.infrastructure;

import com.claims.platform.claims.handling.application.CloseCoolingOffClaims;
import com.claims.platform.claims.handling.application.ExpireInformationRequests;
import com.claims.platform.claims.platform.infrastructure.JobLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Daily jobs (UTC): information-request expiry and auto-close after cooling-off; one instance each. */
@Component
@ConditionalOnProperty(name = "claims.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class HandlingJobsScheduler {
    private final ExpireInformationRequests expireInformationRequests;
    private final CloseCoolingOffClaims closeCoolingOffClaims;
    private final JobLock jobLock;

    public HandlingJobsScheduler(ExpireInformationRequests expireInformationRequests,
                                 CloseCoolingOffClaims closeCoolingOffClaims, JobLock jobLock) {
        this.expireInformationRequests = expireInformationRequests;
        this.closeCoolingOffClaims = closeCoolingOffClaims;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${claims.jobs.info-request-expiry-cron:0 0 1 * * *}", zone = "UTC")
    public void expireInformationRequests() {
        jobLock.runExclusively("info-request-expiry", Duration.ofMinutes(30), expireInformationRequests::runOnce);
    }

    @Scheduled(cron = "${claims.jobs.auto-close-cron:0 30 1 * * *}", zone = "UTC")
    public void autoClose() {
        jobLock.runExclusively("auto-close", Duration.ofMinutes(30), closeCoolingOffClaims::runOnce);
    }
}

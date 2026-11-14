package com.claims.platform.claims.domain;

import com.claims.platform.claims.domain.entity.Claim;

import java.time.Duration;
import java.time.Instant;

public final class SlaClock {
    private SlaClock() {
    }

    public static void pause(Claim claim, Instant now) {
        if (claim.getSlaPausedAt() == null) {
            claim.setSlaPausedAt(now);
        }
    }

    public static void resume(Claim claim, Instant now) {
        if (claim.getSlaPausedAt() == null) {
            return;
        }
        long seconds = Math.max(0, Duration.between(claim.getSlaPausedAt(), now).getSeconds());
        long total = claim.getSlaPausedTotalSec() == null ? 0 : claim.getSlaPausedTotalSec();
        claim.setSlaPausedTotalSec(total + seconds);
        if (claim.getSlaDueAt() != null) {
            claim.setSlaDueAt(claim.getSlaDueAt().plusSeconds(seconds));
        }
        claim.setSlaPausedAt(null);
    }
}

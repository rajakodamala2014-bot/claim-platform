package com.claims.platform.architecture.fixtures.badmodules.intake.application;

import com.claims.platform.architecture.fixtures.badmodules.core.domain.Claim;
import com.claims.platform.architecture.fixtures.badmodules.handling.application.AssessClaim;

/** Violation: intake reaches into the handling module. Using core is fine. */
public class SubmitClaim {
    public void submit() {
        new AssessClaim().assess(new Claim());
    }
}

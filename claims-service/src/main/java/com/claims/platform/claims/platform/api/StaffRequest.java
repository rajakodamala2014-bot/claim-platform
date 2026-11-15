package com.claims.platform.claims.platform.api;

import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.web.security.PlatformUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Staff-surface helpers: caller context from the token, and ETag / If-Match handling. */
public final class StaffRequest {
    private static final Pattern STRONG_ETAG = Pattern.compile("^\"(\\d+)\"$");

    private StaffRequest() {
    }

    public static StaffContext staff(Jwt jwt) {
        PlatformUser user = CurrentUser.of(jwt);
        return new StaffContext(CurrentUser.actor(jwt),
                user.markets().stream().map(MarketCode::of).collect(Collectors.toSet()), user.teamId());
    }

    /** Parses {@code If-Match: "<version>"}. Missing -> 428 IF_MATCH_REQUIRED; malformed -> 400. */
    public static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new DomainException(ErrorCode.IF_MATCH_REQUIRED,
                    "Send If-Match with the claim ETag (from GET /staff/v1/claims/{id}) to change a claim");
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) {
            throw ValidationException.of("If-Match", "must be the claim ETag, e.g. \"3\"");
        }
        return Long.parseLong(matcher.group(1));
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** Response body for staff commands; the new version is also sent as ETag for the next If-Match. */
    public record CommandResponse<T>(java.util.UUID claimId, String status, long version, T result) {
    }

    public static <T> ResponseEntity<CommandResponse<T>> respond(Outcome<T> outcome) {
        return respond(HttpStatus.OK, outcome);
    }

    public static <T> ResponseEntity<CommandResponse<T>> respond(HttpStatus status, Outcome<T> outcome) {
        return ResponseEntity.status(status).eTag(etag(outcome.version()))
                .body(new CommandResponse<>(outcome.claimId().value(), outcome.status(), outcome.version(), outcome.value()));
    }
}

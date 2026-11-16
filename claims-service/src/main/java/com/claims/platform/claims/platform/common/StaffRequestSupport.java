package com.claims.platform.claims.platform.common;

import com.claims.platform.claims.core.dto.response.CommandResponse;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.web.common.PlatformUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Staff-surface helpers: caller context from the token, and ETag / If-Match handling. */
public final class StaffRequestSupport {
    private static final Pattern STRONG_ETAG = Pattern.compile("^\"(\\d{1,18})\"$"); // 18 digits always fit a long

    private StaffRequestSupport() {
    }

    public static StaffContext toStaffContext(Jwt jwt) {
        PlatformUser user = AuthenticatedUser.from(jwt);
        return new StaffContext(AuthenticatedUser.toActor(jwt),
                user.markets().stream().map(MarketCode::of).collect(Collectors.toSet()), user.teamId());
    }

    /** Parses {@code If-Match: "<version>"}. Missing -> 428 IF_MATCH_REQUIRED; malformed -> 400. */
    public static long parseExpectedVersion(String ifMatch) {
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

    public static String toEtag(long version) {
        return "\"" + version + "\"";
    }

    public static <T> ResponseEntity<CommandResponse<T>> toResponseEntity(Outcome<T> outcome) {
        return toResponseEntity(HttpStatus.OK, outcome);
    }

    public static <T> ResponseEntity<CommandResponse<T>> toResponseEntity(HttpStatus status, Outcome<T> outcome) {
        return ResponseEntity.status(status).eTag(toEtag(outcome.version()))
                .body(new CommandResponse<>(outcome.claimId().value(), outcome.status(), outcome.version(), outcome.value()));
    }
}

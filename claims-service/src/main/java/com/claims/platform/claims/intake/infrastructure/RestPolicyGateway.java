package com.claims.platform.claims.intake.infrastructure;

import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.intake.application.PolicyGateway;
import com.claims.platform.claims.intake.domain.CoverCheck;
import com.claims.platform.claims.intake.domain.PolicySnapshot;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.correlation.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST adapter to the Policy admin system. Contract (see docker/policy-system for the local stub):
 * {@code GET /api/v1/policies?holderId=} and {@code POST /api/v1/policies/{policyNumber}/cover-checks}.
 * Every call has a hard timeout; policy lists are cached; failures become "unavailable", never a crash.
 */
public class RestPolicyGateway implements PolicyGateway {
    private static final Logger log = LoggerFactory.getLogger(RestPolicyGateway.class);

    private final RestClient http;
    private final Cache<String, List<PolicySnapshot>> cache;

    public RestPolicyGateway(RestClient.Builder builder, PolicySystemProperties properties) {
        // HTTP/1.1: the JDK client's default h2c upgrade breaks POSTs with a body on many internal servers
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        this.http = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.cache = Caffeine.newBuilder().expireAfterWrite(properties.cacheTtl()).maximumSize(10_000).build();
    }

    @Override
    public List<PolicySnapshot> policiesOf(String holderId) {
        List<PolicySnapshot> cached = cache.getIfPresent(holderId);
        if (cached != null) {
            return cached;
        }
        try {
            PolicyListDto body = http.get()
                    .uri(uri -> uri.path("/api/v1/policies").queryParam("holderId", holderId).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(Headers.CORRELATION_ID, CorrelationIdFilter.current().orElse(""))
                    .retrieve()
                    .body(PolicyListDto.class);
            List<PolicySnapshot> policies = body == null || body.items() == null ? List.of()
                    : body.items().stream().map(PolicyDto::toSnapshot).toList();
            cache.put(holderId, policies);
            return policies;
        } catch (RestClientException ex) {
            log.warn("Policy system unavailable listing policies: {}", ex.getMessage());
            throw new DomainException(ErrorCode.POLICY_SYSTEM_UNAVAILABLE,
                    "The policy system is temporarily unavailable. Please try again shortly.");
        }
    }

    @Override
    public CoverCheck checkCover(String policyNumber, LineOfBusiness lineOfBusiness, LocalDate incidentDate, String peril) {
        try {
            CoverCheckDto body = http.post()
                    .uri("/api/v1/policies/{policyNumber}/cover-checks", policyNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(Headers.CORRELATION_ID, CorrelationIdFilter.current().orElse(""))
                    .body(Map.of("incidentDate", incidentDate.toString(), "peril", peril,
                            "lineOfBusiness", lineOfBusiness.name()))
                    .retrieve()
                    .body(CoverCheckDto.class);
            if (body == null) {
                return new CoverCheck.Unavailable("empty cover-check response");
            }
            return body.covered() ? new CoverCheck.Covered()
                    : new CoverCheck.NotCovered(body.reason() == null ? "NOT_COVERED" : body.reason());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new CoverCheck.NotCovered("POLICY_NOT_FOUND");
            }
            log.warn("Cover check failed for {}: HTTP {}", policyNumber, ex.getStatusCode().value());
            return new CoverCheck.Unavailable("HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("Cover check unavailable for {}: {}", policyNumber, ex.getMessage());
            return new CoverCheck.Unavailable(ex.getClass().getSimpleName());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PolicyListDto(List<PolicyDto> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PolicyDto(String policyNumber, String holderId, String holderName, String marketCode, String lineOfBusiness,
                     String product, String status, LocalDate coverageStart, LocalDate coverageEnd, String currency,
                     Long sumInsuredMinor, Long excessMinor, List<String> coveredPerils) {
        PolicySnapshot toSnapshot() {
            return new PolicySnapshot(policyNumber, holderId, holderName, MarketCode.of(marketCode),
                    LineOfBusiness.valueOf(lineOfBusiness), product, status, coverageStart, coverageEnd, currency,
                    sumInsuredMinor, excessMinor, coveredPerils);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoverCheckDto(boolean covered, String reason) {
    }
}

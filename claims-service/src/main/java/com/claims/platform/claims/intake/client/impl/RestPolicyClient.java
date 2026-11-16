package com.claims.platform.claims.intake.client.impl;

import com.claims.platform.claims.intake.config.PolicySystemProperties;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.intake.cache.PolicyCache;
import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.entity.CoverCheck;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.common.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * REST client of the Policy admin system. Contract (see deployment/docker/policy-system for the local stub):
 * {@code GET /api/v1/policies?holderId=} and {@code POST /api/v1/policies/{policyNumber}/cover-checks}.
 * Every call has a hard timeout; policy lists are cached; failures become "unavailable", never a crash.
 */
public class RestPolicyClient implements PolicyClient {
    private static final Logger log = LoggerFactory.getLogger(RestPolicyClient.class);

    private final RestClient http;
    private final PolicyCache cache;

    public RestPolicyClient(RestClient.Builder builder, PolicySystemProperties properties, PolicyCache cache) {
        // HTTP/1.1: the JDK client's default h2c upgrade breaks POSTs with a body on many internal servers
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        this.http = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.cache = cache;
    }

    @Override
    public List<PolicySnapshot> fetchPoliciesOfHolder(String holderId) {
        Optional<List<PolicySnapshot>> cached = cache.findPoliciesOfHolder(holderId);
        if (cached.isPresent()) {
            return cached.get();
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
            cache.putPoliciesOfHolder(holderId, policies);
            return policies;
        } catch (RestClientException | CancellationException ex) {
            // the JDK request factory's read timeout cancels the call; that can surface unwrapped as a CancellationException
            log.warn("Policy system unavailable listing policies: {}", ex.toString());
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
        } catch (RestClientException | CancellationException ex) {
            log.warn("Cover check unavailable for {}: {}", policyNumber, ex.toString());
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

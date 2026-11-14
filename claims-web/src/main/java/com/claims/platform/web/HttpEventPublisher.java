package com.claims.platform.web;

import com.claims.platform.common.event.EventEnvelope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class HttpEventPublisher {
    private final RestClient restClient;
    private final List<String> consumerUrls;
    private final String internalToken;

    public HttpEventPublisher(RestClient.Builder builder, EventsProperties events, ClaimsSecurityProperties security) {
        this.restClient = builder.build();
        this.consumerUrls = events.getConsumerUrls().stream().filter(url -> url != null && !url.isBlank()).toList();
        this.internalToken = security.getInternalToken();
    }

    public void publish(EventEnvelope event) {
        for (String url : consumerUrls) {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .header(CorrelationIdFilter.HEADER, event.correlationId() == null ? "" : event.correlationId())
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public boolean hasConsumers() {
        return !consumerUrls.isEmpty();
    }
}

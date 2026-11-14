package com.claims.platform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "claims.events")
public class EventsProperties {
    private List<String> consumerUrls = new ArrayList<>();

    public List<String> getConsumerUrls() {
        return consumerUrls;
    }

    public void setConsumerUrls(List<String> consumerUrls) {
        this.consumerUrls = consumerUrls;
    }
}

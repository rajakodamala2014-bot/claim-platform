package com.claims.platform.insights.producer;

import com.claims.platform.common.event.EventEnvelope;

/** Publishes SlaAtRisk / SlaBreached alerts on insights.alerts. */
public interface SlaAlertProducer {
    void publishAlert(EventEnvelope alert);
}

package com.claims.platform.insights.service;

/**
 * Claims about to breach (SlaAtRisk) or past (SlaBreached) their SLA are announced once per due date on
 * insights.alerts (Notifications warns the handler and team lead). The event id is derived from claim + kind
 * + due date, so a re-send after a crash is recognisably the same alert to consumers.
 */
public interface SlaAlertService {
    SlaAlertResult publishDueSlaAlerts();

    record SlaAlertResult(int atRisk, int breached) {
    }
}

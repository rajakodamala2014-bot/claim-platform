package com.claims.platform.notification.domain;

import com.claims.platform.common.event.EventTypes;

import java.util.List;
import java.util.Map;

/**
 * Who is told about which event (the "what Notifications sends" table of the design). Events not listed
 * notify nobody.
 */
public final class NotificationRules {
    private NotificationRules() {
    }

    public static List<Audience> audiencesFor(String eventType, Map<String, Object> data) {
        return switch (eventType) {
            case EventTypes.CLAIM_SUBMITTED, EventTypes.CLAIM_ASSIGNED, EventTypes.INFORMATION_REQUESTED,
                 EventTypes.CLAIM_APPROVED, EventTypes.CLAIM_REJECTED, EventTypes.PAYMENT_CONFIRMED -> List.of(Audience.CUSTOMER);
            case EventTypes.INFORMATION_PROVIDED, EventTypes.INFORMATION_REQUEST_EXPIRED, EventTypes.DECISION_APPROVED,
                 EventTypes.DECISION_RETURNED, EventTypes.PAYMENT_FAILED, EventTypes.COVER_DECLINED,
                 EventTypes.CLAIM_WITHDRAWN, EventTypes.CLAIM_REOPENED -> List.of(Audience.HANDLER);
            case EventTypes.DECISION_PROPOSED -> Boolean.TRUE.equals(data.get("approvalRequired"))
                    ? List.of(Audience.TEAM_LEAD) : List.of();
            case EventTypes.SLA_AT_RISK, EventTypes.SLA_BREACHED -> List.of(Audience.HANDLER, Audience.TEAM_LEAD);
            default -> List.of();
        };
    }

    /**
     * Which staff member is "the handler" for an event: the proposer for decision outcomes, otherwise the
     * handler named on the event, falling back to the claim's assigned handler.
     */
    public static String handlerFor(String eventType, Map<String, Object> data, String assignedHandler) {
        Object explicit = switch (eventType) {
            case EventTypes.DECISION_APPROVED, EventTypes.DECISION_RETURNED -> data.get("proposerId");
            default -> data.get("handlerId");
        };
        return explicit != null ? explicit.toString() : assignedHandler;
    }
}

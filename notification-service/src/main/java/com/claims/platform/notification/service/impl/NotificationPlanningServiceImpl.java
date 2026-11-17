package com.claims.platform.notification.service.impl;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.money.Money;
import com.claims.platform.notification.entity.ClaimContact;
import com.claims.platform.notification.entity.StaffMember;
import com.claims.platform.notification.entity.Template;
import com.claims.platform.notification.repository.ClaimContactRepository;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.repository.PreferenceRepository;
import com.claims.platform.notification.repository.StaffDirectoryRepository;
import com.claims.platform.notification.repository.TemplateRepository;
import com.claims.platform.notification.service.NotificationPlanningService;
import org.springframework.stereotype.Service;
import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.common.NotificationRules;
import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.common.TemplateText;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationPlanningServiceImpl implements NotificationPlanningService {
    private final ClaimContactRepository contacts;
    private final StaffDirectoryRepository staff;
    private final PreferenceRepository preferences;
    private final TemplateRepository templates;
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationPlanningServiceImpl(ClaimContactRepository contacts, StaffDirectoryRepository staff,
                                           PreferenceRepository preferences, TemplateRepository templates,
                                           NotificationRepository notifications, Clock clock) {
        this.contacts = contacts;
        this.staff = staff;
        this.preferences = preferences;
        this.templates = templates;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public List<Notification> planNotifications(EventEnvelope event) {
        Optional<ClaimContact> contact = resolveContact(event);
        if (contact.isEmpty()) {
            return List.of();
        }
        Map<String, String> variables = templateVariables(event, contact.get());
        List<Notification> planned = new ArrayList<>();
        for (Audience audience : NotificationRules.audiencesFor(event.type(), event.data())) {
            switch (audience) {
                case CUSTOMER -> planned.addAll(planForCustomer(event, contact.get(), variables));
                case HANDLER -> {
                    String handler = NotificationRules.handlerFor(event.type(), event.data(), contact.get().handlerId());
                    if (handler != null) {
                        planned.addAll(planForStaff(event, contact.get(), Audience.HANDLER, handler, variables));
                    }
                }
                case TEAM_LEAD -> {
                    String team = Optional.ofNullable(event.data().get("teamId")).map(Object::toString).orElse(contact.get().teamId());
                    if (team != null) {
                        for (StaffMember lead : staff.findTeamLeadsOfTeam(team)) {
                            planned.addAll(planForStaff(event, contact.get(), Audience.TEAM_LEAD, lead.userId(), variables));
                        }
                    }
                }
            }
        }
        planned.forEach(notifications::saveIfAbsent);
        return planned;
    }

    private Optional<ClaimContact> resolveContact(EventEnvelope e) {
        Map<String, Object> d = e.data();
        if (EventTypes.CLAIM_SUBMITTED.equals(e.type())) {
            ClaimContact created = new ClaimContact(e.claimId(), str(d, "claimNumber"), str(d, "claimantId"), e.marketCode(),
                    e.lineOfBusiness(), null, null);
            contacts.save(created);
            return Optional.of(created);
        }
        Optional<ClaimContact> existing = contacts.findByClaimId(e.claimId());
        if (existing.isPresent() && EventTypes.CLAIM_ASSIGNED.equals(e.type())) {
            ClaimContact assigned = existing.get().assignedTo(str(d, "handlerId"), str(d, "teamId"));
            contacts.save(assigned);
            return Optional.of(assigned);
        }
        if (existing.isEmpty() && str(d, "claimNumber") != null) {
            // insights.alerts and claims.claim-events are separate topics with no ordering between them: an alert
            // can arrive before this service has seen the claim. Alerts carry what staff notifications need.
            return Optional.of(new ClaimContact(e.claimId(), str(d, "claimNumber"), null, e.marketCode(), e.lineOfBusiness(),
                    str(d, "handlerId"), str(d, "teamId")));
        }
        return existing;
    }

    private List<Notification> planForCustomer(EventEnvelope e, ClaimContact c, Map<String, String> vars) {
        if (c.claimantId() == null) {
            return List.of();
        }
        Preference preference = preferences.findByUserId(c.claimantId()).orElse(Preference.defaults(c.claimantId()));
        List<Notification> out = new ArrayList<>();
        for (Preference.Delivery delivery : preference.deliveries()) {
            findTemplate(e.type(), Audience.CUSTOMER, delivery.channel(), preference.language()).ifPresent(t ->
                    out.add(Notification.pending(c.claimantId(), Audience.CUSTOMER, c.claimId(), c.claimNumber(), e.eventId(),
                            e.type(), delivery.channel(), delivery.address(), t.language(),
                            TemplateText.render(t.subject(), vars), TemplateText.render(t.body(), vars), clock.instant())));
        }
        return out;
    }

    private List<Notification> planForStaff(EventEnvelope e, ClaimContact c, Audience audience, String userId, Map<String, String> vars) {
        Optional<StaffMember> member = staff.findByUserId(userId);
        List<Notification> out = new ArrayList<>();
        List<Preference.Delivery> deliveries = new ArrayList<>(List.of(new Preference.Delivery(Channel.IN_APP, userId)));
        member.ifPresent(m -> deliveries.add(new Preference.Delivery(Channel.EMAIL, m.email())));
        for (Preference.Delivery delivery : deliveries) {
            findTemplate(e.type(), audience, delivery.channel(), "en").ifPresent(t ->
                    out.add(Notification.pending(userId, audience, c.claimId(), c.claimNumber(), e.eventId(), e.type(),
                            delivery.channel(), delivery.address(), t.language(), TemplateText.render(t.subject(), vars),
                            TemplateText.render(t.body(), vars), clock.instant())));
        }
        return out;
    }

    /** Most specific first: channel+language, any-channel+language, then the base language (zh-Hant → zh), then English. */
    private Optional<Template> findTemplate(String eventType, Audience audience, Channel channel, String language) {
        for (String lang : languageFallbacks(language)) {
            Optional<Template> found = templates.findByKey(eventType, audience, channel, lang)
                    .or(() -> templates.findByKey(eventType, audience, null, lang));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    static List<String> languageFallbacks(String language) {
        java.util.LinkedHashSet<String> chain = new java.util.LinkedHashSet<>();
        chain.add(language);
        int dash = language.indexOf('-');
        if (dash > 0) {
            chain.add(language.substring(0, dash));
        }
        chain.add("en");
        return List.copyOf(chain);
    }

    private Map<String, String> templateVariables(EventEnvelope e, ClaimContact c) {
        Map<String, Object> d = e.data();
        Map<String, String> v = new HashMap<>();
        v.put("claimNumber", Optional.ofNullable(str(d, "claimNumber")).orElse(c.claimNumber()));
        String handlerId = Optional.ofNullable(str(d, "handlerId")).orElse(c.handlerId());
        v.put("handlerName", staffName(handlerId, "Your claim manager"));
        v.put("proposerName", staffName(str(d, "actorId"), "A handler"));
        v.put("approverName", staffName(str(d, "approverId"), "Your team lead"));
        Object items = d.get("items");
        v.put("items", items instanceof List<?> list ? String.join(", ", list.stream().map(Object::toString).toList()) : "");
        Optional.ofNullable(str(d, "dueAt")).ifPresent(due -> v.put("dueDate", Instant.parse(due).atZone(ZoneOffset.UTC).toLocalDate().toString()));
        v.put("amount", formatAmount(d));
        v.put("reason", Optional.ofNullable(str(d, "reasonCode")).or(() -> Optional.ofNullable(str(d, "reason"))).orElse(""));
        v.put("outcome", Optional.ofNullable(str(d, "outcome")).map(o -> o.equals("APPROVE") ? "approve" : "reject").orElse(""));
        v.put("comment", Optional.ofNullable(str(d, "comment")).orElse(""));
        v.put("hoursRemaining", Optional.ofNullable(str(d, "hoursRemaining")).orElse(""));
        v.put("slaDueAt", Optional.ofNullable(str(d, "slaDueAt")).orElse(""));
        return v;
    }

    private String staffName(String userId, String fallback) {
        return userId == null ? fallback : staff.findByUserId(userId).map(StaffMember::name).orElse(fallback);
    }

    private static String formatAmount(Map<String, Object> d) {
        Object currency = d.get("currency");
        Object minor = Optional.ofNullable(d.get("approvedAmountMinor")).orElse(d.get("amountMinor"));
        if (currency == null || !(minor instanceof Number n)) {
            return "";
        }
        return Money.ofMinor(n.longValue(), currency.toString()).toString();
    }

    private static String str(Map<String, Object> d, String key) {
        Object value = d.get(key);
        return value == null ? null : value.toString();
    }
}

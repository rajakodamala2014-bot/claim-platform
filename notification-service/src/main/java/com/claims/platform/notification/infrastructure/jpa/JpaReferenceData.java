package com.claims.platform.notification.infrastructure.jpa;

import com.claims.platform.notification.application.NotificationPorts.Preferences;
import com.claims.platform.notification.application.NotificationPorts.StaffDirectory;
import com.claims.platform.notification.application.NotificationPorts.StaffMember;
import com.claims.platform.notification.application.NotificationPorts.Template;
import com.claims.platform.notification.application.NotificationPorts.Templates;
import com.claims.platform.notification.domain.Audience;
import com.claims.platform.notification.domain.Channel;
import com.claims.platform.notification.domain.Preference;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the notification service's reference data: plain one-table CRUD (customer preferences,
 * admin-edited templates, staff directory), where an ORM removes boilerplate without hiding anything important.
 * Entities are mapped to the domain/application records here, so JPA never leaks past infrastructure.
 */
@Repository
public class JpaReferenceData implements Preferences, Templates, StaffDirectory {
    private final PreferenceRepository preferences;
    private final TemplateRepository templates;
    private final StaffMemberRepository staff;

    JpaReferenceData(PreferenceRepository preferences, TemplateRepository templates, StaffMemberRepository staff) {
        this.preferences = preferences;
        this.templates = templates;
        this.staff = staff;
    }

    // ------------------------------------------------------------------ preferences

    @Override
    @Transactional(readOnly = true)
    public Optional<Preference> findPreference(String userId) {
        return preferences.findById(userId).map(PreferenceEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(Preference preference) {
        PreferenceEntity entity = preferences.findById(preference.userId())
                .orElseGet(() -> new PreferenceEntity(preference.userId()));
        entity.copyFrom(preference);
        preferences.save(entity);
    }

    // ------------------------------------------------------------------ templates

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> find(String eventType, Audience audience, Channel channel, String language) {
        return (channel == null
                ? templates.findByEventTypeAndAudienceAndChannelIsNullAndLanguage(eventType, audience, language)
                : templates.findByEventTypeAndAudienceAndChannelAndLanguage(eventType, audience, channel, language))
                .map(TemplateEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> all() {
        return templates.findAllByOrderByEventTypeAscAudienceAscLanguageAscChannelAsc().stream()
                .map(TemplateEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> byId(String id) {
        return templates.findById(id).map(TemplateEntity::toDomain);
    }

    @Override
    @Transactional
    public void update(String id, String subject, String body) {
        templates.findById(id).ifPresent(t -> t.reword(subject, body)); // dirty checking writes it on commit
    }

    // ------------------------------------------------------------------ staff directory

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> findStaff(String userId) {
        return staff.findById(userId).map(StaffMemberEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> teamLeadsOf(String teamId) {
        return staff.findByTeamIdAndRoleOrderByUserId(teamId, "TEAM_LEAD").stream()
                .map(StaffMemberEntity::toDomain).toList();
    }
}

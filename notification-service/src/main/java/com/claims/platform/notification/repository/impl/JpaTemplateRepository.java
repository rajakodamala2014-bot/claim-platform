package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Template;
import com.claims.platform.notification.entity.TemplateEntity;
import com.claims.platform.notification.repository.TemplateRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** JPA-backed message templates. Rows are mapped to the {@link Template} record here. */
@Repository
public class JpaTemplateRepository implements TemplateRepository {
    private final TemplateEntityRepository templates;

    JpaTemplateRepository(TemplateEntityRepository templates) {
        this.templates = templates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findByKey(String eventType, Audience audience, Channel channel, String language) {
        return (channel == null
                ? templates.findByEventTypeAndAudienceAndChannelIsNullAndLanguage(eventType, audience, language)
                : templates.findByEventTypeAndAudienceAndChannelAndLanguage(eventType, audience, channel, language))
                .map(TemplateEntity::toTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> findAll() {
        return templates.findAllByOrderByEventTypeAscAudienceAscLanguageAscChannelAsc().stream()
                .map(TemplateEntity::toTemplate).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findById(String id) {
        return templates.findById(id).map(TemplateEntity::toTemplate);
    }

    @Override
    @Transactional
    public void updateWording(String id, String subject, String body) {
        templates.findById(id).ifPresent(t -> t.reword(subject, body)); // dirty checking writes it on commit
    }
}

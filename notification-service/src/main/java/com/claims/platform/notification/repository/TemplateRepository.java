package com.claims.platform.notification.repository;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Template;

import java.util.List;
import java.util.Optional;

/** Message templates, editable by managers. */
public interface TemplateRepository {
    /** Exact match only; fallbacks are decided by the caller. {@code channel} null = the any-channel template. */
    Optional<Template> findByKey(String eventType, Audience audience, Channel channel, String language);

    List<Template> findAll();

    Optional<Template> findById(String id);

    void updateWording(String id, String subject, String body);
}

package com.claims.platform.notification.service;

import com.claims.platform.notification.entity.Template;

import java.util.List;

/** Template administration (managers only). */
public interface NotificationTemplateService {
    List<Template> listTemplates();

    /** Changes the wording of a template; placeholders are kept as typed. */
    Template updateTemplate(String id, String subject, String body);
}

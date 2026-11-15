package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Template;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.repository.TemplateRepository;
import com.claims.platform.notification.service.NotificationTemplateService;
import org.springframework.stereotype.Service;

import java.util.List;

/** N6. */
@Service
public class NotificationTemplateServiceImpl implements NotificationTemplateService {
    private final TemplateRepository templates;

    public NotificationTemplateServiceImpl(TemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    public List<Template> listTemplates() {
        return templates.findAll();
    }

    @Override
    public Template updateTemplate(String id, String subject, String body) {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, "subject and body are required");
        }
        templates.findById(id).orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND, "Template " + id + " not found"));
        templates.updateWording(id, subject, body);
        return templates.findById(id).orElseThrow();
    }
}

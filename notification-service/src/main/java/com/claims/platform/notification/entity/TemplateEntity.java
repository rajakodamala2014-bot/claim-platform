package com.claims.platform.notification.entity;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A message template row; {@code channel} null means it applies to any channel. */
@Entity
@Table(schema = "notification", name = "template")
public class TemplateEntity {
    @Id
    private String id;
    @Column(name = "event_type")
    private String eventType;
    @Enumerated(EnumType.STRING)
    private Audience audience;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    private String language;
    private String subject;
    @Column(columnDefinition = "text")
    private String body;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected TemplateEntity() {
    }

    public void reword(String newSubject, String newBody) {
        this.subject = newSubject;
        this.body = newBody;
    }

    public Template toTemplate() {
        return new Template(id, eventType, audience, channel, language, subject, body);
    }
}

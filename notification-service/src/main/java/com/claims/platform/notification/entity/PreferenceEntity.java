package com.claims.platform.notification.entity;

import com.claims.platform.notification.entity.Preference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Persistence shape of a customer's {@link Preference}; the domain record stays free of JPA. */
@Entity
@Table(schema = "notification", name = "preference")
public class PreferenceEntity {
    @Id
    @Column(name = "user_id")
    private String userId;
    private String language;
    private String email;
    @Column(name = "email_enabled")
    private boolean emailEnabled;
    private String phone;
    @Column(name = "sms_enabled")
    private boolean smsEnabled;
    @Column(name = "push_enabled")
    private boolean pushEnabled;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PreferenceEntity() {
    }

    public PreferenceEntity(String userId) {
        this.userId = userId;
    }

    public void copyFrom(Preference p) {
        language = p.language();
        email = p.email();
        emailEnabled = p.emailEnabled();
        phone = p.phone();
        smsEnabled = p.smsEnabled();
        pushEnabled = p.pushEnabled();
    }

    public Preference toPreference() {
        return new Preference(userId, language, email, emailEnabled, phone, smsEnabled, pushEnabled);
    }
}

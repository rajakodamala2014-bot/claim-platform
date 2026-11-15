package com.claims.platform.notification.infrastructure.jpa;

import com.claims.platform.notification.domain.Audience;
import com.claims.platform.notification.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface TemplateRepository extends JpaRepository<TemplateEntity, String> {
    Optional<TemplateEntity> findByEventTypeAndAudienceAndChannelAndLanguage(String eventType, Audience audience,
                                                                             Channel channel, String language);

    /** The any-channel template (channel IS NULL). */
    Optional<TemplateEntity> findByEventTypeAndAudienceAndChannelIsNullAndLanguage(String eventType, Audience audience,
                                                                                   String language);

    List<TemplateEntity> findAllByOrderByEventTypeAscAudienceAscLanguageAscChannelAsc();
}

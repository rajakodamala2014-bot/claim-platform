package com.claims.platform.notification.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFallbackTest {
    @Test
    void regionalLanguagesFallBackToTheirBaseLanguageThenEnglish() {
        assertThat(NotificationPlanningServiceImpl.languageFallbacks("zh-Hant")).containsExactly("zh-Hant", "zh", "en");
        assertThat(NotificationPlanningServiceImpl.languageFallbacks("ms")).containsExactly("ms", "en");
        assertThat(NotificationPlanningServiceImpl.languageFallbacks("en")).containsExactly("en");
    }
}

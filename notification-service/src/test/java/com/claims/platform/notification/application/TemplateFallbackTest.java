package com.claims.platform.notification.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFallbackTest {
    @Test
    void regionalLanguagesFallBackToTheirBaseLanguageThenEnglish() {
        assertThat(PlanNotifications.fallbacks("zh-Hant")).containsExactly("zh-Hant", "zh", "en");
        assertThat(PlanNotifications.fallbacks("ms")).containsExactly("ms", "en");
        assertThat(PlanNotifications.fallbacks("en")).containsExactly("en");
    }
}

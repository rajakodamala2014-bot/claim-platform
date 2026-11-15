package com.claims.platform.common.event;

import com.claims.platform.common.messaging.Topics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypesTest {

    @Test
    void everyDeclaredTypeBelongsToExactlyOneTopic() {
        Set<String> declared = Arrays.stream(EventTypes.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(EventTypesTest::value)
                .collect(Collectors.toSet());

        Set<String> union = new HashSet<>(EventTypes.CLAIM_EVENTS);
        union.addAll(EventTypes.ALERTS);

        assertThat(union).containsExactlyInAnyOrderElementsOf(declared);
        assertThat(EventTypes.CLAIM_EVENTS).doesNotContainAnyElementsOf(EventTypes.ALERTS);
    }

    @Test
    void dlqNamingConvention() {
        assertThat(Topics.dlq(Topics.CLAIM_EVENTS)).isEqualTo("claims.claim-events.dlq");
    }

    private static String value(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

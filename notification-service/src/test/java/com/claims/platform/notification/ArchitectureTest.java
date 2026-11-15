package com.claims.platform.notification;

import com.claims.platform.architecture.CleanArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    private static final String BASE = "com.claims.platform.notification";
    private static final JavaClasses CLASSES = CleanArchitectureRules.productionClasses(BASE);

    @Test
    void importsProductionClasses() {
        assertThat(CLASSES.containPackage(BASE)).isTrue();
    }

    @Test
    void domainIsFrameworkFree() {
        CleanArchitectureRules.domainIsFrameworkFree().check(CLASSES);
    }

    @Test
    void layersPointInwards() {
        CleanArchitectureRules.layersPointInwards().check(CLASSES);
    }
}

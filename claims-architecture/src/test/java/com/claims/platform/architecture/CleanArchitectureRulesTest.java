package com.claims.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves each rule both accepts clean code and rejects a violation (so services can't pass vacuously). */
class CleanArchitectureRulesTest {
    private static final String FIXTURES = "com.claims.platform.architecture.fixtures";

    @Test
    void cleanCodePassesAllRules() {
        JavaClasses good = importPackage("good");

        assertThatCode(() -> CleanArchitectureRules.domainIsFrameworkFree().check(good)).doesNotThrowAnyException();
        assertThatCode(() -> CleanArchitectureRules.layersPointInwards().check(good)).doesNotThrowAnyException();
    }

    @Test
    void domainDependingOnALibraryIsRejected() {
        assertThatThrownBy(() -> CleanArchitectureRules.domainIsFrameworkFree().check(importPackage("baddomain")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyOrder")
                .hasMessageContaining("org.assertj");
    }

    @Test
    void domainDependingOnApplicationIsRejected() {
        assertThatThrownBy(() -> CleanArchitectureRules.layersPointInwards().check(importPackage("badlayer")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Backwards");
    }

    @Test
    void crossModuleDependencyIsRejectedButSharedCoreIsAllowed() {
        String base = FIXTURES + ".badmodules";
        JavaClasses classes = importPackage("badmodules");

        assertThatThrownBy(() -> CleanArchitectureRules.modulesAreIndependent(base, "intake", "handling").check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Slice intake depends on Slice handling")
                .hasMessageContaining("AssessClaim")
                // core is not one of the listed modules, so intake -> core is not a violation
                .message().doesNotContain("Slice core").doesNotContain("core.domain.Claim.<init>");
    }

    private static JavaClasses importPackage(String name) {
        return new ClassFileImporter().importPackages(FIXTURES + "." + name);
    }
}

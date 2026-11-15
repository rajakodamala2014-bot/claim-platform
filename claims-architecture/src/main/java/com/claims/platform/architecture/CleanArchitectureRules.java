package com.claims.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Rules every service must pass. Package convention inside a service (or inside each module of a modular
 * monolith): {@code ..domain..}, {@code ..application..}, {@code ..infrastructure..}, {@code ..api..}.
 */
public final class CleanArchitectureRules {
    /** Packages the domain may use: the JDK, other domain code and the framework-free shared kernel. */
    private static final String[] DOMAIN_ALLOWED = {"java..", "..domain..", "com.claims.platform.common.."};

    private CleanArchitectureRules() {
    }

    /**
     * Imports a service's production classes (tests excluded). Services check rules from plain JUnit Jupiter
     * tests with {@code rule.check(classes)}: the {@code @ArchTest} engine was silently discovering zero tests
     * under surefire, so a violation would not have failed the build.
     */
    public static JavaClasses productionClasses(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(basePackage);
    }

    /** Domain has zero framework imports: no Spring, JPA, Jackson, Lombok, Kafka... */
    public static ArchRule domainIsFrameworkFree() {
        return classes().that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED)
                .because("the domain must stay framework-free (clean architecture)")
                .allowEmptyShould(true);
    }

    /** domain <- application <- infrastructure/api. Adapters never depend on each other. */
    public static ArchRule layersPointInwards() {
        Architectures.LayeredArchitecture layers = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Infrastructure").definedBy("..infrastructure..")
                .layer("Api").definedBy("..api..")
                .whereLayer("Api").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Api", "Infrastructure");
        return layers.because("dependencies must point inwards towards the domain");
    }

    /**
     * Modules of a modular monolith (e.g. intake, handling, settlement under {@code basePackage}) must not
     * depend on each other. They share only the core domain and the platform module.
     */
    public static ArchRule modulesAreIndependent(String basePackage, String... modules) {
        String alternatives = String.join("|", modules);
        return slices().matching(basePackage + ".(" + alternatives + ")..")
                .should().notDependOnEachOther()
                .because("modules communicate only through the core domain, platform ports and events")
                .allowEmptyShould(true);
    }
}

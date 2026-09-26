package com.portfoliomanager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.portfoliomanager",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.portfoliomanager.";

    @ArchTest
    static final ArchRule domainImportsNothingFromTheFrameworkOrOtherLayers =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + "persistence..",
                            BASE + "application..",
                            BASE + "pricing..",
                            BASE + "importing..",
                            BASE + "auth..",
                            BASE + "web..",
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "tools.jackson..",
                            "com.fasterxml..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnApplicationOrWeb =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "persistence..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BASE + "application..", BASE + "web..");

    @ArchTest
    static final ArchRule pricingAndImportingDoNotDependOnUpperLayers =
            noClasses()
                    .that()
                    .resideInAnyPackage(BASE + "pricing..", BASE + "importing..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BASE + "application..", BASE + "web..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnWeb =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(BASE + "web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule webDoesNotTouchPersistenceDirectly =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(BASE + "persistence..")
                    .allowEmptyShould(true);
}

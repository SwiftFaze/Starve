package dev.starve

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(packages = ["dev.starve"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    @ArchTest
    val domainDependsOnNothingElse: ArchRule =
        classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("dev.starve.domain..", "java..", "kotlin..", "org.jetbrains..")
            .allowEmptyShould(true)

    @ArchTest
    val applicationDependsOnlyOnDomain: ArchRule =
        classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "dev.starve.application..",
                "dev.starve.domain..",
                "java..",
                "kotlin..",
                "org.jetbrains..",
            ).allowEmptyShould(true)

    @ArchTest
    val nothingDependsOnWeb: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackage("dev.starve.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.starve.web..")
            .allowEmptyShould(true)

    @ArchTest
    val webDoesNotDependOnInfra: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("dev.starve.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.starve.infra..")
            .allowEmptyShould(true)
}

package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.EvaluationResult
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import spock.lang.Specification

class NullabilityAnnotationUsageTest extends Specification {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("graphql")

    def "should only use JSpecify nullability annotations"() {
        given:
        ArchRule dependencyRule = ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "javax.annotation",
                        "org.jetbrains.annotations"
                )
                .because("We are using JSpecify nullability annotations only. Please change to use JSpecify.")

        when:
        EvaluationResult result = dependencyRule.evaluate(importedClasses)

        then:
        if (result.hasViolation()) {
            println "We are using JSpecify nullability annotations only. Please change the following to use JSpecify instead:"
            result.getFailureReport().getDetails().each { violation ->
                println "- ${violation}"
            }
        }
        !result.hasViolation()
    }
} 
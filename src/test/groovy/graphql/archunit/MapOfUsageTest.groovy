package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.EvaluationResult
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import spock.lang.Specification

class MapOfUsageTest extends Specification {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("graphql")

    def "should not use Map.of()"() {
        given:
        ArchRule mapOfRule = ArchRuleDefinition.noClasses()
                .should()
                .callMethod(Map.class, "of")
                .because("Map.of() does not guarantee insertion order. Use LinkedHashMap instead for consistent serialization order.")

        when:
        EvaluationResult result = mapOfRule.evaluate(importedClasses)

        then:
        if (result.hasViolation()) {
            println "Map.of() usage detected. Please use LinkedHashMap instead for consistent serialization order:"
            result.getFailureReport().getDetails().each { violation ->
                println "- ${violation}"
            }
        }
        !result.hasViolation()
    }
}
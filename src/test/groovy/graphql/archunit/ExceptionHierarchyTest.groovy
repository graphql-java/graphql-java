package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.EvaluationResult
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import graphql.GraphQLException
import spock.lang.Specification

class ExceptionHierarchyTest extends Specification {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("graphql")

    def "all custom exceptions should extend GraphQLException"() {
        given:
        ArchRule rule = ArchRuleDefinition.classes()
                .that().areAssignableTo(RuntimeException.class)
                .and().resideInAPackage("graphql..")
                .and().doNotHaveFullyQualifiedName(GraphQLException.class.getName())
                .and().doNotHaveFullyQualifiedName(RuntimeException.class.getName())
                .should().beAssignableTo(GraphQLException.class)
                .because("all custom exceptions in graphql-java should extend GraphQLException for a consistent exception hierarchy")

        when:
        EvaluationResult result = rule.evaluate(importedClasses)

        then:
        if (result.hasViolation()) {
            println "Exception hierarchy violations detected. All custom exceptions should extend GraphQLException:"
            result.getFailureReport().getDetails().each { violation ->
                println "- ${violation}"
            }
        }
        !result.hasViolation()
    }
}

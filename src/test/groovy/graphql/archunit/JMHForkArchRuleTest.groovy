package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.EvaluationResult
import com.tngtech.archunit.lang.SimpleConditionEvent
import org.openjdk.jmh.annotations.Fork
import spock.lang.Specification

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

class JMHForkArchRuleTest extends Specification {

    def "JMH benchmarks on classes should not have a fork value greater than 2"() {
        given:
        def importedClasses = new ClassFileImporter()
                .importPackages("benchmark", "performance", "graphql.execution")

        def rule = classes()
                .that().areAnnotatedWith(Fork.class)
                .and().areTopLevelClasses()
                .should(haveForkValueNotGreaterThan(2))

        when:
        EvaluationResult result = rule.evaluate(importedClasses)

        then:
        !result.hasViolation()

        cleanup:
        if (result.hasViolation()) {
            println result.getFailureReport().toString()
        }
    }

    private static ArchCondition<JavaClass> haveForkValueNotGreaterThan(int maxFork) {
        return new ArchCondition<JavaClass>("have a @Fork value of at most $maxFork") {
            @Override
            void check(JavaClass javaClass, ConditionEvents events) {
                def forkAnnotation = javaClass.getAnnotationOfType(Fork.class)
                if (forkAnnotation.value() > maxFork) {
                    def message = "Class ${javaClass.name} has a @Fork value of ${forkAnnotation.value()} which is > $maxFork"
                    events.add(SimpleConditionEvent.violated(javaClass, message))
                }
            }
        }
    }
}
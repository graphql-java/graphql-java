package graphql

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/**
 * This test ensures that all public API and experimental API classes in the graphql package 
 * are properly annotated with JSpecify annotations.
 */
class JSpecifyAnnotationsCheck extends Specification {

    private static final Set<String> ALLOWLIST = [] as Set

    def "should ensure all public API and experimental API classes have @NullMarked annotation"() {
        given:
        def classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("graphql")
                .stream()
                .filter { it.isAnnotatedWith("graphql.PublicApi") || it.isAnnotatedWith("graphql.ExperimentalApi") }
                .collect()

        when:
        def classesMissingAnnotation = classes
                .stream()
                .filter { !it.isAnnotatedWith("org.jspecify.annotations.NullMarked") }
                .map { it.name }
                .filter { it -> !ALLOWLIST.contains(it) }
                .collect()

        then:
        if (!classesMissingAnnotation.isEmpty()) {
            println """The following public API and experimental API classes are missing @NullMarked annotation:
            ${classesMissingAnnotation.sort().join("\n")}
            
Add @NullMarked to these public API classes and add @Nullable annotations where appropriate."""
            assert false, "Found ${classesMissingAnnotation.size()} public API and experimental API classes missing @NullMarked annotation"
        }
    }
} 
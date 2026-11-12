package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/**
 * This test ensures that all public API, SPI, and experimental API classes
 * are properly annotated with JSpecify annotations.
 */
class JSpecifyAnnotationsCheck extends Specification {

    def "ensure all public API, SPI, and experimental API classes have @NullMarked annotation"() {
        given:
        def classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("graphql")
                .stream()
                .filter { it.isAnnotatedWith("graphql.PublicApi") || it.isAnnotatedWith("graphql.PublicSpi") || it.isAnnotatedWith("graphql.ExperimentalApi") }
                .collect()

        when:
        def classesMissingAnnotation = classes
                .stream()
                .filter { !isJSpecifyAnnotated(it) }
                .map { it.name }
                .collect()

        then:
        if (!classesMissingAnnotation.isEmpty()) {
            throw new AssertionError("""The following public API, SPI, and experimental API classes are missing a JSpecify annotation:
${classesMissingAnnotation.sort().join("\n")}

Add @NullMarked or @NullUnmarked to these public API classes. See documentation at https://jspecify.dev/docs/user-guide/#nullmarked""")
        }
    }

    private static boolean isJSpecifyAnnotated(JavaClass javaClass) {
        return javaClass.isAnnotatedWith("org.jspecify.annotations.NullMarked") ||
                javaClass.isAnnotatedWith("org.jspecify.annotations.NullUnmarked") ||
                javaClass.enclosingClass.map { isJSpecifyAnnotated(it) }.orElse(false) ||
                javaClass.package.isAnnotatedWith("org.jspecify.annotations.NullMarked") ||
                javaClass.package.isAnnotatedWith("org.jspecify.annotations.NullUnmarked")
    }
}

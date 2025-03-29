package graphql

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/*
 * We selectively shade in a few classes of Guava, however we want to minimise dependencies so we want to keep this list small.
 * This check ensures no new Guava classes are added
 */
class GuavaLimitCheck extends Specification {

    static final String GUAVA_PACKAGE_PREFIX = "com.google.common"

    static final Set<String> ALLOWED_GUAVA_CLASSES = [
            "com.google.common.collect.ImmutableMap",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableList",
            "com.google.common.base.Strings",
            "com.google.common.collect.BiMap",
            "com.google.common.collect.HashBiMap",
            "com.google.common.collect.ImmutableCollection",
            "com.google.common.collect.LinkedHashMultimap",
            "com.google.common.collect.Multimap",
            "com.google.common.collect.Table",
            "com.google.common.collect.Sets",
            "com.google.common.collect.Multimaps",
            "com.google.common.collect.Iterables",
            "com.google.common.collect.HashBasedTable",
            "com.google.common.collect.HashMultimap",
            "com.google.common.collect.Interner",
            "com.google.common.collect.Interners"
    ]

    def "should identify which classes use prohibited Guava dependencies"() {
        given:
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("graphql")

        when:
        Map<String, Set<String>> violationsByClass = [:]

        importedClasses.each { javaClass ->
            def className = javaClass.name
            def guavaUsages = javaClass.getAccessesFromSelf()
                    .collect { it.targetOwner }
                    .findAll { it.packageName.startsWith(GUAVA_PACKAGE_PREFIX) && !ALLOWED_GUAVA_CLASSES.contains(it.fullName) }
                    .toSet()

            if (!guavaUsages.isEmpty()) {
                violationsByClass[className] = guavaUsages
            }
        }

        then:
        violationsByClass.isEmpty()

        cleanup: "if the test fails, provide detailed information about which classes have violations"
        if (!violationsByClass.isEmpty()) {
            def errorMessage = new StringBuilder("Prohibited Guava class usage found:\n")

            violationsByClass.each { className, guavaClasses ->
                errorMessage.append("\nClass: ${className} uses these prohibited Guava classes:\n")
                guavaClasses.each { guavaClass ->
                    errorMessage.append("  - ${guavaClass}\n")
                }
            }

            errorMessage.append("\nEither:\n")
            errorMessage.append("1. Preferred option: Replace them with alternatives that don't depend on Guava\n")
            errorMessage.append("2. If absolutely necessary: Add these Guava classes to the shade configuration in the build.gradle file\n")

            println errorMessage.toString()
        }
    }
}

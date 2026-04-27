package graphql.archunit

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/**
 * This test ensures that all public API and experimental API classes
 * are properly annotated with JSpecify annotations.
 */
class JSpecifyAnnotationsCheck extends Specification {

    private static final Set<String> JSPECIFY_EXEMPTION_LIST = [
            "graphql.analysis.QueryTraverser",
            "graphql.extensions.ExtensionsBuilder",
            "graphql.incremental.DeferPayload",
            "graphql.incremental.DelayedIncrementalPartialResult",
            "graphql.incremental.DelayedIncrementalPartialResultImpl",
            "graphql.incremental.IncrementalExecutionResult",
            "graphql.incremental.IncrementalExecutionResultImpl",
            "graphql.incremental.IncrementalPayload",
            "graphql.incremental.StreamPayload",
            "graphql.introspection.Introspection",
            "graphql.introspection.IntrospectionQuery",
            "graphql.introspection.IntrospectionQueryBuilder",
            "graphql.introspection.IntrospectionResultToSchema",
            "graphql.introspection.IntrospectionWithDirectivesSupport",
            "graphql.introspection.IntrospectionWithDirectivesSupport\$DirectivePredicateEnvironment",
            "graphql.language.ScalarTypeDefinition",
            "graphql.language.ScalarTypeExtensionDefinition",
            "graphql.language.SchemaDefinition",
            "graphql.language.SchemaExtensionDefinition",
            "graphql.language.Selection",
            "graphql.language.SelectionSet",
            "graphql.language.SelectionSetContainer",
            "graphql.language.TypeKind",
            "graphql.language.TypeName",
            "graphql.language.UnionTypeDefinition",
            "graphql.language.UnionTypeExtensionDefinition",
            "graphql.language.VariableDefinition",
            "graphql.normalized.ExecutableNormalizedField",
            "graphql.normalized.ExecutableNormalizedOperation",
            "graphql.normalized.ExecutableNormalizedOperationFactory",
            "graphql.normalized.ExecutableNormalizedOperationToAstCompiler",
            "graphql.normalized.NormalizedInputValue",
            "graphql.normalized.incremental.NormalizedDeferredExecution",
            "graphql.normalized.nf.NormalizedDocument",
            "graphql.normalized.nf.NormalizedDocumentFactory",
            "graphql.normalized.nf.NormalizedField",
            "graphql.normalized.nf.NormalizedOperation",
            "graphql.normalized.nf.NormalizedOperationToAstCompiler",
            "graphql.schema.diffing.SchemaGraph",
            "graphql.schema.validation.OneOfInputObjectRules",
            "graphql.util.CyclicSchemaAnalyzer",
            "graphql.util.querygenerator.QueryGenerator",
            "graphql.util.querygenerator.QueryGeneratorOptions",
            "graphql.util.querygenerator.QueryGeneratorOptions\$QueryGeneratorOptionsBuilder",
            "graphql.util.querygenerator.QueryGeneratorResult"
    ] as Set

    def "ensure all public API and experimental API classes have @NullMarked annotation"() {
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
                .filter { !it.isAnnotatedWith("org.jspecify.annotations.NullMarked") && !it.isAnnotatedWith("org.jspecify.annotations.NullUnmarked") }
                .map { it.name }
                .filter { it -> !JSPECIFY_EXEMPTION_LIST.contains(it) }
                .collect()

        then:
        if (!classesMissingAnnotation.isEmpty()) {
            throw new AssertionError("""The following public API and experimental API classes are missing a JSpecify annotation:
${classesMissingAnnotation.sort().join("\n")}

Add @NullMarked or @NullUnmarked to these public API classes. See documentation at https://jspecify.dev/docs/user-guide/#nullmarked""")
        }
    }

    def "exempted classes should not be annotated with @NullMarked or @NullUnmarked"() {
        given:
        def classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("graphql")

        when:
        def annotatedButExempted = classes.stream()
                .filter { JSPECIFY_EXEMPTION_LIST.contains(it.name) }
                .filter { it.isAnnotatedWith("org.jspecify.annotations.NullMarked") || it.isAnnotatedWith("org.jspecify.annotations.NullUnmarked") }
                .map { it.name }
                .collect()

        then:
        if (!annotatedButExempted.isEmpty()) {
            throw new AssertionError("""The following classes are in the JSpecify exemption list but are annotated with @NullMarked or @NullUnmarked:
${annotatedButExempted.sort().join("\n")}

Please remove them from the exemption list in ${JSpecifyAnnotationsCheck.class.simpleName}.groovy.""")
        }
    }
}

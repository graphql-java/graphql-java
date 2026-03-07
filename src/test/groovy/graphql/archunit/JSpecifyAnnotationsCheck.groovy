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
            "graphql.execution.incremental.DeferredExecution",
            "graphql.execution.instrumentation.ChainedInstrumentation",
            "graphql.execution.instrumentation.DocumentAndVariables",
            "graphql.execution.instrumentation.NoContextChainedInstrumentation",
            "graphql.execution.ResponseMapFactory",
            "graphql.execution.instrumentation.SimpleInstrumentation",
            "graphql.execution.instrumentation.SimpleInstrumentationContext",
            "graphql.execution.instrumentation.SimplePerformantInstrumentation",
            "graphql.execution.instrumentation.fieldvalidation.FieldAndArguments",
            "graphql.execution.instrumentation.fieldvalidation.FieldValidationEnvironment",
            "graphql.language.AbstractDescribedNode",
            "graphql.language.AstNodeAdapter",
            "graphql.language.AstPrinter",
            "graphql.language.AstSignature",
            "graphql.language.AstSorter",
            "graphql.language.AstTransformer",
            "graphql.language.Comment",
            "graphql.language.Definition",
            "graphql.language.DescribedNode",
            "graphql.language.Description",
            "graphql.language.Directive",
            "graphql.language.DirectiveDefinition",
            "graphql.language.DirectiveLocation",
            "graphql.language.DirectivesContainer",
            "graphql.language.Document",
            "graphql.language.EnumTypeDefinition",
            "graphql.language.EnumTypeExtensionDefinition",
            "graphql.language.EnumValueDefinition",
            "graphql.language.Field",
            "graphql.language.FieldDefinition",
            "graphql.language.FragmentDefinition",
            "graphql.language.FragmentSpread",
            "graphql.language.IgnoredChar",
            "graphql.language.IgnoredChars",
            "graphql.language.ImplementingTypeDefinition",
            "graphql.language.InlineFragment",
            "graphql.language.InputObjectTypeDefinition",
            "graphql.language.InputObjectTypeExtensionDefinition",
            "graphql.language.InputValueDefinition",
            "graphql.language.InterfaceTypeDefinition",
            "graphql.language.InterfaceTypeExtensionDefinition",
            "graphql.language.ListType",
            "graphql.language.Node",
            "graphql.language.NodeChildrenContainer",
            "graphql.language.NodeDirectivesBuilder",
            "graphql.language.NodeParentTree",
            "graphql.language.NodeTraverser",
            "graphql.language.NodeVisitor",
            "graphql.language.NodeVisitorStub",
            "graphql.language.NonNullType",
            "graphql.language.ObjectField",
            "graphql.language.ObjectTypeDefinition",
            "graphql.language.ObjectTypeExtensionDefinition",
            "graphql.language.OperationDefinition",
            "graphql.language.OperationTypeDefinition",
            "graphql.language.PrettyAstPrinter",
            "graphql.language.SDLDefinition",
            "graphql.language.SDLExtensionDefinition",
            "graphql.language.SDLNamedDefinition",
            "graphql.language.SourceLocation",
            "graphql.language.Type",
            "graphql.language.TypeDefinition",
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

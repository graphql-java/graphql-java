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
            "graphql.analysis.QueryComplexityCalculator",
            "graphql.analysis.QueryComplexityInfo",
            "graphql.analysis.QueryDepthInfo",
            "graphql.analysis.QueryReducer",
            "graphql.analysis.QueryTransformer",
            "graphql.analysis.QueryTraversalOptions",
            "graphql.analysis.QueryTraverser",
            "graphql.analysis.QueryVisitor",
            "graphql.analysis.QueryVisitorFieldArgumentEnvironment",
            "graphql.analysis.QueryVisitorFieldArgumentInputValue",
            "graphql.analysis.QueryVisitorFieldArgumentValueEnvironment",
            "graphql.analysis.QueryVisitorFieldEnvironment",
            "graphql.analysis.QueryVisitorFragmentDefinitionEnvironment",
            "graphql.analysis.QueryVisitorFragmentSpreadEnvironment",
            "graphql.analysis.QueryVisitorInlineFragmentEnvironment",
            "graphql.analysis.QueryVisitorStub",
            "graphql.analysis.values.ValueTraverser",
            "graphql.execution.ExecutionStrategyParameters",
            "graphql.execution.FetchedValue",
            "graphql.execution.FieldValueInfo",
            "graphql.execution.InputMapDefinesTooManyFieldsException",
            "graphql.execution.MergedSelectionSet",
            "graphql.execution.MissingRootTypeException",
            "graphql.execution.NonNullableValueCoercedAsNullException",
            "graphql.execution.NormalizedVariables",
            "graphql.execution.OneOfNullValueException",
            "graphql.execution.OneOfTooManyKeysException",
            "graphql.execution.ResultNodesInfo",
            "graphql.execution.ResultPath",
            "graphql.execution.SimpleDataFetcherExceptionHandler",
            "graphql.execution.SubscriptionExecutionStrategy",
            "graphql.execution.UnknownOperationException",
            "graphql.execution.UnresolvedTypeException",
            "graphql.execution.conditional.ConditionalNodeDecision",
            "graphql.execution.directives.QueryAppliedDirective",
            "graphql.execution.directives.QueryAppliedDirectiveArgument",
            "graphql.execution.directives.QueryDirectives",
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
            "graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation",
            "graphql.execution.instrumentation.fieldvalidation.SimpleFieldValidation",
            "graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationFieldParameters",
            "graphql.execution.instrumentation.parameters.InstrumentationValidationParameters",
            "graphql.execution.instrumentation.tracing.TracingInstrumentation",
            "graphql.execution.instrumentation.tracing.TracingSupport",
            "graphql.execution.preparsed.PreparsedDocumentEntry",
            "graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport",
            "graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache",
            "graphql.execution.preparsed.persisted.PersistedQueryCacheMiss",
            "graphql.execution.preparsed.persisted.PersistedQueryIdInvalid",
            "graphql.execution.preparsed.persisted.PersistedQueryNotFound",
            "graphql.execution.reactive.DelegatingSubscription",
            "graphql.execution.reactive.SubscriptionPublisher",
            "graphql.extensions.ExtensionsBuilder",
            "graphql.incremental.DeferPayload",
            "graphql.incremental.DelayedIncrementalPartialResult",
            "graphql.incremental.DelayedIncrementalPartialResultImpl",
            "graphql.incremental.IncrementalExecutionResult",
            "graphql.incremental.IncrementalExecutionResultImpl",
            "graphql.incremental.IncrementalPayload",
            "graphql.incremental.StreamPayload",
            "graphql.introspection.GoodFaithIntrospection",
            "graphql.introspection.Introspection",
            "graphql.introspection.IntrospectionQuery",
            "graphql.introspection.IntrospectionQueryBuilder",
            "graphql.introspection.IntrospectionResultToSchema",
            "graphql.introspection.IntrospectionWithDirectivesSupport",
            "graphql.introspection.IntrospectionWithDirectivesSupport\$DirectivePredicateEnvironment",
            "graphql.language.AbstractDescribedNode",
            "graphql.language.AstNodeAdapter",
            "graphql.language.AstPrinter",
            "graphql.language.AstSignature",
            "graphql.language.AstSorter",
            "graphql.language.AstTransformer",
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
            "graphql.language.ImplementingTypeDefinition",
            "graphql.language.InlineFragment",
            "graphql.language.InputObjectTypeDefinition",
            "graphql.language.InputObjectTypeExtensionDefinition",
            "graphql.language.InputValueDefinition",
            "graphql.language.InterfaceTypeDefinition",
            "graphql.language.InterfaceTypeExtensionDefinition",
            "graphql.language.ListType",
            "graphql.language.NodeDirectivesBuilder",
            "graphql.language.NodeParentTree",
            "graphql.language.NodeTraverser",
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
            "graphql.language.ScalarTypeDefinition",
            "graphql.language.ScalarTypeExtensionDefinition",
            "graphql.language.SchemaDefinition",
            "graphql.language.SchemaExtensionDefinition",
            "graphql.language.Selection",
            "graphql.language.SelectionSet",
            "graphql.language.SelectionSetContainer",
            "graphql.language.TypeDefinition",
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
            "graphql.parser.InvalidSyntaxException",
            "graphql.parser.MultiSourceReader",
            "graphql.parser.Parser",
            "graphql.parser.ParserEnvironment",
            "graphql.parser.ParserOptions",
            "graphql.schema.AsyncDataFetcher",
            "graphql.schema.CoercingParseLiteralException",
            "graphql.schema.CoercingParseValueException",
            "graphql.schema.CoercingSerializeException",
            "graphql.schema.DataFetcherFactories",
            "graphql.schema.DataFetcherFactoryEnvironment",
            "graphql.schema.DataFetchingFieldSelectionSet",
            "graphql.schema.DefaultGraphqlTypeComparatorRegistry",
            "graphql.schema.DelegatingDataFetchingEnvironment",
            "graphql.schema.FieldCoordinates",
            "graphql.schema.GraphQLAppliedDirectiveArgument",
            "graphql.schema.GraphQLArgument",
            "graphql.schema.GraphQLCompositeType",
            "graphql.schema.GraphQLDirective",
            "graphql.schema.GraphQLDirectiveContainer",
            "graphql.schema.GraphQLEnumValueDefinition",
            "graphql.schema.GraphQLFieldDefinition",
            "graphql.schema.GraphQLFieldsContainer",
            "graphql.schema.GraphQLImplementingType",
            "graphql.schema.GraphQLInputFieldsContainer",
            "graphql.schema.GraphQLInputObjectField",
            "graphql.schema.GraphQLInputObjectType",
            "graphql.schema.GraphQLInputSchemaElement",
            "graphql.schema.GraphQLInputType",
            "graphql.schema.GraphQLInputValueDefinition",
            "graphql.schema.GraphQLInterfaceType",
            "graphql.schema.GraphQLModifiedType",
            "graphql.schema.GraphQLNamedOutputType",
            "graphql.schema.GraphQLNamedSchemaElement",
            "graphql.schema.GraphQLNamedType",
            "graphql.schema.GraphQLNonNull",
            "graphql.schema.GraphQLNullableType",
            "graphql.schema.GraphQLObjectType",
            "graphql.schema.GraphQLOutputType",
            "graphql.schema.GraphQLSchemaElement",
            "graphql.schema.GraphQLTypeReference",
            "graphql.schema.GraphQLTypeVisitor",
            "graphql.schema.GraphQLTypeVisitorStub",
            "graphql.schema.GraphQLUnmodifiedType",
            "graphql.schema.GraphqlElementParentTree",
            "graphql.schema.GraphqlTypeComparatorEnvironment",
            "graphql.schema.GraphqlTypeComparatorRegistry",
            "graphql.schema.InputValueWithState",
            "graphql.schema.SchemaElementChildrenContainer",
            "graphql.schema.SchemaTransformer",
            "graphql.schema.SchemaTraverser",
            "graphql.schema.SelectedField",
            "graphql.schema.StaticDataFetcher",
            "graphql.schema.diff.DiffCategory",
            "graphql.schema.diff.DiffEvent",
            "graphql.schema.diff.DiffLevel",
            "graphql.schema.diff.DiffSet",
            "graphql.schema.diff.SchemaDiffSet",
            "graphql.schema.diff.reporting.CapturingReporter",
            "graphql.schema.diff.reporting.ChainedReporter",
            "graphql.schema.diff.reporting.PrintStreamReporter",
            "graphql.schema.diffing.SchemaGraph",
            "graphql.schema.idl.CombinedWiringFactory",
            "graphql.schema.idl.MapEnumValuesProvider",
            "graphql.schema.idl.NaturalEnumValuesProvider",
            "graphql.schema.idl.RuntimeWiring",
            "graphql.schema.idl.SchemaDirectiveWiring",
            "graphql.schema.idl.SchemaDirectiveWiringEnvironment",
            "graphql.schema.idl.SchemaGenerator",
            "graphql.schema.idl.SchemaPrinter",
            "graphql.schema.idl.TypeRuntimeWiring",
            "graphql.schema.idl.errors.SchemaProblem",
            "graphql.schema.idl.errors.StrictModeWiringException",
            "graphql.schema.transform.FieldVisibilitySchemaTransformation",
            "graphql.schema.transform.VisibleFieldPredicateEnvironment",
            "graphql.schema.usage.SchemaUsage",
            "graphql.schema.usage.SchemaUsageSupport",
            "graphql.schema.validation.OneOfInputObjectRules",
            "graphql.schema.validation.SchemaValidationErrorClassification",
            "graphql.schema.visibility.BlockedFields",
            "graphql.schema.visibility.DefaultGraphqlFieldVisibility",
            "graphql.schema.visibility.GraphqlFieldVisibility",
            "graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility",
            "graphql.schema.visitor.GraphQLSchemaTraversalControl",
            "graphql.util.CyclicSchemaAnalyzer",
            "graphql.util.NodeAdapter",
            "graphql.util.NodeLocation",
            "graphql.util.NodeMultiZipper",
            "graphql.util.NodeZipper",
            "graphql.util.querygenerator.QueryGenerator",
            "graphql.util.querygenerator.QueryGeneratorOptions",
            "graphql.util.querygenerator.QueryGeneratorOptions\$QueryGeneratorOptionsBuilder",
            "graphql.util.querygenerator.QueryGeneratorResult",
            "graphql.util.TraversalControl",
            "graphql.util.TraverserContext",
            "graphql.util.TreeTransformer",
            "graphql.util.TreeTransformerUtil"
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

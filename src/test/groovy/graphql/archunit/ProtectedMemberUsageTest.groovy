package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/**
 * Ensures that protected members are not used.
 * Only public (with @Internal if needed) or private access should be used.
 * See coding-guidelines.md.
 */
class ProtectedMemberUsageTest extends Specification {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("graphql")

    private static final Set<String> EXEMPTION_LIST = [
            "graphql.GraphQLUnusualConfiguration\$BaseConfig",
            "graphql.GraphQLUnusualConfiguration\$BaseContextConfig",
            "graphql.GraphqlErrorBuilder",
            "graphql.GraphqlErrorException",
            "graphql.GraphqlErrorException\$BuilderBase",
            "graphql.analysis.MaxQueryComplexityInstrumentation",
            "graphql.analysis.MaxQueryDepthInstrumentation",
            "graphql.analysis.NodeVisitorWithTypeTracking",
            "graphql.execution.AbstractAsyncExecutionStrategy",
            "graphql.execution.ExecutionStrategy",
            "graphql.execution.MergedSelectionSet",
            "graphql.execution.SimpleDataFetcherExceptionHandler",
            "graphql.execution.instrumentation.ChainedInstrumentation",
            "graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport",
            "graphql.execution.preparsed.persisted.PersistedQuerySupport",
            "graphql.execution.reactive.CompletionStageMappingOrderedPublisher",
            "graphql.execution.reactive.CompletionStageMappingPublisher",
            "graphql.execution.reactive.CompletionStageOrderedSubscriber",
            "graphql.execution.reactive.CompletionStageSubscriber",
            "graphql.i18n.I18n",
            "graphql.incremental.IncrementalPayload",
            "graphql.incremental.IncrementalPayload\$Builder",
            "graphql.language.AbstractDescribedNode",
            "graphql.language.AbstractNode",
            "graphql.language.Argument",
            "graphql.language.ArrayValue",
            "graphql.language.BooleanValue",
            "graphql.language.Directive",
            "graphql.language.DirectiveDefinition",
            "graphql.language.DirectiveLocation",
            "graphql.language.Document",
            "graphql.language.EnumTypeDefinition",
            "graphql.language.EnumTypeExtensionDefinition",
            "graphql.language.EnumValue",
            "graphql.language.EnumValueDefinition",
            "graphql.language.Field",
            "graphql.language.FieldDefinition",
            "graphql.language.FloatValue",
            "graphql.language.FragmentDefinition",
            "graphql.language.FragmentSpread",
            "graphql.language.InlineFragment",
            "graphql.language.InputObjectTypeDefinition",
            "graphql.language.InputObjectTypeExtensionDefinition",
            "graphql.language.InputValueDefinition",
            "graphql.language.IntValue",
            "graphql.language.InterfaceTypeDefinition",
            "graphql.language.InterfaceTypeExtensionDefinition",
            "graphql.language.ListType",
            "graphql.language.NodeVisitorStub",
            "graphql.language.NonNullType",
            "graphql.language.NullValue",
            "graphql.language.ObjectField",
            "graphql.language.ObjectTypeDefinition",
            "graphql.language.ObjectTypeExtensionDefinition",
            "graphql.language.ObjectValue",
            "graphql.language.OperationDefinition",
            "graphql.language.OperationTypeDefinition",
            "graphql.language.ScalarTypeDefinition",
            "graphql.language.ScalarTypeExtensionDefinition",
            "graphql.language.SchemaDefinition",
            "graphql.language.SchemaDefinition\$Builder",
            "graphql.language.SchemaExtensionDefinition",
            "graphql.language.SchemaExtensionDefinition\$Builder",
            "graphql.language.SelectionSet",
            "graphql.language.StringValue",
            "graphql.language.TypeName",
            "graphql.language.UnionTypeDefinition",
            "graphql.language.UnionTypeExtensionDefinition",
            "graphql.language.VariableDefinition",
            "graphql.language.VariableReference",
            "graphql.parser.CommentParser",
            "graphql.parser.GraphqlAntlrToLanguage",
            "graphql.parser.InvalidSyntaxException",
            "graphql.parser.NodeToRuleCapturingParser",
            "graphql.parser.NodeToRuleCapturingParser\$ParserContext",
            "graphql.parser.Parser",
            "graphql.parser.antlr.GraphqlLexer",
            "graphql.parser.antlr.GraphqlParser",
            "graphql.schema.DelegatingDataFetchingEnvironment",
            "graphql.schema.GraphQLTypeResolvingVisitor",
            "graphql.schema.GraphQLTypeVisitorStub",
            "graphql.schema.GraphqlDirectivesContainerTypeBuilder",
            "graphql.schema.GraphqlTypeBuilder",
            "graphql.schema.diffing.HungarianAlgorithm",
            "graphql.schema.idl.TypeDefinitionRegistry",
            "graphql.schema.idl.errors.BaseError",
            "graphql.schema.transform.FieldVisibilitySchemaTransformation\$TypeObservingVisitor",
            "graphql.schema.validation.AppliedDirectivesAreValid",
            "graphql.schema.visitor.GraphQLSchemaVisitorEnvironmentImpl",
            "graphql.validation.ArgumentValidationUtil",
            "graphql.validation.ValidationUtil",
    ] as Set

    def "should not use protected members - use public with @Internal or private instead"() {
        when:
        def violatingClasses = importedClasses.stream()
                .filter { javaClass ->
                    javaClass.fields.any { it.modifiers.contains(JavaModifier.PROTECTED) } ||
                            javaClass.methods.any { it.modifiers.contains(JavaModifier.PROTECTED) } ||
                            javaClass.constructors.any { it.modifiers.contains(JavaModifier.PROTECTED) }
                }
                .map { it.name }
                .filter { !EXEMPTION_LIST.contains(it) }
                .sorted()
                .collect()

        then:
        if (!violatingClasses.isEmpty()) {
            throw new AssertionError("""The following classes have protected members:
${violatingClasses.join("\n")}

Use public (with @Internal annotation if needed) or private instead of protected. See coding-guidelines.md.
If a class must use protected members, add it to the exemption list in ${ProtectedMemberUsageTest.class.simpleName}.groovy.""")
        }
    }

    def "exempted classes should actually have protected members"() {
        when:
        def exemptedButClean = EXEMPTION_LIST.findAll { exemptedClassName ->
            def javaClass = importedClasses.stream()
                    .filter { it.name == exemptedClassName }
                    .findFirst()
            if (!javaClass.isPresent()) return true
            def cls = javaClass.get()
            !(cls.fields.any { it.modifiers.contains(JavaModifier.PROTECTED) } ||
                    cls.methods.any { it.modifiers.contains(JavaModifier.PROTECTED) } ||
                    cls.constructors.any { it.modifiers.contains(JavaModifier.PROTECTED) })
        }.sort()

        then:
        if (!exemptedButClean.isEmpty()) {
            throw new AssertionError("""The following classes are in the exemption list but no longer have protected members:
${exemptedButClean.join("\n")}

Please remove them from the exemption list in ${ProtectedMemberUsageTest.class.simpleName}.groovy.""")
        }
    }
}

package graphql.validation

import graphql.execution.CoercedVariables
import graphql.execution.TypeFromAST
import graphql.introspection.Introspection
import graphql.language.BooleanValue
import graphql.language.Directive
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.Node
import graphql.language.NodeChildrenContainer
import graphql.language.OperationDefinition
import graphql.normalized.ExecutableNormalizedField
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.parser.Parser
import graphql.parser.ParserEnvironment
import graphql.parser.ParserOptions
import graphql.parser.exceptions.ParseCancelledException
import graphql.parser.exceptions.ParseCancelledTooDeepException
import graphql.parser.exceptions.ParseCancelledTooManyCharsException
import graphql.parser.exceptions.ParseCancelledTooManyNumericLiteralCharactersException
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import spock.lang.Shared
import spock.lang.Specification

class LargeSchemaQueryComplexityTest extends Specification {
    @Shared
    GraphQLSchema schema

    @Shared
    Document document

    def setupSpec() {
        def registry = new TypeDefinitionRegistry()
        // Parts end at definition boundaries; parsing separately avoids retaining the entire SDL parse tree.
        (1..4).each { registry.merge(new SchemaParser().parse(loadResource("large-schema-6.graphqls.part$it"))) }
        schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)
        // This fixture exceeds the default 15,000-token parser limit. Keep the override local.
        def options = ParserOptions.getDefaultParserOptions().transform { it.maxTokens(100_000) }
        document = Parser.parse(ParserEnvironment.newParserEnvironment()
                .document(loadResource('large-schema-6-query.graphql')).parserOptions(options).build())
    }

    def "large schema fixture contains ordinary selections including typename but no root introspection"() {
        given:
        def nodes = collectNodes(document)
        def fields = nodes.findAll { it instanceof Field }

        expect:
        document.getDefinitionsOfType(OperationDefinition).size() == 1
        document.getDefinitionsOfType(FragmentDefinition).size() == 663
        fields.size() == 5687
        fields.count { it.name == Introspection.TypeNameMetaFieldDef.name } == 393
        !fields.any { it.name in [Introspection.SchemaMetaFieldDef.name, Introspection.TypeMetaFieldDef.name] }
        validate(QueryComplexityLimits.DEFAULT).empty
    }

    def "large schema query has exact #metric boundary #count"() {
        given:
        def exact = QueryComplexityLimits.newLimits()
        exact."$metric"(count)
        def below = QueryComplexityLimits.newLimits()
        below."$metric"(count - 1)

        expect:
        validate(exact.build()).empty
        validate(below.build())*.validationErrorType == [error]

        where:
        metric                | count  | error
        'maxMergedFieldCount'  | 4311   | ValidationErrorType.MaxQueryMergedFieldCountExceeded
        'maxFieldOccurrences'  | 91_515 | ValidationErrorType.MaxQueryFieldOccurrencesExceeded
        'maxDepth'             | 15     | ValidationErrorType.MaxQueryDepthExceeded
    }

    def "large query has exact parser #option boundary #count"() {
        given:
        def query = loadResource('large-schema-6-query.graphql')
        def exact = ParserOptions.getDefaultParserOptions().transform {
            it.maxTokens(100_000)
            it."$option"(count)
        }
        def below = exact.transform { it."$option"(count - 1) }

        expect:
        Parser.parse(ParserEnvironment.newParserEnvironment().document(query).parserOptions(exact).build()) != null

        when:
        Parser.parse(ParserEnvironment.newParserEnvironment().document(query).parserOptions(below).build())

        then:
        thrown(failure)

        where:
        option                        | count   | failure
        'maxTokens'                   | 22_480  | ParseCancelledException
        'maxWhitespaceTokens'         | 7414    | ParseCancelledException
        'maxCharacters'               | 194_881 | ParseCancelledTooManyCharsException
        'maxRuleDepth'                | 47      | ParseCancelledTooDeepException
        'maxNumericLiteralCharacters' | 5       | ParseCancelledTooManyNumericLiteralCharactersException
    }

    def "ENF creation count and final tree differ from validation groups with all selections retained"() {
        given:
        def operation = document.getDefinitionsOfType(OperationDefinition).first()
        // These stand-in coerced values satisfy arguments; they are not production request variables.
        def variables = operation.variableDefinitions.collectEntries {
            [(it.name): sampleValue(TypeFromAST.getTypeFromAST(schema, it.type))]
        }
        def structuralDocument = (Document) retainAllConditionalSelections(document)

        when:
        def normalized = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(
                schema, structuralDocument, operation.name, CoercedVariables.of(variables))

        then:
        normalized.operationFieldCount == 5648
        normalized.operationDepth == 15
        countNormalizedFields(normalized.topLevelFields) == 3882
    }

    private List<ValidationError> validate(QueryComplexityLimits limits) {
        new Validator().validateDocument(schema, document, { true }, Locale.ENGLISH, limits)
    }

    private static String loadResource(String name) {
        LargeSchemaQueryComplexityTest.getResource('/' + name).getText('UTF-8')
    }

    private static List<Node> collectNodes(Node node) {
        [node] + node.children.collectMany { collectNodes(it) }
    }

    private static int countNormalizedFields(List<ExecutableNormalizedField> fields) {
        fields.sum(0) { 1 + countNormalizedFields(it.children) } as int
    }

    private static Node retainAllConditionalSelections(Node node) {
        if (node instanceof Directive && node.name in ['skip', 'include']) {
            return retainConditionalSelection(node)
        }
        def children = node.namedChildren.children.collectEntries { name, values ->
            [(name): values.collect { retainAllConditionalSelections(it) }]
        }
        node.withNewChildren(NodeChildrenContainer.newNodeChildrenContainer(children).build())
    }

    private static Directive retainConditionalSelection(Directive directive) {
        def value = BooleanValue.newBooleanValue(directive.name == 'include').build()
        def arguments = directive.arguments.collect { argument -> argument.transform { it.value(value) } }
        directive.transform { it.arguments(arguments) }
    }

    private static Object sampleValue(GraphQLType type) {
        if (type instanceof GraphQLNonNull) {
            return sampleValue(type.wrappedType)
        }
        if (type instanceof GraphQLList) {
            return []
        }
        if (type instanceof GraphQLInputObjectType) {
            return sampleInputObject(type)
        }
        if (type instanceof GraphQLEnumType) {
            return type.values.first().value
        }
        switch (type.name) {
            case 'Boolean': return true
            case 'Int': return 1
            case 'Float': return 1.0d
            default: return 'example'
        }
    }

    private static Map<String, Object> sampleInputObject(GraphQLInputObjectType type) {
        if (type.isOneOf()) {
            def field = type.fieldDefinitions.first()
            return [(field.name): sampleValue(field.type)]
        }
        type.fieldDefinitions.findAll { it.type instanceof GraphQLNonNull && !it.hasSetDefaultValue() }
                .collectEntries { [(it.name): sampleValue(it.type)] }
    }
}

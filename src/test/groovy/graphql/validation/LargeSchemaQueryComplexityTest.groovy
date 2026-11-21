package graphql.validation

import graphql.GraphQLContext
import graphql.normalized.NormalizedDocument
import graphql.introspection.Introspection
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.Node
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.parser.ParserEnvironment
import graphql.parser.ParserOptions
import graphql.parser.exceptions.ParseCancelledException
import graphql.parser.exceptions.ParseCancelledTooDeepException
import graphql.parser.exceptions.ParseCancelledTooManyCharsException
import graphql.parser.exceptions.ParseCancelledTooManyNumericLiteralCharactersException
import graphql.schema.GraphQLSchema
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
        def exact = QueryComplexityLimits.newLimitsWithDefaults()
        exact."$metric"(count)
        def below = QueryComplexityLimits.newLimitsWithDefaults()
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

    def "the prepared graph stores AST fields once without requiring execution variables"() {
        given:
        def context = GraphQLContext.of([(NormalizedDocument.ENABLED): true])

        when:
        def result = new Validator().validateDocumentWithResult(schema, document, { true }, Locale.ENGLISH,
                QueryComplexityLimits.DEFAULT, context)

        then:
        result.errors().empty
        result.normalizedDocument().fields.size() == 5687
        result.normalizedDocument().operations.size() == 1
        result.normalizedDocument().document.is(document)
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

}

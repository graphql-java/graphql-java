package graphql.validation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class FragmentStackSafetyTest extends Specification {
    GraphQLSchema schema = TestUtil.schema('''
        type Query { node: Node }
        type Subscription { event: Node }
        type Node { name: String child: Node }
    ''')

    def "1501 fragment definitions validate and execute on a small stack"() {
        given:
        String query = '{ node { ...F0 } }' + chain('Node', 'name')
        // Parsing uses the default limits: shallow definitions fit even though references form a long chain.
        Document document = Parser.parse(query)
        def limits = QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(2).maxFieldOccurrences(2).maxDepth(2).build()
        def graphql = GraphQL.newGraphQL(schema).build()
        def input = ExecutionInput.newExecutionInput().query(query).root([node: [name: 'value']]).build()
        def validation = new FutureTask({ ->
            assert validate(document, limits).empty
            def result = graphql.execute(input)
            assert result.errors.empty
            assert result.getData() == [node: [name: 'value']]
            true
        } as Callable<Boolean>)
        def thread = new Thread(null, validation, 'fragment-stack-regression', 1024 * 1024)
        thread.daemon = true

        when:
        thread.start()

        then:
        document.getDefinitionsOfType(FragmentDefinition).size() == 1501
        validation.get(30, TimeUnit.SECONDS)
    }

    def "deep programmatic inline fragments use the iterative AST traversal"() {
        given:
        def selection = new SelectionSet([new Field('name')])
        5000.times {
            selection = new SelectionSet([InlineFragment.newInlineFragment().selectionSet(selection).build()])
        }
        def node = Field.newField('node').selectionSet(selection).build()
        def operation = OperationDefinition.newOperationDefinition().selectionSet(new SelectionSet([node])).build()
        def document = new Document([operation])

        expect:
        validate(document, QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(2).maxFieldOccurrences(2).maxDepth(2).build()).empty
    }

    def "long root fragment chains preserve #operation validation"() {
        given:
        def query = "$operation { ...F0 }" + chain(type, selection)

        expect:
        validate(Parser.parse(query))*.validationErrorType == expected

        where:
        operation      | type           | selection                           | expected
        'query'        | 'Query'        | 'node { name }'                     | []
        'query'        | 'Query'        | '__schema { queryType { name } }'  | []
        'subscription' | 'Subscription' | 'event { name }'                    | []
        'subscription' | 'Subscription' | 'a: event { name } b: event { name }' | [ValidationErrorType.SubscriptionMultipleRootFields]
    }

    def "fragment indirection preserves field counts and depths for #selection"() {
        given:
        def document = Parser.parse("{ node { $selection } }" + chain('Node', 'name'))

        expect:
        validate(document, QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(mergedFields).maxFieldOccurrences(occurrences).maxDepth(depth).build()).empty
        validate(document, QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(mergedFields - 1).build())*.validationErrorType == [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        validate(document, QueryComplexityLimits.newLimitsWithDefaults().maxFieldOccurrences(occurrences - 1).build())*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        validate(document, QueryComplexityLimits.newLimitsWithDefaults().maxDepth(depth - 1).build())*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]

        where:
        selection                        | mergedFields | occurrences | depth
        '...F0'                          | 2            | 2           | 2
        '...F0 ...F0'                    | 2            | 3           | 2
        'child { ...F0 } ...F0'           | 4            | 4           | 3
        '...F0 child { ...F0 } name'      | 4            | 5           | 3
    }

    def "operation scoped variable checks follow shared long chains in either definition order"() {
        given:
        String operations = 'query First($enabled: Boolean!) { node { ...F0 } } query Second { node { ...F0 } }'
        String fragments = chain('Node', 'name @include(if: $enabled)')
        def document = Parser.parse(fragmentsFirst ? fragments + operations : operations + fragments)
        def leaf = document.getDefinitionsOfType(FragmentDefinition).last().selectionSet.selections.first()
        def reference = leaf.directives.first().arguments.first().value

        when:
        def errors = validate(document)

        then:
        errors*.validationErrorType == [ValidationErrorType.UndefinedVariable]
        errors[0].locations == [reference.sourceLocation]
        errors[0].queryPath.first() == 'node'
        errors[0].queryPath.last() == 'name'

        where:
        fragmentsFirst << [false, true]
    }

    def "long chains with #ending return normal validation errors"() {
        given:
        def document = Parser.parse('{ node { ...F0 } }' + chain('Node', ending))

        expect:
        validate(document)*.validationErrorType.contains(error)

        where:
        ending       | error
        '...Missing' | ValidationErrorType.UndefinedFragment
        '...F750'    | ValidationErrorType.FragmentCycle
    }

    def "merge conflicts at the end of long chains retain source locations"() {
        given:
        def document = Parser.parse('{ node { ...F0 } }' + chain('Node', 'same: name same: child { name }'))
        def fields = document.getDefinitionsOfType(FragmentDefinition).last().selectionSet.selections

        when:
        def errors = validate(document)

        then:
        errors*.validationErrorType == [ValidationErrorType.FieldsConflict]
        errors[0].locations == fields*.sourceLocation
    }

    def "unused long chains are still reported once per definition"() {
        given:
        def document = Parser.parse('{ node { name } }' + chain('Node', 'name'))

        when:
        def errors = validate(document)

        then:
        errors.size() == Validator.maxValidationErrors
        errors.dropRight(1).every { it.validationErrorType == ValidationErrorType.UnusedFragment }
        errors.last().validationErrorType == ValidationErrorType.MaxValidationErrorsReached
    }

    private List<ValidationError> validate(Document document, QueryComplexityLimits limits = QueryComplexityLimits.DEFAULT) {
        new Validator().validateDocument(schema, document, { true }, Locale.ENGLISH, limits)
    }

    private static String chain(String type, String leaf) {
        (0..<1500).collect { index ->
            "fragment F$index on $type { ...F${index + 1} } "
        }.join('') + "fragment F1500 on $type { $leaf }"
    }
}

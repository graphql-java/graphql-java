package graphql.validation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.parser.Parser
import spock.lang.Specification

class FieldOccurrencesTest extends Specification {
    def schema = TestUtil.schema('''
        type Query { name: String node: Node }
        type Mutation { node: Node }
        type Subscription { node: Node }
        type Node { name: String child: Node }
    ''')

    def "occurrences count fields at every spread with an inclusive boundary for #query"() {
        expect:
        validate(query, count).empty
        validate(query, count - 1)*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        where:
        query                                                                                                      | count
        '{ name name }'                                                                                           | 2
        '{ node { name name } }'                                                                                  | 3
        'mutation { node { name name } }'                                                                         | 3
        'subscription { node { name name } }'                                                                     | 3
        '{ a: node { name } b: node { name } }'                                                                    | 4
        '{ node { ...F ...F } } fragment F on Node { name }'                                                       | 3
        'fragment F on Node { name } query { node { ...F ...F } }'                                                 | 3
        '{ node { ...F ...F } } fragment F on Node { name name }'                                                  | 5
        '{ node { ...F ...G } } fragment F on Node { ...G name } fragment G on Node { name }'                      | 4
        '{ node { ...G ...F } } fragment F on Node { ...G name } fragment G on Node { name }'                      | 4
        '{ node { ...F child { ...F } } } fragment F on Node { name name }'                                       | 6
        '{ node { ... on Node { name } ... { name } } }'                                                          | 3
        '{ node { name @skip(if: true) name @include(if: false) } }'                                              | 3
        'query($skip: Boolean!) { node @skip(if: $skip) { name name } }'                                           | 3
        '{ __type(name: "Query") { ...F ...F } } fragment F on __Type { name name }'                             | 5
    }

    def "default occurrence limit accepts 100000 and rejects 100001"() {
        given:
        def roots = (0..<100).collect { "n$it: node { ...F }" }.join(' ')
        def fragment = 'fragment F on Node { ' + 'name ' * 999 + '}'
        def exact = "{ $roots } $fragment"
        def above = "{ $roots name } $fragment"

        expect:
        validateWithLimits(exact, limits).empty
        validateWithLimits(above, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        where:
        limits << [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.newLimits().build(),
                   QueryComplexityLimits.newLimits().maxDepth(50).build()]
    }

    def "default merged limit accepts 10000 and rejects 10001"() {
        given:
        def roots = (0..<100).collect { "n$it: node { ...F }" }.join(' ')
        def leaves = (0..<99).collect { "v$it: name" }.join(' ')
        def fragment = "fragment F on Node { $leaves }"

        expect:
        validateWithLimits("{ $roots } $fragment", limits).empty
        validateWithLimits("{ $roots name } $fragment", limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryMergedFieldCountExceeded]

        where:
        limits << [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.newLimits().build(),
                   QueryComplexityLimits.newLimits().maxDepth(50).build()]
    }

    def "default depth limit remains enabled when other limits are customized"() {
        given:
        def exact = '{ node { ' + 'child { ' * 98 + 'name' + ' }' * 98 + ' } }'
        def above = '{ node { ' + 'child { ' * 99 + 'name' + ' }' * 99 + ' } }'

        expect:
        validateWithLimits(exact, limits).empty
        validateWithLimits(above, limits)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]

        where:
        limits << [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.newLimits().build(),
                   QueryComplexityLimits.newLimits().maxMergedFieldCount(50_000).build(),
                   QueryComplexityLimits.newLimits().maxFieldOccurrences(200_000).build()]
    }

    def "cached fragment totals and running totals are independent across operations"() {
        given:
        def first = 'query First { node { ...F } }'
        def second = 'query Second { node { ...F ...F } }'
        def query = (reversed ? second + first : first + second) + 'fragment F on Node { name name }'

        expect:
        validate(query, 5).empty
        validate(query, 4)*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        where:
        reversed << [false, true]
    }

    def "cached counts do not skip operation scoped variable validation"() {
        given:
        def query = '''
            query First($include: Boolean!) { node { ...F } }
            query Second { node { ...F } }
            fragment F on Node { name @include(if: $include) }
        '''

        expect:
        validate(query, 2)*.validationErrorType == [ValidationErrorType.UndefinedVariable]
    }

    def "mutually exclusive type branches all contribute occurrences"() {
        given:
        def abstractSchema = TestUtil.schema('''
            type Query { node: I }
            interface I { name: String }
            type A implements I { name: String a: String }
            type B implements I { name: String b: String }
        ''')
        def document = Parser.parse('{ node { name ... on A { name a } ... on B { name b } } }')

        expect:
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH,
                QueryComplexityLimits.newLimits().maxFieldOccurrences(6).build()).empty
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH,
                QueryComplexityLimits.newLimits().maxFieldOccurrences(5).build())*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
    }

    def "fragment occurrence accounting also works when every validation rule is disabled"() {
        given:
        def document = Parser.parse('{ node { ...F ...F } } fragment F on Node { name name }')
        def limits = QueryComplexityLimits.newLimits().maxFieldOccurrences(4).build()

        expect:
        new Validator().validateDocument(schema, document, { rule -> false }, Locale.ENGLISH, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
    }

    def "occurrence limits are independent of validation rule selection"() {
        given:
        def limits = QueryComplexityLimits.newLimits().maxFieldOccurrences(2).build()
        def document = Parser.parse('{ node { name name } }')

        expect:
        new Validator().validateDocument(schema, document, predicate, Locale.ENGLISH, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        where:
        predicate << [
                { rule -> false },
                { rule -> rule != OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED },
                { rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION },
        ]
    }

    def "fragment expansion is bounded without integer overflow"() {
        expect:
        validate(doublingFragments(70), limit)*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        where:
        limit << [100_000, Integer.MAX_VALUE - 1]
    }

    def "the exact benchmark document is rejected by default and can disable only occurrence counting"() {
        given:
        def query = doublingFragments(25)
        def mergedOnly = QueryComplexityLimits.newLimits().maxMergedFieldCount(2).maxDepth(2)
                .maxFieldOccurrences(Integer.MAX_VALUE).build()
        def occurrenceOnly = QueryComplexityLimits.newLimits().maxMergedFieldCount(Integer.MAX_VALUE)
                .maxDepth(Integer.MAX_VALUE).maxFieldOccurrences(100_000).build()

        expect:
        query.length() == 886
        validateWithLimits(query, QueryComplexityLimits.DEFAULT)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        validateWithLimits(query, occurrenceOnly)*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        validateWithLimits(query, mergedOnly).empty
        validateWithLimits(doublingFragments(70), QueryComplexityLimits.NONE).empty
    }

    def "missing and cyclic fragments retain normal validation errors"() {
        expect:
        validate(query, 100)*.validationErrorType.contains(error)

        where:
        query                                                                                                    | error
        '{ node { ...F ...F } } fragment F on Node { name ...Missing }'                                           | ValidationErrorType.UndefinedFragment
        '{ node { ...F ...F } } fragment F on Node { name ...G } fragment G on Node { name ...F }'                  | ValidationErrorType.FragmentCycle
    }

    def "unreferenced fragment definitions do not contribute to operation occurrences"() {
        expect:
        validate('{ node { name } } fragment Unused on Node { name name name }', 2)*.validationErrorType ==
                [ValidationErrorType.UnusedFragment]
    }

    def "explicit builders enable standard limits and NONE disables all limits"() {
        given:
        def empty = QueryComplexityLimits.newLimits().build()
        def depthOnly = QueryComplexityLimits.newLimits().maxDepth(10).build()

        expect:
        empty.maxMergedFieldCount == QueryComplexityLimits.DEFAULT.maxMergedFieldCount
        empty.maxDepth == QueryComplexityLimits.DEFAULT.maxDepth
        empty.maxFieldOccurrences == QueryComplexityLimits.DEFAULT.maxFieldOccurrences
        depthOnly.maxDepth == 10
        depthOnly.maxMergedFieldCount == QueryComplexityLimits.DEFAULT.maxMergedFieldCount
        depthOnly.maxFieldOccurrences == QueryComplexityLimits.DEFAULT.maxFieldOccurrences
        QueryComplexityLimits.NONE.maxDepth == Integer.MAX_VALUE
        QueryComplexityLimits.NONE.maxMergedFieldCount == Integer.MAX_VALUE
        QueryComplexityLimits.NONE.maxFieldOccurrences == Integer.MAX_VALUE
        QueryComplexityLimits.newLimits().maxFieldOccurrences(123).build().toString().contains('maxFieldOccurrences=123')
    }

    def "occurrence limits must be positive"() {
        when:
        QueryComplexityLimits.newLimits().maxFieldOccurrences(limit).build()

        then:
        thrown(IllegalArgumentException)

        where:
        limit << [0, -1]
    }

    def "per request occurrence configuration reaches validation"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def input = ExecutionInput.newExecutionInput('{ node { name name } }')
                .graphQLContext([(QueryComplexityLimits.KEY): QueryComplexityLimits.newLimits().maxFieldOccurrences(limit).build()])
                .build()

        expect:
        graphQL.execute(input).errors.empty == accepted

        where:
        limit | accepted
        3     | true
        2     | false
    }

    def "execution applies the default occurrence limit without request configuration"() {
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(doublingFragments(25))

        then:
        result.errors*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        result.errors[0].message.contains('field occurrences after fragment expansion')
    }

    private String doublingFragments(int levels) {
        '{node{...F0}}' + (0..<levels).collect {
            "fragment F$it on Node{...F${it + 1} ...F${it + 1}}"
        }.join('') + "fragment F$levels on Node{name}"
    }

    private List<ValidationError> validate(String query, int occurrences) {
        validateWithLimits(query, QueryComplexityLimits.newLimits().maxFieldOccurrences(occurrences).build())
    }

    private List<ValidationError> validateWithLimits(String query, QueryComplexityLimits limits) {
        new Validator().validateDocument(schema, Parser.parse(query), { rule -> true }, Locale.ENGLISH, limits)
    }
}

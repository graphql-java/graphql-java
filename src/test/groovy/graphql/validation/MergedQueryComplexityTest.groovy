package graphql.validation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.parser.Parser
import spock.lang.Specification

class MergedQueryComplexityTest extends Specification {
    def schema = TestUtil.schema('''
        type Query { name: String node: Node }
        type Mutation { name: String node: Node }
        type Subscription { name: String node: Node }
        type Node { name: String child: Node }
    ''')

    def "merged count and depth have inclusive boundaries for #query"() {
        expect:
        validate(query, mergedFieldCount, depth).empty
        validate(query, mergedFieldCount - 1, depth)*.validationErrorType == [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        validate(query, mergedFieldCount, depth - 1)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]

        where:
        query                                                                                                      | mergedFieldCount | depth
        '{ node { name name } }'                                                                                   | 2      | 2
        'mutation { node { name name } }'                                                                          | 2      | 2
        'subscription { node { name name } }'                                                                      | 2      | 2
        '{ node { name } node { child { name } } }'                                                                 | 4      | 3
        '{ a: node { name } b: node { name } }'                                                                     | 4      | 2
        '{ node { ...F ...F ...G } } fragment F on Node { name } fragment G on Node { ...F child { name } }'         | 4      | 3
        '{ node { ...F child { ...F } } } fragment F on Node { name name }'                                         | 4      | 3
        '{ node { ... on Node { name } ... { name } } }'                                                            | 2      | 2
        '{ node { name @skip(if: true) child @include(if: false) { name } } }'                                       | 4      | 3
        'query($yes: Boolean!) { node { name child @include(if: $yes) { name } } }'                                  | 4      | 3
        '{ node { ...F } } fragment F on Node { child { ...G } ...G } fragment G on Node { name }'                   | 4      | 3
        '{ node { child { child { name } } name } }'                                                                | 5      | 4
        '{ node { name child { child { name } } } }'                                                                | 5      | 4
    }

    def "leaf-only #operation merges duplicate response names at depth one"() {
        expect:
        validate(operation + ' { name name }', 1, 1).empty
        validate(operation + ' { name name __typename @skip(if: true) }', 2, 1).empty
        validate(operation + ' { name name __typename @skip(if: true) }', 1, 1)*.validationErrorType ==
                [ValidationErrorType.MaxQueryMergedFieldCountExceeded]

        where:
        operation << ['query', 'mutation', 'subscription']
    }

    def "large shared leaf sets are counted at every response position across operations"() {
        given:
        def first = 'query First { first: node { ...Leaves } }'
        def second = 'query Second { a: node { ...Leaves } b: node { ...Leaves } }'
        def query = (firstBeforeSecond ? first + second : second + first) +
                'fragment Leaves on Node { ' + 'name ' * 500 + '}'

        expect:
        validate(query, 4, 2).empty
        validate(query, 3, 2)*.validationErrorType == [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        validate(query, 4, 1)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]

        where:
        firstBeforeSecond << [true, false]
    }

    def "missing introspection sub-selections still enforce restricted coordinates for #selection"() {
        given:
        def document = Parser.parse('{ ' + selection + ' }')

        when:
        new Validator().validateDocument(schema, document, { rule -> true }, Locale.ENGLISH, limits)

        then:
        thrown(GoodFaithIntrospectionExceeded)

        where:
        [selection, limits] << [
                ['a: __schema b: __schema',
                 '__schema { a: types { fields } b: types { fields } }',
                 '__schema { a: types { inputFields } b: types { inputFields } }',
                 '__schema { a: types { interfaces } b: types { interfaces } }',
                 '__schema { a: types { possibleTypes } b: types { possibleTypes } }'],
                [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.NONE]
        ].combinations()
    }

    def "distinct or merged introspection coordinates retain normal missing-selection errors"() {
        expect:
        validate(query, 500, 20)*.validationErrorType == expected

        where:
        query                                      | expected
        '{ __schema __type(name: "Query") }'       | [ValidationErrorType.SubselectionRequired, ValidationErrorType.SubselectionRequired]
        '{ __schema __schema }'                    | [ValidationErrorType.SubselectionRequired, ValidationErrorType.SubselectionRequired]
    }

    def "configuration applies through GraphQL execution"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def input = ExecutionInput.newExecutionInput('{ node { name name name } }')
                .graphQLContext([(QueryComplexityLimits.KEY): QueryComplexityLimits.newLimits().maxMergedFieldCount(limit).build()])
                .build()

        expect:
        graphQL.execute(input).errors.empty == accepted

        where:
        limit | accepted
        2     | true
        1     | false
    }

    def "general limits apply independently of enabled validation rules"() {
        given:
        def document = Parser.parse('{ node { name child { name } } }')
        def limits = QueryComplexityLimits.newLimits().maxMergedFieldCount(3).build()

        expect:
        new Validator().validateDocument(schema, document, predicate, Locale.ENGLISH, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryMergedFieldCountExceeded]

        where:
        predicate << [
                { rule -> false },
                { rule -> rule != OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED },
                { rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION },
        ]
    }

    def "disabling general limits preserves merging errors"() {
        given:
        def document = Parser.parse('{ node { same: name same: child { name } } }')

        expect:
        new Validator().validateDocument(schema, document, { rule -> true }, Locale.ENGLISH, QueryComplexityLimits.NONE)
                .any { it.validationErrorType == ValidationErrorType.FieldsConflict }
    }

    def "conflict paths and ordering stay independent across nested siblings"() {
        given:
        def conflictSchema = TestUtil.schema('''
            type Query { node: Node }
            type Node { name: String nickname: String age: Int child: Node }
        ''')
        def document = Parser.parse('''
            {
                first: node { same: name same: nickname }
                second: node { inner: child { same: name same: age } }
                third: node { same: name same: nickname }
            }
        ''')

        expect:
        new Validator().validateDocument(conflictSchema, document, { rule -> true }, Locale.ENGLISH, limits)*.message == [
                "Validation error (FieldsConflict) : 'second/inner/same' : returns different types 'String' and 'Int'",
                "Validation error (FieldsConflict) : 'first/same' : 'name' and 'nickname' are different fields",
                "Validation error (FieldsConflict) : 'third/same' : 'name' and 'nickname' are different fields",
        ]

        where:
        limits << [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.NONE]
    }

    def "Good Faith remains independent when general limits and other rules are disabled"() {
        given:
        def predicate = { rule -> rule == OperationValidationRule.GOOD_FAITH_INTROSPECTION }
        def normal = Parser.parse('{ node { ' + 'child { ' * 22 + 'name' + ' }' * 22 + ' } }')
        def introspection = Parser.parse('{ __schema { a: types { fields { name } } b: types { fields { name } } } }')

        expect:
        new Validator().validateDocument(schema, normal, predicate, Locale.ENGLISH, QueryComplexityLimits.NONE).empty

        when:
        new Validator().validateDocument(schema, introspection, predicate, Locale.ENGLISH, QueryComplexityLimits.NONE)

        then:
        thrown(GoodFaithIntrospectionExceeded)
    }

    def "shared document caches do not carry counts or depth between operations"() {
        given:
        def first = 'query First { node { ...F } }'
        def second = "query Second { node { ...F $selection } }"
        def query = (firstBeforeSecond ? first + second : second + first) + 'fragment F on Node { name name }'

        expect:
        validate(query, mergedFieldCount, depth)*.validationErrorType == errors

        where:
        firstBeforeSecond | selection           | mergedFieldCount | depth | errors
        true              | ''                  | 2      | 2     | []
        false             | ''                  | 2      | 2     | []
        true              | 'child { ...F }'    | 3      | 3     | [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        false             | 'child { ...F }'    | 3      | 3     | [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        true              | 'child { ...F }'    | 4      | 2     | [ValidationErrorType.MaxQueryDepthExceeded]
        false             | 'child { ...F }'    | 4      | 2     | [ValidationErrorType.MaxQueryDepthExceeded]
    }

    def "abstract selections use the same common-parent sets as merge validation"() {
        given:
        def abstractSchema = TestUtil.schema('''
            type Query { node: I union: U }
            interface I { name: String child: I }
            type A implements I { name: String child: A extra: String }
            type B implements I { name: String child: B extra: String }
            union U = A | B
        ''')
        def document = Parser.parse("{ $selection } $fragments")
        def exact = QueryComplexityLimits.newLimits().maxMergedFieldCount(mergedFieldCount).build()
        def below = QueryComplexityLimits.newLimits().maxMergedFieldCount(mergedFieldCount - 1).build()

        expect:
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH, exact).empty
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH, below)*.validationErrorType ==
                [ValidationErrorType.MaxQueryMergedFieldCountExceeded]

        where:
        selection                                                                    | fragments                 | mergedFieldCount
        'node { name name }'                                                          | ''                        | 2
        'node { ...on I { name } ...on A { name } }'                                   | ''                        | 2
        'node { ...on A { name } ...on B { name } }'                                   | ''                        | 3
        'union { ...on A { name } ...on B { name } }'                                  | ''                        | 3
        'node { ...on A { ...F } ...on B { ...F } }'                                   | 'fragment F on I { name }' | 2
        'node { child { name } ...on A { child { name extra } } }'                     | ''                        | 4
        'node { ...on A { ...on I { child { ...on I { name } ...on B { extra } } } } }' | ''                        | 4
    }

    def "duplicate fragment amplification merges without expanding the response tree"() {
        given:
        def query = '{ node { ...F0 } }' + (0..<40).collect {
            "fragment F$it on Node { ...F${it + 1} ...F${it + 1} }"
        }.join(' ') + 'fragment F40 on Node { name name }'
        def limits = QueryComplexityLimits.newLimits().maxMergedFieldCount(2).maxDepth(2)
                .maxFieldOccurrences(Integer.MAX_VALUE).build()

        expect:
        new Validator().validateDocument(schema, Parser.parse(query), { rule -> true }, Locale.ENGLISH, limits).empty
        validate(query, 1, 2)*.validationErrorType == [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
    }

    def "aliased fragment amplification is bounded without integer overflow"() {
        given:
        def query = '{ node { ...F0 } }' + (0..<levels).collect {
            "fragment F$it on Node { a: child { ...F${it + 1} } b: child { ...F${it + 1} } }"
        }.join(' ') + "fragment F$levels on Node { name }"

        expect:
        validate(query, maxMergedFieldCount, 100)*.validationErrorType == [ValidationErrorType.MaxQueryMergedFieldCountExceeded]

        where:
        levels | maxMergedFieldCount
        18     | 100_000
        40     | Integer.MAX_VALUE - 1
        40     | Integer.MAX_VALUE
    }

    def "invalid names and arguments do not bypass the depth bound"() {
        given:
        def query = '{ node { same: name same: child { ' + 'child { ' * 105 + 'name' + ' }' * 105 + ' } } }'

        expect:
        validate(query, 100_000, 100)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]
    }

    def "invalid selections on scalar fields still count their AST children"() {
        given:
        def query = '{ node { name { ' + 'child { ' * 105 + 'name' + ' }' * 105 + ' } } }'

        expect:
        validate(query, 100_000, 100)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]
    }

    def "cycles remain validation errors even when merge validation is disabled"() {
        given:
        def document = Parser.parse('''
            { node { ...F } }
            fragment F on Node { name child { ...G } }
            fragment G on Node { ...F }
        ''')

        expect:
        new Validator().validateDocument(schema, document,
                { rule -> rule != OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED }, Locale.ENGLISH)
                .any { it.validationErrorType == ValidationErrorType.FragmentCycle }
    }

    def "an in-progress group stops cycles before checking the repeated position depth"() {
        expect:
        validate('{ node { ...F } } fragment F on Node { child { ...F } }', 100, 2)
                .any { it.validationErrorType == ValidationErrorType.FragmentCycle }
    }

    def "depth is checked before expanding child fragments"() {
        given:
        def query = '{ node { child { ...F0 } } }' + (0..<1500).collect {
            "fragment F$it on Node { ...F${it + 1} }"
        }.join(' ') + 'fragment F1500 on Node { name }'

        expect:
        validate(query, 100_000, 1)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]
    }

    private List<ValidationError> validate(String query, int mergedFieldCount, int depth) {
        def limits = QueryComplexityLimits.newLimits().maxMergedFieldCount(mergedFieldCount).maxDepth(depth).build()
        new Validator().validateDocument(schema, Parser.parse(query), { rule -> true }, Locale.ENGLISH, limits)
    }
}

package graphql.validation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.parser.Parser
import spock.lang.Specification

class MergedQueryComplexityTest extends Specification {
    def schema = TestUtil.schema('''
        type Query { name: String node: Node }
        type Node { name: String child: Node }
    ''')

    def "merged count and depth have inclusive boundaries for #query"() {
        expect:
        validate(query, fields, depth).empty
        validate(query, fields - 1, depth)*.validationErrorType == [ValidationErrorType.MaxQueryFieldsExceeded]
        validate(query, fields, depth - 1)*.validationErrorType == [ValidationErrorType.MaxQueryDepthExceeded]

        where:
        query                                                                                                      | fields | depth
        '{ node { name name } }'                                                                                   | 2      | 2
        '{ node { name } node { child { name } } }'                                                                 | 4      | 3
        '{ a: node { name } b: node { name } }'                                                                     | 4      | 2
        '{ node { ...F ...F ...G } } fragment F on Node { name } fragment G on Node { ...F child { name } }'         | 4      | 3
        '{ node { ...F child { ...F } } } fragment F on Node { name name }'                                         | 4      | 3
        '{ node { ... on Node { name } ... { name } } }'                                                            | 2      | 2
        '{ node { name @skip(if: true) child @include(if: false) { name } } }'                                       | 4      | 3
        'query($yes: Boolean!) { node { name child @include(if: $yes) { name } } }'                                  | 4      | 3
        '{ node { ...F } } fragment F on Node { child { ...G } ...G } fragment G on Node { name }'                   | 4      | 3
    }

    def "configuration applies through GraphQL execution"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def input = ExecutionInput.newExecutionInput('{ node { name name name } }')
                .graphQLContext([(QueryComplexityLimits.KEY): QueryComplexityLimits.newLimits().maxFieldsCount(limit).build()])
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
        def limits = QueryComplexityLimits.newLimits().maxFieldsCount(3).build()

        expect:
        new Validator().validateDocument(schema, document, predicate, Locale.ENGLISH, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldsExceeded]

        where:
        predicate << [
                { rule -> false },
                { rule -> rule != OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED },
                { rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION },
        ]
    }

    def "shared document caches do not carry counts or depth between operations"() {
        given:
        def first = 'query First { node { ...F } }'
        def second = "query Second { node { ...F $selection } }"
        def query = (firstBeforeSecond ? first + second : second + first) + 'fragment F on Node { name name }'

        expect:
        validate(query, fields, depth)*.validationErrorType == errors

        where:
        firstBeforeSecond | selection           | fields | depth | errors
        true              | ''                  | 2      | 2     | []
        false             | ''                  | 2      | 2     | []
        true              | 'child { ...F }'    | 3      | 3     | [ValidationErrorType.MaxQueryFieldsExceeded]
        false             | 'child { ...F }'    | 3      | 3     | [ValidationErrorType.MaxQueryFieldsExceeded]
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
        def exact = QueryComplexityLimits.newLimits().maxFieldsCount(fields).build()
        def below = QueryComplexityLimits.newLimits().maxFieldsCount(fields - 1).build()

        expect:
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH, exact).empty
        new Validator().validateDocument(abstractSchema, document, { rule -> true }, Locale.ENGLISH, below)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldsExceeded]

        where:
        selection                                                                    | fragments                 | fields
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

        expect:
        validate(query, 2, 2).empty
        validate(query, 1, 2)*.validationErrorType == [ValidationErrorType.MaxQueryFieldsExceeded]
    }

    def "aliased fragment amplification is bounded without integer overflow"() {
        given:
        def query = '{ node { ...F0 } }' + (0..<levels).collect {
            "fragment F$it on Node { a: child { ...F${it + 1} } b: child { ...F${it + 1} } }"
        }.join(' ') + "fragment F$levels on Node { name }"

        expect:
        validate(query, maxFields, 100)*.validationErrorType == [ValidationErrorType.MaxQueryFieldsExceeded]

        where:
        levels | maxFields
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

    private List<ValidationError> validate(String query, int fields, int depth) {
        def limits = QueryComplexityLimits.newLimits().maxFieldsCount(fields).maxDepth(depth).build()
        new Validator().validateDocument(schema, Parser.parse(query), { rule -> true }, Locale.ENGLISH, limits)
    }
}

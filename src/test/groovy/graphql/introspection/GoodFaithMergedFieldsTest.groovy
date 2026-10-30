package graphql.introspection

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.parser.Parser
import graphql.validation.GoodFaithIntrospectionExceeded
import graphql.validation.OperationValidationRule
import graphql.validation.QueryComplexityLimits
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class GoodFaithMergedFieldsTest extends Specification {
    def schema = TestUtil.schema('''
        type Query { normalField: String node: Node }
        type Node { name: String child: Node }
    ''')

    def "mergeable selections count once at the same response position"() {
        expect:
        validate(query).empty

        where:
        query << [
                '{ __schema { types { fields { name } fields { description } } } }',
                '''{ __schema { types { ...F ...F } } }
                   fragment F on __Type { fields { name } }''',
                '''{ __schema { types { ...F ...G } } }
                   fragment F on __Type { fields { name } }
                   fragment G on __Type { ...F fields { description } }''',
                '''{ __schema { queryType { name } } ...F }
                   fragment F on Query { __schema { types { name } } }''',
                '{ __schema { types { ' + 'name ' * 510 + '} } }',
                '''{ __schema { a: types { ...F } b: types { ...F } } }
                   fragment F on __Type { name name }''',
        ]
    }

    def "cached fragments count again at distinct response positions for #field"() {
        when:
        validate("""{ __schema { a: types { ...F } b: types { ...F } } }
                    fragment F on __Type { ...G }
                    fragment G on __Type { $field { $selection } }""")

        then:
        def error = thrown(GoodFaithIntrospectionExceeded)
        error.toBadFaithError().message.contains("__Type.$field")

        where:
        field           | selection
        'fields'        | 'name'
        'inputFields'   | 'name'
        'interfaces'    | 'name'
        'possibleTypes' | 'name'
    }

    def "root introspection aliases inside fragments are limited on custom roots"() {
        given:
        def customSchema = TestUtil.schema('schema { query: Root } type Root { name: String }')
        def query = """{ ...F ...G }
                     fragment F on Root { a: $field { $selection } }
                     fragment G on Root { b: $field { $selection } }"""

        when:
        new Validator().validateDocument(customSchema, Parser.parse(query), Locale.ENGLISH)

        then:
        def error = thrown(GoodFaithIntrospectionExceeded)
        error.toBadFaithError().message.contains("Root.$coordinate")

        where:
        field                  | coordinate | selection
        '__schema'             | '__schema' | 'queryType { name }'
        '__type(name: "Root")' | '__type'   | 'name'
    }

    def "root #rootField through #placement merges valid selections"() {
        given:
        def query = rootFragments(rootIntrospection(rootField, 'fields { name }'), placement)

        expect:
        validate(query).empty

        where:
        [rootField, placement] << [['__schema', '__type'], ['inline', 'named chain', 'shared fragments']].combinations()
    }

    def "root #rootField through #placement enforces #protection"() {
        given:
        def query = rootFragments(rootIntrospectionViolation(rootField, protection), placement)

        when:
        validate(query)

        then:
        def error = thrown(GoodFaithIntrospectionExceeded)
        error.toBadFaithError().message.contains(expectedDetail)

        where:
        [rootField, placement, protection] << [
                ['__schema', '__type'],
                ['inline', 'named chain', 'shared fragments'],
                ['field count', 'depth', 'restricted field', 'root aliases']
        ].combinations()
        expectedDetail = ['field count': '501 fields', 'depth': 'Query depth 21',
                          'restricted field': '__Type.fields', 'root aliases': "Query.$rootField"][protection]
    }

    def "merged field limit includes each reuse of a cached subtree"() {
        given:
        // __schema, two types fields, and 248 or 249 leaves at each response position.
        def names = (1..leaves).collect { "n$it: name" }.join(' ')
        def query = """{ __schema { a: types { ...F } b: types { ...F } } }
                     fragment F on __Type { $names }"""

        expect:
        rejected(query) == expected

        where:
        leaves | expected
        248    | false
        249    | true
    }

    def "merged field and depth limits include their boundary"() {
        expect:
        rejected(query) == expected

        where:
        query                                                                                                   | expected
        '{ __schema { types { ' + (1..498).collect { "n$it: name" }.join(' ') + ' } } }'                         | false
        '{ __schema { types { ' + (1..499).collect { "n$it: name" }.join(' ') + ' } } }'                         | true
        '{ __type(name: "Query") { ' + 'ofType { ' * 18 + 'name' + ' }' * 18 + ' } }'                          | false
        '{ __type(name: "Query") { ' + 'ofType { ' * 19 + 'name' + ' }' * 19 + ' } }'                          | true
        '''{ __schema { types { ...F ofType { ...F } } } }
           fragment F on __Type { ''' + 'ofType { ' * 17 + 'name' + ' }' * 17 + ' }'                          | true
    }

    def "directives are conservatively included without coercing variables"() {
        expect:
        rejected(query)

        where:
        query << [
                '{ __schema { types { a: fields { name } b: fields @skip(if: true) { name } } } }',
                '{ __schema { types { a: fields { name } b: fields @include(if: false) { name } } } }',
                '''query($include: Boolean!) { __schema { a: types { ...F } b: types @include(if: $include) { ...F } } }
                   fragment F on __Type { fields { name } }''',
                '''{ __schema { a: types { ...F } b: types { ...F @skip(if: true) } } }
                   fragment F on __Type { fields { name } }''',
        ]
    }

    def "good faith runs independently of the overlapping fields rule"() {
        given:
        def predicate = { OperationValidationRule rule -> rule != OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED }

        when:
        new Validator().validateDocument(schema, Parser.parse('{ __schema { a: types { fields { name } } b: types { fields { name } } } }'), predicate, Locale.ENGLISH)

        then:
        thrown(GoodFaithIntrospectionExceeded)

        expect:
        new Validator().validateDocument(schema, Parser.parse('{ __schema { types { fields { name } fields { description } } } }'), predicate, Locale.ENGLISH).empty
    }

    def "good faith can be disabled through its validation rule"() {
        expect:
        new Validator().validateDocument(schema, Parser.parse('{ __schema { a: types { fields { name } } b: types { fields { name } } } }'),
                { rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION }, Locale.ENGLISH).empty
    }

    def "normal operations retain their limits in either document order"() {
        given:
        def normal = 'query Normal { __typename node { ' + 'child { ' * 22 + 'name' + ' }' * 22 + ' } }'
        def introspection = 'query Intro { __schema { queryType { name } } }'

        expect:
        validate(normal).empty
        validate(introspection + normal).empty
        validate(normal + introspection).empty
    }

    def "ordinary operations are not subject to the 500 field limit"() {
        expect:
        validate('{ __typename ' + (1..501).collect { "n$it: normalField" }.join(' ') + ' }').empty
    }

    def "business selections in a mixed operation count toward good faith limits"() {
        expect:
        rejected('{ __schema { queryType { name } } ' + (1..498).collect { "n$it: normalField" }.join(' ') + ' }')
        rejected('{ __type(name: "Query") { name } node { ' + 'child { ' * 20 + 'name' + ' }' * 20 + ' } }')
    }

    def "generic AST work limits remain independent of merged field limits"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def limits = QueryComplexityLimits.newLimits().maxFieldsCount(5).build()
        def input = ExecutionInput.newExecutionInput('{ __schema { types { name name name name } } }')
                .graphQLContext([(QueryComplexityLimits.KEY): limits]).build()

        expect:
        graphQL.execute(input).errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
    }

    def "a tighter generic depth limit still applies to introspection"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def limits = QueryComplexityLimits.newLimits().maxDepth(2).build()
        def input = ExecutionInput.newExecutionInput('{ __type(name: "Query") { ofType { name } } }')
                .graphQLContext([(QueryComplexityLimits.KEY): limits]).build()

        expect:
        graphQL.execute(input).errors[0].message.contains('maximum allowed depth 2')
    }

    def "root interface selections merge with concrete root selections"() {
        given:
        def interfaceRoot = TestUtil.schema('interface Named { name: String } type Query implements Named { name: String }')

        expect:
        new Validator().validateDocument(interfaceRoot,
                Parser.parse('{ __schema { queryType { name } } name ...on Named { name } }'), Locale.ENGLISH).empty
    }

    def "invalid selections and fragment cycles remain validation errors"() {
        expect:
        !validate(query).empty

        where:
        query << [
                '{ __schema { missing { name } } }',
                '{ __schema { types { ...Missing } } }',
                '''{ __schema { types { ...F } } }
                   fragment F on __Type { name ofType { ...F } }''',
                '{ __schema { types { name { unknown } } } }',
                '{ __schema { types { ...on Node { name } } } }',
        ]
    }

    def "mixed abstract selections use validation groups at the limit"() {
        given:
        def abstractSchema = TestUtil.schema('''
            type Query { normalField: String node: I union: U }
            interface I { name: String child: I }
            type A implements I { name: String child: A extra: String }
            type B implements I { name: String child: B extra: String }
            union U = A | B
        ''')
        def base = "{ __type(name: \"Query\") { name } $selection } $fragments"
        def eno = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(abstractSchema, Parser.parse(base),
                null, CoercedVariables.emptyVariables())
        def padding = (1..(500 - validationFields)).collect { "p$it: normalField" }.join(' ')
        def atLimit = "{ __type(name: \"Query\") { name } $padding $selection } $fragments"
        def aboveLimit = "{ __type(name: \"Query\") { name } extra: normalField $padding $selection } $fragments"

        expect:
        eno.operationFieldCount == normalizationFields
        new Validator().validateDocument(abstractSchema, Parser.parse(atLimit), Locale.ENGLISH).empty

        when:
        new Validator().validateDocument(abstractSchema, Parser.parse(aboveLimit), Locale.ENGLISH)

        then:
        thrown(GoodFaithIntrospectionExceeded)

        where:
        selection                                                                        | fragments                      | validationFields | normalizationFields
        'node { name name }'                                                              | ''                             | 4                | 4
        'node { ...on I { name } ...on A { name } }'                                       | ''                             | 4                | 5
        'node { ...on A { name } ...on B { name } }'                                       | ''                             | 5                | 5
        'union { ...on A { name } ...on B { name } }'                                      | ''                             | 5                | 5
        'node { ...on A { ...on I { name } } }'                                            | ''                             | 4                | 4
        'node { ...on A { ...F } ...on B { ...F } }'                                       | 'fragment F on I { name }'      | 4                | 4
        'node { ...on A { ...on I { child { ...on I { name } ...on B { extra } } } } }'     | ''                             | 6                | 5
        'node { child { name } ...on A { child { name extra } } }'                         | ''                             | 6                | 8
        'node { ...F } alias: node { ...F }'                                              | 'fragment F on I { name }'      | 6                | 6
    }

    def "Good Faith counts fragments already checked by another operation"() {
        given:
        def names = (1..leaves).collect { "n$it: name" }.join(' ')
        def normal = 'query Normal { node { ...F } }'
        def introspection = 'query Intro { __type(name: "Query") { name } a: node { ...F } b: node { ...F } }'
        def fragment = "fragment F on Node { $names }"
        def query = (normalFirst ? normal + introspection : introspection + normal) + fragment

        expect:
        rejected(query) == expected

        where:
        leaves | normalFirst | expected
        248    | true        | false
        249    | true        | true
        248    | false       | false
        249    | false       | true
    }

    def "introspection subfields still undergo overlap validation"() {
        expect:
        validate(query).any { it.validationErrorType == ValidationErrorType.FieldsConflict }

        where:
        query << [
                '{ __schema { types { same: name same: kind } } }',
                '{ __schema { types { same: name same: description } } }',
        ]
    }

    def "custom introspection types retain restricted field limits"() {
        given:
        def extendedSchema = new IntrospectionWithDirectivesSupport().apply(schema)

        when:
        new Validator().validateDocument(extendedSchema,
                Parser.parse('{ __schema { types { a: fields { name } b: fields { name } } } }'), Locale.ENGLISH)

        then:
        def error = thrown(GoodFaithIntrospectionExceeded)
        error.toBadFaithError().message.contains('__Type.fields')
    }

    def "public limit helper preserves tighter caller limits"() {
        given:
        def requested = QueryComplexityLimits.newLimits().maxFieldsCount(fields).maxDepth(depth).build()

        when:
        def bounded = GoodFaithIntrospection.goodFaithLimits(requested)

        then:
        bounded.maxFieldsCount == expectedFields
        bounded.maxDepth == expectedDepth
        requested.maxFieldsCount == fields
        requested.maxDepth == depth

        where:
        fields | depth | expectedFields | expectedDepth
        1000   | 100   | 500            | 20
        50     | 10    | 50             | 10
        1000   | 10    | 500            | 10
        50     | 100   | 50             | 20
    }

    private String rootIntrospection(String rootField, String selection, String alias = '') {
        if (rootField == '__schema') {
            return "${alias}__schema { types { $selection } }"
        }
        return "${alias}__type(name: \"Query\") { $selection }"
    }

    private String rootFragments(String selection, String placement) {
        switch (placement) {
            case 'inline':
                return "{ ...on Query { ... { $selection } } }"
            case 'named chain':
                return """{ ...Outer }
                          fragment Outer on Query { ...Inner }
                          fragment Inner on Query { $selection }"""
            case 'shared fragments':
                return """{ ...Left ...Right }
                          fragment Left on Query { ...Shared }
                          fragment Right on Query { ...Shared }
                          fragment Shared on Query { $selection }"""
            default:
                throw new IllegalArgumentException(placement)
        }
    }

    private String rootIntrospectionViolation(String rootField, String protection) {
        int rootDepth = rootField == '__schema' ? 2 : 1
        switch (protection) {
            case 'field count':
                return rootIntrospection(rootField, (1..(501 - rootDepth)).collect { "n$it: name" }.join(' '))
            case 'depth':
                int wrappers = 20 - rootDepth
                return rootIntrospection(rootField, 'ofType { ' * wrappers + 'name' + ' }' * wrappers)
            case 'restricted field':
                return rootIntrospection(rootField, 'a: fields { name } b: fields { name }')
            case 'root aliases':
                return rootIntrospection(rootField, 'name', 'a: ') + rootIntrospection(rootField, 'name', 'b: ')
            default:
                throw new IllegalArgumentException(protection)
        }
    }

    private List validate(String query) {
        new Validator().validateDocument(schema, Parser.parse(query), Locale.ENGLISH)
    }

    private boolean rejected(String query) {
        try {
            validate(query)
            return false
        } catch (GoodFaithIntrospectionExceeded ignored) {
            return true
        }
    }
}

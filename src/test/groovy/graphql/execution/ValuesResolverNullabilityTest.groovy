package graphql.execution

import graphql.ErrorType
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification

class ValuesResolverNullabilityTest extends Specification {

    def "nullable variable with a non-null default at #argumentType with variables #variables"() {
        given:
        def received = []
        DataFetcher fetcher = { env ->
            received.add(env.getArgument('arg'))
            'resolved'
        }
        def graphQL = buildGraphQL(argumentType, fetcher)
        def query = 'query($v: String = "x") { f(arg: ' + literal + ') good }'

        when:
        def result = executeValid(graphQL, query, variables)

        then:
        result.data == [f: fails ? null : 'resolved', good: 'good']
        result.errors.size() == (fails ? 1 : 0)
        result.errors*.path == (fails ? [['f']] : [])
        result.errors.every { it instanceof ExceptionWhileDataFetching && it.exception instanceof NonNullableValueCoercedAsNullException }
        received == (fails ? [] : [expectedValue])

        where:
        argumentType   | literal      | variables | expectedValue     | fails
        '[String!]!'   | '[$v]'       | [:]       | ['x']             | false
        '[String!]!'   | '[$v]'       | [v: 'z']  | ['z']             | false
        '[String!]!'   | '[$v]'       | [v: null] | null              | true
        'RequiredInput'| '{a: $v}'    | [:]       | [a: 'x']          | false
        'RequiredInput'| '{a: $v}'    | [v: 'z']  | [a: 'z']          | false
        'RequiredInput'| '{a: $v}'    | [v: null] | null              | true
        'ListInput'    | '{items:[$v]}'| [:]       | [items: ['x']]    | false
        'ListInput'    | '{items:[$v]}'| [v: 'z']  | [items: ['z']]    | false
        'ListInput'    | '{items:[$v]}'| [v: null] | null              | true
        '[[String!]!]!'| '[[$v]]'     | [:]       | [['x']]           | false
        '[[String!]!]!'| '[[$v]]'     | [v: 'z']  | [['z']]           | false
        '[[String!]!]!'| '[[$v]]'     | [v: null] | null              | true
    }

    def "input field default distinguishes omission from null for #variableDefinition with #variables"() {
        given:
        def received = []
        DataFetcher fetcher = { env ->
            received.add(env.getArgument('arg'))
            'resolved'
        }
        def graphQL = buildGraphQL('DefaultInput', fetcher)
        def query = 'query($v: ' + variableDefinition + ') { f(arg: {a: $v}) good }'

        when:
        def result = executeValid(graphQL, query, variables)

        then:
        result.data == [f: fails ? null : 'resolved', good: 'good']
        result.errors.size() == (fails ? 1 : 0)
        result.errors*.path == (fails ? [['f']] : [])
        result.errors.every { it instanceof ExceptionWhileDataFetching && it.exception instanceof NonNullableValueCoercedAsNullException }
        received == (fails ? [] : [[a: expectedValue]])

        where:
        variableDefinition | variables | expectedValue | fails
        'String'           | [:]       | 'd'           | false
        'String'           | [v: 'z']  | 'z'           | false
        'String'           | [v: null] | null          | true
        'String = "x"'     | [:]       | 'x'           | false
        'String = "x"'     | [v: null] | null          | true
        'String = null'    | [:]       | null          | true
    }

    def "nullable positions preserve missing and null variables in #literal with #variables"() {
        given:
        def received = []
        DataFetcher fetcher = { env ->
            received.add(env.getArgument('arg'))
            'resolved'
        }
        def graphQL = buildGraphQL(argumentType, fetcher)
        def query = 'query($v: String) { f(arg: ' + literal + ') }'

        when:
        def result = executeValid(graphQL, query, variables)

        then:
        result.errors.empty
        result.data == [f: 'resolved']
        received == [expectedValue]

        where:
        argumentType   | literal   | variables | expectedValue
        '[String]'     | '[$v]'    | [:]       | [null]
        '[String]'     | '[$v]'    | [v: null] | [null]
        'NullableInput'| '{a: $v}' | [:]       | [:]
        'NullableInput'| '{a: $v}' | [v: null] | [a: null]
    }

    def "a nullable variable without a non-null default cannot be used as a non-null list item"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = buildGraphQL('[String!]!', fetcher)
        def input = ExecutionInput.newExecutionInput('query($v: ' + variableDefinition + ') { f(arg: [$v]) }').build()

        when:
        def result = graphQL.execute(input)

        then:
        !result.dataPresent
        result.errors.size() == 1
        result.errors[0].errorType == ErrorType.ValidationError
        calls == 0

        where:
        variableDefinition << ['String', 'String = null']
    }

    def "#operation coercion error uses the response alias and propagates through #resultType"() {
        given:
        DataFetcher fetcher = { env ->
            env.getArgument('arg')
            'unexpected'
        }
        def graphQL = buildGraphQL('[String!]!', fetcher, resultType)
        def query = operation + '($v: String = "x") { alias: f(arg: [$v]) }'

        when:
        def result = executeValid(graphQL, query, [v: null])

        then:
        result.dataPresent
        result.data == (resultType == 'String!' ? null : [alias: null])
        result.errors.size() == 1
        result.errors[0].path == ['alias']
        !result.errors[0].locations.empty
        result.errors[0] instanceof ExceptionWhileDataFetching
        result.errors[0].exception instanceof NonNullableValueCoercedAsNullException

        where:
        operation  | resultType
        'query'    | 'String'
        'query'    | 'String!'
        'mutation' | 'String'
        'mutation' | 'String!'
    }

    private GraphQL buildGraphQL(String argumentType, DataFetcher fetcher, String resultType = 'String') {
        def sdl = """
            input RequiredInput { a: String! }
            input DefaultInput { a: String! = "d" }
            input NullableInput { a: String }
            input ListInput { items: [String!] }
            type Query { f(arg: $argumentType): $resultType good: String! }
            type Mutation { f(arg: $argumentType): $resultType }
        """
        TestUtil.graphQL(sdl, [Query: [f: fetcher, good: { 'good' } as DataFetcher], Mutation: [f: fetcher]]).build()
    }

    private ExecutionResult executeValid(GraphQL graphQL, String query, Map<String, Object> variables) {
        def input = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert !ParseAndValidate.parseAndValidate(graphQL.graphQLSchema, input).isFailure()
        graphQL.execute(input)
    }
}

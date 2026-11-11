package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.values.InputInterceptor
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.schema.CoercingParseValueException
import graphql.schema.DataFetcher
import graphql.schema.scalars.ObjectScalar
import spock.lang.Specification

class InputCoercionLimitsTest extends Specification {

    private static final String QUERY = 'query($v: Recursive) { echo(arg: $v) }'

    private static Map<String, Object> nestedObjects(int depth) {
        Map<String, Object> value = [:]
        depth.times { value = [next: value] }
        value
    }

    private static GraphQL graphQL(String nextType = 'Recursive') {
        DataFetcher echo = { env -> true }
        def schema = TestUtil.schema("""
            input Recursive { next: $nextType }
            type Query { echo(arg: Recursive): Boolean }
        """, [Query: [echo: echo]])
        GraphQL.newGraphQL(schema).build()
    }

    private static ExecutionInput input(Object value, InputCoercionLimits limits) {
        ExecutionInput.newExecutionInput(QUERY)
                .variables([v: value])
                .graphQLContext { it.put(InputCoercionLimits.KEY, limits) }
                .build()
    }

    def 'builder retains the default and rejects a nonpositive depth'() {
        expect:
        InputCoercionLimits.newInputCoercionLimits().build().maxDepth == InputCoercionLimits.getDefaultLimits().maxDepth
        InputCoercionLimits.DEFAULT.transform { it.maxDepth(4) }.maxDepth == 4

        when:
        InputCoercionLimits.newInputCoercionLimits().maxDepth(0)

        then:
        thrown(IllegalArgumentException)
    }

    def 'the input object depth limit accepts its boundary and rejects the next level'() {
        given:
        def graphQL = graphQL()
        def limits = InputCoercionLimits.newInputCoercionLimits().maxDepth(3).build()

        when:
        def accepted = graphQL.execute(input(nestedObjects(2), limits))
        def rejected = graphQL.execute(input(nestedObjects(3), limits))

        then:
        accepted.errors.empty
        accepted.data == [echo: true]
        rejected.errors.size() == 1
        rejected.errors[0].message == "Variable 'v' has an invalid value: Input value exceeds the maximum depth of 3."
        rejected.errors[0].locations[0].column == 7
        !rejected.toSpecification().containsKey('data')
    }

    def 'the default limit rejects deeply nested variables before they overflow the stack'() {
        when:
        def result = graphQL().execute(ExecutionInput.newExecutionInput(QUERY)
                .variables([v: nestedObjects(5000)])
                .build())

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('maximum depth of 128')
        !result.toSpecification().containsKey('data')
    }

    def 'list nesting and singleton list coercion count toward depth'() {
        given:
        def graphQL = graphQL('[Recursive]')
        def limits = InputCoercionLimits.newInputCoercionLimits().maxDepth(2).build()

        when:
        def accepted = graphQL.execute(input([next: []], limits))
        def rejected = graphQL.execute(input([next: [:]], limits))

        then:
        accepted.errors.empty
        rejected.errors.size() == 1
        rejected.errors[0].message.contains('maximum depth of 2')
    }

    def 'request override and NONE can raise or disable the default depth'() {
        given:
        def graphQL = graphQL()
        def value = nestedObjects(InputCoercionLimits.DEFAULT_MAX_DEPTH)
        def raisedLimit = InputCoercionLimits.newInputCoercionLimits().maxDepth(130).build()

        expect:
        graphQL.execute(input(value, raisedLimit)).errors.empty
        graphQL.execute(input(value, InputCoercionLimits.NONE)).errors.empty
    }

    def 'async execution returns a request error instead of failing the future'() {
        given:
        def limits = InputCoercionLimits.newInputCoercionLimits().maxDepth(2).build()

        when:
        def result = graphQL().executeAsync(input(nestedObjects(3), limits)).join()

        then:
        result.errors.size() == 1
        !result.toSpecification().containsKey('data')
    }

    def 'an input interceptor cannot bypass the depth limit'() {
        given:
        def limits = InputCoercionLimits.newInputCoercionLimits().maxDepth(2).build()
        InputInterceptor interceptor = { value, type, context, locale ->
            value == [next: [:]] ? nestedObjects(3) : value
        }
        def executionInput = ExecutionInput.newExecutionInput(QUERY)
                .variables([v: [next: [:]]])
                .graphQLContext { it.put(InputCoercionLimits.KEY, limits); it.put(InputInterceptor.class, interceptor) }
                .build()

        when:
        def result = graphQL().execute(executionInput)

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('maximum depth of 2')
    }

    def 'normalized variable traversal has the same depth bound'() {
        given:
        def schema = TestUtil.schema('input Recursive { next: Recursive } type Query { echo(arg: Recursive): Boolean }')
        def definitions = [new VariableDefinition('v', new TypeName('Recursive'))]
        def context = GraphQLContext.newContext().of(InputCoercionLimits.KEY,
                InputCoercionLimits.newInputCoercionLimits().maxDepth(2).build()).build()

        when:
        ValuesResolver.getNormalizedVariableValues(schema, definitions,
                RawVariables.of([v: nestedObjects(3)]), context, Locale.ROOT)

        then:
        def exception = thrown(CoercingParseValueException)
        exception.message.contains('maximum depth of 2')
    }

    def 'cyclic Java maps are rejected at the depth limit'() {
        given:
        Map<String, Object> cycle = [:]
        cycle.next = cycle
        def limits = InputCoercionLimits.newInputCoercionLimits().maxDepth(3).build()

        when:
        def result = graphQL().execute(input(cycle, limits))

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('maximum depth of 3')
    }

    def 'an unrelated raw variable is not traversed'() {
        given:
        def executionInput = ExecutionInput.newExecutionInput(QUERY)
                .variables([v: [:], unrelated: nestedObjects(5000)])
                .build()

        expect:
        graphQL().execute(executionInput).errors.empty
    }

    def 'custom scalar input remains opaque to GraphQL Java'() {
        given:
        def schema = TestUtil.schemaWithInputType(ObjectScalar.OBJECT_SCALAR)
        def definitions = [new VariableDefinition('v', new TypeName(ObjectScalar.OBJECT_SCALAR.name))]
        def value = nestedObjects(5000)
        def context = GraphQLContext.newContext().of(InputCoercionLimits.KEY,
                InputCoercionLimits.newInputCoercionLimits().maxDepth(1).build()).build()

        when:
        def coerced = ValuesResolver.coerceVariableValues(schema, definitions,
                RawVariables.of([v: value]), context, Locale.ROOT)

        then:
        coerced.get('v').is(value)
    }
}

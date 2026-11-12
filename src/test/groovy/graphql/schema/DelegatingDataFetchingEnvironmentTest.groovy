package graphql.schema

import graphql.GraphQLContext
import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

class DelegatingDataFetchingEnvironmentTest extends Specification {

    def "explicit null arguments are distinct from missing arguments - #query"() {
        given:
        DataFetcher fetcher = { delegate ->
            def environment = new DelegatingDataFetchingEnvironment(delegate)
            assert environment.getArgumentOrDefault('missing', null) == null
            assert environment.getVariables() == variables
            assert environment.containsArgument('arg') == supplied
            assert environment.getArguments() == (supplied ? [arg: null] : [:])
            environment.getArgumentOrDefault('arg', 'fallback')
        }
        def wiring = RuntimeWiring.newRuntimeWiring().type('Query', { builder -> builder.dataFetcher('echo', fetcher) })
        def graphQL = TestUtil.graphQL('type Query { echo(arg: String): String }', wiring).build()

        when:
        def result = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())

        then:
        result.errors.empty
        result.getData() == [echo: expected]

        where:
        query                                        | variables     | supplied | expected
        '{ echo(arg: null) }'                         | [:]           | true     | null
        'query($value: String) { echo(arg: $value) }'   | [value: null] | true     | null
        '{ echo }'                                   | [:]           | false    | 'fallback'
    }

    def "calls to underlying delegate"() {

        def root = "Root"
        def source = "Source"
        def args = [arg1: "val1"]
        def variables = [var1: "varVal1"]

        def dfe = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .source(source)
                .arguments(args)
                .root(root)
                .graphQLContext(GraphQLContext.of(["key": "context"]))
                .variables(variables)
                .build()

        when:
        def delegatingDFE = new DelegatingDataFetchingEnvironment(dfe) {
            @Override
            GraphQLContext getGraphQlContext() {
                return GraphQLContext.of(["key": "overriddenContext"])
            }

            @Override
            def getContext() { // Retain for test coverage
                return "overriddenContext"
            }
        }
        then:
        delegatingDFE.getSource() == source
        delegatingDFE.getRoot() == root
        delegatingDFE.getContext() == "overriddenContext"
        delegatingDFE.getGraphQlContext().get("key") == "overriddenContext"
        delegatingDFE.getVariables() == variables
        delegatingDFE.getArguments() == args
        delegatingDFE.getArgumentOrDefault("arg1", "x") == "val1"
        delegatingDFE.getArgumentOrDefault("arg2", "x") == "x"
    }
}

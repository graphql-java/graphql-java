package graphql.nextgen


import graphql.schema.DataFetcher
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.TestUtil.schema

class GraphqlNextGenTest extends Specification {

    def "simple query"() {
        given:
        def dataFetchers = [
                Query: [hello: { env -> "world" } as DataFetcher]
        ]

        def schema = schema('''
            type Query {
                hello : String!
            }
        ''', dataFetchers)

        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def result = graphQL.executeAsync(newExecutionInput('{ hello }')).get()

        then:
        result.data == [hello: 'world']
    }
}

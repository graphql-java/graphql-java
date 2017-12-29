package graphql

import graphql.language.SourceLocation
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class NullVariableCoercionTest extends Specification {

    def "null coercion errors have source locations"() {

        when:

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
          .type(newTypeWiring("Query")
                  .dataFetcher("bar",
                               { env ->
                                 Map<String, Object> map = new HashMap<>()
                                 map.put("id", "def")
                                 return map
                               })
        )
          .type(newTypeWiring("Node")
                  .typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") }))
          .build()


        def schema = TestUtil.schema("""
                schema {
                  query: Query
                }

                type Query {
                  bar(input: BarInput!): Node
                }

                input BarInput {
                  baz: String!
                }

                interface Node {
                  id: String
                }

                type Foo implements Node {
                  id: String
                }
            """, runtimeWiring)


        GraphQL graphQL = GraphQL.newGraphQL(schema)
            .build()

        def variables = ["input": ["baz": null]]

        ExecutionInput varInput = ExecutionInput.newExecutionInput()
            .query('query Bar($input: BarInput!) {bar(input: $input) {id}}')
            .variables(variables)
            .build()

        ExecutionResult varResult = graphQL
            .executeAsync(varInput)
            .join()

        then:

        varResult.data == null
        varResult.errors.size() == 1
        varResult.errors[0].errorType == ErrorType.ValidationError
        varResult.errors[0].message == "Variable 'input' has coerced Null value for NonNull type 'String!'"
        varResult.errors[0].locations == [new SourceLocation(1, 11)]
    }
}


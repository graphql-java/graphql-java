package graphql

import graphql.language.SourceLocation
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class Issue739 extends Specification {

    def "#739 test"() {

        when:

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                .dataFetcher("foo",
                { env ->
                    Map<String, Object> map = new HashMap<>()
                    map.put("id", "abc")
                    return map

                })
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
              foo: Node
              bar(input: BarInput!): Node
            }
            
            input BarInput {
              baz: String!
              boom: Int
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

        ExecutionInput noVarInput = ExecutionInput.newExecutionInput()
                .query('{ bar(input: 123) { id } } ')
                .build()

        ExecutionResult noVarResult = graphQL
                .executeAsync(noVarInput)
                .join()

        then:

        1 == noVarResult.getErrors().size()

        when:
        def variables = ["input": 123]

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
        varResult.errors[0].message == "Variable 'input' has an invalid value. Expected type 'Map' but was 'Integer'." +
                " Variables for input objects must be an instance of type 'Map'.";
        varResult.errors[0].locations == [new SourceLocation(1, 11)]

        when:
        variables = ["input": ["baz": "hi", "boom": "hi"]]

        varInput = ExecutionInput.newExecutionInput()
                .query('query Bar($input: BarInput!) {bar(input: $input) {id}}')
                .variables(variables)
                .build()

        varResult = graphQL
                .executeAsync(varInput)
                .join()

        then:
        varResult.data == null
        varResult.errors.size() == 1
        varResult.errors[0].errorType == ErrorType.ValidationError
        varResult.errors[0].message == "Variable 'boom' has an invalid value. Expected type 'Int' but was 'String'."
        varResult.errors[0].locations == [new SourceLocation(1, 11)]
    }
}

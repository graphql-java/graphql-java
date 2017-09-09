package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

class GraphQLSchemaTest extends Specification {

    def "#698 interfaces copied as expected"() {

        def idl = """
            type Query {
              foo: Node
            }
            
            interface Node {
              id: String
            }
            
            type Foo implements Node {
              id: String
            }
        """

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", { wiring ->
            wiring.dataFetcher("foo", { env ->
                Map<String, Object> map = new HashMap<>();
                map.put("id", "abc")
                return map;
            })
        })
                .type("Node", { wiring ->
            wiring.typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") })
        })
                .build()

        def existingSchema = TestUtil.schema(idl, runtimeWiring)


        GraphQLSchema schema = GraphQLSchema.newSchema(existingSchema).build()

        expect:
        assert 0 == runQuery(existingSchema).getErrors().size()
        assert 0 == runQuery(schema).getErrors().size()
    }


    def runQuery(GraphQLSchema schema) {
        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .build()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("{foo {id}}")
                .build()

        return graphQL
                .executeAsync(executionInput)
                .join()
    }
}

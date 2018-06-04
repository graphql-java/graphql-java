package graphql.schema

import graphql.AssertException
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.starWarsSchema

class GraphQLSchemaTest extends Specification {

    def "getImplementations works as expected"() {
        when:
        List<GraphQLObjectType> objectTypes = starWarsSchema.getImplementations(characterInterface)

        then:
        objectTypes.size() == 2
        objectTypes == [
                humanType, droidType
        ]

    }

    def "isPossibleType works as expected"() {
        expect:
        starWarsSchema.isPossibleType(characterInterface, humanType)
    }

    def "isPossibleType when wrong abstract type is passed expect exception"() {
        when:
        starWarsSchema.isPossibleType(humanType, humanType)
        then:
        thrown(AssertException)
    }

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
                Map<String, Object> map = new HashMap<>()
                map.put("id", "abc")
                return map
            })
        })
                .type("Node", { wiring ->
            wiring.typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") })
        })
                .build()

        def existingSchema = TestUtil.schema(idl, runtimeWiring)


        GraphQLSchema schema = existingSchema.transform({})

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

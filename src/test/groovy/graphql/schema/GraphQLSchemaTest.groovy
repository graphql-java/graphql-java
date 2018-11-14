package graphql.schema

import graphql.AssertException
import graphql.Directives
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class GraphQLSchemaTest extends Specification {

    def "getImplementations works as expected"() {
        when:
        List<GraphQLObjectType> objectTypes = starWarsSchema.getImplementations(characterInterface)

        then:
        objectTypes.size() == 2
        objectTypes == [
                droidType, humanType
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
        } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .type("Node", { wiring ->
            wiring.typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") })
        } as UnaryOperator<TypeRuntimeWiring.Builder>)
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

    def basicSchemaBuilder() {
        GraphQLSchema.newSchema()
                .query(newObject()
                .name("QueryType")
                .field(newFieldDefinition()
                .name("hero")
                .type(GraphQLString)
                .dataFetcher({ env -> null })))
    }

    def additionalType1 = newObject()
            .name("Additional1")
            .field(newFieldDefinition()
            .name("field")
            .type(GraphQLString)
            .dataFetcher({ env -> null }))
            .build()

    def additionalType2 = newObject()
            .name("Additional2")
            .field(newFieldDefinition()
            .name("field")
            .type(GraphQLString)
            .dataFetcher({ env -> null }))
            .build()

    def "clear directives works as expected"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()

        when: "no additional directives have been specified"
        def schema = schemaBuilder.build()
        then:
        schema.directives.size() == 3

        when: "clear directives is called"
        schema = schemaBuilder.clearDirectives().build()
        then:
        schema.directives.empty

        when: "clear directives is called with more directives"
        schema = schemaBuilder.clearDirectives().additionalDirective(Directives.SkipDirective).build()
        then:
        schema.directives.size() == 1

        when: "the schema is transformed, things are copied"
        schema = schema.transform({ bldr -> bldr.additionalDirective(Directives.IncludeDirective) })
        then:
        schema.directives.size() == 2
    }

    def "clear additional types  works as expected"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()

        when: "no additional types have been specified"
        def schema = schemaBuilder.build()
        then:
        schema.additionalTypes.size() == 0

        when: "clear types is called"
        schema = schemaBuilder.clearAdditionalTypes().build()
        then:
        schema.additionalTypes.empty

        when: "clear types is called with additional types"
        schema = schemaBuilder.clearAdditionalTypes().additionalType(additionalType1).build()
        then:
        schema.additionalTypes.size() == 1

        when: "the schema is transformed, things are copied"
        schema = schema.transform({ bldr -> bldr.additionalType(additionalType2) })
        then:
        schema.additionalTypes.size() == 2
    }
}

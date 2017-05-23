package graphql

import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.idl.errors.SchemaProblem

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument

class TestUtil {


    static GraphQLSchema schemaWithInputType(GraphQLInputType inputType) {
        GraphQLArgument.Builder fieldArgument = newArgument().name("arg").type(inputType)
        GraphQLFieldDefinition.Builder name = GraphQLFieldDefinition.newFieldDefinition()
                .name("name").type(GraphQLString).argument(fieldArgument)
        GraphQLObjectType queryType = GraphQLObjectType.newObject().name("query").field(name).build()
        new GraphQLSchema(queryType)
    }

    static dummySchema = GraphQLSchema.newSchema()
            .query(GraphQLObjectType.newObject()
            .name("QueryType")
            .build())
            .build()

    static GraphQLSchema schema(String spec, Map<String, Map<String, DataFetcher>> dataFetchers) {
        def wiring = RuntimeWiring.newRuntimeWiring()
        dataFetchers.each { type, fieldFetchers ->
            def tw = TypeRuntimeWiring.newTypeWiring(type).dataFetchers(fieldFetchers)
            wiring.type(tw)
        }
        schema(spec, wiring)
    }

    static GraphQLSchema schema(String spec, RuntimeWiring.Builder runtimeWiring) {
        schema(spec, runtimeWiring.build())
    }

    static GraphQLSchema schema(String spec, RuntimeWiring runtimeWiring) {
        try {
            def registry = new SchemaParser().parse(spec)
            return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
        } catch (SchemaProblem e) {
            assert false: "The schema could not be compiled : ${e}"
            e.printStackTrace()
            null
        }
    }
}

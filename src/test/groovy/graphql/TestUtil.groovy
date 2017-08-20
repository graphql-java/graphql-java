package graphql

import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.idl.UnionWiringEnvironment
import graphql.schema.idl.WiringFactory
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

    static GraphQLSchema schema(String spec) {
        schema(spec, RuntimeWiring.newRuntimeWiring().wiringFactory(mockWiringFactory).build())
    }

    static GraphQLSchema schemaFile(String fileName, RuntimeWiring wiring) {
        def stream = TestUtil.class.getClassLoader().getResourceAsStream(fileName)

        def typeRegistry = new SchemaParser().parse(new InputStreamReader(stream))
        def schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        schema
    }


    @SuppressWarnings("GroovyMissingReturnStatement")
    static GraphQLSchema schema(String spec, RuntimeWiring runtimeWiring) {
        try {
            def registry = new SchemaParser().parse(spec)
            return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
        } catch (SchemaProblem e) {
            assert false: "The schema could not be compiled : ${e}"
        }
    }

    static WiringFactory mockWiringFactory = new WiringFactory() {

        @Override
        boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
            return true
        }

        @Override
        TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
            new TypeResolver() {
                @Override
                GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            }
        }

        @Override
        boolean providesTypeResolver(UnionWiringEnvironment environment) {
            return true
        }

        @Override
        TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
            new TypeResolver() {
                @Override
                GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            }
        }

        @Override
        boolean providesDataFetcher(FieldWiringEnvironment environment) {
            return true
        }

        @Override
        DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
            return new PropertyDataFetcher(environment.getFieldDefinition().getName())
        }
    }
}

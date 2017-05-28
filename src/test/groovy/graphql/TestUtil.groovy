package graphql

import graphql.language.FieldDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
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
        boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType) {
            return true
        }

        @Override
        boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType) {
            return true
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType) {
            new TypeResolver() {
                @Override
                GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            }
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType) {
            new TypeResolver() {
                @Override
                GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            }
        }

        @Override
        boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return true
        }

        @Override
        DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return new PropertyDataFetcher(definition.getName())
        }
    }

}

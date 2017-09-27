package graphql.schema.idl;

import graphql.Internal;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;

import java.util.Map;

@Internal
public class UnExecutableSchemaGenerator {

    /*
     * Creates just enough runtime wiring to allow a schema to be built but which CANT
     * be sensibly executed
     */
    public static GraphQLSchema makeUnExecutableSchema(TypeDefinitionRegistry registry) {
        RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();
        Map<String, ScalarTypeDefinition> scalars = registry.scalars();
        scalars.forEach((name, v) -> {
            if (!ScalarInfo.isStandardScalar(name)) {
                wiring.scalar(fakeScalar(name));
            }
        });
        RuntimeWiring fakeWiring = wiring.wiringFactory(fakeWiringFactory()).build();

        return new SchemaGenerator().makeExecutableSchema(registry, fakeWiring);
    }

    private static WiringFactory fakeWiringFactory() {
        return new WiringFactory() {
            @Override
            public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
                return true;
            }

            @Override
            public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
                return env -> env.getSchema().getQueryType();
            }

            @Override
            public boolean providesTypeResolver(UnionWiringEnvironment environment) {
                return true;
            }

            @Override
            public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
                return env -> env.getSchema().getQueryType();
            }

            @Override
            public DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
                return env -> new PropertyDataFetcher<>(env.getFieldDefinition().getName());
            }
        };
    }

    private static GraphQLScalarType fakeScalar(String name) {
        return new GraphQLScalarType(name, name, new Coercing() {
            @Override
            public Object serialize(Object dataFetcherResult) {
                return dataFetcherResult;
            }

            @Override
            public Object parseValue(Object input) {
                return input;
            }

            @Override
            public Object parseLiteral(Object input) {
                return input;
            }
        });
    }
}

package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;

@PublicApi
public class MockedWiringFactory implements WiringFactory {

    @Override
    public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return true;
    }

    @Override
    public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
        return env -> {
            throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
        };
    }

    @Override
    public boolean providesTypeResolver(UnionWiringEnvironment environment) {
        return true;
    }

    @Override
    public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
        return env -> {
            throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
        };
    }

    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment environment) {
        return true;
    }

    @Override
    public DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
        return new PropertyDataFetcher(environment.getFieldDefinition().getName());
    }

    @Override
    public boolean providesScalar(ScalarWiringEnvironment environment) {
        if (ScalarInfo.isGraphqlSpecifiedScalar(environment.getScalarTypeDefinition().getName())) {
            return false;
        }
        return true;
    }

    public GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
        return GraphQLScalarType.newScalar().name(environment.getScalarTypeDefinition().getName()).coercing(new Coercing() {
            @Override
            public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
            }

            @Override
            public Object parseValue(Object input) throws CoercingParseValueException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
            }

            @Override
            public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
            }
        }).build();
    }
}

package graphql.schema.idl

import graphql.TypeResolutionEnvironment
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver

/**
 * There is a {@link MockedWiringFactory} in the main code base now but this one is retained
 * for testing purposes.
 */
class TestMockedWiringFactory implements WiringFactory {

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

    @Override
    boolean providesScalar(ScalarWiringEnvironment environment) {
        if (ScalarInfo.isGraphqlSpecifiedScalar(environment.getScalarTypeDefinition().getName())) {
            return false
        }
        return true
    }

    GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
        return GraphQLScalarType.newScalar().name(environment.getScalarTypeDefinition().getName()).coercing(new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                throw new UnsupportedOperationException("Not implemented");
            }
        }).build()
    }
}

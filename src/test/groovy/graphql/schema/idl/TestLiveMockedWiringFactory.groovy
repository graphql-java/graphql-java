package graphql.schema.idl

import graphql.TypeResolutionEnvironment
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.DataFetcher
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver

class TestLiveMockedWiringFactory implements WiringFactory {

    private final Map<String, GraphQLScalarType> scalars

    TestLiveMockedWiringFactory() {
        this.scalars = new HashMap<>()
    }

    TestLiveMockedWiringFactory(List<GraphQLScalarType> scalars) {
        this.scalars = new HashMap<>()
        scalars.forEach({ scalar -> this.scalars.put(scalar.getName(), scalar) })
    }

    @Override
    boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return true
    }

    @Override
    TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
        new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                def fieldType = GraphQLTypeUtil.unwrapAll(env.getFieldType())
                env.getSchema().getImplementations((GraphQLInterfaceType) fieldType).get(0)
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
                def fieldType = GraphQLTypeUtil.unwrapAll(env.getFieldType())
                GraphQLObjectType graphQLObjectType
                def unionFirstType = ((GraphQLUnionType) fieldType).getTypes().get(0)
                if (unionFirstType instanceof GraphQLInterfaceType) {
                    graphQLObjectType = env.getSchema().getImplementations((GraphQLInterfaceType) unionFirstType).get(0)
                } else {
                    graphQLObjectType = unionFirstType as GraphQLObjectType
                }
                return graphQLObjectType
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

    @Override
    GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {

        String scalarName = environment.getScalarTypeDefinition().getName()
        return scalars.computeIfAbsent(scalarName, name -> GraphQLScalarType.newScalar().name(name).coercing(new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked scalar")
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked scalar")
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked scalar")
            }
        }).build())
    }
}

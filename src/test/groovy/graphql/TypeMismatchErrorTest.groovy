package graphql

import graphql.introspection.Introspection
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolverProxy
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class TypeMismatchErrorTest extends Specification {

    @Unroll
    def "test GraphQLTypeToTypeKindMapping mapping #type.getClass().getSimpleName()"(GraphQLType type, Introspection.TypeKind typeKind) {
        expect:
        TypeMismatchError.GraphQLTypeToTypeKindMapping.getTypeKindFromGraphQLType(type) == typeKind

        where:
        type                                                                                  || typeKind
        list(Scalars.GraphQLInt)                                                              || Introspection.TypeKind.LIST
        Scalars.GraphQLInt                                                                    || Introspection.TypeKind.SCALAR
        new GraphQLObjectType("myObject", "...", [], [])                                      || Introspection.TypeKind.OBJECT
        new GraphQLEnumType("myEnum", "...", [])                                              || Introspection.TypeKind.ENUM
        new GraphQLInputObjectType("myInputType", "...", [])                                  || Introspection.TypeKind.INPUT_OBJECT
        new GraphQLInterfaceType("myInterfaceType", "...", [], new TypeResolverProxy())       || Introspection.TypeKind.INTERFACE
        nonNull(Scalars.GraphQLInt)                                                           || Introspection.TypeKind.NON_NULL
        new GraphQLUnionType("myUnion", "...", [Scalars.GraphQLInt], new TypeResolverProxy()) || Introspection.TypeKind.UNION
    }
}

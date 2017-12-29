package graphql

import graphql.introspection.Introspection
import graphql.schema.*
import spock.lang.Specification
import spock.lang.Unroll

class TypeMismatchErrorTest extends Specification {

    @Unroll
    def "test GraphQLTypeToTypeKindMapping mapping #type.getClass().getSimpleName()"(GraphQLType type, Introspection.TypeKind typeKind) {
        expect:
        TypeMismatchError.GraphQLTypeToTypeKindMapping.getTypeKindFromGraphQLType(type) == typeKind

        where:
        type                                                                                  || typeKind
        new GraphQLList(Scalars.GraphQLInt)                                                   || Introspection.TypeKind.LIST
        Scalars.GraphQLInt                                                                    || Introspection.TypeKind.SCALAR
        new GraphQLObjectType("myObject", "...", [], [])                                      || Introspection.TypeKind.OBJECT
        new GraphQLEnumType("myEnum", "...", [])                                              || Introspection.TypeKind.ENUM
        new GraphQLInputObjectType("myInputType", "...", [])                                  || Introspection.TypeKind.INPUT_OBJECT
        new GraphQLInterfaceType("myInterfaceType", "...", [], new TypeResolverProxy())       || Introspection.TypeKind.INTERFACE
        new GraphQLNonNull(Scalars.GraphQLInt)                                                || Introspection.TypeKind.NON_NULL
        new GraphQLUnionType("myUnion", "...", [Scalars.GraphQLInt], new TypeResolverProxy()) || Introspection.TypeKind.UNION
    }
}

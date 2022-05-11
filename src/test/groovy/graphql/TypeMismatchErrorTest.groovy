package graphql

import graphql.introspection.Introspection
import graphql.schema.GraphQLType
import graphql.schema.TypeResolverProxy
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class TypeMismatchErrorTest extends Specification {

    @Unroll
    def "test GraphQLTypeToTypeKindMapping mapping #type.getClass().getSimpleName()"(GraphQLType type, Introspection.TypeKind typeKind) {
        expect:
        TypeMismatchError.GraphQLTypeToTypeKindMapping.getTypeKindFromGraphQLType(type) == typeKind

        where:
        type                                                                                            || typeKind
        list(Scalars.GraphQLInt)                                                                        || Introspection.TypeKind.LIST
        Scalars.GraphQLInt                                                                              || Introspection.TypeKind.SCALAR
        newObject().name("myObject").fields([]).build()                                                 || Introspection.TypeKind.OBJECT
        newEnum().name("myEnum").values([]).build()                                                     || Introspection.TypeKind.ENUM
        newInputObject().name("myInputType").fields([]).build()                                         || Introspection.TypeKind.INPUT_OBJECT
        newInterface().name("myInterfaceType").fields([]).typeResolver(new TypeResolverProxy()).build() || Introspection.TypeKind.INTERFACE
        nonNull(Scalars.GraphQLInt)                                                                     || Introspection.TypeKind.NON_NULL
        newUnionType().name("myUnion").possibleType(newObject().name("test").build()).build()           || Introspection.TypeKind.UNION
    }
}

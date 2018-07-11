package graphql.schema.idl

import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import spock.lang.Specification

class TypeInfoTest extends Specification {


    GraphQLType unwrap1Layer(GraphQLType type) {
        if (type instanceof GraphQLNonNull) {
            type = (type as GraphQLNonNull).wrappedType
        } else if (type instanceof GraphQLList) {
            type = (type as GraphQLList).wrappedType
        }
        type
    }

    def "unwrapping gets to the inner type"() {

        def typeNameFoo = TypeName.newTypeName("foo").build()
        def type = ListType.newListType(NonNullType.newNonNullType(ListType.newListType(typeNameFoo).build()).build()).build()
        def typeInfo = TypeInfo.typeInfo(type)

        expect:

        !typeInfo.isNonNull()
        typeInfo.isList()

        typeInfo.rawType == type
        typeInfo.typeName.getName() == "foo"

        typeInfo.unwrapOne().isNonNull()
        !typeInfo.unwrapOne().isList()
        typeInfo.unwrapOneType() instanceof NonNullType


    }

    def "decoration recreates new types"() {

        //
        // this equals -->  [ [ Foo! ] !]
        //
        def typeNameFoo = new TypeName("foo")
        def type = new ListType(new NonNullType(new ListType(new NonNullType(typeNameFoo))))
        def typeInfo = TypeInfo.typeInfo(type)

        def outputType = GraphQLObjectType.newObject().name("Foo").build()

        def decoratedTypeList1 = typeInfo.decorate(outputType)

        def decoratedTypeNonNull1 = unwrap1Layer(decoratedTypeList1)

        def decoratedTypeList2 = unwrap1Layer(decoratedTypeNonNull1)

        def decoratedTypeNonNull2 = unwrap1Layer(decoratedTypeList2)

        def decoratedType = unwrap1Layer(decoratedTypeNonNull2)

        expect:

        decoratedTypeList1 instanceof GraphQLList

        decoratedTypeNonNull1 instanceof GraphQLNonNull

        decoratedTypeList2 instanceof GraphQLList

        decoratedTypeNonNull2 instanceof GraphQLNonNull

        decoratedType.name == "Foo"
        decoratedType == outputType

    }

    @SuppressWarnings("ChangeToOperator")
    boolean assertEqualsAndHashCode(Type a1, Type a2) {
        assert TypeInfo.typeInfo(a1).equals(TypeInfo.typeInfo(a2))
        assert TypeInfo.typeInfo(a1).hashCode() == TypeInfo.typeInfo(a2).hashCode()
        return true
    }

    @SuppressWarnings("ChangeToOperator")
    boolean assertNotEqualsAndHashCode(Type a1, Type a2) {
        assert !TypeInfo.typeInfo(a1).equals(TypeInfo.typeInfo(a2))
        assert TypeInfo.typeInfo(a1).hashCode() != TypeInfo.typeInfo(a2).hashCode()
        return true
    }

    def "test equality and hashcode"() {

        expect:
        assertEqualsAndHashCode(new TypeName("A"), new TypeName("A"))

        assertEqualsAndHashCode(new NonNullType(new TypeName("A")), new NonNullType(new TypeName("A")))

        assertEqualsAndHashCode(new ListType(new TypeName("A")), new ListType(new TypeName("A")))
        assertEqualsAndHashCode(new NonNullType(new ListType(new TypeName("A"))), new NonNullType(new ListType(new TypeName("A"))))

        assertNotEqualsAndHashCode(new TypeName("A"), new TypeName("B"))

        assertNotEqualsAndHashCode(new NonNullType(new TypeName("A")), new NonNullType(new TypeName("B")))

        assertNotEqualsAndHashCode(new ListType(new TypeName("A")), new TypeName("A"))

        assertNotEqualsAndHashCode(new NonNullType(new ListType(new TypeName("A"))), new ListType(new TypeName("A")))
        assertNotEqualsAndHashCode(new NonNullType(new ListType(new TypeName("A"))), new NonNullType(new ListType(new TypeName("B"))))
    }
}

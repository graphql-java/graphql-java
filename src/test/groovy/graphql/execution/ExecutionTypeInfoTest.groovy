package graphql.execution

import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static ExecutionTypeInfo.newTypeInfo
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class ExecutionTypeInfoTest extends Specification {

    def field1Def = newFieldDefinition().name("field1").type(GraphQLString).build()

    def interfaceType = GraphQLInterfaceType.newInterface().name("Interface")
            .field(field1Def)
            .typeResolver({ env -> null })
            .build()

    def fieldType = GraphQLObjectType.newObject()
            .name("FieldType")
            .field(field1Def)
            .build()

    def rootType = GraphQLObjectType.newObject()
            .name("RootType")
            .field(newFieldDefinition().name("rootField1").type(fieldType))
            .build()


    def "basic hierarchy"() {
        given:
        def rootTypeInfo = newTypeInfo().type(rootType).build()
        def fieldTypeInfo = newTypeInfo().type(fieldType).fieldDefinition(field1Def).parentInfo(rootTypeInfo).build()
        def nonNullFieldTypeInfo = newTypeInfo().type(nonNull(fieldType)).parentInfo(rootTypeInfo).build()
        def listTypeInfo = newTypeInfo().type(list(fieldType)).parentInfo(rootTypeInfo).build()

        expect:
        rootTypeInfo.type() == rootType
        !rootTypeInfo.hasParentType()

        fieldTypeInfo.type() == fieldType
        fieldTypeInfo.hasParentType()
        fieldTypeInfo.parentTypeInfo().type() == rootType
        !fieldTypeInfo.isNonNullType()
        fieldTypeInfo.getFieldDefinition() == field1Def

        nonNullFieldTypeInfo.type() == fieldType
        nonNullFieldTypeInfo.hasParentType()
        nonNullFieldTypeInfo.parentTypeInfo().type() == rootType
        nonNullFieldTypeInfo.isNonNullType()

        listTypeInfo.type() == list(fieldType)
        listTypeInfo.hasParentType()
        listTypeInfo.parentTypeInfo().type() == rootType
        listTypeInfo.isListType()
    }

    def "morphing type works"() {
        given:
        def rootTypeInfo = newTypeInfo().type(rootType).build()
        def interfaceTypeInfo = newTypeInfo().type(interfaceType).parentInfo(rootTypeInfo).build()
        def morphedTypeInfo = interfaceTypeInfo.treatAs(fieldType)

        expect:

        interfaceTypeInfo.type() == interfaceType
        morphedTypeInfo.type() == fieldType
    }

    def "unwrapping stack works"() {

        given:
        // [[String!]!]
        GraphQLType wrappedType = list(nonNull(list(nonNull(GraphQLString))))
        def stack = ExecutionTypeInfo.unwrapType(wrappedType)

        expect:
        stack.pop() == GraphQLString
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.pop() instanceof GraphQLNonNull
        stack.pop() instanceof GraphQLList
        stack.isEmpty()

    }
}

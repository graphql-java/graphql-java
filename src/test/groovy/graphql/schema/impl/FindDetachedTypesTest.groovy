package graphql.schema.impl

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLObjectType
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLUnionType.newUnionType

class FindDetachedTypesTest extends Specification {

    def "type not reachable from any root is detached"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a detached type"
        def detachedType = newObject()
                .name("DetachedType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "type map including both (built-in scalars are attached via Query field)"
        def typeMap = [
                "Query"       : queryType,
                "DetachedType": detachedType,
                "String"      : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "only DetachedType is detached"
        detached*.name as Set == ["DetachedType"] as Set
    }

    def "type reachable from Query is attached"() {
        given: "a custom type"
        def customType = newObject()
                .name("CustomType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLInt))
                .build()

        and: "a query type that references the custom type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("custom")
                        .type(customType))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "CustomType": customType,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "custom type is attached (not detached)"
        detached.empty
    }

    def "type reachable from Mutation is attached"() {
        given: "a custom type"
        def customType = newObject()
                .name("CustomType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLInt))
                .build()

        and: "a mutation type that references the custom type"
        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("createCustom")
                        .type(customType))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "Mutation"  : mutationType,
                "CustomType": customType,
                "String"    : GraphQLString,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, mutationType, null, [])

        then: "custom type is attached (not detached)"
        detached.empty
    }

    def "type reachable from Subscription is attached"() {
        given: "a custom type"
        def customType = newObject()
                .name("CustomType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLInt))
                .build()

        and: "a subscription type that references the custom type"
        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("customChanged")
                        .type(customType))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"       : queryType,
                "Subscription": subscriptionType,
                "CustomType"  : customType,
                "String"      : GraphQLString,
                "Int"         : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, subscriptionType, [])

        then: "custom type is attached (not detached)"
        detached.empty
    }

    def "root types themselves are attached"() {
        given: "query, mutation, and subscription types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("setValue")
                        .type(GraphQLString))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("valueChanged")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"       : queryType,
                "Mutation"    : mutationType,
                "Subscription": subscriptionType,
                "String"      : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, mutationType, subscriptionType, [])

        then: "no root types are detached"
        detached.empty
    }

    def "object type field types are followed"() {
        given: "a nested type"
        def nestedType = newObject()
                .name("NestedType")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "an object type with field of nested type"
        def parentType = newObject()
                .name("ParentType")
                .field(newFieldDefinition()
                        .name("nested")
                        .type(nestedType))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("parent")
                        .type(parentType))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "ParentType": parentType,
                "NestedType": nestedType,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "nested type is attached (not detached)"
        detached.empty
    }

    def "object type field argument types are followed"() {
        given: "an input type"
        def inputType = newInputObject()
                .name("InputType")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "a query type with field that has argument of input type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("filter")
                                .type(inputType)))
                .build()

        and: "type map"
        def typeMap = [
                "Query"    : queryType,
                "InputType": inputType,
                "String"   : GraphQLString,
                "Int"      : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "input type is attached (not detached)"
        detached.empty
    }

    def "object type interface implementations are followed"() {
        given: "an interface type"
        def interfaceType = newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "an object type implementing the interface"
        def objectType = newObject()
                .name("User")
                .withInterface(interfaceType)
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(objectType))
                .build()

        and: "type map"
        def typeMap = [
                "Query" : queryType,
                "User"  : objectType,
                "Node"  : interfaceType,
                "String": GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "interface type is attached (not detached)"
        detached.empty
    }

    def "interface type field types are followed"() {
        given: "a custom type"
        def customType = newObject()
                .name("CustomType")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "an interface type with field of custom type"
        def interfaceType = newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("custom")
                        .type(customType))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(interfaceType))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "Node"      : interfaceType,
                "CustomType": customType,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "custom type is attached (not detached)"
        detached.empty
    }

    def "interface type field argument types are followed"() {
        given: "an input type"
        def inputType = newInputObject()
                .name("InputType")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "an interface type with field that has argument of input type"
        def interfaceType = newInterface()
                .name("Searchable")
                .field(newFieldDefinition()
                        .name("search")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("filter")
                                .type(inputType)))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("searchable")
                        .type(interfaceType))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "Searchable": interfaceType,
                "InputType" : inputType,
                "String"    : GraphQLString,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "input type is attached (not detached)"
        detached.empty
    }

    def "interface extending interface is followed"() {
        given: "a base interface"
        def baseInterface = newInterface()
                .name("BaseInterface")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "an interface extending the base"
        def childInterface = newInterface()
                .name("ChildInterface")
                .withInterface(baseInterface)
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("child")
                        .type(childInterface))
                .build()

        and: "type map"
        def typeMap = [
                "Query"         : queryType,
                "ChildInterface": childInterface,
                "BaseInterface" : baseInterface,
                "String"        : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "base interface is attached (not detached)"
        detached.empty
    }

    def "union member types are followed"() {
        given: "union member types"
        def typeA = newObject()
                .name("TypeA")
                .field(newFieldDefinition()
                        .name("a")
                        .type(GraphQLString))
                .build()

        def typeB = newObject()
                .name("TypeB")
                .field(newFieldDefinition()
                        .name("b")
                        .type(GraphQLInt))
                .build()

        and: "a union type"
        def unionType = newUnionType()
                .name("SearchResult")
                .possibleType(typeA)
                .possibleType(typeB)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .type(unionType))
                .build()

        and: "type map"
        def typeMap = [
                "Query"       : queryType,
                "SearchResult": unionType,
                "TypeA"       : typeA,
                "TypeB"       : typeB,
                "String"      : GraphQLString,
                "Int"         : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "all union member types are attached (not detached)"
        detached.empty
    }

    def "input object field types are followed"() {
        given: "a nested input type"
        def nestedInput = newInputObject()
                .name("NestedInput")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "an input type with field of nested input type"
        def inputType = newInputObject()
                .name("InputType")
                .field(newInputObjectField()
                        .name("nested")
                        .type(nestedInput))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("input")
                                .type(inputType)))
                .build()

        and: "type map"
        def typeMap = [
                "Query"      : queryType,
                "InputType"  : inputType,
                "NestedInput": nestedInput,
                "String"     : GraphQLString,
                "Int"        : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "nested input type is attached (not detached)"
        detached.empty
    }

    def "wrapped types (NonNull, List) are properly unwrapped"() {
        given: "a custom type"
        def customType = newObject()
                .name("CustomType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLInt))
                .build()

        and: "a query type with wrapped field type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("items")
                        .type(nonNull(list(nonNull(customType)))))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "CustomType": customType,
                "Int"       : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "custom type is attached despite wrapping (not detached)"
        detached.empty
    }

    def "type used only in directive argument is attached"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("CustomScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive using the custom scalar"
        def directive = newDirective()
                .name("customDirective")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("value")
                        .type(customScalar))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"       : queryType,
                "CustomScalar": customScalar,
                "String"      : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [directive])

        then: "custom scalar is attached (not detached)"
        detached.empty
    }

    def "directive with multiple arguments attaches all argument types"() {
        given: "custom input types"
        def inputTypeA = newInputObject()
                .name("InputTypeA")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def inputTypeB = newInputObject()
                .name("InputTypeB")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a directive using both input types"
        def directive = newDirective()
                .name("multiArgDirective")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("argA")
                        .type(inputTypeA))
                .argument(newArgument()
                        .name("argB")
                        .type(inputTypeB))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "InputTypeA": inputTypeA,
                "InputTypeB": inputTypeB,
                "String"    : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [directive])

        then: "all directive argument types are attached (not detached)"
        detached.empty
    }

    def "empty schema has no detached types"() {
        given: "a minimal query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map with only Query and String"
        def typeMap = [
                "Query" : queryType,
                "String": GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "no detached types"
        detached.empty
    }

    def "all types attached returns empty set"() {
        given: "multiple types all connected"
        def typeA = newObject()
                .name("TypeA")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def typeB = newObject()
                .name("TypeB")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLInt))
                .build()

        and: "a query type referencing both"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("a")
                        .type(typeA))
                .field(newFieldDefinition()
                        .name("b")
                        .type(typeB))
                .build()

        and: "type map"
        def typeMap = [
                "Query" : queryType,
                "TypeA" : typeA,
                "TypeB" : typeB,
                "String": GraphQLString,
                "Int"   : GraphQLInt
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "no detached types"
        detached.empty
    }

    def "all types detached except roots returns correct set"() {
        given: "multiple detached types"
        def detachedA = newObject()
                .name("DetachedA")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def detachedB = newObject()
                .name("DetachedB")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def detachedC = newObject()
                .name("DetachedC")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"    : queryType,
                "DetachedA": detachedA,
                "DetachedB": detachedB,
                "DetachedC": detachedC,
                "String"   : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "all non-root types are detached"
        detached*.name as Set == ["DetachedA", "DetachedB", "DetachedC"] as Set
    }

    def "circular type references don't cause infinite loop"() {
        given: "two types that reference each other"
        def typeA = newObject()
                .name("TypeA")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def typeB = newObject()
                .name("TypeB")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        // Add circular references
        typeA = typeA.transform { builder ->
            builder.field(newFieldDefinition()
                    .name("b")
                    .type(typeB))
        }

        typeB = typeB.transform { builder ->
            builder.field(newFieldDefinition()
                    .name("a")
                    .type(typeA))
        }

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("a")
                        .type(typeA))
                .build()

        and: "type map"
        def typeMap = [
                "Query" : queryType,
                "TypeA" : typeA,
                "TypeB" : typeB,
                "String": GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "no error and both types are attached"
        detached.empty
    }

    def "complex mixed scenario with attached and detached types"() {
        given: "attached types"
        def attachedObject = newObject()
                .name("AttachedObject")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def attachedEnum = newEnum()
                .name("AttachedEnum")
                .value(newEnumValueDefinition().name("VALUE1").value("VALUE1").build())
                .build()

        and: "detached types"
        def detachedObject = newObject()
                .name("DetachedObject")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def detachedScalar = newScalar()
                .name("DetachedScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type referencing only attached types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("object")
                        .type(attachedObject))
                .field(newFieldDefinition()
                        .name("enum")
                        .type(attachedEnum))
                .build()

        and: "type map"
        def typeMap = [
                "Query"          : queryType,
                "AttachedObject" : attachedObject,
                "AttachedEnum"   : attachedEnum,
                "DetachedObject" : detachedObject,
                "DetachedScalar" : detachedScalar,
                "String"         : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "only detached types are returned"
        detached*.name as Set == ["DetachedObject", "DetachedScalar"] as Set
    }

    def "type reachable through multiple paths is still attached"() {
        given: "a shared type"
        def sharedType = newObject()
                .name("SharedType")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a query type with multiple paths to shared type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("path1")
                        .type(sharedType))
                .field(newFieldDefinition()
                        .name("path2")
                        .type(sharedType))
                .field(newFieldDefinition()
                        .name("path3")
                        .type(list(sharedType)))
                .build()

        and: "type map"
        def typeMap = [
                "Query"     : queryType,
                "SharedType": sharedType,
                "String"    : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "shared type is attached (visited once, not multiple times)"
        detached.empty
    }

    def "type reachable from directive but not roots is still attached"() {
        given: "a type used only in directive"
        def directiveOnlyType = newInputObject()
                .name("DirectiveOnlyType")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a directive using this type"
        def directive = newDirective()
                .name("customDirective")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("config")
                        .type(directiveOnlyType))
                .build()

        and: "a detached type"
        def detachedType = newObject()
                .name("DetachedType")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type map"
        def typeMap = [
                "Query"            : queryType,
                "DirectiveOnlyType": directiveOnlyType,
                "DetachedType"     : detachedType,
                "String"           : GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [directive])

        then: "directive type is attached, but truly detached type is not"
        detached*.name as Set == ["DetachedType"] as Set
    }

    def "deep nesting with wrapped types is properly traversed"() {
        given: "deeply nested types"
        def level3Type = newObject()
                .name("Level3")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def level2Type = newObject()
                .name("Level2")
                .field(newFieldDefinition()
                        .name("level3")
                        .type(nonNull(list(level3Type))))
                .build()

        def level1Type = newObject()
                .name("Level1")
                .field(newFieldDefinition()
                        .name("level2")
                        .type(list(nonNull(level2Type))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("level1")
                        .type(nonNull(level1Type)))
                .build()

        and: "type map"
        def typeMap = [
                "Query" : queryType,
                "Level1": level1Type,
                "Level2": level2Type,
                "Level3": level3Type,
                "String": GraphQLString
        ]

        when: "finding detached types"
        def detached = FindDetachedTypes.findDetachedTypes(typeMap, queryType, null, null, [])

        then: "all nested types are attached"
        detached.empty
    }
}

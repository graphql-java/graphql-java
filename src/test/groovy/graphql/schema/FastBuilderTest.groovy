package graphql.schema

import graphql.AssertException
import graphql.Scalars
import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLTypeReference.typeRef

class FastBuilderTest extends Specification {

    def "scalar type schema matches standard builder"() {
        given: "a custom scalar type"
        def customScalar = newScalar()
                .name("CustomScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type using the scalar"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(customScalar))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building with FastBuilder"
        def fastSchema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(customScalar)
                .build()

        and: "building with standard Builder"
        def standardSchema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry.build())
                .additionalType(customScalar)
                .build()

        then: "schemas are equivalent"
        fastSchema.queryType.name == standardSchema.queryType.name
        fastSchema.getType("CustomScalar") != null
        fastSchema.getType("CustomScalar").name == standardSchema.getType("CustomScalar").name
        // Check that the types in both schemas match (excluding system types added differently)
        fastSchema.typeMap.keySet().containsAll(["Query", "CustomScalar"])
        standardSchema.typeMap.keySet().containsAll(["Query", "CustomScalar"])
    }

    def "duplicate type with different instance throws error"() {
        given: "two different scalar instances with same name"
        def scalar1 = newScalar()
                .name("Duplicate")
                .coercing(GraphQLString.getCoercing())
                .build()
        def scalar2 = newScalar()
                .name("Duplicate")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding both scalars"
        new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(scalar1)
                .additionalType(scalar2)
                .build()

        then: "error is thrown"
        thrown(AssertException)
    }

    def "same type instance can be added multiple times"() {
        given: "a scalar type"
        def scalar = newScalar()
                .name("MyScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(scalar))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding same scalar twice"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(scalar)
                .additionalType(scalar)
                .build()

        then: "no error and scalar is in schema"
        schema.getType("MyScalar") != null
    }

    def "null type is safely ignored"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding null type"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(null)
                .build()

        then: "no error"
        schema.queryType.name == "Query"
    }

    def "built-in directives are added automatically"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .build()

        then: "built-in directives are present"
        schema.getDirective("skip") != null
        schema.getDirective("include") != null
        schema.getDirective("deprecated") != null
        schema.getDirective("specifiedBy") != null
        schema.getDirective("oneOf") != null
        schema.getDirective("defer") != null
    }

    def "query type is required"() {
        when: "creating FastBuilder with null query type"
        new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), null, null, null)

        then: "error is thrown"
        thrown(AssertException)
    }

    def "code registry builder is required"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "creating FastBuilder with null code registry"
        new GraphQLSchema.FastBuilder(null, queryType, null, null)

        then: "error is thrown"
        thrown(AssertException)
    }

    def "mutation and subscription types are optional"() {
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

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema with all root types"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, mutationType, subscriptionType)
                .build()

        then: "all root types are present"
        schema.queryType.name == "Query"
        schema.mutationType.name == "Mutation"
        schema.subscriptionType.name == "Subscription"
        schema.isSupportingMutations()
        schema.isSupportingSubscriptions()
    }

    def "schema description can be set"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema with description"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .description("Test schema description")
                .build()

        then: "description is set"
        schema.description == "Test schema description"
    }

    def "additionalTypes accepts collection"() {
        given: "multiple scalar types"
        def scalar1 = newScalar()
                .name("Scalar1")
                .coercing(GraphQLString.getCoercing())
                .build()
        def scalar2 = newScalar()
                .name("Scalar2")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding types as collection"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalTypes([scalar1, scalar2])
                .build()

        then: "both types are in schema"
        schema.getType("Scalar1") != null
        schema.getType("Scalar2") != null
    }

    // ==================== Phase 2: Directives with Scalar Arguments ====================

    def "directive with type reference argument resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("Foo")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive with type reference argument"
        def directive = newDirective()
                .name("bar")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("arg")
                        .type(typeRef("Foo")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(customScalar))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalType(customScalar)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved"
        def resolvedDirective = schema.getDirective("bar")
        resolvedDirective != null
        resolvedDirective.getArgument("arg").getType() == customScalar
    }

    def "directive with NonNull wrapped type reference resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("MyScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive with NonNull type reference argument"
        def directive = newDirective()
                .name("myDirective")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("arg")
                        .type(GraphQLNonNull.nonNull(typeRef("MyScalar"))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalType(customScalar)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved with NonNull wrapper"
        def resolvedDirective = schema.getDirective("myDirective")
        def argType = resolvedDirective.getArgument("arg").getType()
        argType instanceof GraphQLNonNull
        ((GraphQLNonNull) argType).getWrappedType() == customScalar
    }

    def "directive with List wrapped type reference resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("ListScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive with List type reference argument"
        def directive = newDirective()
                .name("listDirective")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("args")
                        .type(GraphQLList.list(typeRef("ListScalar"))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalType(customScalar)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved with List wrapper"
        def resolvedDirective = schema.getDirective("listDirective")
        def argType = resolvedDirective.getArgument("args").getType()
        argType instanceof GraphQLList
        ((GraphQLList) argType).getWrappedType() == customScalar
    }

    def "missing type reference throws error"() {
        given: "a directive referencing non-existent type"
        def directive = newDirective()
                .name("bar")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("arg")
                        .type(typeRef("NonExistent")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirective(directive)
                .build()

        then: "error for missing type"
        thrown(AssertException)
    }

    def "duplicate directive with different instance throws error"() {
        given: "two different directive instances with same name"
        def directive1 = newDirective()
                .name("duplicate")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .build()
        def directive2 = newDirective()
                .name("duplicate")
                .validLocation(Introspection.DirectiveLocation.OBJECT)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "adding both directives"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirective(directive1)
                .additionalDirective(directive2)
                .build()

        then: "error is thrown"
        thrown(AssertException)
    }

    def "same directive instance can be added multiple times"() {
        given: "a directive"
        def directive = newDirective()
                .name("myDir")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "adding same directive twice"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirective(directive)
                .additionalDirective(directive)
                .build()

        then: "no error and directive is in schema"
        schema.getDirective("myDir") != null
    }

    def "additionalDirectives accepts collection"() {
        given: "multiple directives"
        def directive1 = newDirective()
                .name("dir1")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .build()
        def directive2 = newDirective()
                .name("dir2")
                .validLocation(Introspection.DirectiveLocation.OBJECT)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "adding directives as collection"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirectives([directive1, directive2])
                .build()

        then: "both directives are in schema"
        schema.getDirective("dir1") != null
        schema.getDirective("dir2") != null
    }

    def "directive argument with concrete type (no type reference) works"() {
        given: "a directive with concrete type argument"
        def directive = newDirective()
                .name("withString")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("msg")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirective(directive)
                .build()

        then: "directive argument type remains unchanged"
        def resolvedDirective = schema.getDirective("withString")
        resolvedDirective.getArgument("msg").getType() == GraphQLString
    }

    // ==================== Phase 3: Enumeration Types ====================

    def "enum type can be added to schema"() {
        given: "an enum type"
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .value("PENDING")
                .build()

        and: "a query type using the enum"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(statusEnum))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalType(statusEnum)
                .build()

        then: "enum type is in schema"
        def resolvedEnum = schema.getType("Status")
        resolvedEnum instanceof GraphQLEnumType
        (resolvedEnum as GraphQLEnumType).values.size() == 3
        (resolvedEnum as GraphQLEnumType).getValue("ACTIVE") != null
        (resolvedEnum as GraphQLEnumType).getValue("INACTIVE") != null
        (resolvedEnum as GraphQLEnumType).getValue("PENDING") != null
    }

    def "enum type matches standard builder"() {
        given: "an enum type"
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(statusEnum))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building with FastBuilder"
        def fastSchema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(statusEnum)
                .build()

        and: "building with standard Builder"
        def standardSchema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry.build())
                .additionalType(statusEnum)
                .build()

        then: "schemas have equivalent enum types"
        def fastEnum = fastSchema.getType("Status") as GraphQLEnumType
        def standardEnum = standardSchema.getType("Status") as GraphQLEnumType
        fastEnum.values.size() == standardEnum.values.size()
        fastEnum.getValue("ACTIVE") != null
        fastEnum.getValue("INACTIVE") != null
    }

    def "directive argument with enum type reference resolves correctly"() {
        given: "an enum type"
        def levelEnum = newEnum()
                .name("LogLevel")
                .value("DEBUG")
                .value("INFO")
                .value("WARN")
                .value("ERROR")
                .build()

        and: "a directive with enum type reference argument"
        def directive = newDirective()
                .name("log")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("level")
                        .type(typeRef("LogLevel")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalType(levelEnum)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved to enum"
        def resolvedDirective = schema.getDirective("log")
        resolvedDirective.getArgument("level").getType() == levelEnum
    }
}

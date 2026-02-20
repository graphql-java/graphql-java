package graphql.schema

import graphql.AssertException
import graphql.Scalars
import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLAppliedDirective.newDirective as newAppliedDirective
import static graphql.schema.GraphQLAppliedDirectiveArgument.newArgument as newAppliedArgument
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLTypeReference.typeRef

class FastBuilderTest extends Specification {

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
                .addType(scalar1)
                .addType(scalar2)
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
                .addType(scalar)
                .addType(scalar)
                .build()

        then: "no error and scalar is in schema"
        schema.getType("MyScalar") != null
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
                .addTypes([scalar1, scalar2])
                .build()

        then: "both types are in schema"
        schema.getType("Scalar1") != null
        schema.getType("Scalar2") != null
    }

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
                .addType(customScalar)
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
                .addType(customScalar)
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
                .addType(customScalar)
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
                .addType(statusEnum)
                .build()

        then: "enum type is in schema"
        def resolvedEnum = schema.getType("Status")
        resolvedEnum instanceof GraphQLEnumType
        (resolvedEnum as GraphQLEnumType).values.size() == 3
        (resolvedEnum as GraphQLEnumType).getValue("ACTIVE") != null
        (resolvedEnum as GraphQLEnumType).getValue("INACTIVE") != null
        (resolvedEnum as GraphQLEnumType).getValue("PENDING") != null
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
                .addType(levelEnum)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved to enum"
        def resolvedDirective = schema.getDirective("log")
        resolvedDirective.getArgument("level").getType() == levelEnum
    }

    def "input object type can be added to schema"() {
        given: "an input object type"
        def inputType = newInputObject()
                .name("CreateUserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("email")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createUser")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(inputType)
                .build()

        then: "input type is in schema"
        def resolvedInput = schema.getType("CreateUserInput")
        resolvedInput instanceof GraphQLInputObjectType
        (resolvedInput as GraphQLInputObjectType).getField("name") != null
        (resolvedInput as GraphQLInputObjectType).getField("email") != null
    }

    def "input object type with type reference field resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("DateTime")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "an input object type with type reference"
        def inputType = newInputObject()
                .name("EventInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("startDate")
                        .type(typeRef("DateTime")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createEvent")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(customScalar)
                .addType(inputType)
                .build()

        then: "input field type is resolved"
        def resolvedInput = schema.getType("EventInput") as GraphQLInputObjectType
        resolvedInput.getField("startDate").getType() == customScalar
    }

    def "input object type with nested input object type reference resolves correctly"() {
        given: "an address input type"
        def addressInput = newInputObject()
                .name("AddressInput")
                .field(newInputObjectField()
                        .name("street")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("city")
                        .type(GraphQLString))
                .build()

        and: "a user input type with type reference to address"
        def userInput = newInputObject()
                .name("UserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("address")
                        .type(typeRef("AddressInput")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createUser")
                        .argument(newArgument()
                                .name("input")
                                .type(userInput))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(addressInput)
                .addType(userInput)
                .build()

        then: "nested input field type is resolved"
        def resolvedUser = schema.getType("UserInput") as GraphQLInputObjectType
        resolvedUser.getField("address").getType() == addressInput
    }

    def "input object type with NonNull wrapped type reference resolves correctly"() {
        given: "an enum type"
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        and: "an input type with NonNull type reference"
        def inputType = newInputObject()
                .name("UpdateInput")
                .field(newInputObjectField()
                        .name("status")
                        .type(GraphQLNonNull.nonNull(typeRef("Status"))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("update")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(statusEnum)
                .addType(inputType)
                .build()

        then: "input field type is resolved with NonNull wrapper"
        def resolvedInput = schema.getType("UpdateInput") as GraphQLInputObjectType
        def fieldType = resolvedInput.getField("status").getType()
        fieldType instanceof GraphQLNonNull
        ((GraphQLNonNull) fieldType).getWrappedType() == statusEnum
    }

    def "input object type with List wrapped type reference resolves correctly"() {
        given: "a custom scalar"
        def tagScalar = newScalar()
                .name("Tag")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "an input type with List type reference"
        def inputType = newInputObject()
                .name("PostInput")
                .field(newInputObjectField()
                        .name("title")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("tags")
                        .type(GraphQLList.list(typeRef("Tag"))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createPost")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(tagScalar)
                .addType(inputType)
                .build()

        then: "input field type is resolved with List wrapper"
        def resolvedInput = schema.getType("PostInput") as GraphQLInputObjectType
        def fieldType = resolvedInput.getField("tags").getType()
        fieldType instanceof GraphQLList
        ((GraphQLList) fieldType).getWrappedType() == tagScalar
    }

    def "directive argument can reference input object type"() {
        given: "an input object type"
        def configInput = newInputObject()
                .name("ConfigInput")
                .field(newInputObjectField()
                        .name("enabled")
                        .type(Scalars.GraphQLBoolean))
                .build()

        and: "a directive with input type reference argument"
        def directive = newDirective()
                .name("config")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("settings")
                        .type(typeRef("ConfigInput")))
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
                .addType(configInput)
                .additionalDirective(directive)
                .build()

        then: "directive argument type is resolved to input type"
        def resolvedDirective = schema.getDirective("config")
        resolvedDirective.getArgument("settings").getType() == configInput
    }

    def "schema applied directive with type reference argument resolves correctly"() {
        given: "a custom scalar for directive argument"
        def configScalar = newScalar()
                .name("ConfigValue")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("config")
                .validLocation(Introspection.DirectiveLocation.SCHEMA)
                .argument(newArgument()
                        .name("value")
                        .type(configScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("config")
                .argument(newAppliedArgument()
                        .name("value")
                        .type(typeRef("ConfigValue"))
                        .valueProgrammatic("test"))
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
                .addType(configScalar)
                .additionalDirective(directive)
                .withSchemaAppliedDirective(appliedDirective)
                .build()

        then: "applied directive argument type is resolved"
        def resolved = schema.getSchemaAppliedDirective("config")
        resolved.getArgument("value").getType() == configScalar
    }

    def "type with applied directive argument type reference resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("MyScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("myDir")
                .validLocation(Introspection.DirectiveLocation.ENUM)
                .argument(newArgument()
                        .name("arg")
                        .type(customScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("myDir")
                .argument(newAppliedArgument()
                        .name("arg")
                        .type(typeRef("MyScalar"))
                        .valueProgrammatic("value"))
                .build()

        and: "an enum with the applied directive"
        def enumType = newEnum()
                .name("Status")
                .value("ACTIVE")
                .withAppliedDirective(appliedDirective)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(enumType))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(customScalar)
                .addType(enumType)
                .additionalDirective(directive)
                .build()

        then: "applied directive argument type on enum is resolved"
        def resolvedEnum = schema.getType("Status") as GraphQLEnumType
        def resolvedApplied = resolvedEnum.getAppliedDirective("myDir")
        resolvedApplied.getArgument("arg").getType() == customScalar
    }

    def "input object field with applied directive type reference resolves correctly"() {
        given: "a custom scalar"
        def customScalar = newScalar()
                .name("FieldMeta")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("meta")
                .validLocation(Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION)
                .argument(newArgument()
                        .name("data")
                        .type(customScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("meta")
                .argument(newAppliedArgument()
                        .name("data")
                        .type(typeRef("FieldMeta"))
                        .valueProgrammatic("metadata"))
                .build()

        and: "an input type with field having applied directive"
        def inputType = newInputObject()
                .name("MyInput")
                .field(newInputObjectField()
                        .name("field1")
                        .type(GraphQLString)
                        .withAppliedDirective(appliedDirective))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("query")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(customScalar)
                .addType(inputType)
                .additionalDirective(directive)
                .build()

        then: "applied directive argument type on input field is resolved"
        def resolvedInput = schema.getType("MyInput") as GraphQLInputObjectType
        def field = resolvedInput.getField("field1")
        def resolvedApplied = field.getAppliedDirective("meta")
        resolvedApplied.getArgument("data").getType() == customScalar
    }

    def "multiple schema applied directives work correctly"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "multiple applied directives (no type refs)"
        def applied1 = newAppliedDirective()
                .name("dir1")
                .build()
        def applied2 = newAppliedDirective()
                .name("dir2")
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .withSchemaAppliedDirectives([applied1, applied2])
                .build()

        then: "both applied directives are in schema"
        schema.getSchemaAppliedDirective("dir1") != null
        schema.getSchemaAppliedDirective("dir2") != null
    }

    def "batch schema applied directives with type reference arguments resolve correctly"() {
        given: "a custom scalar for directive arguments"
        def configScalar = newScalar()
                .name("ConfigValue")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("config")
                .validLocation(Introspection.DirectiveLocation.SCHEMA)
                .argument(newArgument()
                        .name("value")
                        .type(configScalar))
                .build()

        and: "applied directives with type references added via batch method"
        def applied1 = newAppliedDirective()
                .name("config")
                .argument(newAppliedArgument()
                        .name("value")
                        .type(typeRef("ConfigValue"))
                        .valueProgrammatic("test1"))
                .build()
        def applied2 = newAppliedDirective()
                .name("config")
                .argument(newAppliedArgument()
                        .name("value")
                        .type(typeRef("ConfigValue"))
                        .valueProgrammatic("test2"))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with FastBuilder using batch withSchemaAppliedDirectives"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(configScalar)
                .additionalDirective(directive)
                .withSchemaAppliedDirectives([applied1, applied2])
                .build()

        then: "both applied directive argument types are resolved"
        def resolvedDirectives = schema.getSchemaAppliedDirectives("config")
        resolvedDirectives.size() == 2
        resolvedDirectives.every { it.getArgument("value").getType() == configScalar }
    }

    def "object type field with type reference resolves correctly"() {
        given: "a custom object type"
        def personType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        and: "a query type with field returning Person via type reference"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("person")
                        .type(typeRef("Person")))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(personType)
                .build()

        then: "field type is resolved"
        def queryField = schema.queryType.getFieldDefinition("person")
        queryField.getType() == personType
    }

    def "object type field with NonNull wrapped type reference resolves correctly"() {
        given: "a custom object type"
        def itemType = newObject()
                .name("Item")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "a query type with NonNull field"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("item")
                        .type(GraphQLNonNull.nonNull(typeRef("Item"))))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(itemType)
                .build()

        then: "field type is resolved with NonNull wrapper"
        def queryField = schema.queryType.getFieldDefinition("item")
        def fieldType = queryField.getType()
        fieldType instanceof GraphQLNonNull
        ((GraphQLNonNull) fieldType).getWrappedType() == itemType
    }

    def "object type field with List wrapped type reference resolves correctly"() {
        given: "a custom object type"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        and: "a query type with List field"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("users")
                        .type(GraphQLList.list(typeRef("User"))))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(userType)
                .build()

        then: "field type is resolved with List wrapper"
        def queryField = schema.queryType.getFieldDefinition("users")
        def fieldType = queryField.getType()
        fieldType instanceof GraphQLList
        ((GraphQLList) fieldType).getWrappedType() == userType
    }

    def "object type implementing interface with type reference resolves correctly"() {
        given: "an interface type"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "an object type implementing interface via type reference"
        def postType = newObject()
                .name("Post")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("post")
                        .type(postType))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .addType(postType)
                .build()

        then: "interface reference is resolved"
        def resolvedPost = schema.getType("Post") as GraphQLObjectType
        resolvedPost.getInterfaces().size() == 1
        resolvedPost.getInterfaces()[0] == nodeInterface
    }

    def "interface to implementations map is built correctly"() {
        given: "an interface type"
        def entityInterface = GraphQLInterfaceType.newInterface()
                .name("Entity")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "multiple object types implementing interface"
        def userType = newObject()
                .name("User")
                .withInterface(entityInterface)
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def productType = newObject()
                .name("Product")
                .withInterface(entityInterface)
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("price")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("entity")
                        .type(entityInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Entity", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                codeRegistry, queryType, null, null)
                .addType(entityInterface)
                .addType(userType)
                .addType(productType)
                .build()

        then: "interface to implementations map is built"
        def implementations = schema.getImplementations(entityInterface)
        implementations.size() == 2
        implementations.any { it.name == "User" }
        implementations.any { it.name == "Product" }
    }

    def "object type field argument with type reference resolves correctly"() {
        given: "an input type"
        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("status")
                        .type(GraphQLString))
                .build()

        and: "an object type"
        def resultType = newObject()
                .name("Result")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "a query type with field having argument with type reference"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("filter")
                                .type(typeRef("FilterInput")))
                        .type(resultType))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(filterInput)
                .addType(resultType)
                .build()

        then: "field argument type is resolved"
        def searchField = schema.queryType.getFieldDefinition("search")
        searchField.getArgument("filter").getType() == filterInput
    }

    def "object type field with applied directive type reference resolves correctly"() {
        given: "a custom scalar"
        def metaScalar = newScalar()
                .name("FieldMetadata")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("fieldMeta")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("info")
                        .type(metaScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("fieldMeta")
                .argument(newAppliedArgument()
                        .name("info")
                        .type(typeRef("FieldMetadata"))
                        .valueProgrammatic("metadata"))
                .build()

        and: "a query type with field having applied directive"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString)
                        .withAppliedDirective(appliedDirective))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(metaScalar)
                .additionalDirective(directive)
                .build()

        then: "applied directive argument type on field is resolved"
        def field = schema.queryType.getFieldDefinition("value")
        def resolvedApplied = field.getAppliedDirective("fieldMeta")
        resolvedApplied.getArgument("info").getType() == metaScalar
    }

    def "object type missing field type reference throws error"() {
        given: "a query type with missing type reference"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("missing")
                        .type(typeRef("NonExistent")))
                .build()

        when: "building"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .build()

        then: "error for missing type"
        thrown(AssertException)
    }

    def "object type with missing interface type reference throws error"() {
        given: "an object type with missing interface reference"
        def objectType = newObject()
                .name("MyObject")
                .withInterface(typeRef("NonExistentInterface"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("obj")
                        .type(objectType))
                .build()

        when: "building"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(objectType)
                .build()

        then: "error for missing interface"
        thrown(AssertException)
    }

    def "interface type can be added to schema"() {
        given: "an interface type"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .build()

        then: "interface type is in schema"
        def resolvedInterface = schema.getType("Node")
        resolvedInterface instanceof GraphQLInterfaceType
        (resolvedInterface as GraphQLInterfaceType).getFieldDefinition("id") != null
    }

    def "interface type field with type reference resolves correctly"() {
        given: "a custom object type"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        and: "an interface type with field returning User via type reference"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("owner")
                        .type(typeRef("User")))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .addType(userType)
                .build()

        then: "interface field type is resolved"
        def resolvedInterface = schema.getType("Node") as GraphQLInterfaceType
        resolvedInterface.getFieldDefinition("owner").getType() == userType
    }

    def "interface extending interface via type reference resolves correctly"() {
        given: "a base interface"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "an interface extending Node via type reference"
        def namedNodeInterface = GraphQLInterfaceType.newInterface()
                .name("NamedNode")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .withInterface(typeRef("Node"))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry with type resolvers"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("NamedNode", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .addType(namedNodeInterface)
                .build()

        then: "interface extension is resolved"
        def resolvedNamedNode = schema.getType("NamedNode") as GraphQLInterfaceType
        resolvedNamedNode.getInterfaces().size() == 1
        resolvedNamedNode.getInterfaces()[0] == nodeInterface
    }

    def "interface type resolver from interface is wired to code registry"() {
        given: "an interface type with inline type resolver"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .typeResolver({ env -> null })
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry (no type resolver)"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .build()

        then: "type resolver is wired"
        def resolvedInterface = schema.getType("Node") as GraphQLInterfaceType
        schema.codeRegistry.getTypeResolver(resolvedInterface) != null
    }

    def "interface field argument with type reference resolves correctly"() {
        given: "an input type"
        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("active")
                        .type(Scalars.GraphQLBoolean))
                .build()

        and: "an interface type with field having argument with type reference"
        def searchableInterface = GraphQLInterfaceType.newInterface()
                .name("Searchable")
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("filter")
                                .type(typeRef("FilterInput")))
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("searchable")
                        .type(searchableInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Searchable", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(searchableInterface)
                .addType(filterInput)
                .build()

        then: "interface field argument type is resolved"
        def resolvedInterface = schema.getType("Searchable") as GraphQLInterfaceType
        resolvedInterface.getFieldDefinition("search").getArgument("filter").getType() == filterInput
    }

    def "interface with missing extended interface type reference throws error"() {
        given: "an interface with missing extended interface reference"
        def childInterface = GraphQLInterfaceType.newInterface()
                .name("ChildInterface")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .withInterface(typeRef("NonExistentInterface"))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("child")
                        .type(childInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("ChildInterface", { env -> null })

        when: "building"
        new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(childInterface)
                .build()

        then: "error for missing interface"
        thrown(AssertException)
    }

    def "interface field with applied directive type reference resolves correctly"() {
        given: "a custom scalar"
        def metaScalar = newScalar()
                .name("InterfaceMetadata")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("interfaceMeta")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("info")
                        .type(metaScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("interfaceMeta")
                .argument(newAppliedArgument()
                        .name("info")
                        .type(typeRef("InterfaceMetadata"))
                        .valueProgrammatic("metadata"))
                .build()

        and: "an interface type with field having applied directive"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString)
                        .withAppliedDirective(appliedDirective))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(metaScalar)
                .addType(nodeInterface)
                .additionalDirective(directive)
                .build()

        then: "applied directive argument type on interface field is resolved"
        def resolvedInterface = schema.getType("Node") as GraphQLInterfaceType
        def field = resolvedInterface.getFieldDefinition("id")
        def resolvedApplied = field.getAppliedDirective("interfaceMeta")
        resolvedApplied.getArgument("info").getType() == metaScalar
    }

    def "union type can be added to schema"() {
        given: "possible types for union"
        def catType = newObject()
                .name("Cat")
                .field(newFieldDefinition()
                        .name("meow")
                        .type(GraphQLString))
                .build()

        def dogType = newObject()
                .name("Dog")
                .field(newFieldDefinition()
                        .name("bark")
                        .type(GraphQLString))
                .build()

        and: "a union with concrete types"
        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(catType)
                .possibleType(dogType)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Pet", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(catType)
                .addType(dogType)
                .addType(petUnion)
                .build()

        then: "union type is in schema"
        def resolvedUnion = schema.getType("Pet")
        resolvedUnion instanceof GraphQLUnionType
        (resolvedUnion as GraphQLUnionType).types.size() == 2
    }

    def "union type with type reference members resolves correctly"() {
        given: "possible types for union"
        def catType = newObject()
                .name("Cat")
                .field(newFieldDefinition()
                        .name("meow")
                        .type(GraphQLString))
                .build()

        def dogType = newObject()
                .name("Dog")
                .field(newFieldDefinition()
                        .name("bark")
                        .type(GraphQLString))
                .build()

        and: "a union with type references"
        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(typeRef("Cat"))
                .possibleType(typeRef("Dog"))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Pet", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(catType)
                .addType(dogType)
                .addType(petUnion)
                .build()

        then: "union member types are resolved"
        def resolvedPet = schema.getType("Pet") as GraphQLUnionType
        resolvedPet.types.collect { it.name }.toSet() == ["Cat", "Dog"].toSet()
        resolvedPet.types[0] in [catType, dogType]
        resolvedPet.types[1] in [catType, dogType]
    }

    def "union type resolver from union is wired to code registry"() {
        given: "possible types for union"
        def catType = newObject()
                .name("Cat")
                .field(newFieldDefinition()
                        .name("meow")
                        .type(GraphQLString))
                .build()

        and: "a union with inline type resolver"
        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(catType)
                .typeResolver({ env -> null })
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        and: "code registry (no type resolver)"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(catType)
                .addType(petUnion)
                .build()

        then: "type resolver is wired"
        def resolvedUnion = schema.getType("Pet") as GraphQLUnionType
        schema.codeRegistry.getTypeResolver(resolvedUnion) != null
    }

    def "union without type resolver throws error"() {
        given: "a union without type resolver"
        def catType = newObject()
                .name("Cat")
                .field(newFieldDefinition()
                        .name("meow")
                        .type(GraphQLString))
                .build()

        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(catType)
                // No type resolver!
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        when: "building without type resolver"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(catType)
                .addType(petUnion)
                .build()

        then: "error is thrown"
        def e = thrown(AssertException)
        e.message.contains("MUST provide a type resolver")
        e.message.contains("Pet")
    }

    def "union with missing member type reference throws error"() {
        given: "a union with missing type reference"
        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(typeRef("NonExistentType"))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Pet", { env -> null })

        when: "building"
        new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(petUnion)
                .build()

        then: "error for missing type"
        thrown(AssertException)
    }

    def "union with applied directive type reference resolves correctly"() {
        given: "a custom scalar"
        def metaScalar = newScalar()
                .name("UnionMetadata")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a directive definition"
        def directive = newDirective()
                .name("unionMeta")
                .validLocation(Introspection.DirectiveLocation.UNION)
                .argument(newArgument()
                        .name("info")
                        .type(metaScalar))
                .build()

        and: "an applied directive with type reference"
        def appliedDirective = newAppliedDirective()
                .name("unionMeta")
                .argument(newAppliedArgument()
                        .name("info")
                        .type(typeRef("UnionMetadata"))
                        .valueProgrammatic("metadata"))
                .build()

        and: "possible type"
        def catType = newObject()
                .name("Cat")
                .field(newFieldDefinition()
                        .name("meow")
                        .type(GraphQLString))
                .build()

        and: "a union with applied directive"
        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(catType)
                .withAppliedDirective(appliedDirective)
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Pet", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(metaScalar)
                .addType(catType)
                .addType(petUnion)
                .additionalDirective(directive)
                .build()

        then: "applied directive argument type on union is resolved"
        def resolvedUnion = schema.getType("Pet") as GraphQLUnionType
        def resolvedApplied = resolvedUnion.getAppliedDirective("unionMeta")
        resolvedApplied.getArgument("info").getType() == metaScalar
    }

    def "interface without type resolver throws error"() {
        given: "an interface without type resolver"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                // No type resolver!
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        when: "building without type resolver"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(nodeInterface)
                .build()

        then: "error is thrown"
        def e = thrown(AssertException)
        e.message.contains("MUST provide a type resolver")
        e.message.contains("Node")
    }

    def "withValidation(false) still requires type resolvers"() {
        given: "an interface without type resolver"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        when: "building with validation disabled but no type resolver"
        new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(nodeInterface)
                .withValidation(false)
                .build()

        then: "error is still thrown for missing type resolver"
        def e = thrown(AssertException)
        e.message.contains("MUST provide a type resolver")
    }

    def "withValidation(true) rejects schema with incomplete interface implementation"() {
        given: "an interface with id field"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        and: "an object claiming to implement interface but missing id field"
        def badImplementor = newObject()
                .name("BadImplementor")
                .withInterface(nodeInterface)
                // Missing id field!
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        and: "code registry with type resolver"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with validation enabled"
        new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .addType(badImplementor)
                .withValidation(true)
                .build()

        then: "validation error is thrown"
        thrown(graphql.schema.validation.InvalidSchemaException)
    }

    def "circular type reference resolves correctly"() {
        given: "types with circular reference"
        def personType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("friend")
                        .type(typeRef("Person")))  // Self-reference
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("person")
                        .type(personType))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(personType)
                .build()

        then: "circular reference is resolved"
        def resolvedPerson = schema.getType("Person") as GraphQLObjectType
        resolvedPerson.getFieldDefinition("friend").getType() == personType
    }

    def "deeply nested type reference resolves correctly"() {
        given: "deeply nested types"
        def innerType = newObject()
                .name("Inner")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "type with deeply nested reference"
        def outerType = newObject()
                .name("Outer")
                .field(newFieldDefinition()
                        .name("inner")
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(typeRef("Inner"))))))
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("outer")
                        .type(outerType))
                .build()

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .addType(innerType)
                .addType(outerType)
                .build()

        then: "deeply nested type reference is resolved"
        def resolvedOuter = schema.getType("Outer") as GraphQLObjectType
        def fieldType = resolvedOuter.getFieldDefinition("inner").getType()
        fieldType instanceof GraphQLNonNull
        def listType = ((GraphQLNonNull) fieldType).getWrappedType()
        listType instanceof GraphQLList
        def itemType = ((GraphQLList) listType).getWrappedType()
        itemType instanceof GraphQLNonNull
        ((GraphQLNonNull) itemType).getWrappedType() == innerType
    }

    def "complex schema builds correctly"() {
        given: "a complex set of types"
        // Interface
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        // Input type
        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("active")
                        .type(Scalars.GraphQLBoolean))
                .build()

        // Object implementing interface
        def userType = newObject()
                .name("User")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        // Another object implementing interface
        def postType = newObject()
                .name("Post")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("author")
                        .type(typeRef("User")))
                .build()

        // Union
        def searchResultUnion = GraphQLUnionType.newUnionType()
                .name("SearchResult")
                .possibleType(typeRef("User"))
                .possibleType(typeRef("Post"))
                .build()

        // Query type
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .argument(newArgument()
                                .name("id")
                                .type(GraphQLString))
                        .type(nodeInterface))
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("filter")
                                .type(typeRef("FilterInput")))
                        .type(GraphQLList.list(typeRef("SearchResult"))))
                .build()

        // Code registry with type resolvers
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("SearchResult", { env -> null })

        when: "building with FastBuilder"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .addType(nodeInterface)
                .addType(filterInput)
                .addType(userType)
                .addType(postType)
                .addType(searchResultUnion)
                .build()

        then: "all types are resolved correctly"
        schema.getType("Node") instanceof GraphQLInterfaceType
        schema.getType("FilterInput") instanceof GraphQLInputObjectType
        schema.getType("User") instanceof GraphQLObjectType
        schema.getType("Post") instanceof GraphQLObjectType
        schema.getType("SearchResult") instanceof GraphQLUnionType

        and: "interface implementations are tracked"
        schema.getImplementations(nodeInterface as GraphQLInterfaceType).size() == 2
        schema.getImplementations(nodeInterface as GraphQLInterfaceType).any { it.name == "User" }
        schema.getImplementations(nodeInterface as GraphQLInterfaceType).any { it.name == "Post" }

        and: "type references are resolved"
        def resolvedUser = schema.getType("User") as GraphQLObjectType
        resolvedUser.getInterfaces()[0] == nodeInterface

        def resolvedPost = schema.getType("Post") as GraphQLObjectType
        resolvedPost.getFieldDefinition("author").getType() == userType

        def resolvedUnion = schema.getType("SearchResult") as GraphQLUnionType
        resolvedUnion.types.any { it.name == "User" }
        resolvedUnion.types.any { it.name == "Post" }

        def searchField = schema.queryType.getFieldDefinition("search")
        searchField.getArgument("filter").getType() == filterInput
    }
}

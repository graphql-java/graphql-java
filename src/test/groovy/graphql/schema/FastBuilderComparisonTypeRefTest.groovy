package graphql.schema

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

/**
 * Comparison tests for Type Reference Resolution in FastBuilder.
 *
 * These tests verify that FastBuilder correctly resolves GraphQLTypeReference instances
 * to their concrete types, matching the behavior of SDL-based schema construction.
 *
 * Pattern: For each test, we define SDL, build schema via SDL parsing, then create
 * equivalent types programmatically using typeRef(), build with FastBuilder, and
 * verify the schemas are equivalent AND that specific type references are resolved.
 */
class FastBuilderComparisonTypeRefTest extends FastBuilderComparisonTest {

    def "object type with type reference field resolves correctly"() {
        given: "SDL with object type referencing another object"
        def sdl = """
            type Query {
                person: Person
            }

            type Person {
                name: String
            }
        """

        and: "programmatically created types with type reference"
        def personType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("person")
                        .type(typeRef("Person")))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [personType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "type reference is resolved in FastBuilder schema"
        def queryField = fastSchema.queryType.getFieldDefinition("person")
        queryField.getType() == personType
    }

    def "object type with NonNull wrapped type reference resolves correctly"() {
        given: "SDL with NonNull object reference"
        def sdl = """
            type Query {
                item: Item!
            }

            type Item {
                id: String
            }
        """

        and: "programmatically created types with NonNull type reference"
        def itemType = newObject()
                .name("Item")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("item")
                        .type(GraphQLNonNull.nonNull(typeRef("Item"))))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [itemType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "type reference is resolved with NonNull wrapper"
        def queryField = fastSchema.queryType.getFieldDefinition("item")
        def fieldType = queryField.getType()
        fieldType instanceof GraphQLNonNull
        ((GraphQLNonNull) fieldType).getWrappedType() == itemType
    }

    def "object type with List wrapped type reference resolves correctly"() {
        given: "SDL with List of objects"
        def sdl = """
            type Query {
                users: [User]
            }

            type User {
                name: String
            }
        """

        and: "programmatically created types with List type reference"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("users")
                        .type(GraphQLList.list(typeRef("User"))))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [userType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "type reference is resolved with List wrapper"
        def queryField = fastSchema.queryType.getFieldDefinition("users")
        def fieldType = queryField.getType()
        fieldType instanceof GraphQLList
        ((GraphQLList) fieldType).getWrappedType() == userType
    }

    def "object type field argument with type reference resolves correctly"() {
        given: "SDL with field argument referencing input type"
        def sdl = """
            type Query {
                search(filter: FilterInput): Result
            }

            input FilterInput {
                status: String
            }

            type Result {
                value: String
            }
        """

        and: "programmatically created types with type reference in argument"
        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("status")
                        .type(GraphQLString))
                .build()

        def resultType = newObject()
                .name("Result")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("filter")
                                .type(typeRef("FilterInput")))
                        .type(resultType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [filterInput, resultType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "argument type reference is resolved"
        def searchField = fastSchema.queryType.getFieldDefinition("search")
        searchField.getArgument("filter").getType() == filterInput
    }

    def "interface type with type reference field resolves correctly"() {
        given: "SDL with interface referencing object type"
        def sdl = """
            type Query {
                node: Node
            }

            interface Node {
                owner: User
            }

            type User {
                name: String
            }
        """

        and: "programmatically created types with type reference in interface"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("owner")
                        .type(typeRef("User")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [nodeInterface, userType], [], codeRegistry)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "interface field type reference is resolved"
        def resolvedInterface = fastSchema.getType("Node") as GraphQLInterfaceType
        resolvedInterface.getFieldDefinition("owner").getType() == userType
    }

    def "interface field argument with type reference resolves correctly"() {
        given: "SDL with interface field argument referencing input type"
        def sdl = """
            type Query {
                searchable: Searchable
            }

            interface Searchable {
                search(filter: FilterInput): String
            }

            input FilterInput {
                active: Boolean
            }
        """

        and: "programmatically created types with type reference in interface argument"
        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("active")
                        .type(Scalars.GraphQLBoolean))
                .build()

        def searchableInterface = GraphQLInterfaceType.newInterface()
                .name("Searchable")
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("filter")
                                .type(typeRef("FilterInput")))
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("searchable")
                        .type(searchableInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Searchable", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [searchableInterface, filterInput], [], codeRegistry)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "interface field argument type reference is resolved"
        def resolvedInterface = fastSchema.getType("Searchable") as GraphQLInterfaceType
        resolvedInterface.getFieldDefinition("search").getArgument("filter").getType() == filterInput
    }

    def "union type with type reference members resolves correctly"() {
        given: "SDL with union of object types"
        def sdl = """
            type Query {
                pet: Pet
            }

            union Pet = Cat | Dog

            type Cat {
                meow: String
            }

            type Dog {
                bark: String
            }
        """

        and: "programmatically created types with type references in union"
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

        def petUnion = GraphQLUnionType.newUnionType()
                .name("Pet")
                .possibleType(typeRef("Cat"))
                .possibleType(typeRef("Dog"))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("pet")
                        .type(petUnion))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Pet", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [catType, dogType, petUnion], [], codeRegistry)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "union member type references are resolved"
        def resolvedPet = fastSchema.getType("Pet") as GraphQLUnionType
        resolvedPet.types.collect { it.name }.toSet() == ["Cat", "Dog"].toSet()
        resolvedPet.types[0] in [catType, dogType]
        resolvedPet.types[1] in [catType, dogType]
    }

    def "input object with type reference field resolves correctly"() {
        given: "SDL with input object referencing custom scalar"
        def sdl = """
            type Query {
                createEvent(input: EventInput): String
            }

            input EventInput {
                name: String
                startDate: DateTime
            }

            scalar DateTime
        """

        and: "programmatically created types with type reference in input"
        def dateTimeScalar = newScalar()
                .name("DateTime")
                .coercing(GraphQLString.getCoercing())
                .build()

        def eventInput = newInputObject()
                .name("EventInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("startDate")
                        .type(typeRef("DateTime")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createEvent")
                        .argument(newArgument()
                                .name("input")
                                .type(eventInput))
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [dateTimeScalar, eventInput])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "input field type reference is resolved"
        def resolvedInput = fastSchema.getType("EventInput") as GraphQLInputObjectType
        resolvedInput.getField("startDate").getType() == dateTimeScalar
    }

    def "input object with nested input object type reference resolves correctly"() {
        given: "SDL with nested input objects"
        def sdl = """
            type Query {
                createUser(input: UserInput): String
            }

            input UserInput {
                name: String
                address: AddressInput
            }

            input AddressInput {
                street: String
                city: String
            }
        """

        and: "programmatically created types with nested type reference"
        def addressInput = newInputObject()
                .name("AddressInput")
                .field(newInputObjectField()
                        .name("street")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("city")
                        .type(GraphQLString))
                .build()

        def userInput = newInputObject()
                .name("UserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .field(newInputObjectField()
                        .name("address")
                        .type(typeRef("AddressInput")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("createUser")
                        .argument(newArgument()
                                .name("input")
                                .type(userInput))
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [addressInput, userInput])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "nested input field type reference is resolved"
        def resolvedUser = fastSchema.getType("UserInput") as GraphQLInputObjectType
        resolvedUser.getField("address").getType() == addressInput
    }

    def "directive argument with type reference resolves correctly"() {
        given: "SDL with directive referencing custom scalar"
        def sdl = """
            type Query {
                value: String
            }

            directive @bar(arg: Foo) on FIELD

            scalar Foo
        """

        and: "programmatically created types with type reference in directive"
        def customScalar = newScalar()
                .name("Foo")
                .coercing(GraphQLString.getCoercing())
                .build()

        def directive = newDirective()
                .name("bar")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("arg")
                        .type(typeRef("Foo")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [customScalar], [directive])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "directive argument type reference is resolved"
        def resolvedDirective = fastSchema.getDirective("bar")
        resolvedDirective != null
        resolvedDirective.getArgument("arg").getType() == customScalar
    }

    def "directive argument with enum type reference resolves correctly"() {
        given: "SDL with directive referencing enum"
        def sdl = """
            type Query {
                value: String
            }

            directive @log(level: LogLevel) on FIELD

            enum LogLevel {
                DEBUG
                INFO
                WARN
                ERROR
            }
        """

        and: "programmatically created types with enum type reference in directive"
        def levelEnum = newEnum()
                .name("LogLevel")
                .value("DEBUG")
                .value("INFO")
                .value("WARN")
                .value("ERROR")
                .build()

        def directive = newDirective()
                .name("log")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("level")
                        .type(typeRef("LogLevel")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [levelEnum], [directive])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "directive argument enum type reference is resolved"
        def resolvedDirective = fastSchema.getDirective("log")
        resolvedDirective.getArgument("level").getType() == levelEnum
    }

    def "directive argument with input object type reference resolves correctly"() {
        given: "SDL with directive referencing input type"
        def sdl = """
            type Query {
                value: String
            }

            directive @config(settings: ConfigInput) on FIELD

            input ConfigInput {
                enabled: Boolean
            }
        """

        and: "programmatically created types with input type reference in directive"
        def configInput = newInputObject()
                .name("ConfigInput")
                .field(newInputObjectField()
                        .name("enabled")
                        .type(Scalars.GraphQLBoolean))
                .build()

        def directive = newDirective()
                .name("config")
                .validLocation(Introspection.DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("settings")
                        .type(typeRef("ConfigInput")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [configInput], [directive])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "directive argument input type reference is resolved"
        def resolvedDirective = fastSchema.getDirective("config")
        resolvedDirective.getArgument("settings").getType() == configInput
    }

    def "schema applied directive with type reference argument resolves correctly"() {
        given: "SDL with schema-level applied directive"
        def sdl = """
            directive @config(value: String) on SCHEMA

            type Query {
                value: String
            }

            schema @config(value: "test") {
                query: Query
            }
        """

        and: "programmatically created types with type reference in applied directive"
        def directive = newDirective()
                .name("config")
                .validLocation(Introspection.DirectiveLocation.SCHEMA)
                .argument(newArgument()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def appliedDirective = newAppliedDirective()
                .name("config")
                .argument(newAppliedArgument()
                        .name("value")
                        .type(GraphQLString)
                        .valueProgrammatic("test"))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def builder = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .additionalDirective(directive)
                .withSchemaAppliedDirective(appliedDirective)

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = builder.build()

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "applied directive argument type is String"
        def resolved = fastSchema.getSchemaAppliedDirective("config")
        resolved.getArgument("value").getType() == GraphQLString
    }

    def "type applied directive with type reference argument resolves correctly"() {
        given: "SDL with enum having applied directive"
        def sdl = """
            directive @myDir(arg: String) on ENUM

            type Query {
                status: Status
            }

            enum Status @myDir(arg: "value") {
                ACTIVE
            }
        """

        and: "programmatically created types with type reference in applied directive"
        def directive = newDirective()
                .name("myDir")
                .validLocation(Introspection.DirectiveLocation.ENUM)
                .argument(newArgument()
                        .name("arg")
                        .type(GraphQLString))
                .build()

        def appliedDirective = newAppliedDirective()
                .name("myDir")
                .argument(newAppliedArgument()
                        .name("arg")
                        .type(GraphQLString)
                        .valueProgrammatic("value"))
                .build()

        def enumType = newEnum()
                .name("Status")
                .value("ACTIVE")
                .withAppliedDirective(appliedDirective)
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(enumType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [enumType], [directive])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "applied directive on type has resolved type"
        def resolvedEnum = fastSchema.getType("Status") as GraphQLEnumType
        def resolvedApplied = resolvedEnum.getAppliedDirective("myDir")
        resolvedApplied.getArgument("arg").getType() == GraphQLString
    }

    def "field applied directive with type reference argument resolves correctly"() {
        given: "SDL with field having applied directive"
        def sdl = """
            directive @fieldMeta(info: String) on FIELD_DEFINITION

            type Query {
                value: String @fieldMeta(info: "metadata")
            }
        """

        and: "programmatically created types with type reference in field applied directive"
        def directive = newDirective()
                .name("fieldMeta")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("info")
                        .type(GraphQLString))
                .build()

        def appliedDirective = newAppliedDirective()
                .name("fieldMeta")
                .argument(newAppliedArgument()
                        .name("info")
                        .type(GraphQLString)
                        .valueProgrammatic("metadata"))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString)
                        .withAppliedDirective(appliedDirective))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [], [directive])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "applied directive on field has resolved type"
        def field = fastSchema.queryType.getFieldDefinition("value")
        def resolvedApplied = field.getAppliedDirective("fieldMeta")
        resolvedApplied.getArgument("info").getType() == GraphQLString
    }

    def "nested type references with NonNull and List resolve correctly"() {
        given: "SDL with deeply nested type wrappers"
        def sdl = """
            type Query {
                outer: Outer
            }

            type Outer {
                inner: [Inner!]!
            }

            type Inner {
                value: String
            }
        """

        and: "programmatically created types with deeply nested type reference"
        def innerType = newObject()
                .name("Inner")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def outerType = newObject()
                .name("Outer")
                .field(newFieldDefinition()
                        .name("inner")
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(typeRef("Inner"))))))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("outer")
                        .type(outerType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [innerType, outerType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "deeply nested type reference is resolved"
        def resolvedOuter = fastSchema.getType("Outer") as GraphQLObjectType
        def fieldType = resolvedOuter.getFieldDefinition("inner").getType()
        fieldType instanceof GraphQLNonNull
        def listType = ((GraphQLNonNull) fieldType).getWrappedType()
        listType instanceof GraphQLList
        def itemType = ((GraphQLList) listType).getWrappedType()
        itemType instanceof GraphQLNonNull
        ((GraphQLNonNull) itemType).getWrappedType() == innerType
    }

    def "circular type reference resolves correctly"() {
        given: "SDL with self-referencing type"
        def sdl = """
            type Query {
                person: Person
            }

            type Person {
                name: String
                friend: Person
            }
        """

        and: "programmatically created types with circular reference"
        def personType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("friend")
                        .type(typeRef("Person")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("person")
                        .type(personType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [personType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "circular reference is resolved"
        def resolvedPerson = fastSchema.getType("Person") as GraphQLObjectType
        resolvedPerson.getFieldDefinition("friend").getType() == personType
    }

    def "complex schema with multiple type references resolves correctly"() {
        given: "SDL with many interconnected types"
        def sdl = """
            type Query {
                node(id: String): Node
                search(filter: FilterInput): [SearchResult]
            }

            interface Node {
                id: String
            }

            input FilterInput {
                active: Boolean
            }

            type User implements Node {
                id: String
                name: String
            }

            type Post implements Node {
                id: String
                title: String
                author: User
            }

            union SearchResult = User | Post
        """

        and: "programmatically created types with multiple type references"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def filterInput = newInputObject()
                .name("FilterInput")
                .field(newInputObjectField()
                        .name("active")
                        .type(Scalars.GraphQLBoolean))
                .build()

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

        def searchResultUnion = GraphQLUnionType.newUnionType()
                .name("SearchResult")
                .possibleType(typeRef("User"))
                .possibleType(typeRef("Post"))
                .build()

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

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("SearchResult", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType, null, null,
                [nodeInterface, filterInput, userType, postType, searchResultUnion],
                [], codeRegistry)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "all type references are resolved"
        // Interface implementations
        def resolvedUser = fastSchema.getType("User") as GraphQLObjectType
        resolvedUser.getInterfaces()[0] == nodeInterface

        // Object field references
        def resolvedPost = fastSchema.getType("Post") as GraphQLObjectType
        resolvedPost.getFieldDefinition("author").getType() == userType

        // Union members
        def resolvedUnion = fastSchema.getType("SearchResult") as GraphQLUnionType
        resolvedUnion.types.any { it.name == "User" }
        resolvedUnion.types.any { it.name == "Post" }

        // Field arguments
        def searchField = fastSchema.queryType.getFieldDefinition("search")
        searchField.getArgument("filter").getType() == filterInput
        def searchReturnType = searchField.getType() as GraphQLList
        searchReturnType.getWrappedType() == searchResultUnion
    }
}

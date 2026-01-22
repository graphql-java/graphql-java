package graphql.schema

import graphql.Scalars
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLTypeReference.typeRef
import static graphql.schema.GraphQLUnionType.newUnionType

/**
 * Comparison tests for Complex Schemas and Directives.
 *
 * Tests that FastBuilder produces schemas equivalent to SDL-parsed schemas
 * for complex type compositions and directive handling.
 */
class FastBuilderComparisonComplexTest extends FastBuilderComparisonTest {

    def "schema with all GraphQL type kinds matches between FastBuilder and standard builder"() {
        given: "SDL with all type kinds"
        def sdl = """
            type Query {
                user: User
                search(input: SearchInput): SearchResult
                status: Status
                timestamp: DateTime
            }

            type Mutation {
                updateUser(input: UserInput): User
            }

            type Subscription {
                userUpdated: User
            }

            interface Node {
                id: ID!
            }

            type User implements Node {
                id: ID!
                name: String!
                posts: [Post!]!
            }

            type Post implements Node {
                id: ID!
                title: String!
                author: User!
            }

            union SearchResult = User | Post

            enum Status {
                ACTIVE
                INACTIVE
                PENDING
            }

            scalar DateTime

            input UserInput {
                name: String!
                status: Status
            }

            input SearchInput {
                query: String!
                limit: Int
            }
        """

        and: "programmatically created types"
        // Custom scalar
        def dateTimeScalar = newScalar()
                .name("DateTime")
                .coercing(GraphQLString.getCoercing())
                .build()

        // Enum
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .value("PENDING")
                .build()

        // Interface
        def nodeInterface = newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .build()

        // Input types
        def userInput = newInputObject()
                .name("UserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newInputObjectField()
                        .name("status")
                        .type(typeRef("Status")))
                .build()

        def searchInput = newInputObject()
                .name("SearchInput")
                .field(newInputObjectField()
                        .name("query")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newInputObjectField()
                        .name("limit")
                        .type(GraphQLInt))
                .build()

        // Object types
        def userType = newObject()
                .name("User")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("posts")
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(typeRef("Post"))))))
                .build()

        def postType = newObject()
                .name("Post")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("author")
                        .type(GraphQLNonNull.nonNull(typeRef("User"))))
                .build()

        // Union
        def searchResultUnion = newUnionType()
                .name("SearchResult")
                .possibleType(typeRef("User"))
                .possibleType(typeRef("Post"))
                .build()

        // Root types
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(typeRef("User")))
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("input")
                                .type(typeRef("SearchInput")))
                        .type(typeRef("SearchResult")))
                .field(newFieldDefinition()
                        .name("status")
                        .type(typeRef("Status")))
                .field(newFieldDefinition()
                        .name("timestamp")
                        .type(typeRef("DateTime")))
                .build()

        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("updateUser")
                        .argument(newArgument()
                                .name("input")
                                .type(typeRef("UserInput")))
                        .type(typeRef("User")))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("userUpdated")
                        .type(typeRef("User")))
                .build()

        and: "code registry with type resolvers"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("SearchResult", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                mutationType,
                subscriptionType,
                [dateTimeScalar, statusEnum, nodeInterface, userInput, searchInput, userType, postType, searchResultUnion],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with circular type references matches between FastBuilder and standard builder"() {
        given: "SDL with circular references"
        def sdl = """
            type Query {
                person: Person
            }

            type Person {
                name: String!
                friends: [Person!]!
                bestFriend: Person
            }
        """

        and: "programmatically created types with circular references"
        def personType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("friends")
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(typeRef("Person"))))))
                .field(newFieldDefinition()
                        .name("bestFriend")
                        .type(typeRef("Person")))
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
    }

    def "schema with deeply nested types matches between FastBuilder and standard builder"() {
        given: "SDL with deeply nested types"
        def sdl = """
            type Query {
                data: Level1
            }

            type Level1 {
                value: String!
                nested: Level2
            }

            type Level2 {
                value: String!
                nested: Level3
            }

            type Level3 {
                value: String!
                nested: Level4
            }

            type Level4 {
                value: String!
                items: [Level1!]!
            }
        """

        and: "programmatically created nested types"
        def level4Type = newObject()
                .name("Level4")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("items")
                        .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(typeRef("Level1"))))))
                .build()

        def level3Type = newObject()
                .name("Level3")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nested")
                        .type(typeRef("Level4")))
                .build()

        def level2Type = newObject()
                .name("Level2")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nested")
                        .type(typeRef("Level3")))
                .build()

        def level1Type = newObject()
                .name("Level1")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nested")
                        .type(typeRef("Level2")))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("data")
                        .type(typeRef("Level1")))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [level1Type, level2Type, level3Type, level4Type]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with mutation and subscription types matches between FastBuilder and standard builder"() {
        given: "SDL with all root types"
        def sdl = """
            type Query {
                getValue: String
            }

            type Mutation {
                setValue(value: String!): String
                deleteValue: Boolean
            }

            type Subscription {
                valueChanged: String
                valueDeleted: Boolean
            }
        """

        and: "programmatically created root types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("getValue")
                        .type(GraphQLString))
                .build()

        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("setValue")
                        .argument(newArgument()
                                .name("value")
                                .type(GraphQLNonNull.nonNull(GraphQLString)))
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("deleteValue")
                        .type(GraphQLBoolean))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("valueChanged")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("valueDeleted")
                        .type(GraphQLBoolean))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, mutationType, subscriptionType)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with custom directives matches between FastBuilder and standard builder"() {
        given: "SDL with custom directives"
        def sdl = """
            directive @auth(requires: Role = ADMIN) on FIELD_DEFINITION
            directive @cache(ttl: Int!) on FIELD_DEFINITION
            directive @deprecated(reason: String = "No longer supported") on FIELD_DEFINITION

            enum Role {
                ADMIN
                USER
                GUEST
            }

            type Query {
                publicField: String
                protectedField: String @auth(requires: ADMIN)
                cachedField: String @cache(ttl: 300)
            }
        """

        and: "programmatically created directives and types"
        def roleEnum = newEnum()
                .name("Role")
                .value("ADMIN")
                .value("USER")
                .value("GUEST")
                .build()

        def authDirective = newDirective()
                .name("auth")
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("requires")
                        .type(typeRef("Role"))
                        .defaultValueProgrammatic("ADMIN"))
                .build()

        def cacheDirective = newDirective()
                .name("cache")
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("ttl")
                        .type(GraphQLNonNull.nonNull(GraphQLInt)))
                .build()

        def deprecatedDirective = newDirective()
                .name("deprecated")
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("reason")
                        .type(GraphQLString)
                        .defaultValueProgrammatic("No longer supported"))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("publicField")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("protectedField")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("cachedField")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [roleEnum],
                [authDirective, cacheDirective, deprecatedDirective]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "custom directive definitions match"
        def fastAuth = fastSchema.getDirective("auth")
        def standardAuth = standardSchema.getDirective("auth")
        fastAuth != null
        standardAuth != null
        fastAuth.name == standardAuth.name
        fastAuth.validLocations() == standardAuth.validLocations()
        fastAuth.getArgument("requires") != null
        standardAuth.getArgument("requires") != null

        def fastCache = fastSchema.getDirective("cache")
        def standardCache = standardSchema.getDirective("cache")
        fastCache != null
        standardCache != null
        fastCache.name == standardCache.name
        fastCache.getArgument("ttl") != null
        standardCache.getArgument("ttl") != null
    }

    def "schema with applied directives on types matches between FastBuilder and standard builder"() {
        given: "SDL with applied directives on types"
        def sdl = """
            directive @entity(tableName: String!) on OBJECT
            directive @deprecated(reason: String) on ENUM | ENUM_VALUE

            type Query {
                user: User
                status: Status
            }

            type User @entity(tableName: "users") {
                id: ID!
                name: String!
            }

            enum Status @deprecated(reason: "Use StatusV2") {
                ACTIVE @deprecated(reason: "Use ENABLED")
                INACTIVE
            }
        """

        and: "programmatically created directives and types"
        def entityDirective = newDirective()
                .name("entity")
                .validLocation(DirectiveLocation.OBJECT)
                .argument(newArgument()
                        .name("tableName")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def deprecatedDirective = newDirective()
                .name("deprecated")
                .validLocations(DirectiveLocation.ENUM, DirectiveLocation.ENUM_VALUE)
                .argument(newArgument()
                        .name("reason")
                        .type(GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(typeRef("User")))
                .field(newFieldDefinition()
                        .name("status")
                        .type(typeRef("Status")))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [userType, statusEnum],
                [entityDirective, deprecatedDirective]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema-level applied directives match between FastBuilder and standard builder"() {
        given: "SDL with schema-level directives"
        def sdl = """
            schema @link(url: "https://example.com/spec") {
                query: Query
            }

            directive @link(url: String!) on SCHEMA

            type Query {
                value: String
            }
        """

        and: "programmatically created directive and schema"
        def linkDirective = newDirective()
                .name("link")
                .validLocation(DirectiveLocation.SCHEMA)
                .argument(newArgument()
                        .name("url")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [],
                [linkDirective]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "built-in directives are present in both FastBuilder and standard builder schemas"() {
        given: "minimal SDL"
        def sdl = """
            type Query {
                value: String
            }
        """

        and: "programmatically created query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "all built-in directives are present in both"
        def builtInDirectives = ["skip", "include", "deprecated", "specifiedBy"]
        builtInDirectives.each { directiveName ->
            assert fastSchema.getDirective(directiveName) != null,
                    "FastBuilder schema missing built-in directive: ${directiveName}"
            assert standardSchema.getDirective(directiveName) != null,
                    "Standard schema missing built-in directive: ${directiveName}"
        }
    }

    def "schema with directives on multiple locations matches between FastBuilder and standard builder"() {
        given: "SDL with directives on various locations"
        def sdl = """
            directive @meta(info: String!) on OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                search(
                    query: String! @meta(info: "Search query")
                    limit: Int @meta(info: "Result limit")
                ): SearchResult @meta(info: "Search results")
            }

            type User @meta(info: "User type") {
                name: String! @meta(info: "User name")
            }

            type Post @meta(info: "Post type") {
                title: String! @meta(info: "Post title")
            }

            union SearchResult @meta(info: "Search result union") = User | Post

            interface Node @meta(info: "Node interface") {
                id: ID! @meta(info: "Node ID")
            }

            enum Status @meta(info: "Status enum") {
                ACTIVE
                INACTIVE
            }

            input UserInput @meta(info: "User input") {
                name: String! @meta(info: "Input name")
            }
        """

        and: "programmatically created directive and types"
        def metaDirective = newDirective()
                .name("meta")
                .validLocations(
                        DirectiveLocation.OBJECT,
                        DirectiveLocation.FIELD_DEFINITION,
                        DirectiveLocation.ARGUMENT_DEFINITION,
                        DirectiveLocation.INTERFACE,
                        DirectiveLocation.UNION,
                        DirectiveLocation.ENUM,
                        DirectiveLocation.INPUT_OBJECT,
                        DirectiveLocation.INPUT_FIELD_DEFINITION
                )
                .argument(newArgument()
                        .name("info")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def nodeInterface = newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .build()

        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def postType = newObject()
                .name("Post")
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def searchResultUnion = newUnionType()
                .name("SearchResult")
                .possibleType(typeRef("User"))
                .possibleType(typeRef("Post"))
                .build()

        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        def userInput = newInputObject()
                .name("UserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("search")
                        .argument(newArgument()
                                .name("query")
                                .type(GraphQLNonNull.nonNull(GraphQLString)))
                        .argument(newArgument()
                                .name("limit")
                                .type(GraphQLInt))
                        .type(typeRef("SearchResult")))
                .build()

        and: "code registry with type resolvers"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("SearchResult", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [nodeInterface, userType, postType, searchResultUnion, statusEnum, userInput],
                [metaDirective],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "meta directive definition matches"
        def fastMeta = fastSchema.getDirective("meta")
        def standardMeta = standardSchema.getDirective("meta")
        fastMeta != null
        standardMeta != null
        fastMeta.name == standardMeta.name
        fastMeta.validLocations().toSet() == standardMeta.validLocations().toSet()
    }

    def "schema with repeatable directives matches between FastBuilder and standard builder"() {
        given: "SDL with repeatable directive"
        def sdl = """
            directive @tag(name: String!) repeatable on FIELD_DEFINITION

            type Query {
                value: String @tag(name: "public") @tag(name: "cached")
            }
        """

        and: "programmatically created repeatable directive"
        def tagDirective = newDirective()
                .name("tag")
                .repeatable(true)
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("name")
                        .type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [],
                [tagDirective]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "directive is repeatable in both"
        def fastTag = fastSchema.getDirective("tag")
        def standardTag = standardSchema.getDirective("tag")
        fastTag.isRepeatable() == standardTag.isRepeatable()
        fastTag.isRepeatable() == true
    }
}

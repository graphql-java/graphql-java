package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.introspection.Introspection.DirectiveLocation

/**
 * Comparison tests for AdditionalTypes (detached types) between FastBuilder and standard Builder.
 *
 * Detached types are types that exist in the schema but are not reachable from root types
 * (Query, Mutation, Subscription) or directive arguments. These tests verify that FastBuilder's
 * FindDetachedTypes implementation produces the same additionalTypes set as the standard builder.
 */
class FastBuilderComparisonAdditionalTypesTest extends FastBuilderComparisonTest {

    def "schema with detached type not reachable from roots has matching additionalTypes"() {
        given: "SDL with a detached type"
        def sdl = """
            type Query {
                value: String
            }

            # DetachedType is not referenced anywhere - it's detached
            type DetachedType {
                field: String
            }
        """

        and: "programmatically created types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def detachedType = newObject()
                .name("DetachedType")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [detachedType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both have DetachedType in additionalTypes"
        standardSchema.additionalTypes*.name.toSet().contains("DetachedType")
        fastSchema.additionalTypes*.name.toSet().contains("DetachedType")
    }

    def "schema with type reachable from Query does not include it in additionalTypes"() {
        given: "SDL with type reachable from Query"
        def sdl = """
            type Query {
                user: User
            }

            type User {
                name: String
            }
        """

        and: "programmatically created types"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(userType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [userType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "User is NOT in additionalTypes for either schema (it's reachable from Query)"
        !standardSchema.additionalTypes*.name.toSet().contains("User")
        !fastSchema.additionalTypes*.name.toSet().contains("User")
    }

    def "schema with type reachable from Mutation does not include it in additionalTypes"() {
        given: "SDL with type reachable from Mutation"
        def sdl = """
            type Query {
                value: String
            }

            type Mutation {
                createUser(input: CreateUserInput): User
            }

            input CreateUserInput {
                name: String
            }

            type User {
                name: String
            }
        """

        and: "programmatically created types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def inputType = newInputObject()
                .name("CreateUserInput")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("createUser")
                        .argument(newArgument()
                                .name("input")
                                .type(inputType))
                        .type(userType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, mutationType, null, [userType, inputType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "User and CreateUserInput are NOT in additionalTypes (reachable from Mutation)"
        !standardSchema.additionalTypes*.name.toSet().contains("User")
        !fastSchema.additionalTypes*.name.toSet().contains("User")
        !standardSchema.additionalTypes*.name.toSet().contains("CreateUserInput")
        !fastSchema.additionalTypes*.name.toSet().contains("CreateUserInput")
    }

    def "schema with type reachable from Subscription does not include it in additionalTypes"() {
        given: "SDL with type reachable from Subscription"
        def sdl = """
            type Query {
                value: String
            }

            type Subscription {
                userUpdated: UserUpdate
            }

            type UserUpdate {
                user: User
            }

            type User {
                name: String
            }
        """

        and: "programmatically created types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def userUpdateType = newObject()
                .name("UserUpdate")
                .field(newFieldDefinition()
                        .name("user")
                        .type(userType))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("userUpdated")
                        .type(userUpdateType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, subscriptionType, [userType, userUpdateType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "UserUpdate and User are NOT in additionalTypes (reachable from Subscription)"
        !standardSchema.additionalTypes*.name.toSet().contains("UserUpdate")
        !fastSchema.additionalTypes*.name.toSet().contains("UserUpdate")
        !standardSchema.additionalTypes*.name.toSet().contains("User")
        !fastSchema.additionalTypes*.name.toSet().contains("User")
    }

    def "schema with type reachable only via directive argument does not include it in additionalTypes"() {
        given: "SDL with type only used in directive argument"
        def sdl = """
            type Query {
                value: String
            }

            # ConfigValue is only used in the directive argument
            scalar ConfigValue

            directive @config(value: ConfigValue) on FIELD
        """

        and: "programmatically created types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def configScalar = newScalar()
                .name("ConfigValue")
                .coercing(GraphQLString.getCoercing())
                .build()

        def directive = newDirective()
                .name("config")
                .validLocation(DirectiveLocation.FIELD)
                .argument(newArgument()
                        .name("value")
                        .type(configScalar))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [configScalar],
                [directive]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "ConfigValue is NOT in additionalTypes (reachable via directive argument)"
        !standardSchema.additionalTypes*.name.toSet().contains("ConfigValue")
        !fastSchema.additionalTypes*.name.toSet().contains("ConfigValue")
    }

    def "complex schema with multiple detached types has matching additionalTypes"() {
        given: "SDL with multiple detached types"
        def sdl = """
            type Query {
                user: User
            }

            type User {
                name: String
            }

            # These types are all detached (not reachable from Query)
            type DetachedOne {
                field: String
            }

            type DetachedTwo {
                field: String
            }

            enum DetachedEnum {
                VALUE_A
                VALUE_B
            }

            input DetachedInput {
                field: String
            }
        """

        and: "programmatically created types"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(userType))
                .build()

        def detachedOne = newObject()
                .name("DetachedOne")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        def detachedTwo = newObject()
                .name("DetachedTwo")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        def detachedEnum = newEnum()
                .name("DetachedEnum")
                .value("VALUE_A")
                .value("VALUE_B")
                .build()

        def detachedInput = newInputObject()
                .name("DetachedInput")
                .field(newInputObjectField()
                        .name("field")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [userType, detachedOne, detachedTwo, detachedEnum, detachedInput]
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "all detached types are in additionalTypes"
        def standardAdditional = standardSchema.additionalTypes*.name.toSet()
        def fastAdditional = fastSchema.additionalTypes*.name.toSet()

        standardAdditional.contains("DetachedOne")
        standardAdditional.contains("DetachedTwo")
        standardAdditional.contains("DetachedEnum")
        standardAdditional.contains("DetachedInput")

        fastAdditional.contains("DetachedOne")
        fastAdditional.contains("DetachedTwo")
        fastAdditional.contains("DetachedEnum")
        fastAdditional.contains("DetachedInput")

        and: "User is NOT in additionalTypes (it's reachable)"
        !standardAdditional.contains("User")
        !fastAdditional.contains("User")
    }

    def "schema with no detached types has empty additionalTypes in both builders"() {
        given: "SDL with all types reachable from Query"
        def sdl = """
            type Query {
                user: User
                post: Post
            }

            type User {
                name: String
                posts: [Post]
            }

            type Post {
                title: String
                author: User
            }
        """

        and: "programmatically created types"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def postType = newObject()
                .name("Post")
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("author")
                        .type(userType))
                .build()

        // Add posts field to userType after postType is created
        userType = userType.transform({ builder ->
            builder.field(newFieldDefinition()
                    .name("posts")
                    .type(GraphQLList.list(postType)))
        })

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("user")
                        .type(userType))
                .field(newFieldDefinition()
                        .name("post")
                        .type(postType))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [userType, postType])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both have empty additionalTypes (all types are reachable)"
        standardSchema.additionalTypes.isEmpty()
        fastSchema.additionalTypes.isEmpty()
    }

    def "schema with detached type transitively referencing other types includes all in additionalTypes"() {
        given: "SDL with detached types that reference each other"
        def sdl = """
            type Query {
                value: String
            }

            # DetachedOne is not reachable from Query
            type DetachedOne {
                nested: DetachedTwo
            }

            # DetachedTwo is referenced by DetachedOne, but neither is reachable
            type DetachedTwo {
                field: String
            }
        """

        and: "programmatically created types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def detachedTwo = newObject()
                .name("DetachedTwo")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        def detachedOne = newObject()
                .name("DetachedOne")
                .field(newFieldDefinition()
                        .name("nested")
                        .type(detachedTwo))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [detachedOne, detachedTwo])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both DetachedOne and DetachedTwo are in additionalTypes"
        def standardAdditional = standardSchema.additionalTypes*.name.toSet()
        def fastAdditional = fastSchema.additionalTypes*.name.toSet()

        standardAdditional.contains("DetachedOne")
        standardAdditional.contains("DetachedTwo")
        fastAdditional.contains("DetachedOne")
        fastAdditional.contains("DetachedTwo")
    }

    def "schema with type implementing interface has correct additionalTypes"() {
        given: "SDL with interface and implementation"
        def sdl = """
            type Query {
                node: Node
            }

            interface Node {
                id: String
            }

            # User implements Node - it's in additionalTypes because interface implementations
            # are not automatically traversed from the interface type itself
            type User implements Node {
                id: String
                name: String
            }
        """

        and: "programmatically created types"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .withInterface(nodeInterface)
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> userType })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [nodeInterface, userType], [], codeRegistry)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "Node is NOT in additionalTypes (reachable from Query)"
        !standardSchema.additionalTypes*.name.toSet().contains("Node")
        !fastSchema.additionalTypes*.name.toSet().contains("Node")

        and: "User IS in additionalTypes (interface implementations are not auto-traversed)"
        standardSchema.additionalTypes*.name.toSet().contains("User")
        fastSchema.additionalTypes*.name.toSet().contains("User")
    }

    def "schema with type used in union is not in additionalTypes"() {
        given: "SDL with union type"
        def sdl = """
            type Query {
                searchResult: SearchResult
            }

            union SearchResult = User | Post

            type User {
                name: String
            }

            type Post {
                title: String
            }
        """

        and: "programmatically created types"
        def userType = newObject()
                .name("User")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def postType = newObject()
                .name("Post")
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .build()

        def searchResultUnion = GraphQLUnionType.newUnionType()
                .name("SearchResult")
                .possibleType(userType)
                .possibleType(postType)
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("searchResult")
                        .type(searchResultUnion))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("SearchResult", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [userType, postType, searchResultUnion],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "SearchResult, User, and Post are NOT in additionalTypes (all reachable from Query)"
        def standardAdditional = standardSchema.additionalTypes*.name.toSet()
        def fastAdditional = fastSchema.additionalTypes*.name.toSet()

        !standardAdditional.contains("SearchResult")
        !standardAdditional.contains("User")
        !standardAdditional.contains("Post")

        !fastAdditional.contains("SearchResult")
        !fastAdditional.contains("User")
        !fastAdditional.contains("Post")
    }

    def "schema with type used in input object field is not in additionalTypes when input is reachable"() {
        given: "SDL with nested input types"
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

        and: "programmatically created types"
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
                        .type(addressInput))
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
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [userInput, addressInput])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "UserInput and AddressInput are NOT in additionalTypes (both reachable from Query)"
        !standardSchema.additionalTypes*.name.toSet().contains("UserInput")
        !fastSchema.additionalTypes*.name.toSet().contains("UserInput")
        !standardSchema.additionalTypes*.name.toSet().contains("AddressInput")
        !fastSchema.additionalTypes*.name.toSet().contains("AddressInput")
    }
}

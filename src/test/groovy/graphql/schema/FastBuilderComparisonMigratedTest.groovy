package graphql.schema

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar

/**
 * Comparison tests migrated from FastBuilderTest.groovy.
 *
 * These tests were originally in FastBuilderTest but have been refactored to use
 * the comparison testing approach: building schemas with both FastBuilder and SDL
 * parsing, then asserting equivalence.
 */
class FastBuilderComparisonMigratedTest extends FastBuilderComparisonTest {

    def "scalar type schema matches standard builder"() {
        given: "SDL for a schema with custom scalar"
        def sdl = """
            scalar CustomScalar

            type Query {
                value: CustomScalar
            }
        """

        and: "programmatically created types"
        def customScalar = newScalar()
                .name("CustomScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(customScalar))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [customScalar])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "enum type schema matches standard builder"() {
        given: "SDL for a schema with enum"
        def sdl = """
            enum Status {
                ACTIVE
                INACTIVE
            }

            type Query {
                status: Status
            }
        """

        and: "programmatically created types"
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(statusEnum))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [statusEnum])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "built-in directives are added automatically in both builders"() {
        given: "SDL for minimal schema"
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

        then: "both schemas have the same built-in directives"
        def coreDirectives = ["skip", "include", "deprecated", "specifiedBy"]

        and: "FastBuilder has all core directives"
        coreDirectives.each { directiveName ->
            assert fastSchema.getDirective(directiveName) != null,
                    "FastBuilder missing directive: ${directiveName}"
        }

        and: "standard builder has all core directives"
        coreDirectives.each { directiveName ->
            assert standardSchema.getDirective(directiveName) != null,
                    "Standard builder missing directive: ${directiveName}"
        }

        and: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "mutation and subscription types work correctly in both builders"() {
        given: "SDL for schema with all root types"
        def sdl = """
            type Query {
                value: String
            }

            type Mutation {
                setValue(input: String): String
            }

            type Subscription {
                valueChanged: String
            }
        """

        and: "programmatically created root types"
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
                        .argument(GraphQLArgument.newArgument()
                                .name("input")
                                .type(GraphQLString))
                        .type(GraphQLString))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("valueChanged")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, mutationType, subscriptionType)

        then: "both schemas support mutations and subscriptions"
        fastSchema.isSupportingMutations()
        standardSchema.isSupportingMutations()
        fastSchema.isSupportingSubscriptions()
        standardSchema.isSupportingSubscriptions()

        and: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with only query type works correctly in both builders"() {
        given: "SDL for schema with only Query"
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

        when: "building with both approaches (no mutation or subscription)"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null)

        then: "both schemas do not support mutations or subscriptions"
        !fastSchema.isSupportingMutations()
        !standardSchema.isSupportingMutations()
        !fastSchema.isSupportingSubscriptions()
        !standardSchema.isSupportingSubscriptions()

        and: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema description is preserved in both builders"() {
        given: "SDL for schema with description"
        def schemaDescription = "Test schema description"
        def sdl = """
            \"\"\"
            ${schemaDescription}
            \"\"\"
            schema {
                query: Query
            }

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

        when: "building standard schema from SDL"
        def standardSchema = buildSchemaFromSDL(sdl)

        and: "building FastBuilder schema with description"
        def fastSchema = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(), queryType, null, null)
                .description(schemaDescription)
                .build()

        then: "both schemas have the same description"
        fastSchema.description == schemaDescription
        standardSchema.description == schemaDescription

        and: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with mutation but no subscription works correctly"() {
        given: "SDL for schema with Query and Mutation only"
        def sdl = """
            type Query {
                value: String
            }

            type Mutation {
                setValue(input: String): String
            }
        """

        and: "programmatically created types"
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
                        .argument(GraphQLArgument.newArgument()
                                .name("input")
                                .type(GraphQLString))
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, mutationType, null)

        then: "both schemas support mutations but not subscriptions"
        fastSchema.isSupportingMutations()
        standardSchema.isSupportingMutations()
        !fastSchema.isSupportingSubscriptions()
        !standardSchema.isSupportingSubscriptions()

        and: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }

    def "schema with multiple scalar types matches standard builder"() {
        given: "SDL for schema with multiple custom scalars"
        def sdl = """
            scalar Scalar1
            scalar Scalar2

            type Query {
                value1: Scalar1
                value2: Scalar2
            }
        """

        and: "programmatically created types"
        def scalar1 = newScalar()
                .name("Scalar1")
                .coercing(GraphQLString.getCoercing())
                .build()

        def scalar2 = newScalar()
                .name("Scalar2")
                .coercing(GraphQLString.getCoercing())
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value1")
                        .type(scalar1))
                .field(newFieldDefinition()
                        .name("value2")
                        .type(scalar2))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [scalar1, scalar2])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both scalars are present in both schemas"
        fastSchema.getType("Scalar1") != null
        fastSchema.getType("Scalar2") != null
        standardSchema.getType("Scalar1") != null
        standardSchema.getType("Scalar2") != null
    }

    def "schema with multiple enum types matches standard builder"() {
        given: "SDL for schema with multiple enums"
        def sdl = """
            enum Status {
                ACTIVE
                INACTIVE
            }

            enum Role {
                ADMIN
                USER
            }

            type Query {
                status: Status
                role: Role
            }
        """

        and: "programmatically created types"
        def statusEnum = newEnum()
                .name("Status")
                .value("ACTIVE")
                .value("INACTIVE")
                .build()

        def roleEnum = newEnum()
                .name("Role")
                .value("ADMIN")
                .value("USER")
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("status")
                        .type(statusEnum))
                .field(newFieldDefinition()
                        .name("role")
                        .type(roleEnum))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType, null, null, [statusEnum, roleEnum])

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both enums are present in both schemas"
        fastSchema.getType("Status") instanceof GraphQLEnumType
        fastSchema.getType("Role") instanceof GraphQLEnumType
        standardSchema.getType("Status") instanceof GraphQLEnumType
        standardSchema.getType("Role") instanceof GraphQLEnumType
    }
}

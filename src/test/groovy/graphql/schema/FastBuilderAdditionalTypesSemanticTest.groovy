package graphql.schema

import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

/**
 * Tests that verify FastBuilder's additionalTypes semantics:
 * additionalTypes contains ALL types except root operation types.
 *
 * This differs from the standard Builder which only includes "detached" types
 * (types not reachable from root types).
 */
class FastBuilderAdditionalTypesSemanticTest extends Specification {

    def "additionalTypes contains all types except Query when only Query root exists"() {
        given:
        def sdl = '''
            type Query {
                user: User
            }
            type User {
                name: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then:
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        // Query should NOT be in additionalTypes (it's a root type)
        !additionalTypeNames.contains("Query")

        // User should be in additionalTypes (non-root type)
        additionalTypeNames.contains("User")
    }

    def "additionalTypes excludes Query, Mutation, and Subscription root types"() {
        given:
        def sdl = '''
            type Query {
                user: User
            }
            type Mutation {
                createUser: User
            }
            type Subscription {
                userCreated: User
            }
            type User {
                name: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then:
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        // Root types should NOT be in additionalTypes
        !additionalTypeNames.contains("Query")
        !additionalTypeNames.contains("Mutation")
        !additionalTypeNames.contains("Subscription")

        // User should be in additionalTypes
        additionalTypeNames.contains("User")
    }

    def "additionalTypes includes types reachable from roots (unlike standard builder)"() {
        given: "SDL where User is reachable from Query"
        def sdl = '''
            type Query {
                user: User
            }
            type User {
                name: String
                address: Address
            }
            type Address {
                city: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then: "FastBuilder includes all non-root types in additionalTypes"
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        // Both User and Address are included even though they're reachable from Query
        additionalTypeNames.contains("User")
        additionalTypeNames.contains("Address")

        // Query is still excluded
        !additionalTypeNames.contains("Query")
    }

    def "additionalTypes includes interface implementations"() {
        given:
        def sdl = '''
            type Query {
                node: Node
            }
            interface Node {
                id: ID
            }
            type User implements Node {
                id: ID
                name: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then:
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        additionalTypeNames.contains("Node")
        additionalTypeNames.contains("User")
        !additionalTypeNames.contains("Query")
    }

    def "additionalTypes includes enum, input, and scalar types"() {
        given:
        def sdl = '''
            type Query {
                status: Status
                search(input: SearchInput): String
            }
            enum Status {
                ACTIVE
                INACTIVE
            }
            input SearchInput {
                query: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then:
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        additionalTypeNames.contains("Status")
        additionalTypeNames.contains("SearchInput")
        !additionalTypeNames.contains("Query")
    }

    def "additionalTypes with custom root type names"() {
        given:
        def sdl = '''
            schema {
                query: MyQuery
                mutation: MyMutation
            }
            type MyQuery {
                value: String
            }
            type MyMutation {
                setValue: String
            }
            type Helper {
                data: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
            new SchemaParser().parse(sdl),
            RuntimeWiring.MOCKED_WIRING
        )

        then:
        def additionalTypeNames = schema.additionalTypes*.name.toSet()

        // Custom root type names should be excluded
        !additionalTypeNames.contains("MyQuery")
        !additionalTypeNames.contains("MyMutation")

        // Non-root types included
        additionalTypeNames.contains("Helper")
    }
}

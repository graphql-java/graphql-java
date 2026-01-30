package graphql.schema.idl

import graphql.schema.GraphQLSchema
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.validation.InvalidSchemaException
import spock.lang.Specification

/**
 * Tests for {@link FastSchemaGenerator}.
 *
 * Note: {@link GraphQLSchema.FastBuilder} is subject to extensive testing directly.
 * The tests in this file are intended to test {@code FastSchemaGenerator} specifically
 * and not the underlying builder.
 */
class FastSchemaGeneratorTest extends Specification {

    def "can create simple schema using FastSchemaGenerator"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        schema != null
        schema.queryType.name == "Query"
        schema.queryType.getFieldDefinition("hello") != null
    }

    def "produces same result as standard SchemaGenerator"() {
        given:
        def sdl = '''
            type Query {
                user(id: ID!): User
                users: [User]
            }

            type User {
                id: ID!
                name: String!
                email: String
            }

            type Mutation {
                createUser(name: String!): User
            }
        '''

        def registry = new SchemaParser().parse(sdl)

        when:
        def standardSchema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)
        def fastSchema = new FastSchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)

        then:
        // Both should have the same query type
        standardSchema.queryType.name == fastSchema.queryType.name
        standardSchema.queryType.fieldDefinitions.size() == fastSchema.queryType.fieldDefinitions.size()

        // Both should have the same mutation type
        standardSchema.mutationType.name == fastSchema.mutationType.name
        standardSchema.mutationType.fieldDefinitions.size() == fastSchema.mutationType.fieldDefinitions.size()

        // Both should have User type with same fields
        def standardUser = standardSchema.getObjectType("User")
        def fastUser = fastSchema.getObjectType("User")
        standardUser != null
        fastUser != null
        standardUser.fieldDefinitions.size() == fastUser.fieldDefinitions.size()
    }

    def "handles schema with interfaces"() {
        given:
        def sdl = '''
            type Query {
                node(id: ID!): Node
            }

            interface Node {
                id: ID!
            }

            type User implements Node {
                id: ID!
                name: String!
            }

            type Post implements Node {
                id: ID!
                title: String!
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        schema != null
        schema.getType("Node") != null
        schema.getType("User") != null
        schema.getType("Post") != null
    }

    def "handles schema with unions"() {
        given:
        def sdl = '''
            type Query {
                search: SearchResult
            }

            union SearchResult = User | Post

            type User {
                id: ID!
                name: String!
            }

            type Post {
                id: ID!
                title: String!
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        schema != null
        schema.getType("SearchResult") != null
        schema.getType("User") != null
        schema.getType("Post") != null
    }

    // Regression tests to ensure FastSchemaGenerator behaves like SchemaGenerator

    def "should throw SchemaProblem for missing type reference even with validation disabled"() {
        given:
        def sdl = '''
            type Query {
                user: UnknownType
            }
        '''

        when:
        new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        thrown(SchemaProblem)
    }

    def "should throw SchemaProblem for duplicate field definitions even with validation disabled"() {
        given:
        def sdl = '''
            type Query {
                hello: String
                hello: Int
            }
        '''

        when:
        new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        thrown(SchemaProblem)
    }

    def "should throw SchemaProblem for invalid interface implementation even with validation disabled"() {
        given:
        def sdl = '''
            type Query {
                node: Node
            }

            interface Node {
                id: ID!
            }

            type User implements Node {
                name: String
            }
        '''

        when:
        new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        // User claims to implement Node but doesn't have the required 'id' field
        // This is caught by SchemaTypeChecker, not SchemaValidator
        thrown(SchemaProblem)
    }

    def "should include introspection types in getAllTypesAsList"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )
        def allTypeNames = schema.getAllTypesAsList().collect { it.name } as Set

        then:
        // Introspection types must be present for introspection queries to work
        allTypeNames.contains("__Schema")
        allTypeNames.contains("__Type")
        allTypeNames.contains("__Field")
        allTypeNames.contains("__InputValue")
        allTypeNames.contains("__EnumValue")
        allTypeNames.contains("__Directive")
        allTypeNames.contains("__TypeKind")
        allTypeNames.contains("__DirectiveLocation")
    }

    def "should include String and Boolean scalars in getAllTypesAsList"() {
        given:
        def sdl = '''
            type Query {
                id: ID
            }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )
        def allTypeNames = schema.getAllTypesAsList().collect { it.name } as Set

        then:
        // String and Boolean are required by introspection types
        // (__Type.name, __Field.name, etc. return String; __Field.isDeprecated returns Boolean)
        allTypeNames.contains("String")
        allTypeNames.contains("Boolean")
    }

    def "introspection types should match standard SchemaGenerator"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''
        def registry = new SchemaParser().parse(sdl)

        when:
        def standardSchema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)
        def fastSchema = new FastSchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)

        def standardTypeNames = standardSchema.getAllTypesAsList().collect { it.name } as Set
        def fastTypeNames = fastSchema.getAllTypesAsList().collect { it.name } as Set

        then:
        // FastBuilder should include all introspection types that standard builder includes
        standardTypeNames.findAll { it.startsWith("__") }.each { introspectionType ->
            assert fastTypeNames.contains(introspectionType) : "Missing introspection type: $introspectionType"
        }
        // FastBuilder should not contain introspection types not in standard builder
        fastTypeNames.findAll { it.startsWith("__") }.each { introspectionType ->
            assert standardTypeNames.contains(introspectionType) : "Extra introspection type: $introspectionType"
        }
    }

    // Validation tests - test that 2-arg method validates and 4-arg with validation=false skips validation

    def "default makeExecutableSchema validates and throws InvalidSchemaException for non-null self-referencing input type"() {
        given:
        // Non-null self-reference in input type is impossible to satisfy
        def sdl = '''
            type Query { test(input: BadInput): String }
            input BadInput { self: BadInput! }
        '''

        when:
        new FastSchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        thrown(InvalidSchemaException)
    }

    def "3-arg makeExecutableSchema with withValidation=false allows non-null self-referencing input type"() {
        given:
        // Non-null self-reference in input type - passes without validation
        def sdl = '''
            type Query { test(input: BadInput): String }
            input BadInput { self: BadInput! }
        '''

        when:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
        )

        then:
        notThrown(InvalidSchemaException)
        schema != null
    }
}

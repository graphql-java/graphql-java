package graphql.schema.idl

import graphql.schema.GraphQLSchema
import spock.lang.Specification

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
}

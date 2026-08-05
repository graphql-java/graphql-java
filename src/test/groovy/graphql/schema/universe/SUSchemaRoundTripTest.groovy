package graphql.schema.universe

import graphql.schema.GraphQLSchema
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import spock.lang.Specification
import spock.lang.Unroll

class SUSchemaRoundTripTest extends Specification {

    @Unroll
    def "GraphQLSchema and SUSchema remain identical through bidirectional SDL round trips: #scenario"() {
        when:
        def nativeSource = nativeSnapshot(sourceSdl)
        def graphQLSource = graphQLSnapshot(sourceSdl)

        and:
        def nativeFromGraphQL = nativeSnapshot(graphQLSource.sdl)
        def graphQLFromNative = graphQLSnapshot(nativeSource.sdl)

        and:
        def nativeSecondGeneration = nativeSnapshot(graphQLFromNative.sdl)
        def graphQLSecondGeneration = graphQLSnapshot(nativeFromGraphQL.sdl)

        then:
        nativeSource == graphQLSource
        nativeFromGraphQL == graphQLSource
        graphQLFromNative == nativeSource
        nativeSecondGeneration == nativeSource
        graphQLSecondGeneration == graphQLSource

        where:
        scenario                            | sourceSdl
        "operation roots and cyclic types"  | operationRootsAndCyclesSdl()
        "directives on every schema kind"   | directivesEverywhereSdl()
        "all input default literal shapes"  | inputDefaultsSdl()
        "descriptions and built-in metadata"| descriptionsAndMetadataSdl()
        "all type and schema extensions"     | extensionsSdl()
        "default roots and unused types"     | defaultRootsAndUnusedTypesSdl()
    }

    private static Map<String, Object> nativeSnapshot(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        SUSchema schema =
                new SchemaUniverse().importSchema("native_round_trip", registry)
        return [
                sdl       : new SUSchemaPrinter(printerOptions()).print(schema),
                query     : schema.queryType.name,
                mutation  : schema.mutationType?.name,
                subscription: schema.subscriptionType?.name,
                types     : schema.types*.name.toSorted(),
                directives: schema.directiveDefinitions*.name.toSorted()
        ]
    }

    private static Map<String, Object> graphQLSnapshot(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        GraphQLSchema schema =
                UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        return [
                sdl       : new SchemaPrinter(printerOptions()).print(schema),
                query     : schema.queryType.name,
                mutation  : schema.mutationType?.name,
                subscription: schema.subscriptionType?.name,
                types     : schema.allTypesAsList*.name.toSorted(),
                directives: schema.directives*.name.toSorted()
        ]
    }

    private static SchemaPrinter.Options printerOptions() {
        return SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
                .includeScalarTypes(true)
                .setComparators(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
    }

    private static String operationRootsAndCyclesSdl() {
        return '''
            schema {
              query: RootQuery
              mutation: RootMutation
              subscription: RootSubscription
            }

            interface Node {
              id: ID!
              owner: Node
            }

            interface Resource implements Node {
              id: ID!
              owner: Node
              url: String!
            }

            type User implements Node & Resource {
              id: ID!
              owner: Node
              url: String!
              friends(limit: Int = 10): [User!]!
            }

            type Photo implements Node & Resource {
              id: ID!
              owner: Node
              url: String!
              related: [SearchResult!]!
            }

            union SearchResult = User | Photo

            type RootQuery {
              node(id: ID!): Node
              search: [SearchResult!]!
            }

            type RootMutation {
              update(id: ID!, input: UpdateInput!): User
            }

            type RootSubscription {
              changed: Node
            }

            input UpdateInput {
              owner: ID
              nested: UpdateInput
            }
        '''
    }

    private static String directivesEverywhereSdl() {
        return '''
            directive @meta(
              text: String = "fallback"
              count: Int
              flags: [Boolean!] = [true, false]
              unset: String
            ) repeatable on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE |
              INPUT_OBJECT | INPUT_FIELD_DEFINITION | DIRECTIVE_DEFINITION

            directive @target @meta(text: "definition")
              @deprecated(reason: "Use @replacement") on FIELD_DEFINITION
            extend directive @target @meta(text: "extension")

            schema @meta(text: "schema") @meta(count: 2) {
              query: Query
            }

            scalar Custom @meta

            interface Named @meta {
              name(prefix: String @meta): String @meta
            }

            type Item implements Named @meta {
              name(prefix: String @meta(count: 3)): String @meta
              custom: Custom
            }

            union Result @meta = Item

            enum Mode @meta {
              FIRST @meta
              SECOND @meta(text: "second")
            }

            input MetaInput @meta {
              enabled: Boolean! @meta
              mode: Mode = FIRST
            }

            type Query @meta {
              result(input: MetaInput @meta): Result @meta(flags: [false])
            }
        '''
    }

    private static String inputDefaultsSdl() {
        return '''
            enum Color {
              RED
              GREEN
              BLUE
            }

            input Nested {
              required: Boolean!
              note: String
            }

            input Defaults {
              nullValue: String = null
              intValue: Int = -7
              floatValue: Float = 1.25
              stringValue: String = "quoted \\"value\\""
              booleanValue: Boolean = true
              enumValue: Color = RED
              listValue: [Int!] = [1, 2, 3]
              objectValue: Nested = {required: true, note: "nested"}
            }

            type Query {
              test(
                defaults: Defaults = {
                  nullValue: null
                  intValue: 9
                  floatValue: 2.5
                  stringValue: "argument"
                  booleanValue: false
                  enumValue: BLUE
                  listValue: [4, 5]
                  objectValue: {required: false}
                }
                nested: [Nested!] = [
                  {required: true}
                  {required: false, note: "two"}
                ]
                color: Color = GREEN
              ): String
            }
        '''
    }

    private static String descriptionsAndMetadataSdl() {
        return '''
            """A directive description."""
            directive @documented(
              """An argument description."""
              value: String = "documented"
            ) on OBJECT | FIELD_DEFINITION

            """A URL scalar."""
            scalar Url
              @specifiedBy(url: "https://example.com/url")

            """A node interface."""
            interface Node {
              """The stable identifier."""
              id: ID!
            }

            """A result union."""
            union Result = Record

            """A state enum."""
            enum State {
              """Still active."""
              ACTIVE
              """No longer active."""
              OLD @deprecated(reason: "Use ACTIVE")
            }

            """Filter input."""
            input Filter {
              """An old input."""
              old: String @deprecated(reason: "Use current")
              current: String
            }

            """A record."""
            type Record implements Node @documented {
              id: ID!
              """An old field."""
              old(
                """An old argument."""
                value: String @deprecated(reason: "Use replacement")
              ): String @deprecated(reason: "Use current")
              url: Url
              state: State
            }

            # Legacy query description.
            type Query {
              record(filter: Filter): Result @documented
            }
        '''
    }

    private static String extensionsSdl() {
        return '''
            directive @mark(value: String = "marked") repeatable on
              SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | INTERFACE |
              UNION | ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            schema {
              query: Query
            }

            extend schema @mark(value: "schema extension") {
              mutation: Mutation
            }

            scalar Token
            extend scalar Token @mark

            interface Node {
              id: ID!
            }

            extend interface Node @mark {
              label: String
            }

            type Item implements Node {
              id: ID!
              label: String
            }

            type Other implements Node {
              id: ID!
              label: String
            }

            union Result = Item
            extend union Result @mark = Other

            enum Status {
              FIRST
            }

            extend enum Status @mark {
              SECOND @mark
            }

            input Filter {
              first: String
            }

            extend input Filter @mark {
              second: Status @mark
            }

            type Query {
              base: Item
            }

            extend type Query @mark {
              added(filter: Filter): Result @mark
            }

            type Mutation {
              update(token: Token): Item
            }
        '''
    }

    private static String defaultRootsAndUnusedTypesSdl() {
        return '''
            scalar UnusedScalar

            type UnusedObject {
              value: UnusedScalar
            }

            type Query {
              query: String
            }

            type Mutation {
              mutate: Boolean
            }

            type Subscription {
              changed: Int
            }
        '''
    }
}

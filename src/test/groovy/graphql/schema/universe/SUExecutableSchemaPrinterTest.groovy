package graphql.schema.universe

import graphql.Scalars
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import graphql.schema.universe.view.SUExecutableSchema
import spock.lang.Specification
import spock.lang.Requires
import spock.lang.Unroll

class SUExecutableSchemaPrinterTest extends Specification {

    def "native SDL import and GraphQLSchema import print the same semantic schema"() {
        given:
        def registry = new SchemaParser().parse(comprehensiveSdl())
        def graphQLSchema =
                UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        def direct = new SchemaUniverse().importSchema("direct", registry)
        def viaGraphQL =
                new SchemaUniverse().importSchema("graphql", graphQLSchema)
        def options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
        def printer = new SchemaPrinter(options)

        when:
        def directSdl = printer.print(executable(direct))
        def viaGraphQLSdl = printer.print(
                SUExecutableSchema.fromGraphQLSchema(
                        viaGraphQL,
                        graphQLSchema))
        def graphQLSdl = new SchemaPrinter(options).print(graphQLSchema)

        then:
        directSdl == viaGraphQLSdl
        directSdl == graphQLSdl

        and:
        def expectedTypeNames = graphQLSchema.allTypesAsList*.name as Set
        (direct.types*.name as Set) == expectedTypeNames
    }

    def "native semantic printing is stable after parsing the printed SDL"() {
        given:
        def options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
        def printer = new SchemaPrinter(options)
        def first = new SchemaUniverse().parseSchema("first", comprehensiveSdl())

        when:
        def firstSdl = printer.print(executable(first))
        def second = new SchemaUniverse().parseSchema("second", firstSdl)
        def secondSdl = printer.print(executable(second))

        then:
        firstSdl == secondSdl
        firstSdl.contains("type Root implements Node @tag")
        firstSdl.contains("SECOND")
        !firstSdl.contains("extend type")
        !firstSdl.contains("extend enum")
    }

    def "executable schema printing uses custom scalar coercing for programmatic defaults"() {
        given:
        def date = GraphQLScalarType.newScalar()
                .name("Date")
                .coercing(Scalars.GraphQLString.coercing)
                .build()
        def query = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .argument(GraphQLArgument.newArgument()
                                .name("external")
                                .type(date)
                                .defaultValueProgrammatic("2026-08-05"))
                        .argument(GraphQLArgument.newArgument()
                                .name("internal")
                                .type(date)
                                .defaultValue("2026-08-06")))
                .build()
        GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
                .query(query)
                .build()
        def universeSchema = new SchemaUniverse()
                .importSchema("programmatic", graphQLSchema)
        def executableSchema = SUExecutableSchema
                .fromGraphQLSchema(universeSchema, graphQLSchema)
        def printer = new SchemaPrinter()

        expect:
        printer.print(executableSchema) == printer.print(graphQLSchema)
        printer.print(executableSchema).contains(
                'external: Date = "2026-08-05"')
        printer.print(executableSchema).contains(
                'internal: Date = "2026-08-06"')
    }

    @Unroll
    def "native executable schema honors #orderingName ordering"() {
        given:
        def sdl = '''
            type Query {
              z(z: Int, a: Int): String
              a: String
            }
        '''
        def registry = new SchemaParser().parse(sdl)
        def graphQLSchema =
                UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        def universeSchema =
                new SchemaUniverse().importSchema(orderingName, registry)
        def options = SchemaPrinter.Options.defaultOptions()
                .setComparators(comparatorRegistry)
        def printer = new SchemaPrinter(options)

        when:
        def nativeSdl = printer.print(executable(universeSchema))
        def graphQLSdl = printer.print(graphQLSchema)

        then:
        nativeSdl.contains(expectedFields)
        graphQLSdl.contains(expectedFields)

        where:
        orderingName | comparatorRegistry                              | expectedFields
        "as_is"      | GraphqlTypeComparatorRegistry.AS_IS_REGISTRY   | "  z(z: Int, a: Int): String\n  a: String"
        "by_name"    | GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY | "  a: String\n  z(a: Int, z: Int): String"
    }

    @Requires({ Boolean.getBoolean("graphql.largeSchemaSdlTest") })
    def "large schema native import and printing match GraphQLSchema"() {
        given:
        def sdl = resource("large-schema-5.graphqls.part1") +
                resource("large-schema-5.graphqls.part2")
        def options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)

        when:
        def canonicalSdl = printNative(sdl, options)
        System.gc()

        then:
        printGraphQL(sdl, options) == canonicalSdl

        when:
        System.gc()

        then:
        printNative(canonicalSdl, options) == canonicalSdl

        when:
        System.gc()

        then:
        printGraphQL(canonicalSdl, options) == canonicalSdl
    }

    private static String comprehensiveSdl() {
        return '''
            directive @tag(
              value: String = "default"
              count: Int
            ) repeatable on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | ENUM | ENUM_VALUE |
              INPUT_OBJECT | INPUT_FIELD_DEFINITION

            """Schema description."""
            schema @tag(value: "schema") {
              query: Root
              mutation: Changes
            }

            extend schema @tag(value: "extension")

            scalar Date @specifiedBy(url: "https://example.com/date")

            interface Node @tag {
              id: ID!
            }

            type Root implements Node @tag {
              id: ID!
              first(
                filter: Filter = {enabled: true}
              ): [String!]! @tag(count: 2)
            }

            extend type Root @tag(value: "extension") {
              second: Choice @deprecated(reason: "Use first")
            }

            type Changes {
              update(input: Filter): Root
            }

            input Filter @tag {
              enabled: Boolean = false
              choice: Choice @deprecated
            }

            enum Choice @tag {
              FIRST
            }

            extend enum Choice {
              SECOND @tag
            }
        '''
    }

    private static String resource(String name) {
        def resource =
                SUExecutableSchemaPrinterTest.getResource("/" + name)
        assert resource != null
        return resource.getText("UTF-8")
    }

    private static String printNative(
            String sdl,
            SchemaPrinter.Options options) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        def schema = new SchemaUniverse().importSchema("direct_large", registry)
        return new SchemaPrinter(options).print(executable(schema))
    }

    private static String printGraphQL(
            String sdl,
            SchemaPrinter.Options options) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        return new SchemaPrinter(options).print(schema)
    }

    private static SUExecutableSchema executable(SUSchema schema) {
        return SUExecutableSchema.newExecutableSchema(schema).build()
    }
}

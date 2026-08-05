package graphql.schema.universe

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import spock.lang.Specification
import spock.lang.Requires

class SUSchemaPrinterTest extends Specification {

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
        def printer = new SUSchemaPrinter(options)

        when:
        def directSdl = printer.print(direct)
        def viaGraphQLSdl = printer.print(viaGraphQL)
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
        def printer = new SUSchemaPrinter(options)
        def first = new SchemaUniverse().parseSchema("first", comprehensiveSdl())

        when:
        def firstSdl = printer.print(first)
        def second = new SchemaUniverse().parseSchema("second", firstSdl)
        def secondSdl = printer.print(second)

        then:
        firstSdl == secondSdl
        firstSdl.contains("type Root implements Node @tag")
        firstSdl.contains("SECOND")
        !firstSdl.contains("extend type")
        !firstSdl.contains("extend enum")
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
        def resource = SUSchemaPrinterTest.getResource("/" + name)
        assert resource != null
        return resource.getText("UTF-8")
    }

    private static String printNative(
            String sdl,
            SchemaPrinter.Options options) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        def schema = new SchemaUniverse().importSchema("direct_large", registry)
        return new SUSchemaPrinter(options).print(schema)
    }

    private static String printGraphQL(
            String sdl,
            SchemaPrinter.Options options) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl)
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        return new SchemaPrinter(options).print(schema)
    }
}

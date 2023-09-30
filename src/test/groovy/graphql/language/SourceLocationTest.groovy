package graphql.language

import graphql.parser.MultiSourceReader
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

import static graphql.schema.FieldCoordinates.coordinates

class SourceLocationTest extends Specification {

    def "can get source location"() {
        def sdl = """
            type Query {
                a : A!
            }
            
            type A {
                b : B
            }
            
            type B {
                c : String
            } 
        """

        def sourceReader = MultiSourceReader.newMultiSourceReader().string(sdl, "sourceName").build()

        def definitionRegistry = new SchemaParser().parse(sourceReader)
        when:
        def schema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, RuntimeWiring.MOCKED_WIRING)
        def schemaElement = schema.getType("Query")
        def location = SourceLocation.getLocation(schemaElement)

        then:
        location.sourceName == "sourceName"
        location.line == 2
        location.column == 13

        when:
        schemaElement = schema.getFieldDefinition(coordinates("Query", "a"))
        location = SourceLocation.getLocation(schemaElement)

        then:
        location.sourceName == "sourceName"
        location.line == 3
        location.column == 17

        when:
        schemaElement = schema.getFieldDefinition(coordinates("Query", "a")).getType()
        // unwrapped
        location = SourceLocation.getLocation(schemaElement)

        then:
        location.sourceName == "sourceName"
        location.line == 6
        location.column == 13

        when:
        schemaElement = schema.getType("A")
        location = SourceLocation.getLocation(schemaElement)

        then:
        location.sourceName == "sourceName"
        location.line == 6
        location.column == 13


    }
}

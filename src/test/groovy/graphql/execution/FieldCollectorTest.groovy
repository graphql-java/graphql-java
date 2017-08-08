package graphql.execution

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.MockedWiringFactory
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

import static graphql.execution.FieldCollectorParameters.newParameters

class FieldCollectorTest extends Specification {


    GraphQLSchema createSchema(String schema) {
        def registry = new SchemaParser().parse(schema)
        return new SchemaGenerator().makeExecutableSchema(registry,
                RuntimeWiring.newRuntimeWiring().wiringFactory(new MockedWiringFactory()).build())
    }

    def "collect fields"() {
        given:
        def schema = createSchema("""
            type Query {
                bar1: String
                bar2: String 
                }
                """)
        def fieldsContainer = schema.getType("Query") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .fieldsContainer(fieldsContainer)
                .build()
        Document document = new Parser().parseDocument("{foo {bar1 bar2 }}")
        Field field = ((OperationDefinition) document.children[0]).selectionSet.selections[0] as Field

        def bar1 = field.selectionSet.selections[0]
        def bar2 = field.selectionSet.selections[1]

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, [field])

        then:
        result['bar1'] == [bar1]
        result['bar2'] == [bar2]

    }
}

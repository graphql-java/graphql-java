package graphql.introspection

import graphql.Scalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.MockedWiringFactory
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import groovy.json.JsonSlurper
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

class IntrospectionBugTest extends Specification {

    def "1509 - can load data and generate schema from json"() {

        when:
        // this json cam from a customer report
        def schemaFile = getClass().getClassLoader().getResourceAsStream("introspection/1509-second-bug-data.json")
        def json = new JsonSlurper().parse(schemaFile)
        def schema = new IntrospectionResultToSchema().createSchemaDefinition(json["data"] as Map<String, Object>)

        then:
        def printedSchema = new SchemaPrinter().print(schema)
        printedSchema != null

        when:
        def schemaProvider = new StringReader(printedSchema)
        def parser = new SchemaParser()
        def schemaGenerator = new SchemaGenerator()
        def typeRegistry = parser.parse(schemaProvider)
        def runtimeWiring = newRuntimeWiring().wiringFactory(new MockedWiringFactory() {
            @Override
            boolean providesScalar(ScalarWiringEnvironment environment) {
                environment.scalarTypeDefinition.getName() == "DateTime"
            }

            @Override
            GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
                Scalars.GraphQLString
            }
        }).build()

        def graphqlSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring)

        then:
        graphqlSchema != null
    }
}

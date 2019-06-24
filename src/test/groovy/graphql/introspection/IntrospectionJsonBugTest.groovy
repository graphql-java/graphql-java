package graphql.introspection

import graphql.Scalars
import graphql.language.Document
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.MockedWiringFactory
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.json.JsonSlurper
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

class IntrospectionJsonBugTest extends Specification {

    Document createSchemaDoc(String fileName) {
        // this json cam from a customer report
        def schemaFile = getClass().getClassLoader().getResourceAsStream(fileName)
        def json = new JsonSlurper().parse(schemaFile)
        def schemaDoc = new IntrospectionResultToSchema().createSchemaDefinition(json["data"] as Map<String, Object>)
        schemaDoc
    }

    TypeDefinitionRegistry parseTypes(String printedSchema) {
        def schemaProvider = new StringReader(printedSchema)
        def parser = new SchemaParser()
        def typeRegistry = parser.parse(schemaProvider)
        typeRegistry
    }

    //
    // custom scalars that are present in the json schema
    //
    static RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(new MockedWiringFactory() {
        @Override
        boolean providesScalar(ScalarWiringEnvironment environment) {
            def name = environment.scalarTypeDefinition.getName()
            ["DateTime", "Date", "Blob", "BigInt", "HTML", "URI",
             "GitObjectID", "GitTimestamp", "X509Certificate", "GitSSHRemote"].contains(name)
        }

        @Override
        GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
            Scalars.GraphQLString
        }
    }).build()

    def options = SchemaPrinter.Options.defaultOptions().includeScalarTypes(true)
    def schemaPrinter = new SchemaPrinter(options)


    def "1509 - can load data and generate schema from json first bug"() {

        when:
        // this json cam from a customer report
        def schema = createSchemaDoc("introspection/1509-first-bug-data.json")

        then:
        def printedSchema = schemaPrinter.print(schema)
        printedSchema != null

        when:
        def typeRegistry = parseTypes(printedSchema)
        def graphqlSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)

        then:
        graphqlSchema != null
    }

    def "1509 - can load data and generate schema from json second bug"() {

        when:
        // this json cam from a customer report
        def schema = createSchemaDoc("introspection/1509-second-bug-data.json")

        then:
        def printedSchema = schemaPrinter.print(schema)
        printedSchema != null

        when:
        def typeRegistry = parseTypes(printedSchema)
        def graphqlSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)

        then:
        graphqlSchema != null
    }
}

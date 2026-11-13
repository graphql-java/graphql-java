package graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class Issue2114 extends Specification {

    def "allow use of repeatable directives on extensions"() {
        given:
        def spec = "type Query {}" +
                "directive @IamRepeatable repeatable on FIELD_DEFINITION" +
                " extend type Query { " +
                "   test: String" +
                "        @IamRepeatable" +
                "        @IamRepeatable" +
                "}"

        when:
        def registry = new SchemaParser().parse(spec)
        def graphQLSchema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build())

        then:
        graphQLSchema != null
    }
}


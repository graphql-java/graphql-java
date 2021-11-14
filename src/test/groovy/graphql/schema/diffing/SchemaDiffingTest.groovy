package graphql.schema.diffing

import spock.lang.Specification

import static graphql.TestUtil.schema

class SchemaDiffingTest extends Specification {


    def "test"() {
        given:
        def schema = schema("""
           type Query {
            hello: String
           } 
        """)

        when:
        def schemaGraph = new SchemaDiffing().createGraph(schema)

        then:
        schemaGraph.size() == 64

    }

    def "test ged"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            hello2: String
           } 
        """)

        when:
        new SchemaDiffing().diff(schema1, schema2)

        then:
        true

    }
}

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
}

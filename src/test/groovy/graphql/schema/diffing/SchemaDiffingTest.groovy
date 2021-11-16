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
        new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        true

    }

    def "test ged2"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: Foo
           } 
           type Foo {
            foo: String 
           }
        """)
        def schema2 = schema("""
           type Query {
            hello2: Foo2
           } 
           type Foo2 {
            foo2: String 
           }
        """)

        when:
        new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        true

    }

    def "delete a field ged2"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
            toDelete: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            hello: String
           } 
        """)

        when:
        new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        true

    }

    def "test example schema"() {
        given:
        def source = buildSourceGraph()
        def target = buildTargetGraph()
        when:
        new SchemaDiffing().diffImpl(source, target)
        then:
        true
    }

    SchemaGraph buildTargetGraph() {
        SchemaGraph targetGraph = new SchemaGraph();
        def a_1 = new Vertex("A", "u1")
        def d = new Vertex("D", "u2")
        def a_2 = new Vertex("A", "u3")
        def a_3 = new Vertex("A", "u4")
        def e = new Vertex("E", "u5")
        targetGraph.addVertex(a_1);
        targetGraph.addVertex(d);
        targetGraph.addVertex(a_2);
        targetGraph.addVertex(a_3);
        targetGraph.addVertex(e);

        targetGraph.addEdge(new Edge(a_1, d, "a"))
        targetGraph.addEdge(new Edge(d, a_2, "a"))
        targetGraph.addEdge(new Edge(a_2, a_3, "a"))
        targetGraph.addEdge(new Edge(a_3, e, "a"))
        targetGraph

    }

    SchemaGraph buildSourceGraph() {
        SchemaGraph sourceGraph = new SchemaGraph();
        def c = new Vertex("C", "v5")
        def a_1 = new Vertex("A", "v1")
        def a_2 = new Vertex("A", "v2")
        def a_3 = new Vertex("A", "v3")
        def b = new Vertex("B", "v4")
        sourceGraph.addVertex(a_1);
        sourceGraph.addVertex(a_2);
        sourceGraph.addVertex(a_3);
        sourceGraph.addVertex(b);
        sourceGraph.addVertex(c);

        sourceGraph.addEdge(new Edge(c, a_1, "b"))
        sourceGraph.addEdge(new Edge(a_1, a_2, "a"))
        sourceGraph.addEdge(new Edge(a_2, a_3, "a"))
        sourceGraph.addEdge(new Edge(a_3, b, "a"))
        sourceGraph

    }
}

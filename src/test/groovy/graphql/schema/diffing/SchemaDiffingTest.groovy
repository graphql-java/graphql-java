package graphql.schema.diffing

import graphql.TestUtil
import graphql.language.OperationDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.SchemaTransformer
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.TestUtil.schema

class SchemaDiffingTest extends Specification {


    def "test schema generation"() {
        given:
        def schema = schema("""
           type Query {
            hello: String
           } 
        """)

        when:
        def schemaGraph = new SchemaGraphFactory().createGraph(schema)

        then:
        schemaGraph.size() == 132

    }

    def "test rename field"() {
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
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 1

    }

    def "test two field renames one type rename"() {
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
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 4

    }

    def "test field type change"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: Boolean
           } 
        """)
        def schema2 = schema("""
           type Query {
            hello: String
           } 
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        /**
         * Deleting the edge from __DUMMY_TYPE_VERTICE to Boolean
         * Insert the edge from __DUMMY_TYPE_VERTICE to String
         */
        diff.size() == 2

    }

    def "change object type name used once"() {
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
            hello: Foo2
           } 
           type Foo2 {
            foo: String 
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 2

    }

    def "remove Interface from Object"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: Foo
            hello2: Foo2
           } 
           interface Node {
                id: ID
           }
           type Foo implements Node{
               id: ID
           }
           type Foo2 implements Node{
               id: ID
           }
        """)
        def schema2 = schema("""
           type Query {
            hello: Foo
            hello2: Foo2
           } 
           interface Node {
                id: ID
           }
           type Foo implements Node{
               id: ID
           }
           type Foo2 {
               id: ID
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 1

    }

    def "change Object into an Interface"() {
        given:
        def schema1 = schema("""
           type Query {
            luna: Pet
           } 
           type Pet {
                name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            luna: Pet
           } 
           interface Pet {
                name: String
           }
           type Dog implements Pet {
                name: String
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        /**
         * 1. Change type Pet into Interface Pet
         * 2,3,4: Insert Object Dog, Insert Field name, Insert __DUMMY_TYPE_VERTICE
         * 5. Insert Edge from Object Dog to Field name
         * 6,7 Insert Edge from Field name to DUMMY_TYPE_VERTICE to Scalar String
         * 8. Insert 'implements' Edge from Object Pet to Interface Pet
         */
        diff.size() == 8

    }

    def "change large schema a bit"() {
        given:
        def largeSchema = TestUtil.schemaFromResource("large-schema-2.graphqls", TestUtil.mockRuntimeWiring)
        int counter = 0;
        def changedOne = SchemaTransformer.transformSchema(largeSchema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.getName() == "field50") {
                    counter++;
                    return changeNode(context, fieldDefinition.transform({ it.name("field50Changed") }))
                }
                return TraversalControl.CONTINUE
            }
        })
        println "changed fields: " + counter
        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(largeSchema, changedOne)
        then:
        diff.size() == 171
    }

    def "change object type name used twice"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: Foo
            hello2: Foo
           } 
           type Foo {
            foo: String 
           }
        """)
        def schema2 = schema("""
           type Query {
            hello: Foo2
            hello2: Foo2
           } 
           type Foo2 {
            foo: String 
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 3

    }

    def "change directive not applied"() {
        given:
        def schema1 = schema("""
           directive @foo on FIELD_DEFINITION  
           type Query {
            hello: String 
           } 
        """)
        def schema2 = schema("""
           directive @foo2 on FIELD_DEFINITION  
           type Query {
            hello: String
           } 
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 1

    }

    def "change directive which is also applied"() {
        given:
        def schema1 = schema("""
           directive @foo on FIELD_DEFINITION  
           type Query {
            hello: String @foo 
           } 
        """)
        def schema2 = schema("""
           directive @foo2 on FIELD_DEFINITION  
           type Query {
            hello: String @foo2
           } 
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 2

    }

    def "delete a field"() {
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
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 5
    }

    def "changing schema a lot"() {
        given:
        def schema1 = schema("""
           type Query {
            pets: [Pet]
           } 
           interface Pet {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pets: [Animal] @deprecated
            animals: [Animal]
           } 
           interface Animal {
            name: String
            friend: Human
           }
           type Human {
                name: String
           }
           interface Pet implements Animal {
            name: String
            friend: Human
           }
           type Dog implements Pet & Animal {
            name: String
            friend: Human
           }
           type Cat implements Pet & Animal {
            name: String
            friend: Human
           }
           type Fish implements Pet & Animal {
            name: String
            friend: Human
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 58

    }

    def "adding a few things "() {
        given:
        def schema1 = schema("""
           type Query {
            pets: [Pet]
           } 
           interface Pet {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pets: [Pet] 
            animals: [Animal]
           } 
           interface Animal {
            name: String
           }
           interface Pet  {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
           type Fish implements Pet{
            name: String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 18
    }

    def "adding a few things plus introducing new interface"() {
        given:
        def schema1 = schema("""
           type Query {
            pets: [Pet]
           } 
           interface Pet {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pets: [Pet] 
            animals: [Animal]
           } 
           interface Animal {
            name: String
           }
           interface Pet  implements Animal {
            name: String
           }
           type Dog implements Pet & Animal {
            name: String
           }
           type Cat implements Pet & Animal {
            name: String
           }
           type Fish implements Pet & Animal {
            name: String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 22
    }

    def "adding a few things plus introducing new interface plus changing return type"() {
        given:
        def schema1 = schema("""
           type Query {
            pets: [Pet]
           } 
           interface Pet {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pets: [Animal] 
            animals: [Animal]
           } 
           interface Animal {
            name: String
           }
           interface Pet  implements Animal {
            name: String
           }
           type Dog implements Pet & Animal {
            name: String
           }
           type Cat implements Pet & Animal {
            name: String
           }
           type Fish implements Pet & Animal {
            name: String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 24
    }

    def "adding a few things plus introducing new interface plus changing return type plus adding field in Interface"() {
        given:
        def schema1 = schema("""
           type Query {
            pets: [Pet]
           } 
           interface Pet {
            name: String
           }
           type Dog implements Pet {
            name: String
           }
           type Cat implements Pet {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pets: [Pet] 
           } 
           interface Animal {
            name: String
            friend: String 
           }
           interface Pet implements Animal {
            name: String
            friend: String
           }
           type Dog implements Pet & Animal {
            name: String
            friend: String
           }
           type Cat implements Pet & Animal {
            name: String
            friend: String
           }
           type Fish implements Pet & Animal {
            name: String
            friend: String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 42
    }

    def "add a field"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            hello: String
            newField: String
           } 
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 5

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

    def "test example schema 2"() {
        given:
        def source = sourceGraph2()
        def target = targetGraph2()
        when:
        new SchemaDiffing().diffImpl(source, target)
        then:
        true
    }

    SchemaGraph sourceGraph2() {
        def source = new SchemaGraph()
        Vertex a = new Vertex("A")
        source.addVertex(a)
        Vertex b = new Vertex("B")
        source.addVertex(b)
        Vertex c = new Vertex("C")
        source.addVertex(c)
        Vertex d = new Vertex("D")
        source.addVertex(d)
        source.addEdge(new Edge(a, b))
        source.addEdge(new Edge(b, c))
        source.addEdge(new Edge(c, d))
        source
    }

    SchemaGraph targetGraph2() {
        def target = new SchemaGraph()
        Vertex a = new Vertex("A")
        Vertex d = new Vertex("D")
        target.addVertex(a)
        target.addVertex(d)
        target
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

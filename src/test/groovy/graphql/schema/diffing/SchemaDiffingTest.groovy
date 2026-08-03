package graphql.schema.diffing

import graphql.TestUtil
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
        schemaGraph.getVerticesByType().keySet().size() == 8
        schemaGraph.getVerticesByType(SchemaGraph.SCHEMA).size() == 1
        schemaGraph.getVerticesByType(SchemaGraph.OBJECT).size() == 7
        schemaGraph.getVerticesByType(SchemaGraph.ENUM).size() == 2
        schemaGraph.getVerticesByType(SchemaGraph.ENUM_VALUE).size() == 27
        schemaGraph.getVerticesByType(SchemaGraph.INTERFACE).size() == 0
        schemaGraph.getVerticesByType(SchemaGraph.UNION).size() == 0
        schemaGraph.getVerticesByType(SchemaGraph.SCALAR).size() == 2
        schemaGraph.getVerticesByType(SchemaGraph.FIELD).size() == 40
        schemaGraph.getVerticesByType(SchemaGraph.ARGUMENT).size() == 11
        schemaGraph.getVerticesByType(SchemaGraph.INPUT_FIELD).size() == 0
        schemaGraph.getVerticesByType(SchemaGraph.INPUT_OBJECT).size() == 0
        schemaGraph.getVerticesByType(SchemaGraph.DIRECTIVE).size() == 7
        schemaGraph.getVerticesByType(SchemaGraph.APPLIED_ARGUMENT).size() == 0
        schemaGraph.getVerticesByType(SchemaGraph.APPLIED_DIRECTIVE).size() == 0
        schemaGraph.size() == 97

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
        diff.each { println it }
        then:
        diff.size() == 1

    }

    def "test rename field 2"() {
        given:
        def schema1 = schema("""
           type Query {
            fixed: String
            hello: String
            f3(arg3: String): String
           } 
           type O1 {
              f1(arg1: String, x: String): String
           }
           
        """)
        def schema2 = schema("""
           type Query {
            hello2: String
            fixed: String
            f3(arg4: String): String
           } 
           type O2 {
            f2(arg2: String, y: String): String
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        diff.each { println it }
        then:
        diff.size() == 6

    }

    def "adding fields and rename and delete"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
            toDelete:String
            newField: String
            newField2: String
           } 
           type Mutation {
            unchanged: Boolean
            unchanged2: Other
           }
           type Other {
            id: ID
           }
        """)
        def schema2 = schema("""
           type Query {
            helloRenamed: String
            newField: String
            newField2: String
           } 
           type Mutation {
            unchanged: Boolean
            unchanged2: Other
           }
           type Other {
            id: ID
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        diff.each { println it }
        then:
        diff.size() == 4

    }

    def "remove field and rename type"() {
        given:
        def schema1 = schema("""
           type Query {
            foo: Foo
           } 
           type Foo {
              bar: Bar
              toDelete:String
           }
           type Bar {
              id: ID
              name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            foo: FooRenamed
           } 
           type FooRenamed {
              bar: Bar
           }
           type Bar {
              id: ID
              name: String
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        diff.each { println it }
        then:
        diff.size() == 5

    }

    def "renamed field and added field and type"() {
        given:
        def schema1 = schema("""
           type Query {
            foo: Foo
           } 
           type Foo {
              foo:String
           }
        """)
        def schema2 = schema("""
           type Query {
            foo: Foo
           } 
           type Foo {
              fooRenamed:String
              bar: Bar
           }
           type Bar {
              id: String
              name: String
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        diff.each { println it }
        then:
        /**
         * 1: Changed Field
         * 2: New Object
         * 3-8: Three new Fields + DummyTypes
         * 9-17: Edges from Object to new Fields (3) + Edges from Field to Dummy Type (3) + Edges from DummyType to String
         * */
        diff.size() == 11

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

    def "inserting interface with same name as previous object"() {
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
        for (EditOperation operation : diff) {
            println operation
        }

        then:
        /**
         * If we would allow to map Object to Interface this would have a result of 8
         */
        diff.size() == 8

    }

    def "remove scalars and add Enums"() {
        given:
        def schema1 = schema("""
        scalar S1
        scalar S2
        scalar S3    
       enum E1{
        E1
       }
       type Query {
           s1: S1
           s2: S2
           s3: S3
           e1: E1
       } 
        """)
        def schema2 = schema("""
           enum E1{
            E1
           }
           enum E2{
            E2
           }
           type Query {
           e1: E1
           e2: E2
           } 
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        for (EditOperation operation : diff) {
            println operation
        }

        then:
        diff.size() == 15

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

    def "change large schema a bit 2"() {
        given:
        def largeSchema = TestUtil.schemaFromResource("large-schema-2.graphqls", TestUtil.mockRuntimeWiring)
        int counter = 0;
        def changedOne = SchemaTransformer.transformSchema(largeSchema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.getName() == "field50") {
                    counter++;
                    return deleteNode(context);
                }
                return TraversalControl.CONTINUE
            }
        })
        println "deleted fields: " + counter
        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(largeSchema, changedOne)
        diff.each { println it }
        then:
        // deleting 171 fields + dummyTypes + 3 edges for each field,dummyType pair = 5*171
        diff.size() == 3 * 171
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

        def diffing = new SchemaDiffing()
        when:
        def diff = diffing.diffGraphQLSchema(schema1, schema2)
        for (EditOperation editOperation : diff) {
            println editOperation
        }

        then:
        diff.size() == 3
    }


    def "added different types and fields"() {
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
        diff.each { println it }

        then:
        diff.size() == 41

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
        operations.size() == 12
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
        operations.size() == 16
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
        operations.size() == 18
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
        operations.size() == 28
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
        diff.size() == 3

    }

    def "add a field and Type"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            hello: String
            newField: Foo
           } 
           type Foo {
              foo: String 
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        diff.each { println it }

        then:
        diff.size() == 7

    }

    def "add a field and Type and remove a field"() {
        given:
        def schema1 = schema("""
           type Query {
            hello: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            newField: Foo
           } 
           type Foo {
              foo: String 
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        diff.size() == 7

    }


    def "change a Union "() {
        given:
        def schema1 = schema("""
           type Query {
            pet: Pet
           } 
           union Pet = Dog | Cat
           type Dog {
            name: String
           }
           type Cat {
            name: String
           }
        """)
        def schema2 = schema("""
           type Query {
            pet: Pet
           } 
           union Pet = Dog | Bird | Fish
           type Dog {
            name: String
           }
           type Bird {
            name: String
           }
           type Fish {
            name: String
           }
        """)

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        /**
         * 1. Change Cat to Bird
         * 2,3: Insert Fish, Insert Fish.name
         * 4. Insert Edge from Fish to Fish.name
         * 5 Insert Edge from Fish.name -> String
         * 6. Insert edge from Pet -> Fish
         */
        diff.size() == 6

    }

    def "adding an argument "() {
        given:
        def schema1 = schema("""
           type Query {
            foo: String
           } 
        """)
        def schema2 = schema("""
           type Query {
            foo(arg: Int): String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 4
    }

    def "changing an argument "() {
        given:
        def schema1 = schema("""
           type Query {
            foo(arg: Int): String
           } 
        """)
        def schema2 = schema("""
           type Query {
            foo(arg2: Boolean): String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 4
    }

    def "input fields"() {
        given:
        def schema1 = schema("""
           type Query {
            foo(arg: I1, arg2: I2): String
           } 
           input I1 {
               f1: String
               f2: String
           }
           input I2 {
               g1: String
               g2: String
           }
        """)
        def schema2 = schema("""
           type Query {
            foo(arg: I1,arg2: I2 ): String
           }
           input I1 {
               f1: String
           }
           input I2 {
               g2: String
               g3: String
               g4: String
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        /**
         * The test here is that f2 is deleted and one g is renamed and g3 is inserted.
         * It would be less operations with f2 renamed to g3, but this would defy expectations.
         *
         */
        operations.size() == 7
    }

    def "arguments in fields"() {
        given:
        def schema1 = schema("""
           type Query {
            a(f1: String, f2:String): String
            b(g1: String, g2:String): O1
           } 
           type O1 {
            c(h1: String, h2:String): String
            d(i1: String, i2:String): O1
           }
        """)
        def schema2 = schema("""
           type Query {
            a(f1: String): String
            b(g2: String, g3:String, g4: String): String
           }
           type O1 {
            c(h1: String, h2:String): String
            renamed(i2: String, i3:String): O1
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        /**
         * Query.f2 deleted
         * O1.b.g1 => O1.b.g4
         * O1.d.i1 -> O.renamed.i3
         * O1.d => O1.renamed
         * Inserted O1.b.g3
         */
        operations.size() == 11
    }

    def "same arguments in different contexts"() {
        given:
        def schema1 = schema("""
           type Query {
               foo(someArg:String): String
           } 
        """)
        def schema2 = schema("""
           type Query {
            field1(arg1: String): String
            field2(arg1: String): String
            field3(arg1: String): String
           } 
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 14
    }

    def "adding enum value"() {
        given:
        def schema1 = schema("""
           type Query {
            foo: Foo
           } 
           enum Foo {
                V1
                V2
           }
        """)
        def schema2 = schema("""
           type Query {
            foo: Foo
           } 
           enum Foo {
                V1
                V2
                V3
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 2
    }

    def "rename enum value"() {
        given:
        def schema1 = schema("""
           type Query {
            foo: Foo
           } 
           enum Foo {
                V1
                V2
           }
        """)
        def schema2 = schema("""
           type Query {
            foo: Foo
           } 
           enum Foo {
                V1
                V3
           }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 1
    }

    def "arguments in directives changed"() {
        given:
        def schema1 = schema('''
            directive @d(a1: String, a2: String) on FIELD_DEFINITION
            type Query {
                foo: String @d
            }
        ''')
        def schema2 = schema("""
            directive @d(a1: String, a3: String, a4: String) on FIELD_DEFINITION
            type Query {
                foo: String @d
            }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        /**
         * change: a2 => a3
         * insert: a4
         * new edge from directive to a4
         * new edge from a4 to String
         */
        operations.size() == 4
    }

    def "change applied argument"() {
        given:
        def schema1 = schema('''
            directive @d(a1: String, a2: String) on FIELD_DEFINITION
            type Query {
                foo: String @d(a1: "S1", a2: "S2")
            }
        ''')
        def schema2 = schema("""
            directive @d(a1: String, a2: String) on FIELD_DEFINITION
            type Query {
                foo: String @d(a2: "S2Changed", a1: "S1Changed")
            }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 2
    }

    def "applied arguments in different contexts"() {
        given:
        def schema1 = schema('''
            directive @d(a1: String, a2: String, b1: String, b2: String, b3: String, b4: String) on FIELD_DEFINITION
            type Query {
                foo: String @d(a1: "a1", a2: "a2")
                foo2: String @d(b1: "b1", b2: "b2")
            }
        ''')
        def schema2 = schema("""
            directive @d(a1: String, a2: String, b1: String, b2: String, b3: String, b4: String) on FIELD_DEFINITION
            type Query {
                foo: String @d(a1: "a1")
                foo2: String @d(b2: "b2", b3: "b3", b4: "b4")
            }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        /**
         * The test here is that the context of the applied argument is considered and that a2 is deleted and one b is inserted and another one changed.
         * Note: this is not longer true
         */
        operations.size() == 8
    }

    def "with directives"() {
        given:
        def schema1 = schema('''
            directive @TopLevelType on OBJECT
            directive @specialId(type: String) on ARGUMENT_DEFINITION
            type Query {
                foo: Foo
            }
            type Foo @TopLevelType {
              user(location: ID! @specialId(type : "someId"), limit: Int = 25, start: Int, title: String): PaginatedList
            }
            type PaginatedList {
              count: Int
            }
        ''')
        def schema2 = schema("""
            directive @TopLevelType on OBJECT
            directive @specialId(type: String) on ARGUMENT_DEFINITION
            type Query {
                foo: Foo
            }
            type Foo @TopLevelType {
              user(location: ID! @specialId(type : "someId"), limit: Int = 25, start: Int, title: String): PaginatedList
              other(after: String, favourite: Boolean,  first: Int = 25, location: ID! @specialId(type : "someId"), label: [String], offset: Int, status: String, type: String): PaginatedList
            }
            type PaginatedList {
              count: Int
            }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)

        then:
        operations.size() == 31
    }

    def "built in directives"() {
        given:
        def schema1 = schema('''
            directive @specialId(type: String) on FIELD_DEFINITION
            
            
            type Query {
                hello: String @specialId(type: "someId")
            }
        ''')
        def schema2 = schema("""
            directive @specialId(type: String) on FIELD_DEFINITION
                
            type Query {
                renamedHello: String @specialId(type: "otherId")
            }
        """)

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 2
    }

    def "unchanged scheme"() {
        given:
        def schema1 = schema('''
            directive @specialId(type: String) on FIELD_DEFINITION
            directive @Magic(owner: String!, type: String!) on FIELD_DEFINITION | ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION

            
            type Query {
                hello: String @specialId(type: "someId")
                foo(arg: Int, arg2: String = "hello"): [Foo]!
                old: Boolean @deprecated
                someOther(input1: MyInput, input2: OtherInput): E
            }
            type Foo { 
                id: ID
                e1: E 
                union: MyUnion
            } 
            union MyUnion = Foo | Bar
            type Bar {
                id: ID
            }
            enum E {
                E1, E2, E3
            }
            input MyInput {
                id: ID
                other: String! @Magic(owner: "Me", type: "SomeType")
            }
            input OtherInput {
                inputField1: ID! @Magic(owner: "O1", type: "T1")
                inputField2: ID! @Magic(owner: "O2", type: "T2")
            }
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema1)
        operations.each { println it }

        then:
        operations.size() == 0
    }

    def "changed query operation type "() {
        given:
        def schema1 = schema('''
            type Query {
                foo: String
            }
            type MyQuery {
                foo: String
            } 
        ''')
        def schema2 = schema('''
            schema {
                query: MyQuery
            }
            type Query {
                foo: String
            }
            type MyQuery {
                foo: String
            } 
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        // delete edge and insert new one
        operations.size() == 2
    }

    def "applied schema directives"() {
        given:
        def schema1 = schema('''
            directive @foo(arg: String) on SCHEMA
            
            schema @foo(arg: "bar") {
                query: MyQuery
            }
            type MyQuery {
                foo: String
            } 
        ''')
        def schema2 = schema('''
            directive @foo(arg: String) on SCHEMA
            
            schema @foo(arg: "barChanged") {
                query: MyQuery
            }
            type MyQuery {
                foo: String
            } 
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        // applied argument changed
        operations.size() == 1

    }

    def "change description"() {
        given:
        def schema1 = schema('''
            "Hello World"
            type Query {
                "helloDesc"
                hello: String
            } 
        ''')
        def schema2 = schema('''
            "Hello World now"
            type Query {
                "helloDescChanged"
                hello: String
            } 
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 2

    }

    def "change default value"() {
        given:
        def schema1 = schema('''
            input I {
                someNumber: Int = 100 
            }
            type Query {
                hello(arg: String = "defaultValue", i: I): String
            } 
        ''')
        def schema2 = schema('''
            input I {
                someNumber: Int = 200 
            }
            type Query {
                hello(arg: String = "defaultValueChanged",i: I): String
            } 
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 2

    }

    def "change field type, but not the wrapped type "() {
        given:
        def schema1 = schema('''
            type Query {
                hello: String
                hello2: String
            } 
        ''')
        def schema2 = schema('''
            type Query {
                hello: String!
                hello2: [[String!]]
            } 
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 2
        operations.findAll({ it.operation == EditOperation.Operation.CHANGE_EDGE }).size() == 2

    }

    def "Recursive input field with default  "() {
        given:
        def schema1 = schema('''
            input I {
                name: String
                field: I = {name: "default name", field: null}
            }
            type Query {
                foo(arg: I): String
            }
        ''')
        def schema2 = schema('''
            input I {
                name: String
                field: [I] = [{name: "default name", field: null}]
            }
            type Query {
                foo(arg: I): String
            }
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        // changing the label of the edge to the type
        operations.size() == 1
        operations.findAll({ it.operation == EditOperation.Operation.CHANGE_EDGE }).size() == 1

    }

    def "directive argument default value changed"() {
        given:
        def schema1 = schema('''
        type Query {
            foo: String
        }
        directive @d(foo:String = "A") on FIELD
        ''')
        def schema2 = schema('''
        type Query {
            foo: String
        }
        directive @d(foo: String = "B") on FIELD
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        // changing the label of the edge to the type
        operations.size() == 1
        operations.findAll({ it.operation == EditOperation.Operation.CHANGE_EDGE }).size() == 1


    }

    def "object applied directive argument change"() {
        given:
        def schema1 = schema('''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }

        ''')
        def schema2 = schema('''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "bar")
        }
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 1
    }

    def "object applied directive rename"() {
        given:
        def schema1 = schema('''
        directive @d1(arg:String) on FIELD_DEFINITION
        directive @d2(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d1(arg: "foo")
        }

        ''')
        def schema2 = schema('''
        directive @d1(arg:String) on FIELD_DEFINITION
        directive @d2(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d2(arg: "foo")
        }
        ''')

        when:
        def operations = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        operations.each { println it }

        then:
        operations.size() == 1
    }


    /*
     * The schema can't be mapped at the moment because
     * the arguments mapping doesn't work.
     * The PossibleMappingCalculator finds two context: "Object.Query" (with one argument vertex) which is deleted
     * and "Object.Foo" (with two argument vertices) which is added. Therefore one isolated vertex is added in the source
     * to align both context.
     *
     * But the parent restrictions dictate that the target parent of i1 must be Query.foo, because Query.echo is fixed mapped
     * to Query.foo. But that would mean i1 is deleted, but there is no isolated argument vertex for the target because of
     * the contexts. So there is no possible mapping and the exception is thrown.
     */

    def "bug produced well known exception"() {
        given:
        def schema1 = schema('''
    type Query {
      echo(i1: String): String
    }
    ''')
        def schema2 = schema('''
    type Query {
      foo: Foo
    }
    type Foo {
      a(i2: String): String
      b(i3: String): String
    }
''')

        when:
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2)
        then:
        def e = thrown(RuntimeException)
        e.message.contains("bug: ")
    }

}



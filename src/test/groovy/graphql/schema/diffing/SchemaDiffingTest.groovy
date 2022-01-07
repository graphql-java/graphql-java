package graphql.schema.diffing

import graphql.TestUtil
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.SchemaTransformer
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.TestUtil.schema
import static graphql.TestUtil.schemaFromResource

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
        diff.each { println it }
        then:
        diff.size() == 1

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
        diff.size() == 6

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
        diff.size() == 7

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
        diff.size() == 17

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
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2, false)

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
        diff.size() == 10

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
        diff.size() == 19

    }

    @Ignore
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

        def changeHandler = new SchemaChangedHandler() {
            @Override
            void fieldRemoved(String description) {
                println "field removed: " + description
            }
        }

        def diffing = new SchemaDiffing()
        when:
        def diff = diffing.diffGraphQLSchema(schema1, schema2)
        for (EditOperation editOperation : diff) {
            println editOperation
        }

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
        def diff = new SchemaDiffing().diffGraphQLSchema(schema1, schema2, false)
        diff.each {println it}

        then:
        diff.size() == 59

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

        then:
        diff.size() == 11

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
        diff.size() == 9

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
         * 2,3,4: Insert Fish, Insert Fish.name, Insert __DummyTypeVertice
         * 5. Insert Edge from Fish to Fish.name
         * 6.7. Insert Edge from Fish.name -> __DummyType --> String
         * 8. Insert edge from Pet -> Fish
         */
        diff.size() == 8

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
        operations.size() == 33
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
}



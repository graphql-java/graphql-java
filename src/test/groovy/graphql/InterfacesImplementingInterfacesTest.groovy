package graphql

import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.validation.InvalidSchemaException
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject

class InterfacesImplementingInterfacesTest extends Specification {
    def 'Simple interface implementing interface'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              id: ID!
              url: String
            }

            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When implementing interface does not declare required field, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              url: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The interface type 'Resource' [@n:n] does not have a field 'id' required via interface 'Node' [@n:n]")
    }

    def 'Transitively implemented interfaces defined in implementing interface'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }

            interface Image implements Resource & Node {
              id: ID!
              url: String
              thumbnail: String
            }
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }


    def 'Transitively implemented interfaces defined in implementing type'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }

            type Image implements Resource & Node {
              id: ID!
              url: String
              thumbnail: String
            }
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When not all transitively implemented interfaces are defined in implementing interface, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }

            interface Image implements Resource & Node {
              id: ID!
              url: String
              thumbnail: String
            }
            
            interface LargeImage implements Image & Resource {
              id: ID!
              url: String
              thumbnail: String
              large: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 2
        assertErrorMessage(error, "The interface type 'LargeImage' [@n:n] must implement 'Node' [@n:n] because it is implemented by 'Image' [@n:n]")
        assertErrorMessage(error, "The interface type 'LargeImage' [@n:n] must implement 'Node' [@n:n] because it is implemented by 'Resource' [@n:n]")
    }

    def 'When not all transitively implemented interfaces are defined in implementing type, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }

            type Image implements Resource {
              id: ID!
              url: String
              thumbnail: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The object type 'Image' [@n:n] must implement 'Node' [@n:n] because it is implemented by 'Resource' [@n:n]")
    }

    def 'When interface implements itself, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node implements Named & Node {
              id: ID!
              name: String
            }
            
            interface Named implements Node & Named {
              id: ID!
              name: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 2
        assertErrorMessage(error, "The interface type 'Node' [@n:n] cannot implement itself")
        assertErrorMessage(error, "The interface type 'Named' [@n:n] cannot implement itself")
    }

    def 'When interface extension implements interface and declares required field, then parsing is successful'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource {
              url: String
            }
            
            extend interface Resource implements Node {
                id: ID!
            }
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When interface extension implements interface but doesn\'t declare required field, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
              extraField: String
            }

            interface Resource {
              url: String
            }
            
            extend interface Resource implements Node {
                id: ID!
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The interface extension type 'Resource' [@n:n] does not have a field 'extraField' required via interface 'Node' [@n:n]")
    }

    def 'When object extension implements all transitive interfaces, then parsing is successful'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              id: ID!
              url: String
            }
            
            type Image {
                thumbnail: String!
            }
            
            extend type Image implements Node & Resource {
                id: ID!
                url: String
            }
            """
        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When object extension does not implement all transitive interfaces, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              id: ID!
              url: String
            }
            
            type Image {
                thumbnail: String!
            }
            
            extend type Image implements Resource {
                id: ID!
                url: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The object extension type 'Image' [@n:n] must implement 'Node' [@n:n] because it is implemented by 'Resource' [@n:n]")
    }

    def 'When interface extension implements all transitive interfaces, then parsing is successful'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              id: ID!
              url: String
            }
            
            interface Image {
                thumbnail: String!
            }
            
            extend interface Image implements Node & Resource {
                id: ID!
                url: String
            }
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When interface extension does not implement all transitive interfaces, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              id: ID!
              url: String
            }
            
            interface Image {
                thumbnail: String!
            }
            
            extend interface Image implements Resource {
                id: ID!
                url: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The interface extension type 'Image' [@n:n] must implement 'Node' [@n:n] because it is implemented by 'Resource' [@n:n]")
    }

    def 'When hierarchy results in circular reference, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Interface1
            }
            
            interface Interface1 implements Interface3 & Interface2 {
              field1: String
              field2: String
              field3: String
            }
            
            interface Interface2 implements Interface1 & Interface3 {
              field1: String
              field2: String
              field3: String
            }
            
            interface Interface3 implements Interface2 & Interface1 {
              field1: String
              field2: String
              field3: String
            }

            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)

        error.errors.size() == 6

        assertErrorMessage(error, "The interface type 'Interface1' [@n:n] cannot implement 'Interface2' [@n:n] as this would result in a circular reference")
        assertErrorMessage(error, "The interface type 'Interface1' [@n:n] cannot implement 'Interface3' [@n:n] as this would result in a circular reference")
        assertErrorMessage(error, "The interface type 'Interface2' [@n:n] cannot implement 'Interface3' [@n:n] as this would result in a circular reference")
        assertErrorMessage(error, "The interface type 'Interface2' [@n:n] cannot implement 'Interface1' [@n:n] as this would result in a circular reference")
        assertErrorMessage(error, "The interface type 'Interface3' [@n:n] cannot implement 'Interface1' [@n:n] as this would result in a circular reference")
        assertErrorMessage(error, "The interface type 'Interface3' [@n:n] cannot implement 'Interface2' [@n:n] as this would result in a circular reference")
    }

    def 'When interface doesn\'t implement transitive interface declared in extension, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Interface1
            }
            
            interface Interface1 implements Interface2 {
              field1: String
              field2: String
            }
            
            interface Interface2  {
              field2: String
            }
            
            interface Interface3 {
              field3: String
            }
            
            extend interface Interface2 implements Interface3 {
              field3: String
            }

            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1

        assertErrorMessage(
                error,
                "The interface type 'Interface1' [@n:n] must implement 'Interface3' [@n:n] because it is implemented by 'Interface2' [@n:n]"
        )
    }

    def 'When interface implements transitive interface declared in extension, then parsing succeeds'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Interface1
            }
            
            interface Interface1 implements Interface2 {
              field1: String
              field2: String
            }
            
            interface Interface2  {
              field2: String
            }
            
            interface Interface3 {
              field3: String
            }
            
            extend interface Interface2 implements Interface3 {
              field3: String
            }
            
            extend interface Interface1 implements Interface3 {
              field3: String
            }
            
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When field required by new extension implementation is declared in original interface type, then parsing succeeds'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Interface1
            }
            
            interface Interface1 {
              field1: String
              field2: String
            }
            
            interface Interface2  {
              field2: String
            }
            
            extend interface Interface1 implements Interface2
            
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When type declares interface and extension declares required field, then parsing succeeds'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Interface1
            }
            
            interface Interface1 implements Interface2 {
              field1: String
            }
            
            interface Interface2  {
              field2: String
            }
            
            extend interface Interface1 {
              field2: String
            }
            
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When interface implements same interface more than once via extensions, then parsing fails'() {
        when:
        def schema = """
           type Query {
              find(id: String!): Type1
           }
           
           type Type1 {
             field1: String
           }
           
           interface Interface2  {
             field20: String
             field21: String
           }
           
           extend type Type1 implements Interface2 {
             field20: String
           }
           
           extend type Type1 implements Interface2 {
             field21: String
           }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 2

        assertErrorMessage(error, "The object extension type 'Type1' [@n:n] can only implement 'Interface2' [@n:n] once.")
    }

    def 'When interface implements same interface more than once, then parsing fails'() {
        when:
        def schema = """
           type Query {
              find(id: String!): Type1
           }
           
           type Type1 implements Interface2 {
             field1: String
             field20: String
           }
           
           interface Interface2  {
             field20: String
             field21: String
           }
           
           extend type Type1 implements Interface2 {
             field21: String
           }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 2

        assertErrorMessage(error, "The object extension type 'Type1' [@n:n] can only implement 'Interface2' [@n:n] once.")
        assertErrorMessage(error, "The object type 'Type1' [@n:n] can only implement 'Interface2' [@n:n] once.")
    }

    def 'When interface implements interface and redefines non-null field as nullable, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              url: String
              id: ID
            }
            
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1

        assertErrorMessage(error, "The interface type 'Resource' [@n:n] has tried to redefine field 'id' defined via interface 'Node' [@n:n] from 'ID!' to 'ID'")
    }

    def 'When interface extension implements interface and redefines non-null field as nullable, then parsing fails'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource {
              url: String
            }
            
            extend interface Resource implements Node {
                id: ID
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1

        assertErrorMessage(error, "The interface extension type 'Resource' [@n:n] has tried to redefine field 'id' defined via interface 'Node' [@n:n] from 'ID!' to 'ID'")
    }

    def 'When interface implements interface and redefines nullable field as non-null, then parsing succeeds'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID
            }

            interface Resource implements Node {
              url: String
              id: ID!
            }
            
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When interface extension implements interface and redefines nullable field as non-null, then parsing succeeds'() {
        when:
        def schema = """
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID
            }

            interface Resource {
              url: String
            }
            
            extend interface Resource implements Node {
                id: ID!
            }
            """

        parseSchema(schema)

        then:
        noExceptionThrown()
    }

    def 'When interface extension implements interface and misses field arguments, then parsing fails'() {
        when:
        def schema = """
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            interface BaseInterface {
                fieldX : Int
            }

            extend interface BaseInterface implements InterfaceType {
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }
            
            type Query {
               mock: String
            }

            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 4

        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType' [@n:n]")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg1:String =\"defaultVal\"' to 'arg1:String =\"defaultValX\"")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg2:String' to 'arg2:String!")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg3:Int' to 'arg3:String")
    }

    def 'When interface implements interface and misses field arguments, then parsing fails'() {
        when:
        def schema = """
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            interface BaseInterface implements InterfaceType {
                fieldX : Int
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }
            
            type Query {
               mock: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 4

        assertErrorMessage(error, "The interface type 'BaseInterface' [@n:n] field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType' [@n:n]")
        assertErrorMessage(error, "The interface type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg1:String =\"defaultVal\"' to 'arg1:String =\"defaultValX\"")
        assertErrorMessage(error, "The interface type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg2:String' to 'arg2:String!")
        assertErrorMessage(error, "The interface type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg3:Int' to 'arg3:String")
    }

    def 'When interface implements interface via extension and misses field arguments, then parsing fails'() {
        when:
        def schema = """
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            interface BaseInterface {
                fieldX : Int
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }
            
            extend interface BaseInterface implements InterfaceType
            
            type BaseType {
                id: ID!
            }

            type Query {
               mock: String
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 4

        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType' [@n:n]")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg1:String =\"defaultVal\"' to 'arg1:String =\"defaultValX\"")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg2:String' to 'arg2:String!")
        assertErrorMessage(error, "The interface extension type 'BaseInterface' [@n:n] has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType' [@n:n] from 'arg3:Int' to 'arg3:String")
    }

    def 'Test query execution'() {
        given:
        def graphQLSchema = createComplexSchema()

        when:
        def result = GraphQL.newGraphQL(graphQLSchema).build().execute("""
            { 
                find { 
                    ... on Node {
                        id
                        ... on Resource {
                            __typename
                            url 
                            ... on Image { 
                                thumbnail 
                            } 
                            ... on File { 
                                path 
                            } 
                        }
                    }
                }
            }
        """)

        then:
        !result.errors
        result.data == [find: [
                [id: '1', url: 'https://image.com/1', thumbnail: 'TN', __typename: 'Image'],
                [id: '2', url: 'https://file.com/1', path: '/file/1', __typename: 'File']
        ]]
    }

    def 'Test introspection query'() {
        given:
        def graphQLSchema = createComplexSchema()

        when:
        def result = GraphQL.newGraphQL(graphQLSchema).build().execute("""
            { 
                nodeType: __type(name: "Node") {
                    possibleTypes {
                        kind
                        name
                    }
                }
                resourceType: __type(name: "Resource") {
                    possibleTypes {
                        kind
                        name
                    }
                    interfaces {
                        kind
                        name
                    }
                } 
                imageType: __type(name: "Image") {
                    interfaces {
                        kind
                        name
                    }
                }
            }
        """)

        then:
        !result.errors
        result.data == [
                nodeType    : [possibleTypes: [[kind: 'OBJECT', name: 'File'], [kind: 'OBJECT', name: 'Image']]],
                imageType   : [interfaces: [[kind: 'INTERFACE', name: 'Resource'], [kind: 'INTERFACE', name: 'Node']]],
                resourceType: [possibleTypes: [[kind: 'OBJECT', name: 'File'], [kind: 'OBJECT', name: 'Image']], interfaces: [[kind: 'INTERFACE', name: 'Node']]]
        ]
    }

    def "interfaces introspection field is empty list for interfaces"() {
        given:
        def graphQLSchema = createComplexSchema()

        when:
        def result = GraphQL.newGraphQL(graphQLSchema).build().execute("""
            { 
                nodeType: __type(name: "Node") {
                    interfaces {
                        kind
                        name
                    }
                }
            }
        """)

        then:
        !result.errors
        result.data == [
                nodeType: [interfaces: []]
        ]

    }

    def "interface type has a reference to implemented interfaces"() {
        when:
        def schema = createComplexSchema()
        def resourceType = schema.getType("Resource") as GraphQLInterfaceType

        then:
        resourceType.getInterfaces().size() == 1
        resourceType.getInterfaces().get(0) instanceof GraphQLInterfaceType
        resourceType.getInterfaces().get(0).getName() == "Node"

    }

    def "GraphQLInterfaceType can can implement interfaces"() {
        given:
        def node1Type = newInterface()
                .name("Node1")
                .field(newFieldDefinition().name("id1").type(GraphQLString).build())
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build();

        def node2Type = newInterface()
                .name("Node2")
                .field(newFieldDefinition().name("id2").type(GraphQLString).build())
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build();

        // references two interfaces: directly and via type ref
        def resource = newInterface()
                .name("Resource")
                .field(newFieldDefinition().name("id1").type(GraphQLString).build())
                .field(newFieldDefinition().name("id2").type(GraphQLString).build())
                .withInterface(GraphQLTypeReference.typeRef("Node1"))
                .withInterface(node2Type)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build();
        def image = newObject()
                .name("Image")
                .field(newFieldDefinition().name("id1").type(GraphQLString).build())
                .field(newFieldDefinition().name("id2").type(GraphQLString).build())
                .withInterface(resource)
                .withInterface(node1Type)
                .withInterface(node2Type)
                .build()
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("image").type(image).build())
                .build()
        def schema = GraphQLSchema.newSchema().query(query).additionalType(node1Type).build();

        when:
        def printedSchema = new SchemaPrinter().print(schema)

        then:
        printedSchema.contains("""
interface Node1 {
  id1: String
}

interface Node2 {
  id2: String
}

interface Resource implements Node1 & Node2 {
  id1: String
  id2: String
}

type Image implements Node1 & Node2 & Resource {
  id1: String
  id2: String
}

type Query {
  image: Image
}
""")
    }

    def "When programmatically created interface does not implement interface correctly, then creation fails"() {
        given:
        def interface1 = newInterface()
                .name("Interface1")
                .field(
                        newFieldDefinition().name("field1").type(GraphQLString)
                                .argument(newArgument().name("arg1").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .field(
                        newFieldDefinition().name("field3").type(GraphQLString)
                                .argument(newArgument().name("arg3").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field4").type(GraphQLString))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface2 = newInterface()
                .name("Interface2")
                .field(
                        newFieldDefinition().name("field1").type(GraphQLString)
                                .argument(newArgument().name("arg1").type(GraphQLInt))
                )
                .field(newFieldDefinition().name("field2").type(GraphQLInt))
                .field(newFieldDefinition().name("field3").type(GraphQLString))
                .withInterface(interface1)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("interface2").type(interface2).build())
                .build()


        when:
        GraphQLSchema.newSchema().query(query).build()

        then:
        def error = thrown(InvalidSchemaException)

        assertErrorMessage(error,
                "interface type 'Interface2' does not implement interface 'Interface1' because field 'field1' argument 'arg1' is defined differently",
                "interface type 'Interface2' does not implement interface 'Interface1' because field 'field2' is defined as 'Int' type and not as 'String' type",
                "interface type 'Interface2' does not implement interface 'Interface1' because field 'field3' is missing argument(s): 'arg3'",
                "interface type 'Interface2' does not implement interface 'Interface1' because field 'field4' is missing"
        )
    }

    def "When programmatically created interface implement interface correctly, then creation succeeds"() {
        given:
        def interface1 = newInterface()
                .name("Interface1")
                .field(
                        newFieldDefinition().name("field1").type(GraphQLString)
                                .argument(newArgument().name("arg1").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .field(
                        newFieldDefinition().name("field3").type(GraphQLString)
                                .argument(newArgument().name("arg3").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field4").type(GraphQLString))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface2 = newInterface()
                .name("Interface2")
                .field(
                        newFieldDefinition().name("field1").type(GraphQLString)
                                .argument(newArgument().name("arg1").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .field(
                        newFieldDefinition().name("field3").type(GraphQLString)
                                .argument(newArgument().name("arg3").type(GraphQLString))
                )
                .field(newFieldDefinition().name("field4").type(GraphQLString))
                .withInterface(interface1)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("interface2").type(interface2).build())
                .build()


        when:
        GraphQLSchema.newSchema().query(query).build()

        then:
        noExceptionThrown()
    }

    def "When programmatically created type does not implement all transitive interfaces, then creation fails"() {
        given:
        def interface1 = newInterface()
                .name("Interface1")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface2 = newInterface()
                .name("Interface2")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .withInterface(interface1)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def type = newObject()
                .name("Type")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .withInterface(interface2)
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("find").type(type).build())
                .build()


        when:
        GraphQLSchema.newSchema().query(query).build()

        then:
        def error = thrown(InvalidSchemaException)

        assertErrorMessage(error, "object type 'Type' must implement 'Interface1' because it is implemented by 'Interface2'")
    }

    def "When programmatically created type implement all transitive interfaces, then creation succeeds"() {
        given:
        def interface1 = newInterface()
                .name("Interface1")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface2 = newInterface()
                .name("Interface2")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .withInterface(interface1)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def type = newObject()
                .name("Type")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .field(newFieldDefinition().name("field2").type(GraphQLString))
                .withInterface(interface1)
                .withInterface(interface2)
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("find").type(type).build())
                .build()


        when:
        GraphQLSchema.newSchema().query(query).build()

        then:
        noExceptionThrown()
    }

    def "When interface implementation results in circular reference, then creation fails"() {
        given:
        def interface1 = newInterface()
                .name("Interface1")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .withInterface(GraphQLTypeReference.typeRef("Interface3"))
                .withInterface(GraphQLTypeReference.typeRef("Interface2"))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface2 = newInterface()
                .name("Interface2")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .withInterface(interface1)
                .withInterface(GraphQLTypeReference.typeRef("Interface3"))
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def interface3 = newInterface()
                .name("Interface3")
                .field(newFieldDefinition().name("field1").type(GraphQLString))
                .withInterface(interface1)
                .withInterface(interface2)
                .typeResolver({ env -> Assert.assertShouldNeverHappen() })
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("find").type(interface3).build())
                .build()


        when:
        GraphQLSchema.newSchema().query(query).build()

        then:
        def error = thrown(InvalidSchemaException)

        assertErrorMessage(error,
                "interface type 'Interface3' cannot implement 'Interface1' because that would result on a circular reference",
                "interface type 'Interface3' cannot implement 'Interface2' because that would result on a circular reference",
                "interface type 'Interface1' cannot implement 'Interface3' because that would result on a circular reference",
                "interface type 'Interface1' cannot implement 'Interface2' because that would result on a circular reference",
                "interface type 'Interface2' cannot implement 'Interface1' because that would result on a circular reference",
                "interface type 'Interface2' cannot implement 'Interface3' because that would result on a circular reference",
        )
    }

    def assertErrorMessage(SchemaProblem error, expectedMessage) {
        def normalizedMessages = error.errors.collect { it.message.replaceAll($/\[@[0-9]+:[0-9]+]/$, '[@n:n]') }

        if (!normalizedMessages.contains(expectedMessage)) {
            return false
        }

        return true
    }

    def assertErrorMessage(InvalidSchemaException exception, String... expectedErrors) {
        def expectedMessage = "invalid schema:\n" + expectedErrors.join("\n")

        return exception.message == expectedMessage
    }

    def parseSchema(schema) {
        def reader = new StringReader(schema)
        def registry = new SchemaParser().parse(reader)

        def options = SchemaGenerator.Options.defaultOptions()

        return new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)
    }

    def createComplexSchema() {
        def sdl = """
            type Query {
               find: [Node]
            }
            
            interface Node {
              id: ID!
            }
            
            interface Resource implements Node {
              id: ID!
              url: String
            }

            type Image implements Resource & Node {
              id: ID!
              url: String
              thumbnail: String
            }
            
            type File implements Resource & Node {
              id: ID!
              url: String
              path: String
            }
            """

        def typeDefinitionRegistry = new SchemaParser().parse(sdl)

        TypeResolver typeResolver = { env ->
            Map<String, Object> obj = env.getObject();
            String id = (String) obj.get("id");
            GraphQLSchema schema = env.getSchema()

            if (id == "1") {
                return (GraphQLObjectType) schema.getType("Image");
            } else {
                return (GraphQLObjectType) schema.getType("File");
            }
        }

        def graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, RuntimeWiring.newRuntimeWiring()
                .type("Query", { typeWiring ->
                    typeWiring.dataFetcher(
                            "find",
                            { e ->
                                [
                                        [id: '1', url: 'https://image.com/1', thumbnail: 'TN'],
                                        [id: '2', url: 'https://file.com/1', path: '/file/1'],
                                ]
                            }
                    )
                })
                .type("Node", { typeWiring ->
                    typeWiring.typeResolver(typeResolver)
                })
                .type("Resource", { typeWiring ->
                    typeWiring.typeResolver(typeResolver)
                })
                .build()
        )

        return graphQLSchema
    }
}

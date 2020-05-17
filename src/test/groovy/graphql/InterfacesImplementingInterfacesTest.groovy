package graphql

import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

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

    def assertErrorMessage(SchemaProblem error, expectedMessage) {
        def normalizedMessages = error.errors.collect { it.message.replaceAll($/\[@[0-9]+:[0-9]+]/$, '[@n:n]') }

        if (!normalizedMessages.contains(expectedMessage)) {
            return false
        }

        return true
    }

    def parseSchema(schema) {
        def reader = new StringReader(schema)
        def registry = new SchemaParser().parse(reader)

        def options = SchemaGenerator.Options.defaultOptions()

        return new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)
    }
}

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
        assertErrorMessage(error, "The interface type 'LargeImage' [@n:n] must implement [Node] because it is implemented by 'Image' [@n:n]")
        assertErrorMessage(error, "The interface type 'LargeImage' [@n:n] must implement [Node] because it is implemented by 'Resource' [@n:n]")
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
        assertErrorMessage(error, "The object type 'Image' [@n:n] must implement [Node] because it is implemented by 'Resource' [@n:n]")
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
                thumbnail: String!
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
                thumbnail: String!
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The object extension type 'Image' [@n:n] must implement [Node] because it is implemented by 'Resource' [@n:n]")
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
                thumbnail: String!
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
                thumbnail: String!
            }
            """

        parseSchema(schema)

        then:
        def error = thrown(SchemaProblem)
        error.errors.size() == 1
        assertErrorMessage(error, "The interface extension type 'Image' [@n:n] must implement [Node] because it is implemented by 'Resource' [@n:n]")
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
        assertErrorMessages(
                error,
                "The interface type 'Interface1' [@n:n] does not implement the following transitive interfaces: [Interface3]"
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

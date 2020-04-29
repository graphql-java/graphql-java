package graphql

import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

class InterfacesImplementingInterfacesTest extends Specification {
    def positionPattern = "\\[@\\d*:\\d*\\]"

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
        error.errors[0].message ==~ "The interface type 'Resource' ${positionPattern} does not have a field 'id' required via interface 'Node' ${positionPattern}"
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
        error.errors.size() == 1
        error.errors[0].message ==~ "The interface type 'LargeImage' ${positionPattern} does not implement the following transitive interfaces: \\[Node\\]"
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
        error.errors[0].message ==~ "The object type 'Image' ${positionPattern} does not implement the following transitive interfaces: \\[Node\\]"
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
        def errorMessages = error.errors.collect { it.message }
        errorMessages.findAll() { it ==~ "The interface type 'Node' ${positionPattern} cannot implement itself" }.size() == 1
        errorMessages.findAll() { it ==~ "The interface type 'Named' ${positionPattern} cannot implement itself" }.size() == 1
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
        error.errors[0].message ==~ "The interface extension type 'Resource' ${positionPattern} does not have a field 'extraField' required via interface 'Node' ${positionPattern}"
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
        error.errors[0].message ==~ "The object extension type 'Image' ${positionPattern} does not implement the following transitive interfaces: \\[Node\\]"
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
        error.errors[0].message ==~ "The interface extension type 'Image' ${positionPattern} does not implement the following transitive interfaces: \\[Node\\]"
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
        error.errors.size() == 3
        def errorMessages = error.errors.collect { it.message }

        errorMessages.findAll() { it ==~ "The interface hierarchy in interface type 'Interface1' ${positionPattern} results in a circular dependency \\[Interface1 -> Interface3 -> Interface2 -> Interface1\\]" }.size() == 1
        errorMessages.findAll() { it ==~ "The interface hierarchy in interface type 'Interface2' ${positionPattern} results in a circular dependency \\[Interface2 -> Interface3 -> Interface1 -> Interface2\\]" }.size() == 1
        errorMessages.findAll() { it ==~ "The interface hierarchy in interface type 'Interface3' ${positionPattern} results in a circular dependency \\[Interface3 -> Interface1 -> Interface2 -> Interface3\\]" }.size() == 1
    }

    def parseSchema(schema) {
        def reader = new StringReader(schema)
        def registry = new SchemaParser().parse(reader)

        def options = SchemaGenerator.Options.defaultOptions()

        return new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)
    }
}

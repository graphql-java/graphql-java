package graphql


import spock.lang.Specification

class InterfacesImplementingInterfacesTest extends Specification {

    // TODO: maybe parameterized this test
    def 'Simple interface implementing interface'() {
        when:
        TestUtil.schema("""
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

            """)


        then:
        noExceptionThrown()
    }

    def 'When implementing interface does not declare required field, then parsing fails'() {
        when:
        TestUtil.schema("""
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
            }

            interface Resource implements Node {
              url: String
            }
            """)


        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The interface type 'Resource'.*does not have a field 'id' required via interface 'Node'.*"
    }

    def 'Transitively implemented interfaces defined in implementing interface'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        noExceptionThrown()
    }


    def 'Transitively implemented interfaces defined in implementing type'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        noExceptionThrown()
    }

    def 'When not all transitively implemented interfaces are defined in implementing interface, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The interface type 'LargeImage'.*does not implement the following transitive interfaces: \\[Node\\].*"
    }

    def 'When not all transitively implemented interfaces are defined in implementing type, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The object type 'Image'.*does not implement the following transitive interfaces: \\[Node\\].*"
    }

    def 'When interface implements itself, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        // TODO: Assert error message
        thrown(AssertionError)
    }

    def 'When interface extension implements interface and declares required field, then parsing is successful'() {
        when:
        TestUtil.schema("""
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
            """)
        then:
        noExceptionThrown()
    }

    def 'When interface extension implements interface but doesn\'t declare required field, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)


        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The interface extension type 'Resource'.*does not have a field 'extraField' required via interface 'Node'.*"
    }

    def 'When object extension implements all transitive interfaces, then parsing is successful'() {
        when:
        TestUtil.schema("""
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
            """)
        then:
        noExceptionThrown()
    }

    def 'When object extension does not implement all transitive interfaces, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)
        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The object extension type 'Image'.*does not implement the following transitive interfaces: \\[Node\\].*"
    }

    def 'When interface extension implements all transitive interfaces, then parsing is successful'() {
        when:
        TestUtil.schema("""
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
            """)
        then:
        noExceptionThrown()
    }

    def 'When interface extension does not implement all transitive interfaces, then parsing fails'() {
        when:
        TestUtil.schema("""
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
            """)
        then:
        def error = thrown(AssertionError)
        error.getMessage() ==~ ".*The interface extension type 'Image'.*does not implement the following transitive interfaces: \\[Node\\].*"
    }
}

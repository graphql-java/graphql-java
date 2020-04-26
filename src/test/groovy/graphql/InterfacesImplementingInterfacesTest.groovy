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
        thrown(AssertionError)
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
        // TODO: Assert error message
        def error = thrown(AssertionError)
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
        // TODO: Assert error message
        thrown(AssertionError)
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

    def 'When interface implements same interface twice, then parsing fails'() {
        when:
        TestUtil.schema("""
            type Query {
               find(id: String!): Node
            }
            
            interface Node {
              id: ID!
              name: String
            }
            
            interface Named implements Node & Node {
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
        // TODO: Assert error message
        thrown(AssertionError)
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
        // TODO: assert error message
        thrown(AssertionError)
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
        // TODO: assert error message
        thrown(AssertionError)
    }
}

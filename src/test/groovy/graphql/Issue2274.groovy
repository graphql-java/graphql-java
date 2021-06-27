package graphql

import graphql.language.AstComparator
import graphql.language.Node
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.parser.Parser
import spock.lang.Specification

class Issue2274 extends Specification {
    boolean isEqual(Node node1, Node node2) {
        return new AstComparator().isEqual(node1, node2)
    }

    // This test happened to work previously, as there were no tokens following 'implements Thing'
    def "extend type without text following works - schema build"() {
        given:
        def spec = '''
            type Query {
                blob : Blob
            }

            type Blob {
                id: ID!
                name: String
            }

            interface Thing {
                name: String
            }

            input Interval {
                now : Int
                then : Int
            }

            # random input
            input AwesomeInput {
                interval: Interval!
                amount: Int!
            }

            extend type Blob implements Thing
        '''

        when:
        def schema = TestUtil.schema(spec)

        then:
        schema != null
    }

    // Placing an input immediately after extend type without braces previously failed
    // The schema could not be compiled : SchemaProblem{errors=[The interface type 'input' is not present when resolving type
    // 'Blob' [@20:13], The interface type 'AwesomeInput' is not present when resolving type 'Blob' [@20:13]]}. Expression: false
    def "extend type with text following works - schema build"() {
        given:
        def spec = '''
            type Query {
                blob : Blob
            }

            type Blob {
                id: ID!
                name: String
            }

            interface Thing {
                name: String
            }

            input Interval {
                now : Int
                then : Int
            }

            extend type Blob implements Thing
            
            # random input
            input AwesomeInput {
                interval: Interval!
                amount: Int!
            }
        '''

        when:
        def schema = TestUtil.schema(spec)

        then:
        schema != null
    }

    def "extend type with text following works - parser check"() {
        given:
        def spec = "extend type Blob implements Thing1 & Thing2 & Thing3"

        and: "expected schema"
        def objSchema = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Blob")
        objSchema.implementz(new TypeName("Thing1"))
        objSchema.implementz(new TypeName("Thing2"))
        objSchema.implementz(new TypeName("Thing3"))

        when:
        def document = new Parser().parseDocument(spec)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], objSchema.build())
    }
}

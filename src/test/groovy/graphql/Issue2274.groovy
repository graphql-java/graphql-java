package graphql

import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class Issue2274 extends Specification {
    // This test happened to work previously, as there are no tokens following 'implements Thing'
    def "extend type without text following works"() {
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

        println new SchemaPrinter().print(schema)
    }

    // Placing an input immediately after extend type without braces previously failed
    def "extend type with text following works"() {
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
        // The schema could not be compiled : SchemaProblem{errors=[The interface type 'input' is not present when resolving type
        // 'Blob' [@20:13], The interface type 'AwesomeInput' is not present when resolving type 'Blob' [@20:13]]}. Expression: false
        schema != null

        println new SchemaPrinter().print(schema)
    }
}

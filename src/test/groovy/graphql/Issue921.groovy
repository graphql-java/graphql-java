package graphql

import graphql.schema.idl.NaturalEnumValuesProvider
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class Issue921 extends Specification {

    static enum ThreadSort {
        NEWEST_FIRST,
        OLDEST_FIRST,
        MOST_COMMENTS_FIRST
    }

    def "can run introspection on a default value enum schema"() {
        def spec = '''
            type Thread {
                id: ID!
                title: String!
                content: String!
            }

            enum ThreadSort {
                NEWEST_FIRST
                OLDEST_FIRST
                MOST_COMMENTS_FIRST
            }
            
            type Query {
                allThreads(sort: ThreadSort = NEWEST_FIRST) : [Thread!]!
            }
            '''

        def typeRuntimeWiring = newTypeWiring('ThreadSort').enumValues(new NaturalEnumValuesProvider(ThreadSort)).build()
        def runtimeWiring = newRuntimeWiring().type(typeRuntimeWiring).build()
        def qLSchema = TestUtil.schema(spec, runtimeWiring)
        def graphql = GraphQL.newGraphQL(qLSchema).build()

        when:
        def result = graphql.execute('''
                {
                  __schema {
                    queryType {
                      fields {
                        args {
                          defaultValue
                        }
                      }
                    }
                  }
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [__schema: [queryType: [fields: [[args: [[defaultValue: "NEWEST_FIRST"]]]]]]]
    }
}

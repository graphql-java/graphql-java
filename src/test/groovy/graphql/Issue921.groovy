package graphql

import groovy.json.JsonOutput
import spock.lang.Specification

class Issue921 extends Specification {

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

        def qLSchema = TestUtil.schema(spec)
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

        def json = JsonOutput.toJson(result.toSpecification())

        json == '{"data":{"__schema":{"queryType":{"fields":[{"args":[{"defaultValue":"NEWEST_FIRST"}]}]}}}}'
    }
}

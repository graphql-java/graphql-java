package graphql.execution.directives

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.GraphQLDirective
import spock.lang.Specification

/**
 * This test currently has way more directives than can be handled today but in the spirit of TDD
 * I am going to leave the parent node directives there so we can expand the directives capabilities
 * into the future
 */
class QueryDirectivesIntegrationTest extends Specification {

    def sdl = '''
        directive @timeout(afterMillis : Int) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
        
        directive @cached(forMillis : Int) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
        
        directive @importance(place : String) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
 
        type Query {
            books(searchString : String) : [Book]
        }
        
        type Book {
         id :  ID
         title : String
         review : String
        }
    '''

    def pathologicalQuery = '''
        fragment Details on Book @timeout(afterMillis: 25) @cached(forMillis : 25) @importance(place:"FragDef") {
            title
            review @timeout(afterMillis: 5) @cached(forMillis : 5)
            ...InnerDetails @timeout(afterMillis: 26) 
        }
        
        fragment InnerDetails on Book  @timeout(afterMillis: 27) {
            review @timeout(afterMillis: 28)
        }
        
        query Books @timeout(afterMillis: 30) @importance(place:"Operation") {
            books(searchString: "monkey") {
                id
                 ...Details @timeout(afterMillis: 20)
                 ...on Book @timeout(afterMillis: 15) {
                    review @timeout(afterMillis: 10) @cached(forMillis : 10)
                }
            }
        }
    '''

    Map<String, QueryDirectives> capturedDirectives

    DataFetcher reviewDF = { env ->
        capturedDirectives.put(env.getMergedField().getName(), env.getQueryDirectives())
        "review"
    }

    DataFetcher titleDF = { env ->
        capturedDirectives.put(env.getMergedField().getName(), env.getQueryDirectives())
        "title"
    }

    def schema = TestUtil.schema(sdl, [Book: [review: reviewDF, title: titleDF]])

    def graphql = GraphQL.newGraphQL(schema).build()

    def execute(String query) {
        def root = [books: [[review: "Text"]]]
        graphql.execute({ input -> input.query(query).root(root) })
    }

    def joinArgs(List<GraphQLDirective> timeoutDirectives) {
        timeoutDirectives.collect({
            def s = it.getName() + "("
            it.arguments.forEach({
                s += it.getName() + ":" + it.getArgumentValue().value
            })
            s += ")"
            s
        }).join(",")
    }

    void setup() {
        capturedDirectives = [:]
    }

    def "can collector directives as expected"() {
        when:
        def er = execute(pathologicalQuery)
        then:
        er.errors.isEmpty()

        Map<String, List<GraphQLDirective>> immediateMap = capturedDirectives["review"].getImmediateDirectivesByName()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:5),cached(forMillis:10)",
                    timeout: "timeout(afterMillis:5),timeout(afterMillis:28),timeout(afterMillis:10)"
        ]

        def immediate = capturedDirectives["review"].getImmediateDirective("cached")
        joinArgs(immediate) == "cached(forMillis:5),cached(forMillis:10)"
    }

    def "wont create directives for peer fields accidentally"() {
        def query = '''query Books {
            books(searchString: "monkey") {
                id
                 ...on Book {
                    review @timeout(afterMillis: 10) @cached(forMillis : 10)
                    title @timeout(afterMillis: 99) @cached(forMillis : 99)
                }
            }
        }
'''
        when:
        def er = execute(query)
        then:
        er.errors.isEmpty()

        Map<String, List<GraphQLDirective>> immediateMap = capturedDirectives["title"].getImmediateDirectivesByName()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:99)",
                    timeout: "timeout(afterMillis:99)"
        ]

        def immediate = capturedDirectives["review"].getImmediateDirective("cached")
        joinArgs(immediate) == "cached(forMillis:10)"
    }

}

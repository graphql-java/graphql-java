package graphql.execution.directives

import graphql.GraphQL
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.GraphQLDirective
import spock.lang.Specification

class FieldDirectiveCollectorIntegrationTest extends Specification {

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

    EncounteredDirectives capturedDirectives

    DataFetcher reviewDF = { env ->
        capturedDirectives = env.getEncounteredDirectives()
    }

    def schema = TestUtil.schema(sdl, [Book: [review: reviewDF]])

    def graphql = GraphQL.newGraphQL(schema).build()

    def execute(String query) {
        def root = [books: [[review: "Text"]]]
        graphql.execute({ input -> input.query(query).root(root) })
    }

    def joinArgs(List<GraphQLDirective> timeoutDirectives) {
        timeoutDirectives.collect({
            def s = it.getName() + "("
            it.arguments.forEach({
                s += it.getName() + ":" + it.getValue()
            })
            s += ")"
            s
        }).join(",")
    }

    void setup() {
        capturedDirectives = null
    }

    def "can collector directives as expected"() {
        when:
        def er = execute(pathologicalQuery)
        then:
        er.errors.isEmpty()

        def closest = capturedDirectives.getClosestDirective("timeout")
        joinArgs(closest) == "timeout(afterMillis:5),timeout(afterMillis:28),timeout(afterMillis:10)"

        def closestImportance = capturedDirectives.getClosestDirective("importance")
        joinArgs(closestImportance) == "importance(place:FragDef)"

        Map<String, List<GraphQLDirective>> immediateMap = capturedDirectives.getImmediateDirectives()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:5),cached(forMillis:10)",
                    timeout: "timeout(afterMillis:5),timeout(afterMillis:28),timeout(afterMillis:10)"
        ]

        def immediate = capturedDirectives.getImmediateDirective("cached")
        joinArgs(immediate) == "cached(forMillis:5),cached(forMillis:10)"
    }

    def "getClosest can be on parent fields"() {

        def query = '''
            query Books {
                books(searchString: "monkey") @importance(place : "books") {
                    id
                    review
                }
            }
        '''

        when:
        def er = execute(query)
        then:
        er.errors.isEmpty()

        def closest = capturedDirectives.getClosestDirective("importance")
        joinArgs(closest) == "importance(place:books)"

        def allDirectives = capturedDirectives.getAllDirectives()
        allDirectives.size() == 1
        allDirectives[0].directiveLocation == Introspection.DirectiveLocation.FIELD
        allDirectives[0].getDirectivesContainer() instanceof Field
        allDirectives[0].getDistance() == 1

        capturedDirectives.getImmediateDirectives().isEmpty()
        capturedDirectives.getImmediateDirective("importance").isEmpty()
    }

    def "empty directives don't use memory"() {

        def query = '''
            query Books {
                books(searchString: "monkey") {
                    id
                    review
                }
            }
        '''

        when:
        def er = execute(query)
        then:
        er.errors.isEmpty()

        capturedDirectives.getAllDirectives() == []
    }
}

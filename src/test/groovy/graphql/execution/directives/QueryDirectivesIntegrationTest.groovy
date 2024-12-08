package graphql.execution.directives


import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.RawVariables
import graphql.language.IntValue
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.normalized.NormalizedInputValue
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
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

    static def joinArgs(List<QueryAppliedDirective> timeoutDirectives) {
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

    def "can collect directives as expected"() {
        when:
        def er = execute(pathologicalQuery)
        then:
        er.errors.isEmpty()

        def immediateMap = capturedDirectives["review"].getImmediateAppliedDirectivesByName()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:5),cached(forMillis:10)",
                    timeout: "timeout(afterMillis:5),timeout(afterMillis:28),timeout(afterMillis:10)"
        ]

        def immediate = capturedDirectives["review"].getImmediateAppliedDirective("cached")
        joinArgs(immediate) == "cached(forMillis:5),cached(forMillis:10)"
    }

    def "can collect merged field directives as expected"() {
        when:
        def query = """
        query Books  {
            books(searchString: "monkey") {
                review @timeout(afterMillis: 10) @cached(forMillis : 10)
                review @timeout(afterMillis: 100) @cached(forMillis : 100)
            }
        }

        """
        def er = execute(query)
        then:
        er.errors.isEmpty()

        def immediateMap = capturedDirectives["review"].getImmediateAppliedDirectivesByName()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:10),cached(forMillis:100)",
                    timeout: "timeout(afterMillis:10),timeout(afterMillis:100)"
        ]

        def immediate = capturedDirectives["review"].getImmediateAppliedDirective("cached")
        joinArgs(immediate) == "cached(forMillis:10),cached(forMillis:100)"

        def immediate2 = capturedDirectives["review"].getImmediateAppliedDirective("timeout")
        joinArgs(immediate2) == "timeout(afterMillis:10),timeout(afterMillis:100)"
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

        def immediateMap = capturedDirectives["title"].getImmediateAppliedDirectivesByName()
        def entries = immediateMap.entrySet().collectEntries({
            [(it.getKey()): joinArgs(it.getValue())]
        })
        entries == [cached : "cached(forMillis:99)",
                    timeout: "timeout(afterMillis:99)"
        ]

        def immediate = capturedDirectives["review"].getImmediateAppliedDirective("cached")
        joinArgs(immediate) == "cached(forMillis:10)"
    }

    def "can capture directive argument values inside ENO path"() {
        def query = TestUtil.parseQuery(pathologicalQuery)
        when:
        def eno = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                schema, query, "Books", RawVariables.emptyVariables())


        then:
        def booksENF = eno.getTopLevelFields()[0]
        booksENF != null
        def bookQueryDirectives = eno.getQueryDirectives(booksENF)
        bookQueryDirectives.immediateAppliedDirectivesByName.isEmpty()

        def reviewField = eno.getCoordinatesToNormalizedFields().get(FieldCoordinates.coordinates("Book", "review"))
        def reviewQueryDirectives = eno.getQueryDirectives(reviewField[0])
        def reviewImmediateDirectivesMap = reviewQueryDirectives.immediateAppliedDirectivesByName
        def argInputValues = simplifiedInputValuesWithState(reviewImmediateDirectivesMap)
        argInputValues == [
                timeout: [
                        [timeout: [[afterMillis: 5]]], [timeout: [[afterMillis: 28]]], [timeout: [[afterMillis: 10]]]
                ],
                cached : [
                        [cached: [[forMillis: 5]]], [cached: [[forMillis: 10]]]
                ]
        ]

        // normalised values are AST values
        def normalizedValues = simplifiedNormalizedValues(reviewQueryDirectives.getNormalizedInputValueByImmediateAppliedDirectives())
        normalizedValues == [
                timeout: [
                        [afterMillis: 5], [afterMillis: 28], [afterMillis: 10]],
                cached : [
                        [forMillis: 5], [forMillis: 10]]
        ]

    }

    def simplifiedInputValuesWithState(Map<String, List<QueryAppliedDirective>> mapOfDirectives) {
        def simpleMap = [:]
        mapOfDirectives.forEach { k, listOfDirectives ->

            def dirVals = listOfDirectives.collect { qd ->
                def argVals = qd.getArguments().collect { arg ->
                    def argValue = arg.getArgumentValue()
                    return [(arg.name): argValue?.value]
                }
                return [(qd.name): argVals]
            }
            simpleMap[k] = dirVals
        }
        return simpleMap
    }

    def simplifiedNormalizedValues(Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> mapOfDirectives) {
        Map<String, List<Map<String, Integer>>> simpleMap = new LinkedHashMap<>()
        mapOfDirectives.forEach { qd, argMap ->
            def argVals = argMap.collect { entry ->
                def argValueAst = entry.value?.value as IntValue // just assume INtValue for these tests
                return [(entry.key): argValueAst?.value?.toInteger()]
            }
            simpleMap.computeIfAbsent(qd.name, { _ -> [] }).addAll(argVals)
        }
        return simpleMap
    }
}

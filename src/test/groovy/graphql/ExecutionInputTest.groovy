package graphql

import graphql.execution.ExecutionId
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.preparsed.persisted.PersistedQuerySupport
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

import static org.awaitility.Awaitility.*

class ExecutionInputTest extends Specification {

    def query = "query { hello }"
    def registry = new DataLoaderRegistry()
    def root = "root"
    def variables = [key: "value"]

    def "build works"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .variables(variables)
                .root(root)
                .graphQLContext({ it.of(["a": "b"]) })
                .locale(Locale.GERMAN)
                .extensions([some: "map"])
                .build()
        then:
        executionInput.graphQLContext.get("a") == "b"
        executionInput.root == root
        executionInput.variables == variables
        executionInput.rawVariables.toMap() == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.query == query
        executionInput.locale == Locale.GERMAN
        executionInput.extensions == [some: "map"]
        executionInput.toString() != null
    }

    def "build without locale"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .variables(variables)
                .root(root)
                .graphQLContext({ it.of(["a": "b"]) })
                .locale(null)
                .extensions([some: "map"])
                .build()
        then:
        executionInput.locale == Locale.getDefault()
    }

    def "map context build works"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .graphQLContext([a: "b"])
                .build()
        then:
        executionInput.graphQLContext.get("a") == "b"
    }

    def "legacy context is defaulted"() {
        // Retaining deprecated method tests for coverage
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.context instanceof GraphQLContext // Retain deprecated for test coverage
        executionInput.getGraphQLContext() == executionInput.getContext() // Retain deprecated for test coverage
    }

    def "graphql context is defaulted"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.graphQLContext instanceof GraphQLContext
    }

    def "locale defaults to JVM default"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.getLocale() == Locale.getDefault()
    }

    def "transform works and copies values"() {
        when:
        def executionInputOld = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .variables(variables)
                .extensions([some: "map"])
                .root(root)
                .graphQLContext({ it.of(["a": "b"]) })
                .locale(Locale.GERMAN)
                .build()
        def graphQLContext = executionInputOld.getGraphQLContext()
        def executionInput = executionInputOld.transform({ bldg -> bldg.query("new query") })

        then:
        executionInput.graphQLContext == graphQLContext
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.locale == Locale.GERMAN
        executionInput.extensions == [some: "map"]
        executionInput.query == "new query"
    }

    def "transform works and sets variables"() {
        when:
        def executionInputOld = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .extensions([some: "map"])
                .root(root)
                .graphQLContext({ it.of(["a": "b"]) })
                .locale(Locale.GERMAN)
                .build()
        def graphQLContext = executionInputOld.getGraphQLContext()
        def executionInput = executionInputOld.transform({ bldg ->
            bldg
                    .query("new query")
                    .variables(variables)
        })

        then:
        executionInput.graphQLContext == graphQLContext
        executionInput.root == root
        executionInput.rawVariables.toMap() == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.locale == Locale.GERMAN
        executionInput.extensions == [some: "map"]
        executionInput.query == "new query"
    }

    def "defaults query into builder as expected"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput("{ q }")
                .locale(Locale.ENGLISH)
                .build()
        then:
        executionInput.query == "{ q }"
        executionInput.locale == Locale.ENGLISH
        executionInput.dataLoaderRegistry != null
        executionInput.variables == [:]
    }

    def "integration test so that values make it right into the data fetchers"() {

        def sdl = '''
            type Query {
                fetch : String
            }
        '''
        DataFetcher df = { DataFetchingEnvironment env ->
            return [
                    "locale"        : env.getLocale().getDisplayName(Locale.ENGLISH),
                    "executionId"   : env.getExecutionId().toString(),
                    "graphqlContext": env.getGraphQlContext().get("a")

            ]
        }
        def schema = TestUtil.schema(sdl, ["Query": ["fetch": df]])
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("{ fetch }")
                .locale(Locale.GERMAN)
                .executionId(ExecutionId.from("ID123"))
                .build()
        executionInput.getGraphQLContext().putAll([a: "b"])

        def er = graphQL.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data["fetch"] == "{locale=German, executionId=ID123, graphqlContext=b}"
    }

    def "can cancel the execution"() {
        def sdl = '''
            type Query {
                fetch1 : Inner
                fetch2 : Inner
            }
            
            type Inner {
                f : String
            }
                
        '''

        CountDownLatch fieldLatch = new CountDownLatch(1)

        DataFetcher df1Sec = { DataFetchingEnvironment env ->
            println("Entering DF1")
            return CompletableFuture.supplyAsync {
                println("DF1 async run")
                fieldLatch.await()
                Thread.sleep(1000)
                return [f: "x"]
            }
        }
        DataFetcher df10Sec = { DataFetchingEnvironment env ->
            println("Entering DF10")
            return CompletableFuture.supplyAsync {
                println("DF10 async run")
                fieldLatch.await()
                Thread.sleep(10000)
                return "x"
            }
        }

        def fetcherMap = ["Query": ["fetch1": df1Sec, "fetch2": df1Sec],
                          "Inner": ["f": df10Sec]
        ]
        def schema = TestUtil.schema(sdl, fetcherMap)
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("query q { fetch1 { f }  fetch2 { f } }")
                .build()

        def cf = graphQL.executeAsync(executionInput)

        Thread.sleep(250) // let it get into the field fetching say

        // lets cancel it
        println("cancelling")
        executionInput.cancel()

        // let the DFs run
        println("make the fields run")
        fieldLatch.countDown()

        println("and await for the overall CF to complete")
        await().atMost(Duration.ofSeconds(60)).until({ -> cf.isDone() })

        def er = cf.join()

        then:
        !er.errors.isEmpty()
        er.errors[0]["message"] == "Execution has been asked to be cancelled"
    }

    def "can cancel request at random times (#testName)"() {
        def sdl = '''
            type Query {
                fetch1 : Inner
                fetch2 : Inner
            }
            
            type Inner {
                inner : Inner
                f : String
            }
                
        '''

        when:

        CountDownLatch fetcherLatch = new CountDownLatch(1)

        DataFetcher df = { DataFetchingEnvironment env ->
            return CompletableFuture.supplyAsync {
                fetcherLatch.countDown()
                def delay = plusOrMinus(dfDelay)
                println("DF ${env.getExecutionStepInfo().getPath()} sleeping for $delay")
                Thread.sleep(delay)
                return [inner: [f: "x"], f: "x"]
            }
        }

        def fetcherMap = ["Query": ["fetch1": df, "fetch2": df],
                          "Inner": ["inner": df]
        ]
        def schema = TestUtil.schema(sdl, fetcherMap)
        def graphQL = GraphQL.newGraphQL(schema).build()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("query q { fetch1 { inner { inner { inner { f }}}} fetch2 { inner { inner { inner { f }}}} }")
                .build()

        def cf = graphQL.executeAsync(executionInput)

        // wait for at least one fetcher to run
        fetcherLatch.await()

        // using a random number MAY make this test flaky - but so be it.  We want ot make sure that
        // if we cancel then we dont lock up.  We have deterministic tests for that so lets hav
        // some random ones
        //
        def randomCancel = plusOrMinus(dfDelay)
        Thread.sleep(randomCancel)

        // now make it cancel
        println("Cancelling after $randomCancel")
        executionInput.cancel()

        await().atMost(Duration.ofSeconds(10)).until({ -> cf.isDone() })

        def er = cf.join()

        then:
        !er.errors.isEmpty()
        er.errors[0]["message"] == "Execution has been asked to be cancelled"

        where:
        testName  | dfDelay
        "50 ms"   | plusOrMinus(50)
        "100 ms"  | plusOrMinus(100)
        "200 ms"  | plusOrMinus(200)
        "500 ms"  | plusOrMinus(500)
        "1000 ms" | plusOrMinus(1000)
    }

    def "uses persisted query marker when query is null"() {
        when:
        ExecutionInput.newExecutionInput().query(null).build()
        then:
        thrown(AssertException)
    }

    def "uses persisted query marker when query is null and extensions contains persistedQuery"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .extensions([persistedQuery: "any"])
                .query(null)
                .build()
        then:
        executionInput.query == PersistedQuerySupport.PERSISTED_QUERY_MARKER
    }

    def "uses persisted query marker when query is empty and extensions contains persistedQuery"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .extensions([persistedQuery: "any"])
                .query("")
                .build()
        then:
        executionInput.query == PersistedQuerySupport.PERSISTED_QUERY_MARKER
    }

    def "can cancel at specific places"() {
        def sdl = '''
            type Query {
                fetch1 : Inner
                fetch2 : Inner
            }
            
            type Inner {
                inner : Inner
                f : String
            }
                
        '''

        when:

        DataFetcher df = { DataFetchingEnvironment env ->
            return CompletableFuture.supplyAsync {
                return [inner: [f: "x"], f: "x"]
            }
        }

        def fetcherMap = ["Query": ["fetch1": df, "fetch2": df],
                          "Inner": ["inner": df]
        ]


        def queryText = "query q { fetch1 { inner { inner { inner { f }}}} fetch2 { inner { inner { inner { f }}}} }"
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(queryText)
                .build()

        Instrumentation instrumentation = new Instrumentation() {
            @Override
            ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
                executionInput.cancel()
                return null
            }
        }
        def schema = TestUtil.schema(sdl, fetcherMap)
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()


        def er = awaitAsync(graphQL, executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0]["message"] == "Execution has been asked to be cancelled"

        when:
        executionInput = ExecutionInput.newExecutionInput()
                .query(queryText)
                .build()

        instrumentation = new Instrumentation() {
            @Override
            InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
                executionInput.cancel()
                return null
            }
        }
        schema = TestUtil.schema(sdl, fetcherMap)
        graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()

        er = awaitAsync(graphQL, executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0]["message"] == "Execution has been asked to be cancelled"

        when:
        executionInput = ExecutionInput.newExecutionInput()
                .query(queryText)
                .build()

        instrumentation = new Instrumentation() {

            @Override
            InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
                executionInput.cancel()
                return null
            }
        }
        schema = TestUtil.schema(sdl, fetcherMap)
        graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()

        er = awaitAsync(graphQL, executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0]["message"] == "Execution has been asked to be cancelled"

    }

    private static ExecutionResult awaitAsync(GraphQL graphQL, ExecutionInput executionInput) {
        def cf = graphQL.executeAsync(executionInput)
        await().atMost(Duration.ofSeconds(10)).until({ -> cf.isDone() })
        return cf.join()
    }

    private static int plusOrMinus(int integer) {
        int half = (int) (integer / 2)
        def intVal = TestUtil.rand((integer - half), (integer + half))
        return intVal
    }
}

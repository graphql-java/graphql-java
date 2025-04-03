package graphql

import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.EngineRunningObserver
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.EngineRunningObserver.ENGINE_RUNNING_OBSERVER_KEY
import static graphql.execution.EngineRunningObserver.RunningState
import static graphql.execution.EngineRunningObserver.RunningState.NOT_RUNNING
import static graphql.execution.EngineRunningObserver.RunningState.RUNNING

class EngineRunningTest extends Specification {


    private static List<RunningState> trackStates(ExecutionInput ei) {
        List<RunningState> states = new CopyOnWriteArrayList<>();
        ei.getGraphQLContext().put(ENGINE_RUNNING_OBSERVER_KEY, {
            ExecutionId executionId, GraphQLContext context, RunningState running ->
                states.add(running)
        } as EngineRunningObserver);
        states
    }

    def "engine running state is observed"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        def df = { env ->
            return "world"
        } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<RunningState> states = trackStates(ei)

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [hello: "world"]
        states == [RUNNING, NOT_RUNNING]
    }

    def "engine running state is observed with one async datafetcher"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        CompletableFuture cf = new CompletableFuture();
        def df = { env ->
            return cf;
        } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<RunningState> states = trackStates(ei)

        when:
        def er = graphQL.executeAsync(ei)
        then:
        states == [RUNNING, NOT_RUNNING]

        when:
        states.clear();
        cf.complete("world")

        then:
        states == [RUNNING, NOT_RUNNING]
        er.get().data == [hello: "world"]
    }

    def "engine running state is observed with one dependent async datafetcher"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        CompletableFuture cf = new CompletableFuture();
        def df = { env ->
            return cf.thenApply { it -> it }
        } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<RunningState> states = trackStates(ei)

        when:
        def er = graphQL.executeAsync(ei)
        then:
        states == [RUNNING, NOT_RUNNING]

        when:
        states.clear();
        cf.complete("world")

        then:
        er.get().data == [hello: "world"]
        states == [RUNNING, NOT_RUNNING]
    }


    def "datafetcher failing with async exception handler"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        def df = { env ->
            throw new RuntimeException("boom")
        } as DataFetcher

        ReentrantLock reentrantLock = new ReentrantLock()
        reentrantLock.lock();

        def exceptionHandler = { param ->
            def async = CompletableFuture.supplyAsync {
                reentrantLock.lock();
                return DataFetcherExceptionHandlerResult.newResult(GraphqlErrorBuilder
                        .newError(param.dataFetchingEnvironment).message("recovered").build()).build()
            }
            return async
        } as DataFetcherExceptionHandler

        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).defaultDataFetcherExceptionHandler(exceptionHandler).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<RunningState> states = trackStates(ei)

        when:
        def er = graphQL.executeAsync(ei)
        states.clear()
        reentrantLock.unlock()
        def result = er.get()

        then:
        result.errors.collect { it.message } == ["recovered"]
        // we expect simply going from running to finshed
        states == [RUNNING, NOT_RUNNING]
    }


    def "engine running state is observed with two async datafetcher"() {
        given:
        def sdl = '''

        type Query {
          hello: String
          hello2: String
        }
        '''
        CompletableFuture cf1 = new CompletableFuture();
        CompletableFuture cf2 = new CompletableFuture();
        def df = { env ->
            return cf1;
        } as DataFetcher
        def df2 = { env ->
            return cf2
        } as DataFetcher

        def fetchers = ["Query": ["hello": df, "hello2": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello hello2 }"
        def ei = newExecutionInput(query).build()

        List<RunningState> states = trackStates(ei)

        when:
        def er = graphQL.executeAsync(ei)
        then:
        states == [RUNNING, NOT_RUNNING]

        when:
        states.clear();
        cf1.complete("world")

        then:
        states == [RUNNING, NOT_RUNNING]

        when:
        states.clear();
        cf2.complete("world2")

        then:
        states == [RUNNING, NOT_RUNNING]
        er.get().data == [hello: "world", hello2: "world2"]
    }
}

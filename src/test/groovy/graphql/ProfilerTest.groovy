package graphql

import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.language.OperationDefinition
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput
import static graphql.ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_COMPLETED
import static graphql.ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_NOT_COMPLETED
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.setEnableDataLoaderChaining
import static java.util.concurrent.CompletableFuture.supplyAsync

class ProfilerTest extends Specification {


    def "one field"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''
        def schema = TestUtil.schema(sdl, [Query: [
                hello: { DataFetchingEnvironment dfe -> return "world" } as DataFetcher
        ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ hello }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [hello: "world"]

        then:
        profilerResult.getFieldsFetched() == ["/hello"] as Set

    }

    def "collects instrumentation list"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''
        def schema = TestUtil.schema(sdl, [Query: [
                hello: { DataFetchingEnvironment dfe -> return "world" } as DataFetcher
        ]])
        Instrumentation fooInstrumentation = new Instrumentation() {};
        Instrumentation barInstrumentation = new Instrumentation() {};
        ChainedInstrumentation chainedInstrumentation = new ChainedInstrumentation(
                new ChainedInstrumentation(new SimplePerformantInstrumentation()),
                new ChainedInstrumentation(fooInstrumentation, barInstrumentation),
                new SimplePerformantInstrumentation())


        def graphql = GraphQL.newGraphQL(schema).instrumentation(chainedInstrumentation).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ hello }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [hello: "world"]

        then:
        profilerResult.getInstrumentationClasses() == ["graphql.execution.instrumentation.SimplePerformantInstrumentation",
                                                       "graphql.ProfilerTest\$1",
                                                       "graphql.ProfilerTest\$2",
                                                       "graphql.execution.instrumentation.SimplePerformantInstrumentation"]

    }


    def "two DF with list"() {
        given:
        def sdl = '''
            type Query {
                foo: [Foo]
            }
            type Foo {
                id: String
                bar: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe -> return [[id: "1"], [id: "2"], [id: "3"]] } as DataFetcher],
                Foo  : [
                        bar: { DataFetchingEnvironment dfe -> dfe.source.id } as DataFetcher
                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id bar } }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [[id: "1", bar: "1"], [id: "2", bar: "2"], [id: "3", bar: "3"]]]

        then:
        profilerResult.getFieldsFetched() == ["/foo", "/foo/bar", "/foo/id"] as Set
        profilerResult.getTotalDataFetcherInvocations() == 7
        profilerResult.getTotalCustomDataFetcherInvocations() == 4
        profilerResult.getTotalPropertyDataFetcherInvocations() == 3
    }

    def "records timing"() {
        given:
        def sdl = '''
            type Query {
                foo: Foo
            }
            type Foo {
                id: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(500)
                                "1"
                            }
                        } as DataFetcher],
                Foo  : [
                        id: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(500)
                                dfe.source
                            }
                        } as DataFetcher
                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id  } }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [id: "1"]]
        // the total execution time must be more than 1 second,
        // the engine should take less than 500ms
        profilerResult.getTotalExecutionTime() > Duration.ofSeconds(1).toNanos()
        profilerResult.getEngineTotalRunningTime() > Duration.ofMillis(1).toNanos()
        profilerResult.getEngineTotalRunningTime() < Duration.ofMillis(500).toNanos()


    }

    def "data fetcher result types"() {
        given:
        def sdl = '''
            type Query {
                foo: [Foo]
            }
            type Foo {
                id: String
                name: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(100)
                                return [[id: "1", name: "foo"]]
                            }
                        } as DataFetcher],
                Foo  : [
                        name: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.completedFuture(dfe.source.name)
                        } as DataFetcher
                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id name } }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [[id: "1", name: "foo"]]]
        profilerResult.getDataFetcherResultType() == ["/foo/name": COMPLETABLE_FUTURE_COMPLETED, "/foo": COMPLETABLE_FUTURE_NOT_COMPLETED]


    }

    def "operation details"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''
        def schema = TestUtil.schema(sdl, [Query: [
                hello: { DataFetchingEnvironment dfe -> return "world" } as DataFetcher
        ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("query MyQuery { hello }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [hello: "world"]

        then:
        profilerResult.getOperationName() == "MyQuery"
        profilerResult.getOperationType() == OperationDefinition.Operation.QUERY


    }

    def "dataloader usage"() {
        given:
        def sdl = '''

        type Query {
          dogName: String
          catName: String
        }
        '''
        int batchLoadCalls = 0
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls++
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                assert keys.size() == 2
                return ["Luna", "Tiger"]
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def df1 = { env ->
            return env.getDataLoader("name").load("Key1").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoader("name").load("Key2").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher

        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).profileExecution(true).build()
        setEnableDataLoaderChaining(ei.graphQLContext, true)

        when:
        def efCF = graphQL.executeAsync(ei)
        Awaitility.await().until { efCF.isDone() }
        def er = efCF.get()
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult
        then:
        er.data == [dogName: "Luna", catName: "Tiger"]
        batchLoadCalls == 2
        profilerResult.isDataLoaderChainingEnabled()
        profilerResult.getDataLoaderLoadInvocations() == [name: 4]
        profilerResult.getChainedStrategyDispatching() == [1] as Set
        profilerResult.getDispatchEvents().size() == 2
        profilerResult.getDispatchEvents()[0].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[0].level == 1
        profilerResult.getDispatchEvents()[0].count == 2
        profilerResult.getDispatchEvents()[1].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[1].level == 1
        profilerResult.getDispatchEvents()[1].count == 2

    }


}

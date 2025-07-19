package graphql

import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
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
import java.util.concurrent.atomic.AtomicInteger

import static graphql.ExecutionInput.newExecutionInput
import static graphql.ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_COMPLETED
import static graphql.ProfilerResult.DataFetcherResultType.COMPLETABLE_FUTURE_NOT_COMPLETED
import static graphql.ProfilerResult.DataFetcherResultType.MATERIALIZED
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

    def "introspection fields are ignored"() {
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
                .query("{ hello __typename alias:__typename __schema {types{name}} __type(name: \"Query\") {name} }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData()["hello"] == "world"

        then:
        profilerResult.getFieldsFetched() == ["/hello",] as Set
        profilerResult.getTotalDataFetcherInvocations() == 1

    }

    def "pure introspection "() {
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
                .query("{ __schema {types{name}} __type(name: \"Query\") {name} }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData()["__schema"] != null

        then:
        profilerResult.getFieldsFetched() == [] as Set
        profilerResult.getTotalDataFetcherInvocations() == 0

    }


    def "instrumented data fetcher"() {
        given:
        def sdl = '''
            type Query {
                dog: Dog 
            }
            type Dog {
                name: String
                age: Int
            }
        '''


        def dogDf = { DataFetchingEnvironment dfe -> return [name: "Luna", age: 5] } as DataFetcher

        Instrumentation instrumentation = new Instrumentation() {
            @Override
            DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                if (parameters.getField().getName() == "name") {
                    // wrapping a PropertyDataFetcher
                    return { DataFetchingEnvironment dfe ->
                        def result = dataFetcher.get(dfe)
                        return result
                    } as DataFetcher
                }
                return dataFetcher
            }

        }
        def dfs = [Query: [
                dog: dogDf
        ]]
        def schema = TestUtil.schema(sdl, dfs)
        def graphql = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ dog {name age} }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [dog: [name: "Luna", age: 5]]

        then:
        profilerResult.getTotalDataFetcherInvocations() == 3
        profilerResult.getTotalTrivialDataFetcherInvocations() == 1
        profilerResult.getTotalTrivialDataFetcherInvocations() == 1
        profilerResult.getTotalCustomDataFetcherInvocations() == 1
        profilerResult.getDataFetcherResultType() == ["/dog": MATERIALIZED]
    }


    def "manual dataloader dispatch"() {
        given:
        def sdl = '''

        type Query {
          dog: String
        }
        '''
        AtomicInteger batchLoadCalls = new AtomicInteger()
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                return ["Luna"]
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def df1 = { env ->
            def loader = env.getDataLoader("name")
            def result = loader.load("Key1")
            loader.dispatch()
            return result
        } as DataFetcher

        def fetchers = ["Query": ["dog": df1]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dog } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).profileExecution(true).build()
        setEnableDataLoaderChaining(ei.graphQLContext, true)

        when:
        def efCF = graphQL.executeAsync(ei)
        Awaitility.await().until { efCF.isDone() }
        def er = efCF.get()
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult
        then:
        er.data == [dog: "Luna"]
        batchLoadCalls.get() == 1
        then:
        profilerResult.getDispatchEvents()[0].type == ProfilerResult.DispatchEventType.MANUAL_DISPATCH
        profilerResult.getDispatchEvents()[0].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[0].keyCount == 1
        profilerResult.getDispatchEvents()[0].level == 1

    }

    def "cached dataloader values"() {
        given:
        def sdl = '''

        type Query {
          dog: Dog
        }
        type Dog {
          name: String
        }
        '''
        AtomicInteger batchLoadCalls = new AtomicInteger()
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                return ["Luna"]
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def dogDF = { env ->
            def loader = env.getDataLoader("name")
            def result = loader.load("Key1").thenCompose {
                return loader.load("Key1") // This should hit the cache
            }
        } as DataFetcher

        def nameDF = { env ->
            def loader = env.getDataLoader("name")
            def result = loader.load("Key1").thenCompose {
                return loader.load("Key1") // This should hit the cache
            }
        } as DataFetcher


        def fetchers = [Query: [dog: dogDF], Dog: [name: nameDF]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dog {name } } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).profileExecution(true).build()
        setEnableDataLoaderChaining(ei.graphQLContext, true)

        when:
        def efCF = graphQL.executeAsync(ei)
        Awaitility.await().until { efCF.isDone() }
        def er = efCF.get()
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult
        then:
        er.data == [dog: [name: "Luna"]]
        batchLoadCalls.get() == 1
        then:
        profilerResult.getDataLoaderLoadInvocations().get("name") == 4
        profilerResult.getDispatchEvents()[0].type == ProfilerResult.DispatchEventType.STRATEGY_DISPATCH
        profilerResult.getDispatchEvents()[0].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[0].keyCount == 1
        profilerResult.getDispatchEvents()[0].level == 1

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
                                                       "graphql.ProfilerTest\$2",
                                                       "graphql.ProfilerTest\$3",
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
        profilerResult.getTotalTrivialDataFetcherInvocations() == 3
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
                            // blocking the engine for 1ms
                            // so that engineTotalRunningTime time is more than 1ms
                            Thread.sleep(1)
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
                text: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(100)
                                return [[id: "1", name: "foo1"], [id: "2", name: "foo2"]]
                            }
                        } as DataFetcher],
                Foo  : [
                        name: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.completedFuture(dfe.source.name)
                        } as DataFetcher,
                        text: { DataFetchingEnvironment dfe ->
                            return "text"
                        } as DataFetcher

                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id name text } foo2: foo { id name text} }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [[id: "1", name: "foo1", text: "text"], [id: "2", name: "foo2", text: "text"]], foo2: [[id: "1", name: "foo1", text: "text"], [id: "2", name: "foo2", text: "text"]]]
        then:
        profilerResult.getTotalDataFetcherInvocations() == 14
        profilerResult.getTotalCustomDataFetcherInvocations() == 10
        profilerResult.getDataFetcherResultType() == ["/foo/name" : COMPLETABLE_FUTURE_COMPLETED,
                                                      "/foo/text" : MATERIALIZED,
                                                      "/foo2/name": COMPLETABLE_FUTURE_COMPLETED,
                                                      "/foo2/text": MATERIALIZED,
                                                      "/foo2"     : COMPLETABLE_FUTURE_NOT_COMPLETED,
                                                      "/foo"      : COMPLETABLE_FUTURE_NOT_COMPLETED]
        profilerResult.shortSummaryMap().get("dataFetcherResultTypes") == ["COMPLETABLE_FUTURE_COMPLETED"    : "(count:2, invocations:4)",
                                                                           "COMPLETABLE_FUTURE_NOT_COMPLETED": "(count:2, invocations:2)",
                                                                           "MATERIALIZED"                    : "(count:2, invocations:4)"]


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
        profilerResult.getDispatchEvents().size() == 2
        profilerResult.getDispatchEvents()[0].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[0].level == 1
        profilerResult.getDispatchEvents()[0].keyCount == 2
        profilerResult.getDispatchEvents()[1].dataLoaderName == "name"
        profilerResult.getDispatchEvents()[1].level == 1
        profilerResult.getDispatchEvents()[1].keyCount == 2

    }


}

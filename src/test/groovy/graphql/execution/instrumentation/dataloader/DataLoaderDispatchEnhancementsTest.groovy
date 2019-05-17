package graphql.execution.instrumentation.dataloader


import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.Stream

class DataLoaderDispatchEnhancementsTest extends Specification {

    def REP_COUNT = 1000

    class A {
        String a
    }

    AtomicInteger fetchCounter = new AtomicInteger(0)

    BatchLoader<Integer, A> batchLoaderA = new BatchLoader<Integer, A>() {
        @Override
        CompletionStage<List<Character>> load(List<Integer> keys) {
            fetchCounter.incrementAndGet()
            return CompletableFuture.completedFuture(keys.stream().map{key ->
                char[] value = [((char) key.intValue())]
                A a = new A()
                a.a =  new String(value)
                a
            }.collect(Collectors.toList()))
        }
    }

    def registry() {
        DataLoaderRegistry registry = new DataLoaderRegistry()
        registry.register("A", DataLoader.newDataLoader(batchLoaderA))
        registry
    }

    DataFetcher queryDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.input
            environment.getDataLoader("A").load(Integer.parseInt(id))
        }
    }

    def simpleWiring() {
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("query", queryDataFetcher).build()).build()
        wiring
    }

    def queryTemplate = "{query(input:%s) { a }}"

    def schema = graphql.TestUtil.schemaFile("simpleSchema.graphqls", simpleWiring())

    def "basic batch loading is possible across executions via instrumentation"() {
        given:
        def registry = registry()
        FieldLevelTrackingApproach approach = new FieldLevelTrackingApproach(registry)
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(
                DataLoaderDispatcherInstrumentationOptions.newOptions().withTrackingApproach{approach})
        fetchCounter.set(0)
        List<ExecutionInput> executionInputList = new ArrayList<>()
        for (int i = 0; i < REP_COUNT; i++) {
            String query = String.format(queryTemplate, i)
            executionInputList.add(ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(registry).build())
        }

        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()
        when:
        List<CompletableFuture<ExecutionResult>> resultFutures = executionInputList.parallelStream()
                .map{input -> graphql.executeAsync(input)}.collect(Collectors.toList())
        resultFutures.forEach{future -> future.get()}
        then:
        0 < fetchCounter.get() &&  fetchCounter.get() < REP_COUNT
    }

    def "Request batch loading across executions performs as expected"() {
        given:
        def registry = registry()
        fetchCounter.set(0)
        List<ExecutionInput> executionInputList = new ArrayList<>()
        List<ExecutionId> ids = Stream.generate{ExecutionId.generate()}.limit(REP_COUNT).collect(Collectors.toList())
        for (int i = 0; i < REP_COUNT; i++) {
            String query = String.format(queryTemplate, i)
            ExecutionId id = ids.get(i)
            executionInputList.add(ExecutionInput.newExecutionInput()
                    .query(query).executionId(id).dataLoaderRegistry(registry).build())
        }

        def tracking = new RequestLevelTrackingApproach(ids, registry)
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(
                DataLoaderDispatcherInstrumentationOptions.newOptions().withTrackingApproach{tracking})
        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()
                when:
        List<CompletableFuture<ExecutionResult>> resultFutures = executionInputList.stream()
                .map{input -> graphql.executeAsync(input)}.collect(Collectors.toList())
        resultFutures.forEach{future -> future.get()}
        then:
        fetchCounter.get() == 1
    }
}

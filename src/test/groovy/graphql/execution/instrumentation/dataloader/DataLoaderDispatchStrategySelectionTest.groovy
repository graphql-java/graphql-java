package graphql.execution.instrumentation.dataloader

import graphql.EngineRunningState
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQL
import graphql.MutationSchema
import graphql.Profiler
import graphql.TestUtil
import graphql.execution.DataLoaderDispatchStrategy
import graphql.execution.Execution
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStrategy
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.ValueUnboxer
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.parser.Parser
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
import static java.util.concurrent.CompletableFuture.completedFuture
import static java.util.concurrent.TimeUnit.SECONDS

class DataLoaderDispatchStrategySelectionTest extends Specification {

    def "per-level opt-out preserves automatic dispatch of directly accessed loaders"() {
        given:
        def loader = DataLoaderFactory.newDataLoader({ keys -> completedFuture(keys) } as BatchLoader)
        def registry = new DataLoaderRegistry().register("names", loader)
        DataFetcher fetcher = { environment -> registry.getDataLoader("names").load("Luna") }
        def graphQL = GraphQL.newGraphQL(TestUtil.schema("type Query { name: String }", [Query: [name: fetcher]])).build()
        def input = ExecutionInput.newExecutionInput("{ name }").dataLoaderRegistry(registry).build()
        GraphQL.unusualConfiguration(input).dataloaderConfig().enableDataLoaderExhaustedDispatching(false)

        when:
        def result = graphQL.executeAsync(input).get(5, SECONDS)

        then:
        result.errors.empty
        result.data == [name: "Luna"]
    }

    def "selects the configured dispatch strategy for #operation with exhausted=#exhausted and chaining=#chaining"() {
        given:
        def input = ExecutionInput.newExecutionInput("$operation { __typename }")
                .dataLoaderRegistry(new DataLoaderRegistry())
                .build()
        if (exhausted != null) {
            input.graphQLContext.put(ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, exhausted)
        }
        if (chaining != null) {
            input.graphQLContext.put(ENABLE_DATA_LOADER_CHAINING, chaining)
        }
        def strategy = Mock(ExecutionStrategy)
        def execution = new Execution(strategy, strategy, strategy, SimplePerformantInstrumentation.INSTANCE, ValueUnboxer.DEFAULT, false)

        when:
        execution.execute(Parser.parse(input.query), MutationSchema.schema, ExecutionId.generate(), input, null,
                new EngineRunningState(input, Profiler.NO_OP), Profiler.NO_OP).join()

        then:
        1 * strategy.execute(_, _) >> { ExecutionContext context, ExecutionStrategyParameters parameters ->
            assert context.dataLoaderDispatcherStrategy.class == expected
            completedFuture(new ExecutionResultImpl([__typename: "Root"], []))
        }

        where:
        [operation, exhausted, chaining, expected] << [
                ["query", "mutation", "subscription"],
                [
                        [null, null, ExhaustedDataLoaderDispatchStrategy],
                        [null, false, ExhaustedDataLoaderDispatchStrategy],
                        [null, true, PerLevelDataLoaderDispatchStrategy],
                        [false, null, PerLevelDataLoaderDispatchStrategy],
                        [false, false, PerLevelDataLoaderDispatchStrategy],
                        [false, true, PerLevelDataLoaderDispatchStrategy],
                        [true, null, ExhaustedDataLoaderDispatchStrategy],
                        [true, false, ExhaustedDataLoaderDispatchStrategy],
                ]
        ].combinations().collect { [it[0]] + it[1] }
    }

    def "automatic dispatch stays disabled without a registry or when explicitly disabled"() {
        given:
        def inputBuilder = ExecutionInput.newExecutionInput("{ __typename }")
                .graphQLContext([(ENABLE_DATA_LOADER_CHAINING): true, (ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING): true])
        if (withRegistry) {
            inputBuilder.dataLoaderRegistry(new DataLoaderRegistry())
        }
        def input = inputBuilder.build()
        def strategy = Mock(ExecutionStrategy)
        def execution = new Execution(strategy, strategy, strategy, SimplePerformantInstrumentation.INSTANCE, ValueUnboxer.DEFAULT, disableAutomaticDispatch)

        when:
        execution.execute(Parser.parse(input.query), MutationSchema.schema, ExecutionId.generate(), input, null,
                new EngineRunningState(input, Profiler.NO_OP), Profiler.NO_OP).join()

        then:
        1 * strategy.execute(_, _) >> { ExecutionContext context, ExecutionStrategyParameters parameters ->
            assert context.dataLoaderDispatcherStrategy.is(DataLoaderDispatchStrategy.NO_OP)
            completedFuture(new ExecutionResultImpl([__typename: "Root"], []))
        }

        where:
        withRegistry | disableAutomaticDispatch
        false        | false
        false        | true
        true         | true
    }
}

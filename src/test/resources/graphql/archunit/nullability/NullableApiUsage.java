package nullability;

import graphql.extensions.ExtensionsBuilder;
import graphql.execution.CoercedVariables;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext;
import graphql.incremental.StreamPayload;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitor;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DelegatingDataFetchingEnvironment;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

@NullMarked
public class NullableApiUsage {
    public static void nullableResolvers() {
        DataFetcher<@Nullable String> fetcher = environment -> null;
        AsyncDataFetcher<@Nullable String> async = AsyncDataFetcher.async(fetcher);
        AsyncDataFetcher<@Nullable String> withExecutor = AsyncDataFetcher.async(fetcher, Runnable::run);
        DataFetcherFactory<@Nullable String> factory = DataFetcherFactories.useDataFetcher(fetcher);
        BiFunction<DataFetchingEnvironment, @Nullable Object, @Nullable Object> mapper = (environment, value) -> null;
        DataFetcherFactories.wrapDataFetcher(fetcher, mapper);
    }

    public static CompletableFuture<@Nullable String> nullableAsyncResult(DataFetchingEnvironment environment) {
        DataFetcher<String> fetcher = ignored -> null;
        return AsyncDataFetcher.async(fetcher).get(environment);
    }

    public static void nullableSpiInputs(Map<String, @Nullable Object> variables) {
        CoercedVariables.of(variables);
        ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER.provide("{ field }", null, null);
        ValueUnboxer.DEFAULT.unbox(null);
        InstrumentationState state = InstrumentationState.ofState(new InstrumentationState() {});
        state.toString();
    }

    public static void nullableInstrumentationCallbacks(Map<String, @Nullable Object> result) {
        ExecuteObjectInstrumentationContext.nonNullCtx(null).onCompleted(result, null);
        ExecutionStrategyInstrumentationContext.nonNullCtx(null).onCompleted(null, null);
        FieldFetchingInstrumentationContext context = FieldFetchingInstrumentationContext.nonNullCtx(null);
        context.onFetchedValue(null);
        context.onCompleted(null, null);
    }

    public static @Nullable String nullableArgument(DelegatingDataFetchingEnvironment environment) {
        return environment.getArgumentOrDefault("arg", null);
    }

    public static Map<String, @Nullable Object> nullableArguments(DataFetchingEnvironment environment) {
        return environment.getArguments();
    }

    public static Map<String, @Nullable Object> nullableVariables(DelegatingDataFetchingEnvironment environment) {
        return environment.getVariables();
    }

    public static Map<Object, @Nullable Object> nullableExtensions(Map<Object, @Nullable Object> values) {
        return ExtensionsBuilder.newExtensionsBuilder().addValue("null", null).addValues(values).buildExtensions();
    }

    public static @Nullable List<@Nullable String> nullableItems(StreamPayload payload) {
        return payload.<String>getItems();
    }

    public static @Nullable Object nullableTraversal(Node<?> node, NodeVisitor visitor) {
        return new NodeTraverser().depthFirst(visitor, node);
    }

    public static @Nullable Object nullableContext(TraverserContext<?> context) {
        context.setAccumulate(null);
        return context.getNewAccumulate();
    }

    public static Map<@Nullable String, List<TraverserContext<Node<?>>>> nullableChildKeys(TraverserContext<Node<?>> context) {
        return context.getChildrenContexts();
    }
}

package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.PublicSpi;
import graphql.execution.FieldValueInfo;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@PublicSpi
public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void onFieldValuesException() {

    }

    /**
     * This creates a no-op {@link InstrumentationContext} if the one pass in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     *
     * @return a non null {@link InstrumentationContext} that maybe a no-op
     */
    @Nonnull
    @Internal
    static ExecutionStrategyInstrumentationContext nonNullCtx(ExecutionStrategyInstrumentationContext nullableContext) {
        return nullableContext == null ? NOOP : nullableContext;
    }

    @Internal
    ExecutionStrategyInstrumentationContext NOOP = new ExecutionStrategyInstrumentationContext() {
        @Override
        public void onDispatched(CompletableFuture<ExecutionResult> result) {
        }

        @Override
        public void onCompleted(ExecutionResult result, Throwable t) {
        }
    };

}

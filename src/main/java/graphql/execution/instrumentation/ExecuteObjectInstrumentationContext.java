package graphql.execution.instrumentation;

import graphql.Internal;
import graphql.PublicSpi;
import graphql.execution.FieldValueInfo;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@PublicSpi
public interface ExecuteObjectInstrumentationContext extends InstrumentationContext<Map<String, @Nullable Object>> {

    @Internal
    ExecuteObjectInstrumentationContext NOOP = new ExecuteObjectInstrumentationContext() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(@Nullable Map<String, @Nullable Object> result, @Nullable Throwable t) {
        }
    };

    /**
     * This creates a no-op {@link InstrumentationContext} if the one pass in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     *
     * @return a non null {@link InstrumentationContext} that maybe a no-op
     */
    @Internal
    static ExecuteObjectInstrumentationContext nonNullCtx(@Nullable ExecuteObjectInstrumentationContext nullableContext) {
        return nullableContext == null ? NOOP : nullableContext;
    }

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
    }

    default void onFieldValuesException() {
    }

}

package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.FieldValueInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@PublicSpi
public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default CompletableFuture<Void> onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
        return CompletableFuture.completedFuture(null);
    }

    default void onFieldValuesException() {

    }

}

package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.CompleteValueInfo;

import java.util.List;

public interface ExecutionStrategyContext extends InstrumentationContext<ExecutionResult> {

    default void completeValuesInfo(List<CompleteValueInfo> completeValueInfoList) {

    }
}

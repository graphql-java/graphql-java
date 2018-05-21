package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.CompleteValueInfo;

import java.util.List;

public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    // onValuesInfo and FieldValueInfo
    default void completeValuesInfo(List<CompleteValueInfo> completeValueInfoList) {

    }
}

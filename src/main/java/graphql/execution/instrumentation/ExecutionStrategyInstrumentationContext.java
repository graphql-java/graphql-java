package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.FieldValueInfo;

import java.util.List;

public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }
}

package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.FieldValueInfo;

import java.util.List;

@PublicSpi
public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void onFieldValuesException() {

    }

}

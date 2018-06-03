package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.FieldValueInfo;
import graphql.language.Field;

import java.util.List;

public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {

    }

    default void onDeferredField(List<Field> field) {

    }
}

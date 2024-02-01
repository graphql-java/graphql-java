package graphql.execution.instrumentation;

import graphql.execution.FieldValueInfo;
import graphql.execution.defer.DeferredCall;

public interface DeferredFieldInstrumentationContext extends InstrumentationContext<DeferredCall.FieldWithExecutionResult> {

    default void onFieldValueInfo(FieldValueInfo fieldValueInfo) {
        System.out.println("DeferredFieldInstrumentationContext.onFieldValueInfo() [default]: " + fieldValueInfo);

    }

}

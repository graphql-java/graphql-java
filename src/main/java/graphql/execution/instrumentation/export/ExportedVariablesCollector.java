package graphql.execution.instrumentation.export;

import graphql.execution.instrumentation.InstrumentationState;

import java.util.Map;

public interface ExportedVariablesCollector extends InstrumentationState {

    Map<String, Object> getVariables();

    void collect(ExportedVariablesCollectionEnvironment env);
}

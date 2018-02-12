package graphql.execution.instrumentation.export;

import graphql.execution.instrumentation.InstrumentationPreExecutionState;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.Map;

/**
 * This is called to collect exported variables ready to be passed into a new query
 */
public interface ExportedVariablesCollector extends InstrumentationPreExecutionState, InstrumentationState {

    /**
     * @return the map of variables collected so far
     */
    Map<String, Object> getVariables();

    /**
     * Called to collect variables, which are inside the environment
     *
     * @param env the collecting environment
     */
    void collect(ExportedVariablesCollectionEnvironment env);
}

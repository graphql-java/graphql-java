package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;

import java.util.Map;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationExecutionParameters {
    private final String query;
    private final String operation;
    private final Object context;
    private final Map<String, Object> arguments;

    public InstrumentationExecutionParameters(String query, String operation, Object context, Map<String, Object> arguments) {
        this.query = query;
        this.operation = operation;
        this.context = context;
        this.arguments = arguments;
    }

    public String getQuery() {
        return query;
    }

    public String getOperation() {
        return operation;
    }

    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}

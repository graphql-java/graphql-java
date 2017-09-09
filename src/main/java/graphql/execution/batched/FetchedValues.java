package graphql.execution.batched;

import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionTypeInfo;

import java.util.List;

public class FetchedValues {

    private final List<FetchedValue> fetchedValues;
    private final ExecutionTypeInfo executionTypeInfo;
    private final ExecutionPath path;

    public FetchedValues(List<FetchedValue> fetchedValues, ExecutionTypeInfo executionTypeInfo, ExecutionPath path) {
        this.fetchedValues = fetchedValues;
        this.executionTypeInfo = executionTypeInfo;
        this.path = path;
    }

    public List<FetchedValue> getValues() {
        return fetchedValues;
    }

    public ExecutionTypeInfo getExecutionTypeInfo() {
        return executionTypeInfo;
    }

    public ExecutionPath getPath() {
        return path;
    }
}

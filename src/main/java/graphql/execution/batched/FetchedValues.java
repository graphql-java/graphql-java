package graphql.execution.batched;

import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionInfo;

import java.util.List;

@Deprecated
public class FetchedValues {

    private final List<FetchedValue> fetchedValues;
    private final ExecutionInfo executionInfo;
    private final ExecutionPath path;

    public FetchedValues(List<FetchedValue> fetchedValues, ExecutionInfo executionInfo, ExecutionPath path) {
        this.fetchedValues = fetchedValues;
        this.executionInfo = executionInfo;
        this.path = path;
    }

    public List<FetchedValue> getValues() {
        return fetchedValues;
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
    }

    public ExecutionPath getPath() {
        return path;
    }
}

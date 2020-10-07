package graphql.execution.batched;

import graphql.PublicApi;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;

import java.util.List;

@Deprecated
@PublicApi
public class FetchedValues {

    private final List<FetchedValue> fetchedValues;
    private final ExecutionStepInfo executionStepInfo;
    private final ResultPath path;

    public FetchedValues(List<FetchedValue> fetchedValues, ExecutionStepInfo executionStepInfo, ResultPath path) {
        this.fetchedValues = fetchedValues;
        this.executionStepInfo = executionStepInfo;
        this.path = path;
    }

    public List<FetchedValue> getValues() {
        return fetchedValues;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public ResultPath getPath() {
        return path;
    }
}

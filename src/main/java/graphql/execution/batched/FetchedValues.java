package graphql.execution.batched;

import graphql.PublicApi;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

@Deprecated
@PublicApi
public class FetchedValues {

    private final List<FetchedValue> fetchedValues;
    private final DataFetchingEnvironment dataFetchingEnvironment;

    public FetchedValues(List<FetchedValue> fetchedValues, DataFetchingEnvironment dataFetchingEnvironment) {
        this.fetchedValues = fetchedValues;
        this.dataFetchingEnvironment = dataFetchingEnvironment;
    }

    public List<FetchedValue> getValues() {
        return fetchedValues;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return dataFetchingEnvironment.getExecutionStepInfo();
    }

    public ResultPath getPath() {
        return dataFetchingEnvironment.getExecutionStepInfo().getPath();
    }

    public DataFetchingEnvironment getDataFetchingEnvironment() {
        return dataFetchingEnvironment;
    }
}

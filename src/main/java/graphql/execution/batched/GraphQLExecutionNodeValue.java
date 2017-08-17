package graphql.execution.batched;

public class GraphQLExecutionNodeValue {

    private final ResultContainer resultContainer;
    private final Object value;

    public GraphQLExecutionNodeValue(ResultContainer resultContainer, /*Nullable*/ Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    public ResultContainer getResultContainer() {
        return resultContainer;
    }

    /*Nullable*/
    public Object getValue() {
        return value;
    }
}

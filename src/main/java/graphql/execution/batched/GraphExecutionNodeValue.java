package graphql.execution.batched;

public class GraphExecutionNodeValue {

    private final GraphqlExecutionResultContainer resultContainer;
    private final Object value;

    public GraphExecutionNodeValue(GraphqlExecutionResultContainer resultContainer, /*Nullable*/ Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    public GraphqlExecutionResultContainer getResultContainer() {
        return resultContainer;
    }

    /*Nullable*/ public Object getValue() {
        return value;
    }
}

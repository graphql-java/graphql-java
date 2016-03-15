package graphql.execution.batched;

public class GraphQLExecutionNodeValue {

    private final GraphQLExecutionResultContainer resultContainer;
    private final Object value;

    public GraphQLExecutionNodeValue(GraphQLExecutionResultContainer resultContainer, /*Nullable*/ Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    public GraphQLExecutionResultContainer getResultContainer() {
        return resultContainer;
    }

    /*Nullable*/
    public Object getValue() {
        return value;
    }
}

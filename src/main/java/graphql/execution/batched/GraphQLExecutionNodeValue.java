package graphql.execution.batched;

public class GraphQLExecutionNodeValue {

    private final GraphqlExecutionResultContainer2 resultContainer;
    private final Object value;

    public GraphQLExecutionNodeValue(GraphqlExecutionResultContainer2 resultContainer, /*Nullable*/ Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    public GraphqlExecutionResultContainer2 getResultContainer() {
        return resultContainer;
    }

    /*Nullable*/ public Object getValue() {
        return value;
    }
}

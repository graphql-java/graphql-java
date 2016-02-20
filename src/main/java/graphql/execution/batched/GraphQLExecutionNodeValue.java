package graphql.execution.batched;

/**
 * <p>GraphQLExecutionNodeValue class.</p>
 *
 * @author Andreas Marek
 */
public class GraphQLExecutionNodeValue {

    private final GraphQLExecutionResultContainer resultContainer;
    private final Object value;

    /**
     * <p>Constructor for GraphQLExecutionNodeValue.</p>
     *
     * @param resultContainer a {@link graphql.execution.batched.GraphQLExecutionResultContainer} object.
     * @param value a {@link java.lang.Object} object.
     */
    public GraphQLExecutionNodeValue(GraphQLExecutionResultContainer resultContainer, /*Nullable*/ Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>resultContainer</code>.</p>
     *
     * @return a {@link graphql.execution.batched.GraphQLExecutionResultContainer} object.
     */
    public GraphQLExecutionResultContainer getResultContainer() {
        return resultContainer;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    /*Nullable*/ public Object getValue() {
        return value;
    }
}

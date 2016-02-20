package graphql.execution.batched;

import java.util.Map;

class GraphQLExecutionNodeDatum extends GraphQLExecutionResultContainer {
    private final Map<String, Object> parentResult;
    private final Object source;

    /**
     * <p>Constructor for GraphQLExecutionNodeDatum.</p>
     *
     * @param parentResult a {@link java.util.Map} object.
     * @param source a {@link java.lang.Object} object.
     */
    public GraphQLExecutionNodeDatum(Map<String, Object> parentResult, Object source) {
        this.parentResult = parentResult;
        this.source = source;
    }

    /**
     * <p>Getter for the field <code>parentResult</code>.</p>
     *
     * @return a {@link java.util.Map} object.
     */
    public Map<String, Object> getParentResult() {
        return parentResult;
    }


    /** {@inheritDoc} */
    @Override
    public void putResult(String fieldName, Object value) {
        parentResult.put(fieldName, value);
    }

    /**
     * <p>Getter for the field <code>source</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getSource() {
        return source;
    }
}

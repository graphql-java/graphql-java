package graphql.execution.batched;

import java.util.List;

/**
 * <p>GraphQLExecutionResultList class.</p>
 *
 * @author Andreas Marek
 */
public class GraphQLExecutionResultList extends GraphQLExecutionResultContainer {

    private final List<Object> results;

    /**
     * <p>Constructor for GraphQLExecutionResultList.</p>
     *
     * @param results a {@link java.util.List} object.
     */
    public GraphQLExecutionResultList(List<Object> results) {
        this.results = results;
    }

    /** {@inheritDoc} */
    @Override
    public void putResult(String fieldName, Object value) {
        results.add(value);
    }
}

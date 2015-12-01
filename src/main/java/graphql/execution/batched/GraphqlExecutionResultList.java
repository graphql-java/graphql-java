package graphql.execution.batched;

import java.util.List;

public class GraphqlExecutionResultList extends GraphqlExecutionResultContainer {

    private final List<Object> results;

    public GraphqlExecutionResultList(List<Object> results) {
        this.results = results;
    }

    @Override
    public void putResult(String fieldName, Object value) {
        results.add(value);
    }
}

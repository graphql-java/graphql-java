package graphql.execution.batched;

import java.util.List;

public class GraphqlExecutionResultList2 extends GraphqlExecutionResultContainer2 {

    private final List<Object> results;

    public GraphqlExecutionResultList2(List<Object> results) {
        this.results = results;
    }

    @Override
    public void putResult(String fieldName, Object value) {
        results.add(value);
    }
}

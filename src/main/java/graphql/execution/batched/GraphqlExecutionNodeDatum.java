package graphql.execution.batched;

import java.util.Map;

class GraphqlExecutionNodeDatum extends GraphqlExecutionResultContainer {
    private final Map<String, Object> parentResult;
    private final Object source;

    public GraphqlExecutionNodeDatum(Map<String, Object> parentResult, Object source) {
        this.parentResult = parentResult;
        this.source = source;
    }

    public Map<String, Object> getParentResult() {
        return parentResult;
    }


    @Override
    public void putResult(String fieldName, Object value) {
        parentResult.put(fieldName, value);
    }

    public Object getSource() {
        return source;
    }
}

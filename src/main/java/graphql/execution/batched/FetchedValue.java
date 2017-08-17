package graphql.execution.batched;

public class FetchedValue {

    private final ResultContainer resultContainer;
    private final Object value;

    public FetchedValue(ResultContainer resultContainer, Object value) {
        this.resultContainer = resultContainer;
        this.value = value;
    }

    public ResultContainer getResultContainer() {
        return resultContainer;
    }

    public Object getValue() {
        return value;
    }
}

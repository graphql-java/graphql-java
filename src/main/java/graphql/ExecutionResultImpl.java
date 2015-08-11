package graphql;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<>();
    private Map<String, Object> data;

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(Map<String, Object> data, List<? extends GraphQLError> errors) {
        this.data = data;
        this.errors.addAll(errors);
    }

    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> result) {
        this.data = result;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }


}

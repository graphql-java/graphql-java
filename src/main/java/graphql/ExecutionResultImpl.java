package graphql;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutionResultImpl implements ExecutionResult {

    private Map<String, Object> data;

    private final List<GraphQLError> errors = new ArrayList<>();

    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(Map<String, Object> data, List<? extends GraphQLError> errors) {
        this.data = data;
        this.errors.addAll(errors);
    }


    public void setData(Map<String, Object> result) {
        this.data = result;
    }


    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }


}

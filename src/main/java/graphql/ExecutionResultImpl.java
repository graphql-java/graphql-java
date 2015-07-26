package graphql;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutionResultImpl implements ExecutionResult {

    private Map<String, Object> result;

    private final List<GraphQLError> errors = new ArrayList<>();

    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(Map<String, Object> result,List<? extends GraphQLError> errors) {
        this.result = result;
        this.errors.addAll(errors);
    }


    public void setResult(Map<String, Object> result) {
        this.result = result;
    }


    @Override
    public Map<String, Object> getData() {
        return result;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }


}

package graphql;


import java.util.ArrayList;
import java.util.List;

public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<GraphQLError>();
    private Object data;

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this.data = data;

        if (errors != null) {
            this.errors.addAll(errors);
        }
    }

    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    @Override
    public Object getData() {
        return data;
    }

    public void setData(Object result) {
        this.data = result;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<GraphQLError>(errors);
    }


}

package graphql;


import java.util.ArrayList;
import java.util.List;

@Internal
public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<>();
    private Object data;

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this(null, errors);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this.data = data;

        if (errors != null && !errors.isEmpty()) {
            this.errors.addAll(errors);
        }

    }



    @Override
    public <T> T getData() {
        //noinspection unchecked
        return (T) data;
    }

    public void setData(Object result) {
        this.data = result;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }

    @Override
    public ExecutionResult toSpecification() {
        return (errors == null || errors.isEmpty()) ? new ExecutionResultWithoutErrors(data) : this;
    }


    private static class ExecutionResultWithoutErrors implements ExecutionResult {

        private Object data;
        private final transient List<GraphQLError> errors = new ArrayList<>();

        ExecutionResultWithoutErrors(Object data) {
            this.data = data;
        }

        @Override
        public <T> T getData() {
            //noinspection unchecked
            return (T) data;
        }

        @Override
        public List<GraphQLError> getErrors() {
            return errors;
        }

        @Override
        public ExecutionResult toSpecification() {
            return this;
        }
    }

}

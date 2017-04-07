package graphql;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<>();
    private Object data;
    private Map<Object,Object> extensions = null;

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this(null,errors,null);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this(data,errors,null);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors, Map<Object,Object> extensions) {
        this.data = data;

        if (errors != null && !errors.isEmpty()) {
            this.errors.addAll(errors);
        }

        if (extensions != null && !extensions.isEmpty()) {
            this.extensions = new HashMap<>(extensions);
        }
    }

    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public void addExtensions(Map<Object, Object> extensions) {
        this.extensions.putAll(extensions);
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
    public Map<Object, Object> getExtensions() {
        return extensions == null ? null : new HashMap<>(extensions);
    }
}

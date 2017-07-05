package graphql;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<>();
    private final Object data;
    private final transient boolean dataPresent;

    public ExecutionResultImpl(GraphQLError error) {
        this(false, null, Collections.singletonList(error));
    }

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this(false, null, errors);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this(true, data, errors);
    }

    private ExecutionResultImpl(boolean dataPresent, Object data, List<? extends GraphQLError> errors) {
        this.dataPresent = dataPresent;
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

    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (dataPresent) {
            result.put("data", data);
        }
        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

}

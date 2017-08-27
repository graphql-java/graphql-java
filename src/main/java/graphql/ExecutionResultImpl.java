package graphql;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Internal
public class ExecutionResultImpl implements ExecutionResult {

    private final Object data;
    private final List<GraphQLError> errors;
    private final transient boolean dataPresent;
    private final transient Map<Object, Object> extensions;

    public ExecutionResultImpl(GraphQLError error) {
        this(false, null, Collections.singletonList(error), null);
    }

    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this(false, null, errors, null);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this(true, data, errors, null);
    }

    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors, Map<Object, Object> extensions) {
        this(true, data, errors, extensions);
    }

    private ExecutionResultImpl(boolean dataPresent, Object data, List<? extends GraphQLError> errors, Map<Object, Object> extensions) {
        this.dataPresent = dataPresent;
        this.data = data;

        if (errors != null && !errors.isEmpty()) {
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        } else {
            this.errors = Collections.emptyList();
        }

        this.extensions = extensions;
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
    public Map<Object, Object> getExtensions() {
        return extensions;
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (dataPresent) {
            result.put("data", data);
        }
        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errorsToSpec(errors));
        }
        if (extensions != null) {
            result.put("extensions", extensions);
        }
        return result;
    }

    private Object errorsToSpec(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    @Override
    public String toString() {
        return "ExecutionResultImpl{" +
                "data=" + data +
                ", errors=" + errors +
                ", dataPresent=" + dataPresent +
                ", extensions=" + extensions +
                '}';
    }
}

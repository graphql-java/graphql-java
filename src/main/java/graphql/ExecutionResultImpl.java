package graphql;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    public boolean isDataPresent() {
        return dataPresent;
    }

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals")
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

    public ExecutionResultImpl transform(Consumer<Builder> builderConsumer) {
        Builder builder = newExecutionResult().from(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newExecutionResult() {
        return new Builder();
    }

    public static class Builder {
        private boolean dataPresent;
        private Object data;
        private List<GraphQLError> errors = new ArrayList<>();
        private Map<Object, Object> extensions;

        public Builder from(ExecutionResultImpl executionResult) {
            dataPresent = executionResult.isDataPresent();
            data = executionResult.getData();
            errors = new ArrayList<>(executionResult.getErrors());
            extensions = executionResult.getExtensions();
            return this;
        }

        public Builder data(Object data) {
            dataPresent = true;
            this.data = data;
            return this;
        }

        public Builder errors(List<GraphQLError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder addErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return this;
        }

        public Builder addError(GraphQLError error) {
            this.errors.add(error);
            return this;
        }

        public Builder extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Builder addExtension(String key, Object value) {
            this.extensions = (this.extensions == null ? new LinkedHashMap<>() : this.extensions);
            this.extensions.put(key, value);
            return this;
        }

        public ExecutionResultImpl build() {
            return new ExecutionResultImpl(dataPresent, data, errors, extensions);
        }
    }
}

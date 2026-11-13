package graphql;


import com.google.common.collect.ImmutableList;
import graphql.collect.ImmutableKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.map;

@Internal
public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors;
    private final Object data;
    private final transient Map<Object, Object> extensions;
    private final transient boolean dataPresent;

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

    public ExecutionResultImpl(ExecutionResultImpl other) {
        this(other.dataPresent, other.data, other.errors, other.extensions);
    }

    public <T extends Builder<T>> ExecutionResultImpl(Builder<T> builder) {
        this(builder.dataPresent, builder.data, builder.errors, builder.extensions);
    }

    private ExecutionResultImpl(boolean dataPresent, Object data, List<? extends GraphQLError> errors, Map<Object, Object> extensions) {
        this.dataPresent = dataPresent;
        this.data = data;

        if (errors != null) {
            this.errors = ImmutableList.copyOf(errors);
        } else {
            this.errors = ImmutableKit.emptyList();
        }

        this.extensions = extensions;
    }

    public boolean isDataPresent() {
        return dataPresent;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return errors;
    }

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T> T getData() {
        //noinspection unchecked
        return (T) data;
    }

    @Override
    public Map<Object, Object> getExtensions() {
        return extensions;
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errorsToSpec(errors));
        }
        if (dataPresent) {
            result.put("data", data);
        }
        if (extensions != null) {
            result.put("extensions", extensions);
        }
        return result;
    }

    private Object errorsToSpec(List<GraphQLError> errors) {
        return map(errors, GraphQLError::toSpecification);
    }

    @SuppressWarnings("unchecked")
    static ExecutionResult fromSpecification(Map<String, Object> specificationMap) {
        ExecutionResult.Builder<?> builder = ExecutionResult.newExecutionResult();
        Object data = specificationMap.get("data");
        if (data != null) {
            builder.data(data);
        }
        List<Map<String, Object>> errors = (List<Map<String, Object>>) specificationMap.get("errors");
        if (errors != null) {
            builder.errors(GraphqlErrorHelper.fromSpecification(errors));
        }
        Map<Object, Object> extensions = (Map<Object, Object>) specificationMap.get("extensions");
        if (extensions != null) {
            builder.extensions(extensions);
        }
        return builder.build();
    }

    @Override
    public String toString() {
        return "ExecutionResultImpl{" +
                "errors=" + errors +
                ", data=" + data +
                ", dataPresent=" + dataPresent +
                ", extensions=" + extensions +
                '}';
    }

    public static <T extends Builder<T>> Builder<T> newExecutionResult() {
        return new Builder<>();
    }

    public static class Builder<T extends Builder<T>> implements ExecutionResult.Builder<T> {
        private boolean dataPresent;
        private Object data;
        private List<GraphQLError> errors = new ArrayList<>();
        private Map<Object, Object> extensions;

        @Override
        public T from(ExecutionResult executionResult) {
            dataPresent = executionResult.isDataPresent();
            data = executionResult.getData();
            errors = new ArrayList<>(executionResult.getErrors());
            extensions = executionResult.getExtensions();
            return (T) this;
        }

        @Override
        public T data(Object data) {
            dataPresent = true;
            this.data = data;
            return (T) this;
        }

        @Override
        public T errors(List<GraphQLError> errors) {
            this.errors = errors;
            return (T) this;
        }

        @Override
        public T addErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return (T) this;
        }

        @Override
        public T addError(GraphQLError error) {
            this.errors.add(error);
            return (T) this;
        }

        @Override
        public T extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return (T) this;
        }

        @Override
        public T addExtension(String key, Object value) {
            this.extensions = (this.extensions == null ? new LinkedHashMap<>() : this.extensions);
            this.extensions.put(key, value);
            return (T) this;
        }

        @Override
        public ExecutionResult build() {
            return new ExecutionResultImpl(dataPresent, data, errors, extensions);
        }
    }
}

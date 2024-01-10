package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.execution.ResultPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@ExperimentalApi
public abstract class IncrementalItem {
    private final List<Object> path;
    private final List<GraphQLError> errors;
    private final transient Map<Object, Object> extensions;

    protected IncrementalItem(List<Object> path, List<GraphQLError> errors, Map<Object, Object> extensions) {
        this.path = path;
        this.errors = errors;
        this.extensions = extensions;
    }

    public List<Object> getPath() {
        return this.path;
    }

    public List<GraphQLError> getErrors() {
        return this.errors;
    }

    public Map<Object, Object> getExtensions() {
        return this.extensions;
    }

    protected Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errorsToSpec(errors));
        }
        if (extensions != null) {
            result.put("extensions", extensions);
        }
        if (path != null) {
            result.put("path", path);
        }
        return result;
    }

    protected Object errorsToSpec(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    protected static abstract class Builder<T extends IncrementalItem> {
        protected List<Object> path;
        protected List<GraphQLError> errors = new ArrayList<>();
        protected Map<Object, Object> extensions;

        public Builder<T> from(T incrementalExecutionResult) {
            this.path = incrementalExecutionResult.getPath();
            this.errors = new ArrayList<>(incrementalExecutionResult.getErrors());
            this.extensions = incrementalExecutionResult.getExtensions();
            return this;
        }

        public Builder<T> path(ResultPath path) {
            if (path != null) {
                this.path = path.toList();
            }
            return this;
        }

        public Builder<T> path(List<Object> path) {
            this.path = path;
            return this;
        }

        public Builder<T> errors(List<GraphQLError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder<T> addErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return this;
        }

        public Builder<T> addError(GraphQLError error) {
            this.errors.add(error);
            return this;
        }

        public Builder<T> extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Builder<T> addExtension(String key, Object value) {
            this.extensions = (this.extensions == null ? new LinkedHashMap<>() : this.extensions);
            this.extensions.put(key, value);
            return this;
        }

        public abstract T build();
    }
}

package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.execution.ResultPath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Represents a payload that can be resolved after the initial response.
 */
@ExperimentalApi
public abstract class IncrementalPayload {
    private final List<Object> path;
    private final String label;
    private final List<GraphQLError> errors;
    private final transient Map<Object, Object> extensions;

    protected IncrementalPayload(List<Object> path, String label, List<GraphQLError> errors, Map<Object, Object> extensions) {
        this.path = path;
        this.errors = errors;
        this.label = label;
        this.extensions = extensions;
    }

    /**
     * @return list of field names and indices from root to the location of the corresponding `@defer` or `@stream` directive.
     */
    public List<Object> getPath() {
        return this.path;
    }

    /**
     * @return value derived from the corresponding `@defer` or `@stream` directive.
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * @return a list of field errors encountered during execution.
     */
    @Nullable
    public List<GraphQLError> getErrors() {
        return this.errors;
    }

    /**
     * @return a map of extensions or null if there are none
     */
    @Nullable
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

    protected static abstract class Builder<T extends IncrementalPayload> {
        protected List<Object> path;
        protected String label;
        protected List<GraphQLError> errors = new ArrayList<>();
        protected Map<Object, Object> extensions;

        public Builder<T> from(T incrementalExecutionResult) {
            this.path = incrementalExecutionResult.getPath();
            this.label = incrementalExecutionResult.getLabel();
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

        public Builder<T> label(String label) {
            this.label = label;
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

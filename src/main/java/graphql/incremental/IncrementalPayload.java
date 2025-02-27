package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public Map<String, Object> toSpecification() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("path", path);

        if (label != null) {
            result.put("label", label);
        }

        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errorsToSpec(errors));
        }
        if (extensions != null) {
            result.put("extensions", extensions);
        }
        return result;
    }

    protected Object errorsToSpec(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    public int hashCode() {
        return Objects.hash(path, label, errors, extensions);
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        IncrementalPayload that = (IncrementalPayload) obj;
        return Objects.equals(path, that.path) &&
                Objects.equals(label, that.label) &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(extensions, that.extensions);
    }

    protected static abstract class Builder<T extends Builder<T>> {
        protected List<Object> path;
        protected String label;
        protected List<GraphQLError> errors = new ArrayList<>();
        protected Map<Object, Object> extensions;

        public T from(IncrementalPayload incrementalPayload) {
            this.path = incrementalPayload.getPath();
            this.label = incrementalPayload.getLabel();
            if (incrementalPayload.getErrors() != null) {
                this.errors = new ArrayList<>(incrementalPayload.getErrors());
            }
            this.extensions = incrementalPayload.getExtensions();
            return (T) this;
        }

        public T path(ResultPath path) {
            if (path != null) {
                this.path = path.toList();
            }
            return (T) this;
        }

        public T path(List<Object> path) {
            this.path = path;
            return (T) this;
        }

        public T label(String label) {
            this.label = label;
            return (T) this;
        }

        public T errors(List<GraphQLError> errors) {
            this.errors = errors;
            return (T) this;
        }

        public T addErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return (T) this;
        }

        public T addError(GraphQLError error) {
            this.errors.add(error);
            return (T) this;
        }

        public T extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return (T) this;
        }

        public T addExtension(String key, Object value) {
            this.extensions = (this.extensions == null ? new LinkedHashMap<>() : this.extensions);
            this.extensions.put(key, value);
            return (T) this;
        }
    }
}

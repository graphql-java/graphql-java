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
public class IncrementalItem {
    private final List<Object> path;
    private final List<GraphQLError> errors;
    private final transient Map<Object, Object> extensions;

    private IncrementalItem(List<Object> path, List<GraphQLError> errors, Map<Object, Object> extensions) {
        this.path = path;
        this.errors = errors;
        this.extensions = extensions;
    }

    IncrementalItem(IncrementalItem incrementalItem) {
        this(incrementalItem.getPath(), incrementalItem.getErrors(), incrementalItem.getExtensions());
    }

    public List<Object> getPath() {
        return null;
    }

    public List<GraphQLError> getErrors() {
        return null;
    }

    public Map<Object, Object> getExtensions() {
        return null;
    }

    public Map<String, Object> toSpecification() {
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

    private Object errorsToSpec(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    static IncrementalItem.Builder newIncrementalExecutionResult() {
        return new IncrementalItem.Builder();
    }

    public static class Builder {
        private List<Object> path;
        private List<GraphQLError> errors = new ArrayList<>();
        private Map<Object, Object> extensions;

        public IncrementalItem.Builder from(IncrementalItem incrementalExecutionResult) {
            path = incrementalExecutionResult.getPath();
            errors = new ArrayList<>(incrementalExecutionResult.getErrors());
            extensions = incrementalExecutionResult.getExtensions();
            return this;
        }

        public IncrementalItem.Builder path(ResultPath path) {
            if (path != null) {
                this.path = path.toList();
            }
            return this;
        }

        public IncrementalItem.Builder path(List<Object> path) {
            this.path = path;
            return this;
        }

        public IncrementalItem.Builder errors(List<GraphQLError> errors) {
            this.errors = errors;
            return this;
        }

        public IncrementalItem.Builder addErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return this;
        }

        public IncrementalItem.Builder addError(GraphQLError error) {
            this.errors.add(error);
            return this;
        }

        public IncrementalItem.Builder extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public IncrementalItem.Builder addExtension(String key, Object value) {
            this.extensions = (this.extensions == null ? new LinkedHashMap<>() : this.extensions);
            this.extensions.put(key, value);
            return this;
        }

        public IncrementalItem build() {
            return new IncrementalItem(path, errors, extensions);
        }
    }
}

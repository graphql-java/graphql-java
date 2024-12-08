package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.GraphQLError;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a defer payload
 */
@ExperimentalApi
public class DeferPayload extends IncrementalPayload {
    private final Object data;

    private DeferPayload(Object data, List<Object> path, String label, List<GraphQLError> errors, Map<Object, Object> extensions) {
        super(path, label, errors, extensions);
        this.data = data;
    }

    /**
     * @param <T> the type to cast the result to
     * @return the resolved data
     */
    @Nullable
    public <T> T getData() {
        // noinspection unchecked
        return (T) this.data;
    }

    /**
     * @return a map of this payload that strictly follows the spec
     */
    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("data", data);
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        DeferPayload that = (DeferPayload) obj;
        return Objects.equals(data, that.data);
    }

    /**
     * @return a {@link DeferPayload.Builder} that can be used to create an instance of {@link DeferPayload}
     */
    public static DeferPayload.Builder newDeferredItem() {
        return new DeferPayload.Builder();
    }

    public static class Builder extends IncrementalPayload.Builder<Builder> {
        private Object data = null;

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder from(DeferPayload deferredItem) {
            super.from(deferredItem);
            this.data = deferredItem.data;
            return this;
        }

        public Builder from(ExecutionResult executionResult) {
            this.data = executionResult.getData();
            this.errors = executionResult.getErrors();
            this.extensions = executionResult.getExtensions();

            return this;
        }

        public DeferPayload build() {
            return new DeferPayload(data, this.path, this.label, this.errors, this.extensions);
        }
    }
}

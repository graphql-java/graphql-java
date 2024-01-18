package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.GraphQLError;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * @return the resolved data
     * @param <T> the type to cast the result to
     */
    @Nullable
    public <T> T getData() {
        //noinspection unchecked
        return (T) this.data;
    }

    /**
     * @return a map of this payload that strictly follows the spec
     */
    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());

        if (data != null) {
            map.put("data", data);
        }

        return map;
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

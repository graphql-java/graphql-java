package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a stream payload
 */
@ExperimentalApi
public class StreamPayload extends IncrementalPayload {
    private final List<Object> items;

    private StreamPayload(List<Object> items, List<Object> path, String label, List<GraphQLError> errors, Map<Object, Object> extensions) {
        super(path, label, errors, extensions);
        this.items = items;
    }

    /**
     * @return the resolved list of items
     * @param <T> the type to cast the result to
     */
    @Nullable
    public <T> List<T> getItems() {
        //noinspection unchecked
        return (List<T>) this.items;
    }

    /**
     * @return a map of this payload that strictly follows the spec
     */
    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());

        if (items != null) {
            map.put("items", items);
        }

        return map;
    }

    /**
     * @return a {@link Builder} that can be used to create an instance of {@link StreamPayload}
     */
    public static StreamPayload.Builder newStreamedItem() {
        return new StreamPayload.Builder();
    }

    public static class Builder extends IncrementalPayload.Builder<StreamPayload> {
        private List<Object> items = null;

        public Builder items(List<Object> items) {
            this.items = items;
            return this;
        }

        public Builder from(StreamPayload streamedItem) {
            super.from(streamedItem);
            this.items = streamedItem.items;
            return this;
        }

        @Override
        public StreamPayload build() {
            return new StreamPayload(items, this.path, this.label, this.errors, this.extensions);
        }
    }
}

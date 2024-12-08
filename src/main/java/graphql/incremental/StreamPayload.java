package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * @param <T> the type to cast the result to
     * @return the resolved list of items
     */
    @Nullable
    public <T> List<T> getItems() {
        // noinspection unchecked
        return (List<T>) this.items;
    }

    /**
     * @return a map of this payload that strictly follows the spec
     */
    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());
        map.put("items", items);
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), items);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        StreamPayload that = (StreamPayload) obj;
        return Objects.equals(items, that.items);
    }

    /**
     * @return a {@link Builder} that can be used to create an instance of {@link StreamPayload}
     */
    public static StreamPayload.Builder newStreamedItem() {
        return new StreamPayload.Builder();
    }

    public static class Builder extends IncrementalPayload.Builder<Builder> {
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

        public StreamPayload build() {
            return new StreamPayload(items, this.path, this.label, this.errors, this.extensions);
        }
    }
}

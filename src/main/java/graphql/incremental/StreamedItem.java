package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExperimentalApi
public class StreamedItem extends IncrementalItem {
    private final List<Object> items;

    private StreamedItem(List<Object> items, List<Object> path, List<GraphQLError> errors, Map<Object, Object> extensions) {
        super(path, errors, extensions);
        this.items = items;
    }

    public <T> List<T> getItems() {
        //noinspection unchecked
        return (List<T>) this.items;
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());

        if (items != null) {
            map.put("items", items);
        }

        return map;
    }

    public static StreamedItem.Builder newStreamedItem() {
        return new StreamedItem.Builder();
    }

    public static class Builder extends IncrementalItem.Builder<StreamedItem> {
        private List<Object> items = null;

        public Builder items(List<Object> items) {
            this.items = items;
            return this;
        }

        public Builder from(StreamedItem streamedItem) {
            super.from(streamedItem);
            this.items = streamedItem.items;
            return this;
        }

        @Override
        public StreamedItem build() {
            return new StreamedItem(items, this.path, this.errors, this.extensions);
        }
    }
}

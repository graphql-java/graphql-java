package graphql.defer;

import graphql.ExperimentalApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExperimentalApi
public class StreamedItem extends IncrementalItem {
    private final List<Object> items;

    private StreamedItem(List<Object> items, IncrementalItem incrementalExecutionResult) {
        super(incrementalExecutionResult);
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

    public static class Builder extends IncrementalItem.Builder {
        private List<Object> items = null;
        private final IncrementalItem.Builder builder = IncrementalItem.newIncrementalExecutionResult();

        public Builder items(List<Object> items) {
            this.items = items;
            return this;
        }

        public Builder from(IncrementalItem incrementalExecutionResult) {
            builder.from(incrementalExecutionResult);
            return this;
        }

        public IncrementalItem build() {
            IncrementalItem build = builder.build();
            return new StreamedItem(items, build);
        }
    }
}

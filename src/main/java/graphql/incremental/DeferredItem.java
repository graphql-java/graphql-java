package graphql.incremental;

import graphql.ExperimentalApi;
import graphql.GraphQLError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExperimentalApi
public class DeferredItem extends IncrementalItem {
    private final Object data;

    private DeferredItem(Object data, List<Object> path, List<GraphQLError> errors, Map<Object, Object> extensions) {
        super(path, errors, extensions);
        this.data = data;
    }

    public <T> T getData() {
        //noinspection unchecked
        return (T) this.data;
    }

    @Override
    public Map<String, Object> toSpecification() {
        Map<String, Object> map = new LinkedHashMap<>(super.toSpecification());

        if (data != null) {
            map.put("data", data);
        }

        return map;
    }

    public static DeferredItem.Builder newDeferredItem() {
        return new DeferredItem.Builder();
    }

    public static class Builder extends IncrementalItem.Builder<DeferredItem> {
        private Object data = null;

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder from(DeferredItem deferredItem) {
            super.from(deferredItem);
            this.data = deferredItem.data;
            return this;
        }

        @Override
        public DeferredItem build() {
            return new DeferredItem(data, this.path, this.errors, this.extensions);
        }
    }
}

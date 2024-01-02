package graphql.incremental;

import graphql.ExperimentalApi;

import java.util.LinkedHashMap;
import java.util.Map;

@ExperimentalApi
public class DeferredItem extends IncrementalItem {
    private final Object data;

    private DeferredItem(Object data, IncrementalItem incrementalExecutionResult) {
        super(incrementalExecutionResult);
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

    public static class Builder extends IncrementalItem.Builder  {
        private Object data = null;
        private final IncrementalItem.Builder builder = IncrementalItem.newIncrementalExecutionResult();

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder from(IncrementalItem incrementalExecutionResult) {
            builder.from(incrementalExecutionResult);
            return this;
        }

        public IncrementalItem build() {
            IncrementalItem build = builder.build();
            return new DeferredItem(data, build);
        }
    }
}

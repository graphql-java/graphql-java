package graphql.language;

import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@PublicApi
public class ChildrenContainer {

    private final Map<String, List<Node>> children = new LinkedHashMap<>();

    private ChildrenContainer(Map<String, List<Node>> children) {
        this.children.putAll(Assert.assertNotNull(children));
    }

    public <T extends Node> List<T> getList(String key) {
        return (List<T>) children.getOrDefault(key, new ArrayList<>());
    }

    public <T extends Node> T getSingleValueOrNull(String key) {
        List<? extends Node> result = children.getOrDefault(key, new ArrayList<>());
        if (result.size() > 1) {
            throw new IllegalStateException("children " + key + " is not a single value");
        }
        return result.size() > 0 ? (T) result.get(0) : null;
    }


    public static Builder newChildrenContainer() {
        return new Builder();
    }

    public ChildrenContainer transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public boolean isEmpty() {
        return this.children.isEmpty();
    }

    public static class Builder {
        private final Map<String, List<Node>> children = new LinkedHashMap<>();

        private Builder() {

        }

        private Builder(ChildrenContainer other) {
            this.children.putAll(other.children);
        }

        public Builder child(String key, Node child) {
            children.computeIfAbsent(key, (k) -> new ArrayList<>());
            children.get(key).add(child);
            return this;
        }

        public Builder children(String key, List<? extends Node> children) {
            this.children.computeIfAbsent(key, (k) -> new ArrayList<>());
            this.children.get(key).addAll(children);
            return this;
        }

        public Builder replaceChild(String key, int index, Node newChild) {
            this.children.get(key).set(index, newChild);
            return this;
        }

        public ChildrenContainer build() {
            return new ChildrenContainer(this.children);

        }
    }
}

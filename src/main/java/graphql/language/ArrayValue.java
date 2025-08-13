package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class ArrayValue extends AbstractNode<ArrayValue> implements Value<ArrayValue> {

    public static final String CHILD_VALUES = "values";
    private final ImmutableList<Value> values;

    @Internal
    protected ArrayValue(List<Value> values, @Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.values = ImmutableList.copyOf(values);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param values of the array
     */
    public ArrayValue(List<Value> values) {
        this(values, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public static Builder newArrayValue() {
        return new Builder();
    }

    public List<Value> getValues() {
        return values;
    }

    @Override
    public List<Node> getChildren() {
        return ImmutableList.copyOf(values);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_VALUES, values)
                .build();
    }

    @Override
    public ArrayValue withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .values(newChildren.getChildren(CHILD_VALUES))
        );
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "ArrayValue{" +
                "values=" + values +
                '}';
    }

    @Override
    public ArrayValue deepCopy() {
        List<Value> copiedValues = assertNotNull(deepCopy(values));
        return new ArrayValue(copiedValues, getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData());
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitArrayValue(this, context);
    }

    public ArrayValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Value> values = emptyList();
        private ImmutableList<Comment> comments = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(ArrayValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.values = ImmutableList.copyOf(existing.getValues());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder values(List<Value> values) {
            this.values = ImmutableList.copyOf(values);
            return this;
        }

        public Builder value(Value value) {
            this.values = ImmutableKit.addToList(this.values, value);
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }


        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }

        public ArrayValue build() {
            return new ArrayValue(values, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}

package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
public class ArrayValue extends AbstractNode<ArrayValue> implements Value<ArrayValue> {

    private final List<Value> values = new ArrayList<>();

    public static final String CHILD_VALUES = "values";

    @Internal
    protected ArrayValue(List<Value> values, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.values.addAll(values);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param values of the array
     */
    public ArrayValue(List<Value> values) {
        this(values, null, new ArrayList<>(), IgnoredChars.EMPTY);
    }

    public List<Value> getValues() {
        return values;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(values);
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
    public boolean isEqualTo(Node o) {
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
        return new ArrayValue(deepCopy(values), getSourceLocation(), getComments(), getIgnoredChars());
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitArrayValue(this, context);
    }

    public static Builder newArrayValue() {
        return new Builder();
    }

    public ArrayValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Value> values = new ArrayList<>();
        private List<Comment> comments = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(ArrayValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.values = existing.getValues();
            this.ignoredChars = existing.getIgnoredChars();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder values(List<Value> values) {
            this.values = values;
            return this;
        }

        public Builder value(Value value) {
            this.values.add(value);
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public ArrayValue build() {
            ArrayValue arrayValue = new ArrayValue(values, sourceLocation, comments, ignoredChars);
            return arrayValue;
        }
    }
}

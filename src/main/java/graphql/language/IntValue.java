package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static graphql.language.NodeUtil.assertNewChildrenAreEmpty;

@PublicApi
public class IntValue extends AbstractNode<IntValue> implements ScalarValue<IntValue> {

    private final BigInteger value;

    @Internal
    protected IntValue(BigInteger value, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param value of the Int
     */
    public IntValue(BigInteger value) {
        this(value, null, new ArrayList<>(), IgnoredChars.EMPTY);
    }

    public BigInteger getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer().build();
    }

    @Override
    public IntValue withNewChildren(NodeChildrenContainer newChildren) {
        assertNewChildrenAreEmpty(newChildren);
        return this;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IntValue that = (IntValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public IntValue deepCopy() {
        return new IntValue(value, getSourceLocation(), getComments(), IgnoredChars.EMPTY);
    }

    @Override
    public String toString() {
        return "IntValue{" +
                "value=" + value +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitIntValue(this, context);
    }

    public static Builder newIntValue() {
        return new Builder();
    }

    public static Builder newIntValue(BigInteger value) {
        return new Builder().value(value);
    }

    public IntValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private BigInteger value;
        private List<Comment> comments = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(IntValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.value = existing.getValue();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder value(BigInteger value) {
            this.value = value;
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

        public IntValue build() {
            IntValue intValue = new IntValue(value, sourceLocation, comments, ignoredChars);
            return intValue;
        }
    }
}

package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class IntValue extends AbstractNode<IntValue> implements Value<IntValue> {

    private final BigInteger value;

    @Internal
    protected IntValue(BigInteger value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param value
     */
    public IntValue(BigInteger value) {
        super(null, new ArrayList<>());
        this.value = value;
    }

    public BigInteger getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntValue that = (IntValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public IntValue deepCopy() {
        return new IntValue(value, getSourceLocation(), getComments());
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

        public IntValue build() {
            IntValue intValue = new IntValue(value, sourceLocation, comments);
            return intValue;
        }
    }
}

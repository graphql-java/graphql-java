package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class FloatValue extends AbstractNode<FloatValue> implements Value<FloatValue> {

    private final BigDecimal value;

    @Internal
    protected FloatValue(BigDecimal value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public FloatValue(BigDecimal value) {
        this(value, null, new ArrayList<>());
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public String toString() {
        return "FloatValue{" +
                "value=" + value +
                '}';
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FloatValue that = (FloatValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public FloatValue deepCopy() {
        return new FloatValue(value, getSourceLocation(), getComments());
    }

    public FloatValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFloatValue(this, context);
    }

    public static Builder newFloatValue() {
        return new Builder();
    }

    public static Builder newFloatValue(BigDecimal value) {
        return new Builder().value(value);
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private BigDecimal value;
        private List<Comment> comments = new ArrayList<>();

        private Builder() {
        }

        private Builder(FloatValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.value = existing.getValue();
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder value(BigDecimal value) {
            this.value = value;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public FloatValue build() {
            FloatValue floatValue = new FloatValue(value, sourceLocation, comments);
            return floatValue;
        }
    }
}

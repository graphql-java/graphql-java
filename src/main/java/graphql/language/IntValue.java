package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class IntValue extends AbstractNode<IntValue> implements Value<IntValue> {

    private final BigInteger value;

    private IntValue(BigInteger value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
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


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private BigInteger value;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
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

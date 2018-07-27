package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class StringValue extends AbstractNode<StringValue> implements Value<StringValue> {

    private final String value;

    @Internal
    protected StringValue(String value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param value
     */
    public StringValue(String value) {
        super(null, new ArrayList<>());
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        return result;
    }

    @Override
    public String toString() {
        return "StringValue{" +
                "value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringValue that = (StringValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public StringValue deepCopy() {
        return new StringValue(value, getSourceLocation(), getComments());
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitStringValue(this, context);
    }

    public static Builder newStringValue() {
        return new Builder();
    }

    public static Builder newStringValue(String value) {
        return new Builder().value(value);
    }

    public StringValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String value;
        private List<Comment> comments = new ArrayList<>();

        private Builder() {
        }

        private Builder(StringValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.value = existing.getValue();
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public StringValue build() {
            StringValue stringValue = new StringValue(value, sourceLocation, comments);
            return stringValue;
        }
    }
}

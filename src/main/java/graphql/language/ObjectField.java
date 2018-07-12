package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class ObjectField extends AbstractNode<ObjectField> implements NamedNode<ObjectField> {

    private final String name;
    private final Value value;

    @Internal
    protected ObjectField(String name, Value value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public ObjectField(String name, Value value) {
        this(name, value, null, new ArrayList<>());
    }

    @Override
    public String getName() {
        return name;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectField that = (ObjectField) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }

    @Override
    public ObjectField deepCopy() {
        return new ObjectField(name, deepCopy(this.value), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "ObjectField{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitObjectField(this, context);
    }

    public static Builder newObjectField() {
        return new Builder();
    }

    public ObjectField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private List<Comment> comments = new ArrayList<>();
        private Value value;

        private Builder() {
        }


        private Builder(ObjectField existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.value = existing.getValue();
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder value(Value value) {
            this.value = value;
            return this;
        }

        public ObjectField build() {
            ObjectField objectField = new ObjectField(name, value, sourceLocation, comments);
            return objectField;
        }
    }
}

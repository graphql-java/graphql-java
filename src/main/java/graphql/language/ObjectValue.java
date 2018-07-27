package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class ObjectValue extends AbstractNode<ObjectValue> implements Value<ObjectValue> {

    private final List<ObjectField> objectFields = new ArrayList<>();

    @Internal
    protected ObjectValue(List<ObjectField> objectFields, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.objectFields.addAll(objectFields);
    }

    /**
     * alternative to using a Builder for convenience
     */
    public ObjectValue(List<ObjectField> objectFields) {
        this(objectFields, null, new ArrayList<>());
    }

    public List<ObjectField> getObjectFields() {
        return new ArrayList<>(objectFields);
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(objectFields);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectValue that = (ObjectValue) o;

        return true;

    }

    @Override
    public ObjectValue deepCopy() {
        return new ObjectValue(deepCopy(objectFields), getSourceLocation(), getComments());
    }


    @Override
    public String toString() {
        return "ObjectValue{" +
                "objectFields=" + objectFields +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitObjectValue(this, context);
    }


    public static Builder newObjectValue() {
        return new Builder();
    }

    public ObjectValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<ObjectField> objectFields = new ArrayList<>();
        private List<Comment> comments = new ArrayList<>();

        private Builder() {
        }

        private Builder(ObjectValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.objectFields = existing.getObjectFields();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder objectFields(List<ObjectField> objectFields) {
            this.objectFields = objectFields;
            return this;
        }

        public Builder objectField(ObjectField objectField) {
            this.objectFields.add(objectField);
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public ObjectValue build() {
            ObjectValue objectValue = new ObjectValue(objectFields, sourceLocation, comments);
            return objectValue;
        }
    }
}

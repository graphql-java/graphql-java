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
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class ObjectValue extends AbstractNode<ObjectValue> implements Value<ObjectValue> {

    private final ImmutableList<ObjectField> objectFields;

    public static final String CHILD_OBJECT_FIELDS = "objectFields";

    @Internal
    protected ObjectValue(List<ObjectField> objectFields, @Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.objectFields = ImmutableList.copyOf(objectFields);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param objectFields the list of field that make up this object value
     */
    public ObjectValue(List<ObjectField> objectFields) {
        this(objectFields, null, emptyList(), IgnoredChars.EMPTY, ImmutableKit.emptyMap());
    }

    public List<ObjectField> getObjectFields() {
        return objectFields;
    }

    @Override
    public List<Node> getChildren() {
        return ImmutableList.copyOf(objectFields);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_OBJECT_FIELDS, objectFields)
                .build();
    }

    @Override
    public ObjectValue withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .objectFields(newChildren.getChildren(CHILD_OBJECT_FIELDS))
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
    public ObjectValue deepCopy() {
        List<ObjectField> copiedFields = assertNotNull(deepCopy(objectFields));
        return new ObjectValue(copiedFields, getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData());
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

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<ObjectField> objectFields = emptyList();
        private ImmutableList<Comment> comments = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(ObjectValue existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.objectFields = ImmutableList.copyOf(existing.getObjectFields());
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder objectFields(List<ObjectField> objectFields) {
            this.objectFields = ImmutableList.copyOf(objectFields);
            return this;
        }

        public Builder objectField(ObjectField objectField) {
            this.objectFields = ImmutableKit.addToList(objectFields, objectField);
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

        public ObjectValue build() {
            return new ObjectValue(objectFields, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}

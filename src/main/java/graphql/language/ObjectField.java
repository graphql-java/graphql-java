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
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class ObjectField extends AbstractNode<ObjectField> implements NamedNode<ObjectField> {

    private final String name;
    private final Value value;

    public static final String CHILD_VALUE = "value";

    @Internal
    protected ObjectField(String name, Value value, @Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.name = assertNotNull(name);
        this.value = assertNotNull(value);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name  of the field
     * @param value of the field
     */
    public ObjectField(String name, Value value) {
        this(name, value, null, emptyList(), IgnoredChars.EMPTY, ImmutableKit.emptyMap());
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
        return ImmutableList.of(value);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_VALUE, value)
                .build();
    }

    @Override
    public ObjectField withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .value(newChildren.getChildOrNull(CHILD_VALUE))
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

        ObjectField that = (ObjectField) o;

        return Objects.equals(name, that.name);

    }

    @Override
    public ObjectField deepCopy() {
        return new ObjectField(name, assertNotNull(deepCopy(this.value), "value deepCopy should not return null"), getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData());
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

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private ImmutableList<Comment> comments = emptyList();
        private Value value;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }


        private Builder(ObjectField existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.value = existing.getValue();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
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
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder value(Value value) {
            this.value = value;
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


        public ObjectField build() {
            return new ObjectField(name, value, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}

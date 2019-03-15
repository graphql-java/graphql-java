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
public class Argument extends AbstractNode<Argument> implements NamedNode<Argument> {

    private final String name;
    private final Value value;

    public static final String CHILD_VALUE = "value";

    @Internal
    protected Argument(String name, Value value, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.name = name;
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name  of the argument
     * @param value of the argument
     */
    public Argument(String name, Value value) {
        this(name, value, null, new ArrayList<>(), IgnoredChars.EMPTY);
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
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_VALUE, value)
                .build();
    }

    @Override
    public Argument withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .value(newChildren.getChildOrNull(CHILD_VALUE))
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

        Argument that = (Argument) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public Argument deepCopy() {
        return new Argument(name, deepCopy(value), getSourceLocation(), getComments(), getIgnoredChars());
    }

    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitArgument(this, context);
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(String name, Value value) {
        return new Builder().name(name).value(value);
    }

    public Argument transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Value value;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(Argument existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.value = existing.getValue();
            this.ignoredChars = existing.getIgnoredChars();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(Value value) {
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

        public Argument build() {
            Argument argument = new Argument(name, value, sourceLocation, comments, ignoredChars);
            return argument;
        }
    }
}

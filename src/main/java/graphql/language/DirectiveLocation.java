package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static graphql.language.NodeUtil.assertNewChildrenAreEmpty;

// This should probably be an enum... but the grammar
// doesn't enforce the names. These are the current names:
//    QUERY
//    MUTATION
//    FIELD
//    FRAGMENT_DEFINITION
//    FRAGMENT_SPREAD
//    INLINE_FRAGMENT
@PublicApi
public class DirectiveLocation extends AbstractNode<DirectiveLocation> implements NamedNode<DirectiveLocation> {
    private final String name;

    @Internal
    protected DirectiveLocation(String name, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.name = name;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the directive location
     */
    public DirectiveLocation(String name) {
        this(name, null, new ArrayList<>(), IgnoredChars.EMPTY);
    }

    @Override
    public String getName() {
        return name;
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
    public DirectiveLocation withNewChildren(NodeChildrenContainer newChildren) {
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

        DirectiveLocation that = (DirectiveLocation) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public DirectiveLocation deepCopy() {
        return new DirectiveLocation(name, getSourceLocation(), getComments(), getIgnoredChars());
    }

    @Override
    public String toString() {
        return "DirectiveLocation{" +
                "name='" + name + "'" +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitDirectiveLocation(this, context);
    }

    public static Builder newDirectiveLocation() {
        return new Builder();
    }

    public DirectiveLocation transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(DirectiveLocation existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public DirectiveLocation build() {
            DirectiveLocation directiveLocation = new DirectiveLocation(name, sourceLocation, comments, ignoredChars);
            return directiveLocation;
        }
    }
}

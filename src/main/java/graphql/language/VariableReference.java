package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class VariableReference extends AbstractNode<VariableReference> implements Value<VariableReference>, NamedNode<VariableReference> {

    private final String name;

    @Internal
    protected VariableReference(String name, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public VariableReference(String name) {
        super(null, new ArrayList<>());
        this.name = name;
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
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableReference that = (VariableReference) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public VariableReference deepCopy() {
        return new VariableReference(name, getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "VariableReference{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitVariableReference(this, context);
    }

    public static Builder newVariableReference() {
        return new Builder();
    }

    public VariableReference transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;

        private Builder() {
        }

        private Builder(VariableReference existing) {
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

        public VariableReference build() {
            VariableReference variableReference = new VariableReference(name, sourceLocation, comments);
            return variableReference;
        }
    }
}

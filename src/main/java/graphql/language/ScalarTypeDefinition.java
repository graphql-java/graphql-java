package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class ScalarTypeDefinition extends AbstractNode<ScalarTypeDefinition> implements TypeDefinition<ScalarTypeDefinition>, DirectivesContainer<ScalarTypeDefinition> {

    private final String name;
    private final Description description;
    private final List<Directive> directives;

    @Internal
    protected ScalarTypeDefinition(String name, List<Directive> directives, Description description, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.directives = directives;
        this.description = description;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public ScalarTypeDefinition(String name) {
        this(name, new ArrayList<>(), null, null, new ArrayList<>());
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
    }

    @Override
    public String getName() {
        return name;
    }


    public Description getDescription() {
        return description;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScalarTypeDefinition that = (ScalarTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public ScalarTypeDefinition deepCopy() {
        return new ScalarTypeDefinition(name, deepCopy(directives), description, getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "ScalarTypeDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitScalarTypeDefinition(this, context);
    }

    public static Builder newScalarTypeDefinition() {
        return new Builder();
    }

    public ScalarTypeDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();

        private Builder() {
        }

        private Builder(ScalarTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
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

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives.add(directive);
            return this;
        }

        public ScalarTypeDefinition build() {
            ScalarTypeDefinition scalarTypeDefinition = new ScalarTypeDefinition(name,
                    directives,
                    description,
                    sourceLocation,
                    comments);
            return scalarTypeDefinition;
        }
    }
}

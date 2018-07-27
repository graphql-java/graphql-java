package graphql.language;


import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EnumValueDefinition extends AbstractNode<EnumValueDefinition> implements DirectivesContainer<EnumValueDefinition> {
    private final String name;
    private final Description description;
    private final List<Directive> directives;


    @Internal
    protected EnumValueDefinition(String name,
                                List<Directive> directives,
                                Description description,
                                SourceLocation sourceLocation,
                                List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.description = description;
        this.directives = (null == directives) ? new ArrayList<>() : directives;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public EnumValueDefinition(String name) {
        this(name, new ArrayList<>(), null, null, new ArrayList<>());
    }

    /**
     * alternative to using a Builder for convenience
     */
    public EnumValueDefinition(String name, List<Directive> directives) {
        this(name, directives, null, null, new ArrayList<>());
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
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

        EnumValueDefinition that = (EnumValueDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public EnumValueDefinition deepCopy() {
        return new EnumValueDefinition(name, deepCopy(directives), description, getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "EnumValueDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumValueDefinition(this, context);
    }

    public static Builder newEnumValueDefinition() {
        return new Builder();
    }

    public EnumValueDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<Directive> directives;

        private Builder() {
        }

        private Builder(EnumValueDefinition existing) {
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

        public EnumValueDefinition build() {
            EnumValueDefinition enumValueDefinition = new EnumValueDefinition(name, directives, description, sourceLocation, comments);
            return enumValueDefinition;
        }
    }
}

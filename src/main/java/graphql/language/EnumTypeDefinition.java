package graphql.language;

import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class EnumTypeDefinition extends AbstractNode<EnumTypeDefinition> implements TypeDefinition<EnumTypeDefinition>, DirectivesContainer<EnumTypeDefinition> {
    private final String name;
    private final Description description;
    private final List<EnumValueDefinition> enumValueDefinitions;
    private final List<Directive> directives;

    @Internal
    protected EnumTypeDefinition(String name,
                       List<EnumValueDefinition> enumValueDefinitions,
                       List<Directive> directives,
                       Description description,
                       SourceLocation sourceLocation,
                       List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.description = description;
        this.directives = (null == directives) ? new ArrayList<>() : directives;
        this.enumValueDefinitions = enumValueDefinitions;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public EnumTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), null, null, new ArrayList<>());
    }

    public List<EnumValueDefinition> getEnumValueDefinitions() {
        return new ArrayList<>(enumValueDefinitions);
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
        result.addAll(enumValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumTypeDefinition that = (EnumTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public EnumTypeDefinition deepCopy() {
        return new EnumTypeDefinition(name,
                deepCopy(enumValueDefinitions),
                deepCopy(directives),
                description,
                getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "name='" + name + '\'' +
                ", enumValueDefinitions=" + enumValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumTypeDefinition(this, context);
    }

    public static Builder newEnumTypeDefinition() {
        return new Builder();
    }

    public EnumTypeDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<EnumValueDefinition> enumValueDefinitions = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();

        private Builder() {
        }

        private Builder(EnumTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.enumValueDefinitions = existing.getEnumValueDefinitions();
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

        public Builder enumValueDefinitions(List<EnumValueDefinition> enumValueDefinitions) {
            this.enumValueDefinitions = enumValueDefinitions;
            return this;
        }

        public Builder enumValueDefinition(EnumValueDefinition enumValueDefinition) {
            this.enumValueDefinitions.add(enumValueDefinition);
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

        public EnumTypeDefinition build() {
            EnumTypeDefinition enumTypeDefinition = new EnumTypeDefinition(name, enumValueDefinitions, directives, description, sourceLocation, comments);
            return enumTypeDefinition;
        }
    }
}

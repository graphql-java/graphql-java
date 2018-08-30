package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class UnionTypeDefinition extends AbstractNode<UnionTypeDefinition> implements TypeDefinition<UnionTypeDefinition>, DirectivesContainer<UnionTypeDefinition> {

    private final String name;
    private final Description description;
    private final List<Directive> directives;
    private final List<Type> memberTypes;

    @Internal
    protected UnionTypeDefinition(String name,
                        List<Directive> directives,
                        List<Type> memberTypes,
                        Description description,
                        SourceLocation sourceLocation,
                        List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.directives = directives;
        this.memberTypes = memberTypes;
        this.description = description;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public UnionTypeDefinition(String name,
                               List<Directive> directives) {
        this(name, directives, new ArrayList<>(), null, null, new ArrayList<>());
    }

    /**
     * alternative to using a Builder for convenience
     */
    public UnionTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), null, null, new ArrayList<>());
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
    }

    public List<Type> getMemberTypes() {
        return new ArrayList<>(memberTypes);
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
        result.addAll(memberTypes);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnionTypeDefinition that = (UnionTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public UnionTypeDefinition deepCopy() {
        return new UnionTypeDefinition(name,
                deepCopy(directives),
                deepCopy(memberTypes),
                description,
                getSourceLocation(),
                getComments()
        );
    }

    @Override
    public String toString() {
        return "UnionTypeDefinition{" +
                "name='" + name + '\'' +
                "directives=" + directives +
                ", memberTypes=" + memberTypes +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitUnionTypeDefinition(this, context);
    }

    public static Builder newUnionTypeDefinition() {
        return new Builder();
    }

    public UnionTypeDefinition transform(Consumer<Builder> builderConsumer) {
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
        private List<Type> memberTypes = new ArrayList<>();

        private Builder() {
        }

        private Builder(UnionTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.memberTypes = existing.getMemberTypes();
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

        public Builder memberTypes(List<Type> memberTypes) {
            this.memberTypes = memberTypes;
            return this;
        }

        public Builder memberType(Type memberType) {
            this.memberTypes.add(memberType);
            return this;
        }

        public UnionTypeDefinition build() {
            UnionTypeDefinition unionTypeDefinition = new UnionTypeDefinition(name,
                    directives,
                    memberTypes,
                    description,
                    sourceLocation,
                    comments);
            return unionTypeDefinition;
        }
    }
}

package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.FpKit;
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
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class UnionTypeDefinition extends AbstractDescribedNode<UnionTypeDefinition> implements TypeDefinition<UnionTypeDefinition>, DirectivesContainer<UnionTypeDefinition>, NamedNode<UnionTypeDefinition> {

    private final String name;
    private final NodeUtil.DirectivesHolder directives;
    private final ImmutableList<Type> memberTypes;

    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_MEMBER_TYPES = "memberTypes";

    @Internal
    protected UnionTypeDefinition(String name,
                                  List<Directive> directives,
                                  List<Type> memberTypes,
                                  @Nullable Description description,
                                  @Nullable SourceLocation sourceLocation,
                                  List<Comment> comments,
                                  IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.memberTypes = ImmutableList.copyOf(memberTypes);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name       of the union
     * @param directives on the union
     */
    public UnionTypeDefinition(String name,
                               List<Directive> directives) {
        this(name, directives, emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the union
     */
    public UnionTypeDefinition(String name) {
        this(name, emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    @Override
    public List<Directive> getDirectives() {
        return directives.getDirectives();
    }

    @Override
    public Map<String, List<Directive>> getDirectivesByName() {
        return directives.getDirectivesByName();
    }

    @Override
    public List<Directive> getDirectives(String directiveName) {
        return directives.getDirectives(directiveName);
    }

    @Override
    public boolean hasDirective(String directiveName) {
        return directives.hasDirective(directiveName);
    }

    public List<Type> getMemberTypes() {
        return memberTypes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        return FpKit.concat(directives.getDirectives(), memberTypes);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .children(CHILD_MEMBER_TYPES, memberTypes)
                .build();
    }

    @Override
    public UnionTypeDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .memberTypes(newChildren.getChildren(CHILD_MEMBER_TYPES))
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

        UnionTypeDefinition that = (UnionTypeDefinition) o;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public UnionTypeDefinition deepCopy() {
        return new UnionTypeDefinition(name,
                assertNotNull(deepCopy(directives.getDirectives())),
                assertNotNull(deepCopy(memberTypes)),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
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

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Description description;
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<Type> memberTypes = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(UnionTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.memberTypes = ImmutableList.copyOf(existing.getMemberTypes());
            this.ignoredChars = existing.getIgnoredChars();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
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

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }

        public Builder memberTypes(List<Type> memberTypes) {
            this.memberTypes = ImmutableList.copyOf(memberTypes);
            return this;
        }

        public Builder memberType(Type memberType) {
            this.memberTypes = ImmutableKit.addToList(memberTypes, memberType);
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


        public UnionTypeDefinition build() {
            return new UnionTypeDefinition(name,
                    directives,
                    memberTypes,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}

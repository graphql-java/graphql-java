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

import java.util.ArrayList;
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
public class EnumTypeDefinition extends AbstractDescribedNode<EnumTypeDefinition> implements
        TypeDefinition<EnumTypeDefinition>, DirectivesContainer<EnumTypeDefinition>, NamedNode<EnumTypeDefinition> {
    private final String name;
    private final ImmutableList<EnumValueDefinition> enumValueDefinitions;
    private final NodeUtil.DirectivesHolder directives;

    public static final String CHILD_ENUM_VALUE_DEFINITIONS = "enumValueDefinitions";
    public static final String CHILD_DIRECTIVES = "directives";

    @Internal
    protected EnumTypeDefinition(String name,
            List<EnumValueDefinition> enumValueDefinitions,
            List<Directive> directives,
            @Nullable Description description,
            @Nullable SourceLocation sourceLocation,
            List<Comment> comments,
            IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.enumValueDefinitions = ImmutableKit.nonNullCopyOf(enumValueDefinitions);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the enum
     */
    public EnumTypeDefinition(String name) {
        this(name, emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public List<EnumValueDefinition> getEnumValueDefinitions() {
        return enumValueDefinitions;
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(enumValueDefinitions);
        result.addAll(directives.getDirectives());
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_ENUM_VALUE_DEFINITIONS, enumValueDefinitions)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public EnumTypeDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .enumValueDefinitions(newChildren.getChildren(CHILD_ENUM_VALUE_DEFINITIONS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES)));
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EnumTypeDefinition that = (EnumTypeDefinition) o;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public EnumTypeDefinition deepCopy() {
        return new EnumTypeDefinition(name,
                assertNotNull(deepCopy(enumValueDefinitions)),
                assertNotNull(deepCopy(directives.getDirectives())),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
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

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Description description;
        private ImmutableList<EnumValueDefinition> enumValueDefinitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(EnumTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.enumValueDefinitions = ImmutableList.copyOf(existing.getEnumValueDefinitions());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
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

        public Builder enumValueDefinitions(List<EnumValueDefinition> enumValueDefinitions) {
            this.enumValueDefinitions = ImmutableList.copyOf(enumValueDefinitions);
            return this;
        }

        public Builder enumValueDefinition(EnumValueDefinition enumValueDefinition) {
            this.enumValueDefinitions = ImmutableKit.addToList(enumValueDefinitions, enumValueDefinition);
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

        public EnumTypeDefinition build() {
            return new EnumTypeDefinition(assertNotNull(name), enumValueDefinitions, directives, description,
                    sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}

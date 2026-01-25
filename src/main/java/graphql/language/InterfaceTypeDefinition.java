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
public class InterfaceTypeDefinition extends AbstractDescribedNode<InterfaceTypeDefinition> implements ImplementingTypeDefinition<InterfaceTypeDefinition>, DirectivesContainer<InterfaceTypeDefinition>, NamedNode<InterfaceTypeDefinition> {

    private final String name;
    private final ImmutableList<Type> implementz;
    private final ImmutableList<FieldDefinition> definitions;
    private final NodeUtil.DirectivesHolder directives;

    public static final String CHILD_IMPLEMENTZ = "implementz";
    public static final String CHILD_DEFINITIONS = "definitions";
    public static final String CHILD_DIRECTIVES = "directives";

    @Internal
    protected InterfaceTypeDefinition(String name,
                                      List<Type> implementz,
                                      List<FieldDefinition> definitions,
                                      List<Directive> directives,
                                      @Nullable Description description,
                                      @Nullable SourceLocation sourceLocation,
                                      List<Comment> comments,
                                      IgnoredChars ignoredChars,
                                      Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.implementz = ImmutableList.copyOf(implementz);
        this.definitions = ImmutableList.copyOf(definitions);
        this.directives = NodeUtil.DirectivesHolder.of(directives);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the interface
     */
    public InterfaceTypeDefinition(String name) {
        this(name, emptyList(), emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    @Override
    public List<Type> getImplements() {
        return implementz;
    }

    @Override
    public List<FieldDefinition> getFieldDefinitions() {
        return definitions;
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
        result.addAll(implementz);
        result.addAll(definitions);
        result.addAll(directives.getDirectives());
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_IMPLEMENTZ, implementz)
                .children(CHILD_DEFINITIONS, definitions)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public InterfaceTypeDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .implementz(newChildren.getChildren(CHILD_IMPLEMENTZ))
                .definitions(newChildren.getChildren(CHILD_DEFINITIONS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
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

        InterfaceTypeDefinition that = (InterfaceTypeDefinition) o;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public InterfaceTypeDefinition deepCopy() {
        return new InterfaceTypeDefinition(name,
                assertNotNull(deepCopy(implementz), "implementz cannot be null"),
                assertNotNull(deepCopy(definitions), "definitions cannot be null"),
                assertNotNull(deepCopy(directives.getDirectives()), "directives cannot be null"),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "InterfaceTypeDefinition{" +
                "name='" + name + '\'' +
                ", implements=" + implementz +
                ", fieldDefinitions=" + definitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInterfaceTypeDefinition(this, context);
    }


    public static Builder newInterfaceTypeDefinition() {
        return new Builder();
    }

    public InterfaceTypeDefinition transform(Consumer<Builder> builderConsumer) {
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
        private ImmutableList<Type> implementz = emptyList();
        private ImmutableList<FieldDefinition> definitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }


        private Builder(InterfaceTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.definitions = ImmutableList.copyOf(existing.getFieldDefinitions());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
            this.implementz = ImmutableList.copyOf(existing.getImplements());
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

        public Builder implementz(List<Type> implementz) {
            this.implementz = ImmutableList.copyOf(implementz);
            return this;
        }

        public Builder implementz(Type implement) {
            this.implementz = ImmutableKit.addToList(implementz, implement);
            return this;
        }


        public Builder definitions(List<FieldDefinition> definitions) {
            this.definitions = ImmutableList.copyOf(definitions);
            return this;
        }

        public Builder definition(FieldDefinition definition) {
            this.definitions = ImmutableKit.addToList(definitions, definition);
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


        public InterfaceTypeDefinition build() {
            return new InterfaceTypeDefinition(name,
                    implementz,
                    definitions,
                    directives,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}

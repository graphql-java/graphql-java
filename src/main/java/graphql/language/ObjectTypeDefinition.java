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
public class ObjectTypeDefinition extends AbstractDescribedNode<ObjectTypeDefinition> implements ImplementingTypeDefinition<ObjectTypeDefinition>, DirectivesContainer<ObjectTypeDefinition>, NamedNode<ObjectTypeDefinition> {
    private final String name;
    private final ImmutableList<Type> implementz;
    private final NodeUtil.DirectivesHolder directives;
    private final ImmutableList<FieldDefinition> fieldDefinitions;

    public static final String CHILD_IMPLEMENTZ = "implementz";
    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";

    @Internal
    protected ObjectTypeDefinition(String name,
                                   List<Type> implementz,
                                   List<Directive> directives,
                                   List<FieldDefinition> fieldDefinitions,
                                   @Nullable Description description,
                                   @Nullable SourceLocation sourceLocation,
                                   List<Comment> comments,
                                   IgnoredChars ignoredChars,
                                   Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.implementz = ImmutableList.copyOf(implementz);
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.fieldDefinitions = ImmutableList.copyOf(fieldDefinitions);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the object type
     */
    public ObjectTypeDefinition(String name) {
        this(name, emptyList(), emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    @Override
    public List<Type> getImplements() {
        return implementz;
    }

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
    public List<FieldDefinition> getFieldDefinitions() {
        return fieldDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(implementz);
        result.addAll(directives.getDirectives());
        result.addAll(fieldDefinitions);
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_IMPLEMENTZ, implementz)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .children(CHILD_FIELD_DEFINITIONS, fieldDefinitions)
                .build();
    }

    @Override
    public ObjectTypeDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder.implementz(newChildren.getChildren(CHILD_IMPLEMENTZ))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .fieldDefinitions(newChildren.getChildren(CHILD_FIELD_DEFINITIONS)));
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ObjectTypeDefinition that = (ObjectTypeDefinition) o;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public ObjectTypeDefinition deepCopy() {
        return new ObjectTypeDefinition(name,
                assertNotNull(deepCopy(implementz), "implementz deepCopy should not return null"),
                assertNotNull(deepCopy(directives.getDirectives()), "directives deepCopy should not return null"),
                assertNotNull(deepCopy(fieldDefinitions), "fieldDefinitions deepCopy should not return null"),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "ObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", implements=" + implementz +
                ", directives=" + directives +
                ", fieldDefinitions=" + fieldDefinitions +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitObjectTypeDefinition(this, context);
    }

    public static Builder newObjectTypeDefinition() {
        return new Builder();
    }

    public ObjectTypeDefinition transform(Consumer<Builder> builderConsumer) {
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
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<FieldDefinition> fieldDefinitions = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(ObjectTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.implementz = ImmutableList.copyOf(existing.getImplements());
            this.fieldDefinitions = ImmutableList.copyOf(existing.getFieldDefinitions());
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

        public Builder implementz(List<Type> implementz) {
            this.implementz = ImmutableList.copyOf(implementz);
            return this;
        }

        public Builder implementz(Type implement) {
            this.implementz = ImmutableKit.addToList(implementz, implement);
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

        public Builder fieldDefinitions(List<FieldDefinition> fieldDefinitions) {
            this.fieldDefinitions = ImmutableList.copyOf(fieldDefinitions);
            return this;
        }

        public Builder fieldDefinition(FieldDefinition fieldDefinition) {
            this.fieldDefinitions = ImmutableKit.addToList(fieldDefinitions, fieldDefinition);
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

        public ObjectTypeDefinition build() {
            return new ObjectTypeDefinition(name,
                    implementz,
                    directives,
                    fieldDefinitions,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}

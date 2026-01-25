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
public class VariableDefinition extends AbstractNode<VariableDefinition> implements DirectivesContainer<VariableDefinition>, NamedNode<VariableDefinition> {

    private final String name;
    private final Type type;
    private final @Nullable Value defaultValue;
    private final NodeUtil.DirectivesHolder directives;

    public static final String CHILD_TYPE = "type";
    public static final String CHILD_DEFAULT_VALUE = "defaultValue";
    public static final String CHILD_DIRECTIVES = "directives";

    @Internal
    protected VariableDefinition(String name,
                                 Type type,
                                 @Nullable Value defaultValue,
                                 List<Directive> directives,
                                 @Nullable SourceLocation sourceLocation,
                                 List<Comment> comments,
                                 IgnoredChars ignoredChars,
                                 Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = NodeUtil.DirectivesHolder.of(directives);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name         of the variable
     * @param type         of the variable
     * @param defaultValue of the variable
     */
    public VariableDefinition(String name,
                              Type type,
                              @Nullable Value defaultValue) {
        this(name, type, defaultValue, emptyList(), null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the variable
     * @param type of the variable
     */
    public VariableDefinition(String name,
                              Type type) {
        this(name, type, null, emptyList(), null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public @Nullable Value getDefaultValue() {
        return defaultValue;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
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
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        if (defaultValue != null) {
            result.add(defaultValue);
        }
        result.addAll(directives.getDirectives());
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_TYPE, type)
                .child(CHILD_DEFAULT_VALUE, defaultValue)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public VariableDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .type(newChildren.getChildOrNull(CHILD_TYPE))
                .defaultValue(newChildren.getChildOrNull(CHILD_DEFAULT_VALUE))
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

        VariableDefinition that = (VariableDefinition) o;

        return Objects.equals(this.name, that.name);

    }

    @Override
    public VariableDefinition deepCopy() {
        return new VariableDefinition(name,
                assertNotNull(deepCopy(type)),
                deepCopy(defaultValue),
                assertNotNull(deepCopy(directives.getDirectives())),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "VariableDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitVariableDefinition(this, context);
    }


    public static Builder newVariableDefinition() {
        return new Builder();
    }

    public static Builder newVariableDefinition(String name) {
        return new Builder().name(name);
    }

    public static Builder newVariableDefinition(String name, Type type) {
        return new Builder().name(name).type(type);
    }

    public static Builder newVariableDefinition(String name, Type type, @Nullable Value defaultValue) {
        return new Builder().name(name).type(type).defaultValue(defaultValue);
    }

    public VariableDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private ImmutableList<Comment> comments = emptyList();
        private Type type;
        private Value defaultValue;
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(VariableDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.type = existing.getType();
            this.defaultValue = existing.getDefaultValue();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Value defaultValue) {
            this.defaultValue = defaultValue;
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

        public VariableDefinition build() {
            return new VariableDefinition(
                    name,
                    type,
                    defaultValue,
                    directives,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}

package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
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
public class FieldDefinition extends AbstractDescribedNode<FieldDefinition>
        implements DirectivesContainer<FieldDefinition>, NamedNode<FieldDefinition> {
    private final String name;
    private final @Nullable Type type;
    private final ImmutableList<InputValueDefinition> inputValueDefinitions;
    private final NodeUtil.DirectivesHolder directives;

    public static final String CHILD_TYPE = "type";
    public static final String CHILD_INPUT_VALUE_DEFINITION = "inputValueDefinition";
    public static final String CHILD_DIRECTIVES = "directives";

    @Internal
    protected FieldDefinition(String name,
            @Nullable Type type,
            List<InputValueDefinition> inputValueDefinitions,
            List<Directive> directives,
            @Nullable Description description,
            @Nullable SourceLocation sourceLocation,
            List<Comment> comments,
            IgnoredChars ignoredChars,
            Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.type = type;
        this.inputValueDefinitions = ImmutableList.copyOf(inputValueDefinitions);
        this.directives = NodeUtil.DirectivesHolder.of(directives);
    }

    public FieldDefinition(String name,
            Type type) {
        this(name, type, emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public Type getType() {
        return assertNotNull(type, () -> "type cannot be null");
    }

    @Override
    public String getName() {
        return name;
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
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
        result.addAll(inputValueDefinitions);
        result.addAll(directives.getDirectives());
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_TYPE, type)
                .children(CHILD_INPUT_VALUE_DEFINITION, inputValueDefinitions)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public FieldDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .type(newChildren.getChildOrNull(CHILD_TYPE))
                .inputValueDefinitions(newChildren.getChildren(CHILD_INPUT_VALUE_DEFINITION))
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

        FieldDefinition that = (FieldDefinition) o;

        return Objects.equals(this.name, that.name);
    }

    @Override
    public FieldDefinition deepCopy() {
        return new FieldDefinition(name,
                deepCopy(type),
                assertNotNull(deepCopy(inputValueDefinitions)),
                assertNotNull(deepCopy(directives.getDirectives())),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFieldDefinition(this, context);
    }

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    public FieldDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeDirectivesBuilder {
        private @Nullable SourceLocation sourceLocation;
        private @Nullable String name;
        private ImmutableList<Comment> comments = emptyList();
        private @Nullable Type type;
        private @Nullable Description description;
        private ImmutableList<InputValueDefinition> inputValueDefinitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(FieldDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.name = existing.getName();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.type = existing.getType();
            this.description = existing.getDescription();
            this.inputValueDefinitions = ImmutableList.copyOf(existing.getInputValueDefinitions());
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder sourceLocation(@Nullable SourceLocation sourceLocation) {
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

        public Builder type(@Nullable Type type) {
            this.type = type;
            return this;
        }

        public Builder description(@Nullable Description description) {
            this.description = description;
            return this;
        }

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = ImmutableList.copyOf(inputValueDefinitions);
            return this;
        }

        public Builder inputValueDefinition(InputValueDefinition inputValueDefinition) {
            this.inputValueDefinitions = ImmutableKit.addToList(inputValueDefinitions, inputValueDefinition);
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

        public FieldDefinition build() {
            return new FieldDefinition(assertNotNull(name), type, inputValueDefinitions, directives, description,
                    sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
